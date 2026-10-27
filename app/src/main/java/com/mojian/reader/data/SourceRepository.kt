package com.mojian.reader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.coroutines.coroutineContext

class SourceRepository(private val context: Context) {
    private val file = context.filesDir.resolve("book_sources.json")
    private val _state = MutableStateFlow(SourceUiState())
    val state: StateFlow<SourceUiState> = _state.asStateFlow()

    suspend fun initialize() {
        if (_state.value.isReady) return
        withContext(Dispatchers.IO) {
            val loaded = if (file.exists()) {
                runCatching { readSources() }.getOrElse { emptyList() }
            } else {
                runCatching { readBundledSources() }.getOrElse { listOf(sampleSource()) }
            }
            _state.update { it.copy(sources = loaded, isReady = true) }
            save()
        }
    }

    suspend fun addSource(
        name: String,
        url: String,
        group: String,
        weight: Int,
        searchUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return@withContext false
        val source = BookSource(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            group = group.trim().ifBlank { "未分组" },
            url = url.trim(),
            weight = weight,
            searchUrl = searchUrl.trim(),
            rules = SourceRules(search = SourceSearchRule(searchUrl = searchUrl.trim()))
        )
        _state.update { it.copy(sources = it.sources + source) }
        save()
        true
    }

    suspend fun updateWeight(id: String, weight: Int) = withContext(Dispatchers.IO) {
        mutate(id) { it.copy(weight = weight) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        mutate(id) { it.copy(enabled = enabled) }
    }

    suspend fun setExploreEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        mutate(id) { it.copy(enabledExplore = enabled) }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        _state.update { it.copy(sources = it.sources.filterNot { source -> source.id == id }) }
        save()
    }

    suspend fun importSources(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return@withContext 0
        val parsed = when {
            text.trimStart().startsWith("[") -> parseArray(JSONArray(text))
            text.trimStart().startsWith("{") -> listOf(parseLegacySource(JSONObject(text)))
            else -> emptyList()
        }.filter { it.name.isNotBlank() && it.url.isNotBlank() }
        if (parsed.isEmpty()) return@withContext 0
        _state.update { current ->
            current.copy(sources = current.sources + parsed)
        }
        save()
        parsed.size
    }

    suspend fun testSource(id: String, keyword: String = "测试"): SourceTestResult =
        withContext(Dispatchers.IO) {
            val source = _state.value.sources.firstOrNull { it.id == id }
                ?: return@withContext SourceTestResult(false, message = "书源不存在")
            _state.update { it.copy(testingId = id) }
            val started = System.currentTimeMillis()
            val result = runCatching { request(source, keyword, started) }
                .getOrElse { error ->
                    SourceTestResult(
                        success = false,
                        elapsedMs = System.currentTimeMillis() - started,
                        message = error.message ?: "连接失败"
                    )
                }
            mutate(id) { current ->
                current.copy(
                    respondTimeMs = result.elapsedMs,
                    successCount = current.successCount + if (result.success) 1 else 0,
                    failureCount = current.failureCount + if (result.success) 0 else 1,
                    lastTestAt = System.currentTimeMillis(),
                    lastError = result.message.takeIf { !result.success }
                )
            }
            _state.update { it.copy(testingId = null) }
            result
        }

    suspend fun refreshAllSources(): Int = withContext(Dispatchers.IO) {
        val enabled = _state.value.sources.filter { it.enabled }
        if (enabled.isEmpty()) return@withContext 0
        _state.update { it.copy(refreshing = true) }
        var healthy = 0
        enabled.forEach { source ->
            coroutineContext.ensureActive()
            val result = runCatching { testSource(source.id) }.getOrNull()
            if (result?.success == true) healthy += 1
        }
        _state.update { it.copy(refreshing = false) }
        healthy
    }

