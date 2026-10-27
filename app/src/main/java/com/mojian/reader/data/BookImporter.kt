package com.mojian.reader.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.abs

internal data class ImportedBook(
    val book: Book,
    val chapters: List<Chapter>,
    val sourceBytes: ByteArray? = null,
    val coverBytes: ByteArray? = null
)

internal class BookImporter(private val context: Context) {
    private val supported = setOf("epub", "txt", "html", "htm", "md", "markdown", "docx", "pdf")

    fun import(uri: Uri): ImportedBook {
        val resolver = context.contentResolver
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "未命名书籍"

        val extension = displayName.substringAfterLast('.', "").lowercase()
        if (extension !in supported) {
            throw IllegalArgumentException("暂不支持 .$extension 格式")
        }

        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("文件超过 48 MB，请先在外部工具中压缩或拆分")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("无法读取这个文件")

        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "未命名书籍" }
        val parsed = when (extension) {
            "epub" -> parseEpub(bytes, fallbackTitle)
            "txt" -> parsePlainText(readTextSmart(bytes), fallbackTitle)
            "md", "markdown" -> parseMarkdown(readTextSmart(bytes), fallbackTitle)
            "html", "htm" -> parseHtml(bytes, fallbackTitle)
            "docx" -> parseDocx(bytes, fallbackTitle)
            "pdf" -> parsePdf(bytes, fallbackTitle)
            else -> error("Unsupported format")
        }

        val format = when (extension) {
            "epub" -> BookFormat.EPUB
            "txt" -> BookFormat.TXT
            "html", "htm" -> BookFormat.HTML
            "md", "markdown" -> BookFormat.MARKDOWN
            "docx" -> BookFormat.DOCX
            else -> BookFormat.PDF
        }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val wordCount = parsed.chapters.sumOf { chapter ->
            chapter.content.count { !it.isWhitespace() }
        }
        val accentIndex = abs(displayName.hashCode()) % 6

