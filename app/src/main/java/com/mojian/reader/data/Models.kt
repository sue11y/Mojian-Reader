package com.mojian.reader.data

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.roundToInt

enum class BookFormat(val label: String) {
    EPUB("EPUB"),
    TXT("TXT"),
    HTML("HTML"),
    MARKDOWN("MD"),
    DOCX("DOCX"),
    PDF("PDF")
}

enum class ReadingStatus(val label: String) {
    UNREAD("未读"),
    READING("在读"),
    FINISHED("读完")
}

data class Bookmark(
    val id: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val preview: String,
    val createdAt: Long
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val format: BookFormat,
    val sourceName: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val addedAt: Long,
    val lastOpenedAt: Long = 0L,
    val chapterIndex: Int = 0,
    val chapterProgress: Float = 0f,
    val chapterCount: Int = 0,
    val wordCount: Int = 0,
    val accentIndex: Int = 0,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val bookmarks: List<Bookmark> = emptyList(),
    val coverPath: String? = null
) {
    val progress: Float
        get() {
            if (chapterCount <= 0) return 0f
            return ((chapterIndex + chapterProgress.coerceIn(0f, 1f)) / chapterCount)
                .coerceIn(0f, 1f)
        }

    val progressPercent: Int
        get() = (progress * 100).roundToInt()
}

data class Chapter(
    val index: Int,
    val title: String,
    val content: String
)

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val categories: List<String> = listOf("古典文学", "诗词", "随笔"),
    val isReady: Boolean = false,
    val importMessage: String? = null,
    val importing: ImportProgress? = null
)

data class ImportProgress(
    val current: Int,
    val total: Int,
    val fileName: String
)

data class ImportOutcome(
    val imported: Int,
    val failures: List<String>
)

data class SearchHit(
    val book: Book,
    val chapterIndex: Int? = null,
    val chapterTitle: String? = null,
    val snippet: String? = null,
    val kind: SearchKind = SearchKind.METADATA
)

enum class SearchKind {
    METADATA,
    CONTENT
}

enum class ReaderThemeStyle(val label: String) {
    PAPER("纸白"),
    SAGE("青笺"),
    NIGHT("夜色"),
    YELLOW("羊皮"),
    GREEN("竹青"),
    PINK("桃粉"),
    BLUE("月蓝"),
    AMOLED("A屏黑")
}

data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color
)

fun readerPalette(theme: ReaderThemeStyle): ReaderPalette = when (theme) {
    ReaderThemeStyle.PAPER -> ReaderPalette(
        background = Color(0xFFF4EEDF),
        surface = Color(0xFFFBF7ED),
        text = Color(0xFF29322E),
        muted = Color(0xFF778079),
        rule = Color(0xFFDDD5C6),
        accent = Color(0xFF173E35)
    )
    ReaderThemeStyle.SAGE -> ReaderPalette(
        background = Color(0xFFE4ECE5),
        surface = Color(0xFFF0F4EF),
        text = Color(0xFF26342D),
        muted = Color(0xFF6C7B72),
        rule = Color(0xFFC9D5CB),
        accent = Color(0xFF3D6955)
    )
    ReaderThemeStyle.NIGHT -> ReaderPalette(
        background = Color(0xFF171C1A),
        surface = Color(0xFF222926),
        text = Color(0xFFE9E6DC),
        muted = Color(0xFF909B94),
        rule = Color(0xFF323B37),
        accent = Color(0xFFE0A077)
    )
    ReaderThemeStyle.YELLOW -> ReaderPalette(
        background = Color(0xFFF0E4CB),
        surface = Color(0xFFF7EEDD),
        text = Color(0xFF4F3A20),
        muted = Color(0xFF8A7559),
        rule = Color(0xFFD9C6A2),
        accent = Color(0xFF9A5F23)
    )
    ReaderThemeStyle.GREEN -> ReaderPalette(
        background = Color(0xFFDFEDD9),
        surface = Color(0xFFECF4E8),
        text = Color(0xFF2E3D2E),
        muted = Color(0xFF687A66),
        rule = Color(0xFFC3D6BD),
        accent = Color(0xFF52724D)
    )
    ReaderThemeStyle.PINK -> ReaderPalette(
        background = Color(0xFFF6E2DD),
        surface = Color(0xFFFBEEE9),
        text = Color(0xFF51302F),
        muted = Color(0xFF8D6A67),
        rule = Color(0xFFE5C8C1),
        accent = Color(0xFFA6524D)
    )
    ReaderThemeStyle.BLUE -> ReaderPalette(
        background = Color(0xFFE0E8EE),
        surface = Color(0xFFEEF3F6),
        text = Color(0xFF2E3943),
        muted = Color(0xFF687780),
        rule = Color(0xFFC6D3DB),
        accent = Color(0xFF486B83)
    )
    ReaderThemeStyle.AMOLED -> ReaderPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF111111),
        text = Color(0xFFE9E9E4),
        muted = Color(0xFF8C8C86),
        rule = Color(0xFF2A2A2A),
        accent = Color(0xFFD8D8D1)
    )
}

data class ReaderPreferences(
    val theme: ReaderThemeStyle = ReaderThemeStyle.PAPER,
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.75f,
    val useSerif: Boolean = true
)

internal fun File.safeName(): String = name.substringBeforeLast('.').ifBlank { name }