    suspend fun searchBooks(keyword: String): List<RemoteBook> = withContext(Dispatchers.IO) {
        val query = keyword.trim()
        if (query.isBlank()) return@withContext emptyList()
        val sources = _state.value.sources
            .filter { it.enabled }
            .sortedWith(
                compareByDescending<BookSource> { sourceHealthScore(it.health) }
                    .thenByDescending { it.weight }
                    .thenByDescending { it.successRate }
            )
            .take(MAX_SEARCH_SOURCES)
        if (sources.isEmpty()) return@withContext emptyList()

        val permit = Semaphore(6)
        val results = mutableListOf<RemoteBook>()
        withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            coroutineScope {
                val channel = Channel<List<RemoteBook>>(sources.size)
                sources.forEach { source ->
                    launch {
                        val batch = permit.withPermit {
                            runCatching { searchOne(source, query) }.getOrDefault(emptyList())
                        }
                        channel.send(batch)
                    }
                }
                repeat(sources.size) {
                    if (results.size >= 60) return@coroutineScope
                    val batch = channel.receive()
                    results += batch
                    coroutineContext.ensureActive()
                }
            }
        }
        results.take(60)
    }

    suspend fun fetchChapters(sourceId: String, url: String): List<RemoteChapter> =
        withContext(Dispatchers.IO) {
            val source = _state.value.sources.firstOrNull { it.id == sourceId }
                ?: return@withContext emptyList()
            val html = httpGet(absoluteUrl(source, url), source.httpUserAgent)
            val document = Jsoup.parse(html)
            val listRule = source.rules.toc.chapterList.trim()
            if (listRule.isBlank() || listRule.startsWith("<js>")) {
                return@withContext emptyList()
            }
            val listSelector = parseRule(listRule).selector
                ?: return@withContext emptyList()
            val items = safeSelect(document, listSelector)
            items.mapNotNull { item ->
                val title = firstValue(item, source.rules.toc.chapterName, RuleAttr.TEXT)
                    ?: item.text().trim().take(80)
                val href = firstValue(item, source.rules.toc.chapterUrl, RuleAttr.HREF)
                    ?: return@mapNotNull null
                if (title.isBlank() || href.isBlank()) return@mapNotNull null
                val trimmedBase = url.trimEnd('/')
                RemoteChapter(
                    title = title,
                    url = absoluteUrl(source, href, url),
                    fallbackUrl = if (trimmedBase != url) {
                        absoluteUrl(source, href, trimmedBase)
                    } else {
                        null
                    }
                )
            }.distinctBy { it.url }
        }

    suspend fun fetchChapterContent(
        sourceId: String,
        url: String,
        fallbackUrl: String? = null
    ): String =
        withContext(Dispatchers.IO) {
            val source = _state.value.sources.firstOrNull { it.id == sourceId }
                ?: return@withContext ""
            val primary = httpGet(absoluteUrl(source, url), source.httpUserAgent)
            val html = if (primary.isBlank() || isErrorPage(primary)) {
                fallbackUrl?.let { httpGet(absoluteUrl(source, it), source.httpUserAgent) }
                    .orEmpty()
            } else {
                primary
            }
            val document = Jsoup.parse(html)
            val contentRule = source.rules.content.content.trim()
            val rawHtml = if (contentRule.isBlank()) {
                document.body()?.html().orEmpty()
            } else {
                parseRule(contentRule).selector
                    ?.let { safeSelect(document, it) }
                    ?.joinToString("\n") { it.html() }
                    .orEmpty()
                    .ifBlank { document.body()?.html().orEmpty() }
            }
            val text = Jsoup.parse(rawHtml).text().replace('\u00A0', ' ')
            val replaceRules = ContentCleaner.parseLegacyRules(
                source.rules.content.replaceRegex.ifBlank { source.rules.replaceRegex }
            )
            ContentCleaner.clean(text, ContentCleaner.defaultRules + replaceRules)
        }

    private fun isErrorPage(html: String): Boolean {
        if (html.isBlank()) return true
        val markers = listOf(
            "该页面不存在",
            "页面不存在",
            "返回首页",
            "404 Not Found",
            "抱歉，您访问的页面"
        )
        return markers.any { html.contains(it, ignoreCase = true) }
    }

    private fun request(source: BookSource, keyword: String, startedAt: Long): SourceTestResult {
        val url = source.searchUrl.ifBlank { source.url }
            .replace("{{key}}", java.net.URLEncoder.encode(keyword, "UTF-8"))
            .replace("{{page}}", "1")
        if (url.isBlank()) return SourceTestResult(false, message = "缺少搜索地址")
        val target = if (url.startsWith("http")) url else source.url.trimEnd('/') + "/" + url.trimStart('/')
        val requestUrl = target.substringBefore(',')
        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.requestMethod = "GET"
        connection.setRequestProperty(
            "User-Agent",
            source.httpUserAgent.ifBlank { "MojianReader/2.0" }
        )
        return try {
            val code = connection.responseCode
            val elapsed = System.currentTimeMillis() - startedAt
            if (code in 200..299) {
                SourceTestResult(
                    success = true,
                    statusCode = code,
                    elapsedMs = elapsed,
                    message = "连接成功"
                )
            } else {
                SourceTestResult(
                    success = false,
                    statusCode = code,
                    elapsedMs = elapsed,
                    message = "HTTP $code"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun mutate(id: String, transform: (BookSource) -> BookSource) {
        _state.update { current ->
            current.copy(
                sources = current.sources.map { if (it.id == id) transform(it) else it }
            )
        }
        save()
    }

    private fun searchOne(source: BookSource, query: String): List<RemoteBook> {
        val searchUrl = source.searchUrl.ifBlank { source.rules.search.searchUrl }
        if (searchUrl.isBlank() || source.url.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = buildSearchRequest(source, searchUrl, encoded)
        val document = Jsoup.parse(
            httpRequest(
                url = request.url,
                userAgent = source.httpUserAgent,
                connectTimeout = 3000,
                readTimeout = 5000,
                method = request.method,
                body = request.body,
                contentType = request.contentType
            )
        )
        val listRule = source.rules.search.bookList.trim()
        if (listRule.isBlank()) return emptyList()
        val listSelector = parseRule(listRule).selector ?: return emptyList()
        val items = safeSelect(document, listSelector)
        return items.take(25).mapNotNull { item ->
            val title = firstValue(item, source.rules.search.name, RuleAttr.TEXT)
                ?: item.selectFirst("a")?.text()?.trim().orEmpty()
            val author = firstValue(item, source.rules.search.author, RuleAttr.TEXT).orEmpty()
            val href = firstValue(item, source.rules.search.bookUrl, RuleAttr.HREF)
                ?: item.selectFirst("a")?.attr("href").orEmpty()
            if (title.isBlank() || href.isBlank()) return@mapNotNull null
            RemoteBook(
                source = source,
                title = title.take(80),
                author = author.take(60),
                url = absoluteUrl(source, href, request.url),
                intro = firstValue(item, source.rules.search.intro, RuleAttr.TEXT)?.take(160),
                latestChapter = firstValue(item, source.rules.search.lastChapter, RuleAttr.TEXT)
            )
        }.distinctBy { it.url }
    }

    private fun buildSearchRequest(source: BookSource, raw: String, encodedKey: String): SearchRequest {
        val comma = raw.indexOf(',')
        val path = if (comma >= 0) raw.substring(0, comma).trim() else raw.trim()
        val target = absoluteUrl(source, path)
            .replace("{{key}}", encodedKey)
            .replace("{{page}}", "1")
        val config = if (comma >= 0) {
            runCatching { JSONObject(raw.substring(comma + 1)) }.getOrNull()
        } else {
            null
        }
        val method = config?.optString("method")?.uppercase()?.ifBlank { "GET" } ?: "GET"
        val rawBody = config?.optString("body").orEmpty()
            .replace("{{key}}", encodedKey)
            .replace("{{page}}", "1")
        val type = config?.optString("type").orEmpty().lowercase()
        val contentType = when {
            type.contains("json") -> "application/json; charset=UTF-8"
            method == "POST" && rawBody.isNotBlank() -> "application/x-www-form-urlencoded; charset=UTF-8"
            else -> null
        }
        return SearchRequest(
            url = target,
            method = method,
            body = rawBody,
            contentType = contentType
        )
    }

    private fun httpGet(
        url: String,
        userAgent: String,
        connectTimeout: Int = 5000,
        readTimeout: Int = 8000
    ): String = httpRequest(
        url = url,
        userAgent = userAgent,
        connectTimeout = connectTimeout,
        readTimeout = readTimeout,
        method = "GET",
        body = null,
        contentType = null
    )

    private fun httpRequest(
        url: String,
        userAgent: String,
        connectTimeout: Int,
        readTimeout: Int,
        method: String,
        body: String?,
        contentType: String?
    ): String {
        if (url.isBlank()) return ""
        return try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.requestMethod = method
            connection.setRequestProperty(
                "User-Agent",
                userAgent.ifBlank { "MojianReader/2.0" }
            )
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            if (method == "POST" && !body.isNullOrEmpty()) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            if (stream == null) "" else {
                val bytes = stream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        if (output.size() > 2_000_000) break
                    }
                    output.toByteArray()
                }
                val utf8 = String(bytes, StandardCharsets.UTF_8)
                if (utf8.contains('\uFFFD')) String(bytes, charset("GB18030")) else utf8
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        ""
        }
    }

    private fun sourceHealthScore(health: SourceHealth): Int = when (health) {
        SourceHealth.GOOD -> 3
        SourceHealth.UNKNOWN -> 2
        SourceHealth.WARN -> 1
        SourceHealth.BAD -> 0
    }

    private fun absoluteUrl(source: BookSource, path: String, base: String = ""): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val baseUrl = base.ifBlank { source.url }
        return runCatching { URL(URL(baseUrl), path).toString() }
            .getOrElse { source.url.trimEnd('/') + "/" + path.trimStart('/') }
    }

    private fun firstValue(element: Element, raw: String, defaultAttr: RuleAttr): String? {
        for (alternative in raw.split(Regex("""&&|\|\|"""))) {
            val parsed = parseRule(alternative)
            val selected = parsed.selector?.let { selector ->
                runCatching { element.selectFirst(selector) }.getOrNull()
            } ?: element
            val value = when (parsed.attr) {
                RuleAttr.TEXT -> selected.text().trim()
                RuleAttr.HTML -> selected.html()
                RuleAttr.HREF -> selected.attr("href")
                RuleAttr.SRC -> selected.attr("src")
                RuleAttr.CONTENT -> selected.attr("content")
                else -> selected.text().trim()
            }
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun safeSelect(element: Element, selector: String): org.jsoup.select.Elements =
        runCatching { element.select(selector) }
            .getOrDefault(org.jsoup.select.Elements(0))

    private fun save() {
        val array = JSONArray()
        _state.value.sources.forEach { array.put(sourceToJson(it)) }
        val temp = file.resolveSibling("book_sources.json.tmp")
        temp.writeText(array.toString())
        if (file.exists() && !file.delete()) {
            file.writeText(array.toString())
        } else if (!temp.renameTo(file)) {
            file.writeText(array.toString())
        }
        if (temp.exists()) temp.delete()
    }

    private fun readSources(): List<BookSource> {
        val root = file.readText().trim()
        return if (root.startsWith("[")) parseArray(JSONArray(root)) else emptyList()
    }

    private fun readBundledSources(): List<BookSource> {
        val text = context.assets.open("book_sources.json")
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
        return if (text.trimStart().startsWith("[")) parseArray(JSONArray(text)) else emptyList()
    }

    private fun parseArray(array: JSONArray): List<BookSource> =
        List(array.length()) { index -> parseLegacySource(array.getJSONObject(index)) }

    private fun parseLegacySource(json: JSONObject): BookSource {
        val rules = json.optJSONObject("rules")
        val legacySearch = json.optJSONObject("ruleSearch")
        val legacyBookInfo = json.optJSONObject("ruleBookInfo")
        val legacyToc = json.optJSONObject("ruleToc")
        val legacyContent = json.optJSONObject("ruleContent")
        return BookSource(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", json.optString("bookSourceName", "未命名书源")),
            group = json.optString("group", json.optString("bookSourceGroup", "未分组")),
            url = json.optString("url", json.optString("bookSourceUrl", "")),
            enabled = json.optBoolean("enabled", true),
            enabledExplore = json.optBoolean("enabledExplore", json.optBoolean("exploreEnabled", false)),
            weight = json.optInt("weight", 0),
            respondTimeMs = json.optLong("respondTime", json.optLong("respondTimeMs", 0)),
            successCount = json.optInt("successCount", 0),
            failureCount = json.optInt("failureCount", 0),
            lastTestAt = json.optLong("lastTestAt", 0),
            lastError = json.optString("lastError").takeIf { it.isNotBlank() },
            searchUrl = json.optString("searchUrl", rules?.optString("searchUrl").orEmpty()),
            exploreUrl = json.optString("exploreUrl", ""),
            enabledCookieJar = json.optBoolean("enabledCookieJar", false),
            loginUrl = json.optString("loginUrl", ""),
            concurrentRate = json.optString("concurrentRate", ""),
            httpUserAgent = json.optString("httpUserAgent", ""),
            rules = SourceRules(
                search = parseSearchRule(rules ?: legacySearch ?: json).copy(
                    searchUrl = json.optString("searchUrl", rules?.optString("searchUrl").orEmpty())
                ),
                bookInfo = parseBookInfoRule(rules ?: legacyBookInfo ?: json),
                toc = parseTocRule(rules ?: legacyToc ?: json),
                content = parseContentRule(rules ?: legacyContent ?: json),
                replaceRegex = json.optString(
                    "replaceRegex",
                    (rules ?: json).optString("replaceRegex")
                )
            )
        )
    }

    private fun parseSearchRule(json: JSONObject): SourceSearchRule = SourceSearchRule(
        searchUrl = json.optString("searchUrl", ""),
        bookList = json.optString("bookList", ""),
        name = json.optString("name", ""),
        author = json.optString("author", ""),
        bookUrl = json.optString("bookUrl", ""),
        kind = json.optString("kind", ""),
        intro = json.optString("intro", ""),
        lastChapter = json.optString("lastChapter", "")
    )

    private fun parseBookInfoRule(json: JSONObject): SourceBookInfoRule = SourceBookInfoRule(
        init = json.optString("init", ""),
        name = json.optString("name", ""),
        author = json.optString("author", ""),
        kind = json.optString("kind", ""),
        intro = json.optString("intro", ""),
        lastChapter = json.optString("lastChapter", "")
    )

    private fun parseTocRule(json: JSONObject): SourceTocRule = SourceTocRule(
        chapterList = json.optString("chapterList", ""),
        chapterName = json.optString("chapterName", ""),
        chapterUrl = json.optString("chapterUrl", "")
    )

    private fun parseContentRule(json: JSONObject): SourceContentRule = SourceContentRule(
        content = json.optString("content", ""),
        replaceRegex = json.optString("replaceRegex", ""),
        imageStyle = json.optString("imageStyle", "")
    )

    private fun sourceToJson(source: BookSource): JSONObject = JSONObject().apply {
        put("id", source.id)
        put("name", source.name)
        put("group", source.group)
        put("url", source.url)
        put("enabled", source.enabled)
        put("enabledExplore", source.enabledExplore)
        put("weight", source.weight)
        put("respondTimeMs", source.respondTimeMs)
        put("successCount", source.successCount)
        put("failureCount", source.failureCount)
        put("lastTestAt", source.lastTestAt)
        put("lastError", source.lastError)
        put("searchUrl", source.searchUrl)
        put("exploreUrl", source.exploreUrl)
        put("enabledCookieJar", source.enabledCookieJar)
        put("loginUrl", source.loginUrl)
        put("concurrentRate", source.concurrentRate)
        put("httpUserAgent", source.httpUserAgent)
        put("rules", JSONObject().apply {
            put("searchUrl", source.rules.search.searchUrl)
            put("bookList", source.rules.search.bookList)
            put("name", source.rules.search.name)
            put("author", source.rules.search.author)
            put("bookUrl", source.rules.search.bookUrl)
            put("kind", source.rules.search.kind)
            put("intro", source.rules.search.intro)
            put("lastChapter", source.rules.search.lastChapter)
            put("chapterList", source.rules.toc.chapterList)
            put("chapterName", source.rules.toc.chapterName)
            put("chapterUrl", source.rules.toc.chapterUrl)
            put("content", source.rules.content.content)
            put("contentReplaceRegex", source.rules.content.replaceRegex)
            put("imageStyle", source.rules.content.imageStyle)
            put("replaceRegex", source.rules.replaceRegex)
        })
    }

    private fun sampleSource(): BookSource = BookSource(
        id = "sample-source",
        name = "示例书源",
        group = "示例",
        url = "https://example.com",
        weight = 0,
        searchUrl = "/search?q={{key}}",
        rules = SourceRules(
            search = SourceSearchRule(
                searchUrl = "/search?q={{key}}",
                bookList = ".book-item",
                name = ".title",
                author = ".author",
                bookUrl = "a"
            )
        )
    )

    private companion object {
        const val MAX_SEARCH_SOURCES = 12
        const val SEARCH_TIMEOUT_MS = 10_000L
    }
}

private enum class RuleAttr {
    TEXT,
    HTML,
    HREF,
    SRC,
    CONTENT
}

private data class ParsedRule(
    val selector: String?,
    val attr: RuleAttr
)

private data class SearchRequest(
    val url: String,
    val method: String,
    val body: String?,
    val contentType: String?
)

private fun parseRule(raw: String): ParsedRule {
    val value = raw.trim().substringBefore("##").trim()
    val parts = value.split('@')
    val attributeToken = parts.lastOrNull().orEmpty()
    val attribute = when (attributeToken) {
        "text" -> RuleAttr.TEXT
        "html" -> RuleAttr.HTML
        "href" -> RuleAttr.HREF
        "src" -> RuleAttr.SRC
        "content" -> RuleAttr.CONTENT
        else -> null
    }
    val selectorParts = if (attribute != null) parts.dropLast(1) else parts
    val selector = selectorParts
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { normalizeSelector(it) }
        .ifBlank { null }
    return ParsedRule(
        selector = selector,
        attr = attribute ?: RuleAttr.TEXT
    )
}

private fun normalizeSelector(raw: String): String {
    var selector = raw.trim()
    if (selector.startsWith("id.")) selector = "#" + selector.removePrefix("id.")
    if (selector.startsWith("class.")) selector = "." + selector.removePrefix("class.")
    if (selector.startsWith("tag.")) selector = selector.removePrefix("tag.")
    selector = Regex("""(?:^|[\s>+~])(?:class|id|tag)\.([^\s>+~]+)""").replace(selector) { match ->
        val prefix = when {
            match.value.trimStart().startsWith("class.") -> "."
            match.value.trimStart().startsWith("id.") -> "#"
            else -> ""
        }
        prefix + match.groupValues[1]
    }
    selector = Regex("""(\.|!)(\d+)$""").replace(selector) { match ->
        ":eq(${match.groupValues[2]})"
    }
    selector = Regex("""(\.|!)(\d+)(?=$|[\s>+~])""").replace(selector) { match ->
        ":eq(${match.groupValues[2]})"
    }
    selector = selector.replace('!', ':')
    return selector
}