        return ImportedBook(
            book = Book(
                id = id,
                title = parsed.title.ifBlank { fallbackTitle },
                author = parsed.author.ifBlank { "未知作者" },
                format = format,
                sourceName = displayName,
                category = defaultCategory(format),
                addedAt = now,
                chapterCount = parsed.chapters.size,
                wordCount = wordCount,
                accentIndex = accentIndex
            ),
            chapters = parsed.chapters,
            sourceBytes = if (format == BookFormat.PDF) bytes else null,
            coverBytes = parsed.coverBytes
        )
    }

    private fun parsePlainText(raw: String, fallbackTitle: String): ParsedBook {
        val normalized = ContentCleaner.clean(normalizeText(raw))
        if (normalized.isBlank()) throw IllegalArgumentException("文件里没有可读取的正文")
        val detectedAuthor = Regex("""(?m)^\s*作者\s*[:：]\s*(.{1,40})$""")
            .find(normalized.take(5000))
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()
        val title = Regex("""(?m)^\s*(?:书名|作品名)\s*[:：]\s*(.{1,60})$""")
            .find(normalized.take(3000))
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
        return ParsedBook(title, detectedAuthor, splitByChapters(normalized))
    }

    private fun parseMarkdown(raw: String, fallbackTitle: String): ParsedBook {
        val normalized = normalizeText(raw)
        val heading = Regex("""(?m)^#{1,3}\s+(.+)$""")
        val matches = heading.findAll(normalized).toList()
        val chapters = if (matches.size >= 2) {
            matches.mapIndexed { index, match ->
                val start = match.range.last + 1
                val end = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
                Chapter(
                    index = index,
                    title = match.groupValues[1].trim(),
                    content = normalized.substring(start, end).trim()
                )
            }.filter { it.content.isNotBlank() }
        } else {
            splitByChapters(normalized)
        }
        return ParsedBook(fallbackTitle, "", chapters.ifEmpty { fallbackChapters(normalized) })
    }

    private fun parseHtml(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        val title = document.selectFirst("title")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
        val blocks = document.body()
            .select("h1, h2, h3, p, li, blockquote")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val text = blocks.joinToString("\n\n").ifBlank { document.body().text() }
        return ParsedBook(title, "", splitByChapters(ContentCleaner.clean(normalizeText(text))))
    }

    private fun parseDocx(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val xml = unzip(bytes)["word/document.xml"]
            ?: throw IllegalArgumentException("DOCX 中缺少正文文件")
        val source = String(xml, StandardCharsets.UTF_8)
            .replace("</w:p>", "\n")
            .replace("<w:tab/>", "\t")
            .replace("<w:br/>", "\n")
        val text = Jsoup.parse(source, "", Parser.xmlParser()).wholeText()
        val normalized = ContentCleaner.clean(normalizeText(text))
        if (normalized.isBlank()) throw IllegalArgumentException("DOCX 中没有可读取的文字")
        return ParsedBook(fallbackTitle, "", splitByChapters(normalized))
    }

    private fun parsePdf(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val pageCount = try {
            context.cacheDir.resolve("inspect-${UUID.randomUUID()}.pdf").also {
                it.writeBytes(bytes)
            }.let { file ->
                try {
                    android.os.ParcelFileDescriptor.open(
                        file,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    ).use { descriptor ->
                        PdfRenderer(descriptor).use { it.pageCount }
                    }
                } finally {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            throw IllegalArgumentException("PDF 无法打开，文件可能已损坏或加密")
        }
        if (pageCount <= 0) throw IllegalArgumentException("PDF 没有可显示的页面")
        return ParsedBook(
            title = fallbackTitle,
            author = "",
            chapters = List(pageCount) { index ->
                Chapter(index, "第 ${index + 1} 页", "")
            }
        )
    }

    private fun parseEpub(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val entries = unzip(bytes)
        val container = entries["META-INF/container.xml"]
            ?.toString(StandardCharsets.UTF_8)
            ?: throw IllegalArgumentException("EPUB 缺少 container.xml")
        val containerDoc = Jsoup.parse(container, "", Parser.xmlParser())
        val opfPath = containerDoc.selectFirst("rootfile")?.attr("full-path")
            ?: throw IllegalArgumentException("EPUB 缺少内容清单")
        val opfBytes = entries[opfPath]
            ?: throw IllegalArgumentException("EPUB 内容清单不存在")
        val baseDir = opfPath.substringBeforeLast('/', "")
        val opf = Jsoup.parse(opfBytes.toString(StandardCharsets.UTF_8), "", Parser.xmlParser())
        val title = opf.getElementsByTag("dc:title").firstOrNull()?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
        val author = opf.getElementsByTag("dc:creator").firstOrNull()?.text()?.trim().orEmpty()
        val manifest = opf.select("manifest > item").associateBy { it.attr("id") }
        val spineIds = opf.select("spine > itemref").map { it.attr("idref") }
        val chapters = spineIds.mapIndexedNotNull { index, id ->
            val item = manifest[id] ?: return@mapIndexedNotNull null
            if (!item.attr("media-type").contains("html", ignoreCase = true)) {
                return@mapIndexedNotNull null
            }
            val href = resolvePath(baseDir, item.attr("href"))
            val contentBytes = entries[href] ?: return@mapIndexedNotNull null
            val document = Jsoup.parse(ByteArrayInputStream(contentBytes), null, "")
            val heading = document.selectFirst("h1, h2, h3, title")?.text()?.trim()
            val blocks = document.body()
                .select("h1, h2, h3, p, li")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val bodyText = blocks.joinToString("\n\n").ifBlank {
                document.body().text().trim()
            }
            val cleanedBody = ContentCleaner.clean(bodyText)
            if (cleanedBody.isBlank()) return@mapIndexedNotNull null
            Chapter(
                index = 0,
                title = heading?.takeIf { it.isNotBlank() } ?: "第 ${index + 1} 章",
                content = cleanedBody
            )
        }.mapIndexed { index, chapter -> chapter.copy(index = index) }
        if (chapters.isEmpty()) {
            throw IllegalArgumentException("EPUB 中没有可读取的章节")
        }

        val coverId = opf.selectFirst("meta[name=cover]")?.attr("content")
        val coverItem = coverId?.let { manifest[it] }
            ?: manifest.values.firstOrNull {
                it.attr("properties").split(' ').contains("cover-image")
            }
        val coverBytes = coverItem?.let {
            entries[resolvePath(baseDir, it.attr("href"))]
        }
        return ParsedBook(title, author, chapters, coverBytes)
    }

    private fun defaultCategory(format: BookFormat): String = when (format) {
        BookFormat.PDF -> "资料"
        BookFormat.DOCX -> "资料"
        else -> "未分类"
    }

    private fun readTextSmart(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        val utf8 = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        if (utf8 != null && !utf8.contains('\uFFFD')) return utf8
        return runCatching { String(bytes, charset("GB18030")) }
            .getOrElse { String(bytes, StandardCharsets.UTF_8) }
    }

    private fun normalizeText(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    private fun splitByChapters(text: String): List<Chapter> {
        val chapterPattern = TxtTocRuleLibrary.firstMatching(text)?.compiled
        if (chapterPattern == null) return fallbackChapters(text)
        val matches = chapterPattern.findAll(text).toList()
        if (matches.size >= 2) {
            val result = mutableListOf<Chapter>()
            val introEnd = matches.first().range.first
            if (introEnd > 220) {
                result += Chapter(0, "前言", text.substring(0, introEnd).trim())
            }
            matches.forEachIndexed { index, match ->
                val start = match.range.last + 1
                val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
                val content = text.substring(start, end).trim()
                if (content.isNotBlank()) {
                    result += Chapter(result.size, match.value.trim(), content)
                }
            }
            if (result.isNotEmpty()) return result
        }
        return fallbackChapters(text)
    }

    private fun fallbackChapters(text: String): List<Chapter> {
        if (text.length <= 9000) {
            return listOf(Chapter(0, "正文", text))
        }
        val maxChunks = 1500
        val chunkSize = maxOf(8000, (text.length / maxChunks) + 1)
        val result = mutableListOf<Chapter>()
        var offset = 0
        while (offset < text.length) {
            val target = (offset + chunkSize).coerceAtMost(text.length)
            val newline = text.indexOf('\n', target).takeIf { it in (target + 1)..(target + 700) }
            val end = newline ?: target
            result += Chapter(
                result.size,
                "第 ${result.size + 1} 节",
                text.substring(offset, end).trim()
            )
            offset = end
        }
        return result
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        if (total > MAX_ARCHIVE_TOTAL_BYTES) {
                            throw IllegalArgumentException("压缩包解压后过大，无法在内存中解析")
                        }
                        if (output.size() > MAX_ENTRY_BYTES) {
                            throw IllegalArgumentException("压缩包内有异常的大文件")
                        }
                    }
                    entries[entry.name] = output.toByteArray()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun resolvePath(base: String, href: String): String {
        val decoded = Uri.decode(href.substringBefore('#')).replace('\\', '/')
        val parts = (if (base.isBlank()) decoded else "$base/$decoded").split('/')
        val stack = ArrayDeque<String>()
        parts.forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private data class ParsedBook(
        val title: String,
        val author: String,
        val chapters: List<Chapter>,
        val coverBytes: ByteArray? = null
    )

    private companion object {
        const val MAX_IMPORT_BYTES = 48 * 1024 * 1024
        const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
        const val MAX_ARCHIVE_TOTAL_BYTES = 64 * 1024 * 1024
    }
}
