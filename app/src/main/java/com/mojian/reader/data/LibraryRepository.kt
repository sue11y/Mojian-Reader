package com.mojian.reader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

class LibraryRepository(private val context: Context) {
    private val libraryDir = context.filesDir.resolve("library")
    private val libraryFile = libraryDir.resolve("library.json")
    private val mutex = Mutex()
    private val importer = BookImporter(context)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    suspend fun initialize() {
        if (_state.value.isReady) return
        withContext(Dispatchers.IO) {
            libraryDir.mkdirs()
            if (libraryFile.exists()) {
                runCatching { readLibrary() }
                    .onSuccess { snapshot ->
                        _state.update {
                            it.copy(
                                books = snapshot.first.sortedByDescending { book -> book.lastOpenedAt },
                                categories = snapshot.second,
                                isReady = true
                            )
                        }
                    }
                    .onFailure {
                        seedSamples()
                    }
            } else {
                seedSamples()
            }
            if (!_state.value.isReady) {
                _state.update { it.copy(isReady = true) }
            }
        }
    }

    suspend fun importBooks(uris: List<Uri>): ImportOutcome = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ImportOutcome(0, emptyList())
        val failures = mutableListOf<String>()
        var imported = 0
        uris.forEachIndexed { index, uri ->
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "文件 ${index + 1}"
            _state.update {
                it.copy(
                    importing = ImportProgress(index + 1, uris.size, displayName)
                )
            }
            try {
                val item = importer.import(uri)
                persistImportedBook(item)
                imported += 1
            } catch (error: OutOfMemoryError) {
                failures += "$displayName：文件过大，内存不足"
            } catch (error: Exception) {
                failures += "$displayName：${error.message ?: "导入失败"}"
            }
            coroutineContext.ensureActive()
        }
        _state.update {
            it.copy(
                importing = null,
                books = it.books.sortedByDescending { book -> book.lastOpenedAt }
            )
        }
        ImportOutcome(imported, failures)
    }

    suspend fun loadChapters(bookId: String): List<Chapter> = withContext(Dispatchers.IO) {
        val bookDir = bookDirectory(bookId)
        val descriptors = readChapterDescriptors(bookDir)
        descriptors.mapIndexed { index, descriptor ->
            Chapter(
                index = index,
                title = descriptor.title,
                content = bookDir.resolve("chapters").resolve(descriptor.fileName)
                    .takeIf { it.exists() }
                    ?.readText()
                    .orEmpty()
            )
        }
    }

    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.length < 1) return@withContext emptyList()
        val needle = normalized.lowercase()
        val hits = mutableListOf<SearchHit>()
        val books = _state.value.books

        books.forEach { book ->
            coroutineContext.ensureActive()
            val metadata = listOf(
                book.title,
                book.author,
                book.category,
                book.format.label,
                book.tags.joinToString(" ")
            ).joinToString(" ").lowercase()
            if (needle in metadata) {
                hits += SearchHit(book = book)
            }
            if (book.format == BookFormat.PDF) return@forEach

            val bookDir = bookDirectory(book.id)
            val descriptors = readChapterDescriptors(bookDir)
            var matchesInBook = 0
            for (descriptor in descriptors) {
                coroutineContext.ensureActive()
                if (matchesInBook >= 3 || hits.size >= 80) break
                val content = bookDir.resolve("chapters")
                    .resolve(descriptor.fileName)
                    .takeIf { it.exists() }
                    ?.readText()
                    ?: continue
                val lower = content.lowercase()
                val position = lower.indexOf(needle)
                if (position >= 0) {
                    hits += SearchHit(
                        book = book,
                        chapterIndex = descriptor.index,
                        chapterTitle = descriptor.title,
                        snippet = makeSnippet(content, position, normalized.length),
                        kind = SearchKind.CONTENT
                    )
                    matchesInBook += 1
                }
            }
        }
        hits
    }

    suspend fun openBook(bookId: String): Book? = withContext(Dispatchers.IO) {
        val current = _state.value.books.firstOrNull { it.id == bookId } ?: return@withContext null
        val updated = current.copy(
            lastOpenedAt = System.currentTimeMillis(),
            status = if (current.progress >= 0.995f) {
                ReadingStatus.FINISHED
            } else {
                ReadingStatus.READING
            }
        )
        replaceBook(updated)
        updated
    }

    suspend fun updateProgress(
        bookId: String,
        chapterIndex: Int,
        chapterProgress: Float = 0f
    ) = withContext(Dispatchers.IO) {
        val current = _state.value.books.firstOrNull { it.id == bookId } ?: return@withContext
        val completed = current.chapterCount > 0 &&
            chapterIndex >= current.chapterCount - 1 &&
            chapterProgress >= 0.995f
        replaceBook(
            current.copy(
                chapterIndex = chapterIndex.coerceAtLeast(0),
                chapterProgress = chapterProgress.coerceIn(0f, 1f),
                lastOpenedAt = System.currentTimeMillis(),
                status = if (completed) ReadingStatus.FINISHED else ReadingStatus.READING
            )
        )
    }

    suspend fun toggleBookmark(
        bookId: String,
        chapterIndex: Int,
        chapterTitle: String,
        preview: String
    ) = withContext(Dispatchers.IO) {
        val current = _state.value.books.firstOrNull { it.id == bookId } ?: return@withContext
        val existing = current.bookmarks.firstOrNull { it.chapterIndex == chapterIndex }
        val updatedBookmarks = if (existing != null) {
            current.bookmarks.filterNot { it.chapterIndex == chapterIndex }
        } else {
            current.bookmarks + Bookmark(
                id = UUID.randomUUID().toString(),
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                preview = preview.take(90),
                createdAt = System.currentTimeMillis()
            )
        }
        replaceBook(current.copy(bookmarks = updatedBookmarks))
    }

    suspend fun setCategory(bookId: String, category: String) = withContext(Dispatchers.IO) {
        val current = _state.value.books.firstOrNull { it.id == bookId } ?: return@withContext
        replaceBook(current.copy(category = category))
    }

    suspend fun setReadingStatus(bookId: String, status: ReadingStatus) =
        withContext(Dispatchers.IO) {
            val current = _state.value.books.firstOrNull { it.id == bookId } ?: return@withContext
            replaceBook(
                current.copy(
                    status = status,
                    chapterIndex = if (status == ReadingStatus.FINISHED) {
                        (current.chapterCount - 1).coerceAtLeast(0)
                    } else {
                        current.chapterIndex
                    },
                    chapterProgress = if (status == ReadingStatus.FINISHED) 1f else current.chapterProgress
                )
            )
        }

    suspend fun addCategory(name: String): Boolean = withContext(Dispatchers.IO) {
        val clean = name.trim().take(12)
        if (clean.isBlank()) return@withContext false
        if (_state.value.categories.any { it.equals(clean, ignoreCase = true) }) {
            return@withContext false
        }
        val categories = _state.value.categories + clean
        _state.update { it.copy(categories = categories) }
        saveLibrary()
        true
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        val updated = _state.value.books.filterNot { it.id == bookId }
        bookDirectory(bookId).deleteRecursively()
        _state.update { it.copy(books = updated) }
        saveLibrary()
    }

    suspend fun exportBackup(target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(target)?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    libraryDir.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val relative = file.relativeTo(libraryDir).invariantSeparatorsPath
                            zip.putNextEntry(ZipEntry(relative))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                }
            } ?: error("无法创建备份文件")
        }.isSuccess
    }

    suspend fun importRemoteBook(remote: RemoteBook, chapters: List<Chapter>): Boolean =
        withContext(Dispatchers.IO) {
            if (chapters.isEmpty()) return@withContext false
            val now = System.currentTimeMillis()
            val wordCount = chapters.sumOf { chapter ->
                chapter.content.count { !it.isWhitespace() }
            }
            val book = Book(
                id = UUID.randomUUID().toString(),
                title = remote.title.ifBlank { "未命名书籍" },
                author = remote.author.ifBlank { "未知作者" },
                format = BookFormat.TXT,
                sourceName = remote.source.name,
                category = remote.source.group.ifBlank { "书源" },
                addedAt = now,
                chapterCount = chapters.size,
                wordCount = wordCount,
                accentIndex = abs(remote.title.hashCode()) % 6
            )
            persistImportedBook(ImportedBook(book = book, chapters = chapters))
            true
        }

    private fun persistImportedBook(item: ImportedBook) {
        val bookDir = bookDirectory(item.book.id)
        bookDir.resolve("chapters").mkdirs()
        var coverPath: String? = null
        item.coverBytes?.let { bytes ->
            val coverFile = bookDir.resolve("cover.image")
            coverFile.writeBytes(bytes)
            coverPath = coverFile.absolutePath
        }
        if (item.sourceBytes != null) {
            bookDir.resolve("source.pdf").writeBytes(item.sourceBytes)
        }
        item.chapters.forEach { chapter ->
            val fileName = "%05d.txt".format(chapter.index)
            bookDir.resolve("chapters").resolve(fileName).writeText(chapter.content)
        }
        writeChapterDescriptors(bookDir, item.chapters)
        val storedBook = item.book.copy(coverPath = coverPath)
        _state.update { it.copy(books = it.books + storedBook) }
        saveLibrary()
    }

    private fun replaceBook(book: Book) {
        _state.update { current ->
            current.copy(
                books = current.books.map { if (it.id == book.id) book else it }
                    .sortedWith(
                        compareByDescending<Book> { it.lastOpenedAt }
                            .thenByDescending { it.addedAt }
                    )
            )
        }
        saveLibrary()
    }

    private fun saveLibrary() {
        libraryDir.mkdirs()
        val payload = JSONObject().apply {
            put("version", 1)
            put("categories", JSONArray(_state.value.categories))
            put("books", JSONArray().apply {
                _state.value.books.forEach { book -> put(bookToJson(book)) }
            })
        }
        val temp = libraryDir.resolve("library.json.tmp")
        temp.writeText(payload.toString())
        if (libraryFile.exists() && !libraryFile.delete()) {
            libraryFile.writeText(payload.toString())
        } else if (!temp.renameTo(libraryFile)) {
            libraryFile.writeText(payload.toString())
        }
        if (temp.exists()) temp.delete()
    }

    private fun readLibrary(): Pair<List<Book>, List<String>> {
        val root = JSONObject(libraryFile.readText())
        val categories = root.optJSONArray("categories")
            ?.let { array -> List(array.length()) { array.getString(it) } }
            ?.filter { it.isNotBlank() }
            .orEmpty()
            .ifEmpty { listOf("未分类") }
        val books = root.optJSONArray("books")
            ?.let { array ->
                List(array.length()) { index -> jsonToBook(array.getJSONObject(index)) }
            }
            .orEmpty()
        return books to categories
    }

    private fun bookToJson(book: Book): JSONObject = JSONObject().apply {
        put("id", book.id)
        put("title", book.title)
        put("author", book.author)
        put("format", book.format.name)
        put("sourceName", book.sourceName)
        put("category", book.category)
        put("tags", JSONArray(book.tags))
        put("addedAt", book.addedAt)
        put("lastOpenedAt", book.lastOpenedAt)
        put("chapterIndex", book.chapterIndex)
        put("chapterProgress", book.chapterProgress.toDouble())
        put("chapterCount", book.chapterCount)
        put("wordCount", book.wordCount)
        put("accentIndex", book.accentIndex)
        put("status", book.status.name)
        put("coverPath", book.coverPath)
        put("bookmarks", JSONArray().apply {
            book.bookmarks.forEach { bookmark ->
                put(JSONObject().apply {
                    put("id", bookmark.id)
                    put("chapterIndex", bookmark.chapterIndex)
                    put("chapterTitle", bookmark.chapterTitle)
                    put("preview", bookmark.preview)
                    put("createdAt", bookmark.createdAt)
                })
            }
        })
    }

    private fun jsonToBook(json: JSONObject): Book {
        val tags = json.optJSONArray("tags")
            ?.let { array -> List(array.length()) { array.getString(it) } }
            .orEmpty()
        val bookmarks = json.optJSONArray("bookmarks")
            ?.let { array ->
                List(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    Bookmark(
                        id = item.getString("id"),
                        chapterIndex = item.getInt("chapterIndex"),
                        chapterTitle = item.getString("chapterTitle"),
                        preview = item.optString("preview"),
                        createdAt = item.optLong("createdAt")
                    )
                }
            }
            .orEmpty()
        return Book(
            id = json.getString("id"),
            title = json.getString("title"),
            author = json.optString("author", "未知作者"),
            format = runCatching { BookFormat.valueOf(json.getString("format")) }
                .getOrDefault(BookFormat.TXT),
            sourceName = json.optString("sourceName"),
            category = json.optString("category", "未分类"),
            tags = tags,
            addedAt = json.optLong("addedAt"),
            lastOpenedAt = json.optLong("lastOpenedAt"),
            chapterIndex = json.optInt("chapterIndex"),
            chapterProgress = json.optDouble("chapterProgress", 0.0).toFloat(),
            chapterCount = json.optInt("chapterCount"),
            wordCount = json.optInt("wordCount"),
            accentIndex = json.optInt("accentIndex"),
            status = runCatching { ReadingStatus.valueOf(json.optString("status")) }
                .getOrDefault(ReadingStatus.UNREAD),
            bookmarks = bookmarks,
            coverPath = json.optString("coverPath").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun writeChapterDescriptors(bookDir: File, chapters: List<Chapter>) {
        val array = JSONArray()
        chapters.forEach { chapter ->
            array.put(JSONObject().apply {
                put("index", chapter.index)
                put("title", chapter.title)
                put("fileName", "%05d.txt".format(chapter.index))
            })
        }
        bookDir.resolve("chapters.json").writeText(array.toString())
    }

    private fun readChapterDescriptors(bookDir: File): List<ChapterDescriptor> {
        val file = bookDir.resolve("chapters.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ChapterDescriptor(
                    index = item.optInt("index", index),
                    title = item.optString("title", "第 ${index + 1} 章"),
                    fileName = item.getString("fileName")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun makeSnippet(content: String, position: Int, queryLength: Int): String {
        val start = (position - 34).coerceAtLeast(0)
        val end = (position + queryLength + 62).coerceAtMost(content.length)
        return buildString {
            if (start > 0) append("…")
            append(content.substring(start, end).replace('\n', ' ').replace(Regex("""\s+"""), " "))
            if (end < content.length) append("…")
        }
    }

    private fun bookDirectory(bookId: String): File {
        require(bookId.matches(Regex("""[a-zA-Z0-9-]{8,64}"""))) { "Invalid book id" }
        return libraryDir.resolve(bookId)
    }

    private fun seedSamples() {
        if (_state.value.books.isNotEmpty()) return
        seedBook(
            title = "桃花源记",
            author = "陶渊明",
            category = "古典文学",
            accent = 0,
            chapters = listOf(
                Chapter(0, "桃花源记", SAMPLE_PEACH_BLOSSOM.trimIndent())
            )
        )
        seedBook(
            title = "岳阳楼记",
            author = "范仲淹",
            category = "古典文学",
            accent = 1,
            chapters = listOf(
                Chapter(0, "岳阳楼记", SAMPLE_YUEYANG.trimIndent())
            )
        )
        seedBook(
            title = "人间词话·选读",
            author = "王国维",
            category = "诗词",
            accent = 2,
            progressChapter = 1,
            progressFraction = 0.38f,
            chapters = listOf(
                Chapter(0, "境界", SAMPLE_JINGJIE.trimIndent()),
                Chapter(1, "有我之境", SAMPLE_YOUWO.trimIndent()),
                Chapter(2, "隔与不隔", SAMPLE_GEBUGE.trimIndent())
            )
        )
        val categories = setOf("古典文学", "诗词", "随笔", "资料", "未分类") +
            _state.value.books.map { it.category }
        _state.update { it.copy(categories = categories.toList().sorted()) }
        saveLibrary()
    }

    private fun seedBook(
        title: String,
        author: String,
        category: String,
        accent: Int,
        chapters: List<Chapter>,
        progressChapter: Int = 0,
        progressFraction: Float = 0f
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val wordCount = chapters.sumOf { chapter -> chapter.content.count { !it.isWhitespace() } }
        val book = Book(
            id = id,
            title = title,
            author = author,
            format = BookFormat.TXT,
            sourceName = "$title.txt",
            category = category,
            addedAt = now - abs(title.hashCode() % 86400000),
            lastOpenedAt = if (progressChapter >= 0) now - 1_800_000 else 0L,
            chapterIndex = progressChapter,
            chapterProgress = progressFraction,
            chapterCount = chapters.size,
            wordCount = wordCount,
            accentIndex = accent,
            status = if (progressChapter >= 0) ReadingStatus.READING else ReadingStatus.UNREAD
        )
        persistImportedBook(ImportedBook(book = book, chapters = chapters))
    }

    private data class ChapterDescriptor(
        val index: Int,
        val title: String,
        val fileName: String
    )

    private companion object {
        const val SAMPLE_PEACH_BLOSSOM = """
            晋太元中，武陵人捕鱼为业。缘溪行，忘路之远近。忽逢桃花林，夹岸数百步，中无杂树，芳草鲜美，落英缤纷。渔人甚异之，复前行，欲穷其林。

            林尽水源，便得一山，山有小口，仿佛若有光。便舍船，从口入。初极狭，才通人。复行数十步，豁然开朗。土地平旷，屋舍俨然，有良田、美池、桑竹之属。阡陌交通，鸡犬相闻。其中往来种作，男女衣着，悉如外人。黄发垂髫，并怡然自乐。

            见渔人，乃大惊，问所从来。具答之。便要还家，设酒杀鸡作食。村中闻有此人，咸来问讯。自云先世避秦时乱，率妻子邑人来此绝境，不复出焉，遂与外人间隔。问今是何世，乃不知有汉，无论魏晋。此人一一为具言所闻，皆叹惋。余人各复延至其家，皆出酒食。停数日，辞去。此中人语云：“不足为外人道也。”

            既出，得其船，便扶向路，处处志之。及郡下，诣太守，说如此。太守即遣人随其往，寻向所志，遂迷，不复得路。

            南阳刘子骥，高尚士也，闻之，欣然规往。未果，寻病终。后遂无问津者。
        """

        const val SAMPLE_YUEYANG = """
            庆历四年春，滕子京谪守巴陵郡。越明年，政通人和，百废具兴，乃重修岳阳楼，增其旧制，刻唐贤今人诗赋于其上，属予作文以记之。

            予观夫巴陵胜状，在洞庭一湖。衔远山，吞长江，浩浩汤汤，横无际涯，朝晖夕阴，气象万千，此则岳阳楼之大观也，前人之述备矣。然则北通巫峡，南极潇湘，迁客骚人，多会于此，览物之情，得无异乎？

            若夫淫雨霏霏，连月不开，阴风怒号，浊浪排空，日星隐曜，山岳潜形，商旅不行，樯倾楫摧，薄暮冥冥，虎啸猿啼。登斯楼也，则有去国怀乡，忧谗畏讥，满目萧然，感极而悲者矣。

            至若春和景明，波澜不惊，上下天光，一碧万顷，沙鸥翔集，锦鳞游泳，岸芷汀兰，郁郁青青。而或长烟一空，皓月千里，浮光跃金，静影沉璧，渔歌互答，此乐何极！登斯楼也，则有心旷神怡，宠辱偕忘，把酒临风，其喜洋洋者矣。

            嗟夫！予尝求古仁人之心，或异二者之为，何哉？不以物喜，不以己悲，居庙堂之高则忧其民，处江湖之远则忧其君。是进亦忧，退亦忧。然则何时而乐耶？其必曰“先天下之忧而忧，后天下之乐而乐”乎！噫！微斯人，吾谁与归？时六年九月十五日。
        """

        const val SAMPLE_JINGJIE = """
            词以境界为最上。有境界则自成高格，自有名句。五代、北宋之词所以独绝者在此。

            有造境，有写境，此理想与写实二派之所由分。然二者颇难分别。因大诗人所造之境，必合乎自然，所写之境，亦必邻于理想故也。

            有有我之境，有无我之境。“泪眼问花花不语，乱红飞过秋千去。”“可堪孤馆闭春寒，杜鹃声里斜阳暮。”有我之境也。“采菊东篱下，悠然见南山。”“寒波澹澹起，白鸟悠悠下。”无我之境也。
        """

        const val SAMPLE_YOUWO = """
            有我之境，以我观物，故物皆著我之色彩。无我之境，以物观物，故不知何者为我，何者为物。古人为词，写有我之境者为多，然未始不能写无我之境，此在豪杰之士能自树立耳。

            无我之境，人惟于静中得之。有我之境，于由动之静时得之。故一优美，一宏壮也。
        """

        const val SAMPLE_GEBUGE = """
            白石写景之作，如“二十四桥仍在，波心荡、冷月无声”，“数峰清苦，商略黄昏雨”，“高树晚蝉，说西风消息”，虽格韵高绝，然如雾里看花，终隔一层。

            梅溪、梦窗诸家写景之病，皆在一“隔”字。北宋风流，渡江遂绝。抑真有运会存乎其间耶？

            问“隔”与“不隔”之别。曰：陶、谢之诗不隔，延年则稍隔矣。东坡之诗不隔，山谷则稍隔矣。“池塘生春草”“空梁落燕泥”等二句，妙处唯在不隔。
        """
    }
}
