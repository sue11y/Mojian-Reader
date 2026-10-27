package com.mojian.reader.data

data class BookSource(
    val id: String,
    val name: String,
    val group: String = "未分组",
    val url: String = "",
    val enabled: Boolean = true,
    val enabledExplore: Boolean = false,
    val weight: Int = 0,
    val respondTimeMs: Long = 0L,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastTestAt: Long = 0L,
    val lastError: String? = null,
    val searchUrl: String = "",
    val exploreUrl: String = "",
    val enabledCookieJar: Boolean = false,
    val loginUrl: String = "",
    val concurrentRate: String = "",
    val httpUserAgent: String = "",
    val rules: SourceRules = SourceRules()
) {
    val attempts: Int
        get() = successCount + failureCount

    val successRate: Int
        get() = if (attempts == 0) 0 else (successCount * 100f / attempts).toInt()

    val health: SourceHealth
        get() = when {
            failureCount > 0 && successCount == 0 -> SourceHealth.BAD
            failureCount > successCount -> SourceHealth.WARN
            successCount > 0 && failureCount == 0 -> SourceHealth.GOOD
            else -> SourceHealth.UNKNOWN
        }
}

data class SourceRules(
    val search: SourceSearchRule = SourceSearchRule(),
    val bookInfo: SourceBookInfoRule = SourceBookInfoRule(),
    val toc: SourceTocRule = SourceTocRule(),
    val content: SourceContentRule = SourceContentRule(),
    val replaceRegex: String = ""
)

data class SourceSearchRule(
    val searchUrl: String = "",
    val bookList: String = "",
    val name: String = "",
    val author: String = "",
    val bookUrl: String = "",
    val kind: String = "",
    val intro: String = "",
    val lastChapter: String = ""
)

data class SourceBookInfoRule(
    val init: String = "",
    val name: String = "",
    val author: String = "",
    val kind: String = "",
    val intro: String = "",
    val lastChapter: String = ""
)

data class SourceTocRule(
    val chapterList: String = "",
    val chapterName: String = "",
    val chapterUrl: String = ""
)

data class SourceContentRule(
    val content: String = "",
    val replaceRegex: String = "",
    val imageStyle: String = ""
)

enum class SourceHealth(val label: String) {
    UNKNOWN("未测试"),
    GOOD("正常"),
    WARN("一般"),
    BAD("异常")
}

data class SourceUiState(
    val sources: List<BookSource> = emptyList(),
    val isReady: Boolean = false,
    val testingId: String? = null,
    val refreshing: Boolean = false,
    val message: String? = null
)

data class SourceTestResult(
    val success: Boolean,
    val statusCode: Int = 0,
    val elapsedMs: Long = 0L,
    val message: String
)

data class RemoteBook(
    val source: BookSource,
    val title: String,
    val author: String,
    val url: String,
    val coverUrl: String? = null,
    val intro: String? = null,
    val latestChapter: String? = null
)

data class RemoteChapter(
    val title: String,
    val url: String,
    val fallbackUrl: String? = null
)

data class RemoteBookDetail(
    val book: RemoteBook,
    val chapters: List<RemoteChapter>
)

data class RemoteFavorite(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val author: String,
    val url: String,
    val coverUrl: String? = null,
    val latestChapter: String? = null,
    val addedAt: Long,
    val chapterIndex: Int = 0,
    val chapterOffset: Int = 0,
    val chapterOffsetPx: Int = 0,
    val horizontalMode: Boolean = false,
    val chapters: List<RemoteChapter> = emptyList()
)

data class RemoteLibraryState(
    val favorites: List<RemoteFavorite> = emptyList(),
    val isReady: Boolean = false
)

enum class RemotePageMode {
    VERTICAL,
    HORIZONTAL
}
