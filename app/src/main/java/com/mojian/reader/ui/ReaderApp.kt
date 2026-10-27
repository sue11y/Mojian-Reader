package com.mojian.reader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mojian.reader.data.BookFormat
import com.mojian.reader.data.Bookmark
import com.mojian.reader.data.Chapter
import com.mojian.reader.data.LibraryRepository
import com.mojian.reader.data.ReaderSettingsRepository
import com.mojian.reader.data.RemoteBook
import com.mojian.reader.data.RemoteCacheRepository
import com.mojian.reader.data.RemoteFavorite
import com.mojian.reader.data.RemoteLibraryRepository
import com.mojian.reader.data.RemotePageMode
import com.mojian.reader.data.SourceRepository
import com.mojian.reader.ui.theme.Paper
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReaderApp(
    repository: LibraryRepository,
    settingsRepository: ReaderSettingsRepository,
    sourceRepository: SourceRepository,
    remoteLibraryRepository: RemoteLibraryRepository,
    remoteCacheRepository: RemoteCacheRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by repository.state.collectAsStateWithLifecycle()
    val preferences by settingsRepository.state.collectAsStateWithLifecycle()
    val sourceState by sourceRepository.state.collectAsStateWithLifecycle()
    val remoteLibrary by remoteLibraryRepository.state.collectAsStateWithLifecycle()
    val remoteCacheSize by remoteCacheRepository.sizeBytes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.SHELF) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var detailBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceBook by remember { mutableStateOf<RemoteBook?>(null) }
    var remoteRead by remember { mutableStateOf<RemoteReadSession?>(null) }
    var readingBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var readerStartChapter by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        repository.initialize()
        sourceRepository.initialize()
        remoteLibraryRepository.initialize()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val outcome = repository.importBooks(uris)
                val message = buildString {
                    if (outcome.imported > 0) {
                        append("已导入 ${outcome.imported} 本书")
                    }
                    if (outcome.failures.isNotEmpty()) {
                        if (isNotEmpty()) append("；")
                        append(outcome.failures.first())
                        if (outcome.failures.size > 1) {
                            append("，另有 ${outcome.failures.size - 1} 个失败")
                        }
                    }
                }
                snackbar.showSnackbar(message.ifBlank { "没有导入文件" })
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = repository.exportBackup(uri)
                snackbar.showSnackbar(if (success) "书库备份已导出" else "备份导出失败")
            }
        }
    }

    val sourceImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val count = sourceRepository.importSources(uri)
                snackbar.showSnackbar(if (count > 0) "已导入 $count 个书源" else "没有识别到有效书源")
            }
        }
    }

    val detailBook = library.books.firstOrNull { it.id == detailBookId }
    val readingBook = library.books.firstOrNull { it.id == readingBookId }
        ?.let { book ->
            if (readerStartChapter != null) {
                book.copy(chapterIndex = readerStartChapter!!)
            } else {
                book
            }
        }

    fun openBook(bookId: String, chapterIndex: Int? = null) {
        readerStartChapter = chapterIndex
        scope.launch {
            repository.openBook(bookId)
            readingBookId = bookId
        }
    }

    if (remoteRead != null) {
        RemoteReaderScreen(
            remote = remoteRead!!.remote,
            chapters = remoteRead!!.chapters,
            initialIndex = remoteRead!!.initialIndex,
            initialOffset = remoteRead!!.initialOffset,
            initialOffsetPx = remoteRead!!.initialOffsetPx,
            initialPageMode = remoteRead!!.initialPageMode,
            preferences = preferences,
            onBack = { remoteRead = null },
            onFetchContent = { chapter ->
                val sourceId = remoteRead!!.remote.source.id
                remoteCacheRepository.get(sourceId, chapter.url)
                    ?: sourceRepository.fetchChapterContent(
                        sourceId,
                        chapter.url,
                        chapter.fallbackUrl
                    ).also { content ->
                        remoteCacheRepository.put(sourceId, chapter.url, content)
                    }
            },
            onPrefetch = { nextChapters ->
                val sourceId = remoteRead!!.remote.source.id
                nextChapters.forEach { chapter ->
                    if (remoteCacheRepository.get(sourceId, chapter.url) == null) {
                        val content = sourceRepository.fetchChapterContent(
                            sourceId,
                            chapter.url,
                            chapter.fallbackUrl
                        )
                        remoteCacheRepository.put(sourceId, chapter.url, content)
                    }
                }
            },
            onFontSizeChange = settingsRepository::setFontSize,
            onThemeChange = settingsRepository::setTheme,
            onPageModeChange = { mode ->
                remoteRead?.favoriteId?.let { favoriteId ->
                    scope.launch {
                        remoteLibraryRepository.setPageMode(
                            favoriteId,
                            mode == RemotePageMode.HORIZONTAL
                        )
                    }
                }
            },
            onProgress = { chapterIndex, chapterOffset, chapterOffsetPx ->
                remoteRead?.favoriteId?.let { favoriteId ->
                    scope.launch {
                        remoteLibraryRepository.updateProgress(
                            favoriteId,
                            chapterIndex,
                            chapterOffset,
                            chapterOffsetPx
                        )
                    }
                }
            }
        )
    } else if (readingBook != null) {
        BackHandler {
            readingBookId = null
            readerStartChapter = null
        }
        if (readingBook.format == BookFormat.PDF) {
            val sourceFile = File(
                context.filesDir,
                "library/${readingBook.id}/source.pdf"
            )
            PdfReaderScreen(
                book = readingBook,
                sourceFile = sourceFile,
                initialPage = readingBook.chapterIndex,
                theme = preferences.theme,
                onBack = {
                    readingBookId = null
                    readerStartChapter = null
                },
                onProgress = { page, progress ->
                    scope.launch {
                        repository.updateProgress(readingBook.id, page, progress)
                    }
                }
            )
        } else {
            ReaderScreen(
                book = readingBook,
                preferences = preferences,
                onBack = {
                    readingBookId = null
                    readerStartChapter = null
                },
                onLoadChapters = repository::loadChapters,
                onProgress = { chapter, progress ->
                    scope.launch {
                        repository.updateProgress(readingBook.id, chapter, progress)
                    }
                },
                onToggleBookmark = { chapter, title, preview ->
                    scope.launch {
                        repository.toggleBookmark(readingBook.id, chapter, title, preview)
                    }
                },
                onThemeChange = settingsRepository::setTheme,
                onFontSizeChange = settingsRepository::setFontSize,
                onLineHeightChange = settingsRepository::setLineHeight,
                onSerifChange = settingsRepository::setSerif
            )
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Paper,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AppBottomBar(
                    selected = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    AppTab.SHELF -> ShelfScreen(
                        books = library.books,
                        categories = library.categories,
                        remoteFavorites = remoteLibrary.favorites,
                        onOpenBook = { openBook(it) },
                        onOpenRemoteFavorite = { favorite ->
                            val source = sourceState.sources.firstOrNull { it.id == favorite.sourceId }
                            if (source == null) {
                                scope.launch { snackbar.showSnackbar("这个收藏对应的书源已删除") }
                            } else {
                                remoteRead = RemoteReadSession(
                                    remote = RemoteBook(
                                        source = source,
                                        title = favorite.title,
                                        author = favorite.author,
                                        url = favorite.url,
                                        coverUrl = favorite.coverUrl,
                                        latestChapter = favorite.latestChapter
                                    ),
                                    chapters = favorite.chapters,
                                    initialIndex = favorite.chapterIndex.coerceIn(
                                        0,
                                        (favorite.chapters.size - 1).coerceAtLeast(0)
                                    ),
                                    initialOffset = favorite.chapterOffset,
                                    initialOffsetPx = favorite.chapterOffsetPx,
                                    initialPageMode = if (favorite.horizontalMode) {
                                        RemotePageMode.HORIZONTAL
                                    } else {
                                        RemotePageMode.VERTICAL
                                    },
                                    favoriteId = favorite.id
                                )
                            }
                        },
                        onBookOptions = { detailBookId = it },
                        onImport = { showImport = true },
                        onSearch = { selectedTab = AppTab.SEARCH },
                        onAddCategory = { category ->
                            scope.launch {
                                val added = repository.addCategory(category)
                                if (!added) snackbar.showSnackbar("分类名称为空或已经存在")
                            }
                        }
                    )
                    AppTab.SOURCES -> SourceManagerScreen(
                        state = sourceState,
                        onBack = { selectedTab = AppTab.SHELF },
                        onToggleEnabled = { source, enabled ->
                            scope.launch { sourceRepository.setEnabled(source.id, enabled) }
                        },
                        onToggleExplore = { source, enabled ->
                            scope.launch { sourceRepository.setExploreEnabled(source.id, enabled) }
                        },
                        onWeightChange = { source, weight ->
                            scope.launch { sourceRepository.updateWeight(source.id, weight) }
                        },
                        onDelete = { source ->
                            scope.launch { sourceRepository.delete(source.id) }
                        },
                        onTest = { source ->
                            scope.launch {
                                val result = sourceRepository.testSource(source.id)
                                snackbar.showSnackbar(
                                    if (result.success) "测试通过 · ${result.elapsedMs}ms"
                                    else result.message
                                )
                            }
                        },
                        onRefreshAll = {
                            scope.launch {
                                val healthy = sourceRepository.refreshAllSources()
                                snackbar.showSnackbar("检测完成，$healthy 个书源正常")
                            }
                        },
                        onImport = { sourceImportLauncher.launch(arrayOf("application/json")) },
                        onAdd = { name, url, group, weight, searchUrl ->
                            scope.launch {
                                val added = sourceRepository.addSource(
                                    name,
                                    url,
                                    group,
                                    weight,
                                    searchUrl
                                )
                                if (!added) snackbar.showSnackbar("书源名称不能为空")
                            }
                        }
                    )
                    AppTab.SEARCH -> SearchScreen(
                        books = library.books,
                        sources = sourceState.sources,
                        onSearch = repository::search,
                        onSourceSearch = sourceRepository::searchBooks,
                        onOpenBook = { bookId, chapterIndex ->
                            openBook(bookId, chapterIndex)
                        },
                        onOpenSourceBook = { sourceBook = it }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        books = library.books,
                        preferences = preferences,
                        cacheSizeBytes = remoteCacheSize,
                        onThemeChange = settingsRepository::setTheme,
                        onFontSizeChange = settingsRepository::setFontSize,
                        onLineHeightChange = settingsRepository::setLineHeight,
                        onSerifChange = settingsRepository::setSerif,
                        onExport = {
                            exportLauncher.launch(
                                "墨间书库备份-${System.currentTimeMillis()}.zip"
                            )
                        },
                        onClearCache = {
                            scope.launch {
                                remoteCacheRepository.clear()
                                snackbar.showSnackbar("在线缓存已清理")
                            }
                        }
                    )
                }
            }
        }

        if (showImport) {
            ImportSheet(
                importing = library.importing,
                onDismiss = { if (library.importing == null) showImport = false },
                onPickFiles = {
                    showImport = false
                    importLauncher.launch(arrayOf("*/*"))
                }
            )
        }

        if (detailBook != null) {
            BookDetailSheet(
                book = detailBook,
                categories = library.categories,
                onDismiss = { detailBookId = null },
                onCategoryChange = { category ->
                    scope.launch {
                        repository.setCategory(detailBook.id, category)
                    }
                },
                onStatusChange = { status ->
                    scope.launch {
                        repository.setReadingStatus(detailBook.id, status)
                    }
                },
                onDelete = {
                    detailBookId = null
                    scope.launch {
                        repository.deleteBook(detailBook.id)
                        snackbar.showSnackbar("已从书架删除")
                    }
                },
                onRemoveBookmark = { bookmark: Bookmark ->
                    scope.launch {
                        repository.toggleBookmark(
                            detailBook.id,
                            bookmark.chapterIndex,
                            bookmark.chapterTitle,
                            bookmark.preview
                        )
                    }
                }
            )
        }

        if (sourceBook != null) {
            SourceBookDetailSheet(
                remote = sourceBook!!,
                onDismiss = { sourceBook = null },
                onFetchChapters = sourceRepository::fetchChapters,
                onReadChapters = { chapters, index ->
                    remoteRead = RemoteReadSession(sourceBook!!, chapters, index)
                    sourceBook = null
                },
                onImport = { remote, chapters ->
                    val parsed = chapters.mapIndexed { index, chapter ->
                        Chapter(
                            index = index,
                            title = chapter.title.ifBlank { "第 ${index + 1} 章" },
                            content = sourceRepository.fetchChapterContent(
                                remote.source.id,
                                chapter.url,
                                chapter.fallbackUrl
                            )
                        )
                    }.filter { it.content.isNotBlank() }
                    val success = repository.importRemoteBook(remote, parsed)
                    snackbar.showSnackbar(
                        if (success) "已导入《${remote.title}》" else "导入失败，没有解析到正文"
                    )
                },
                onFavorite = { remote, chapters ->
                    val added = remoteLibraryRepository.addFavorite(remote, chapters)
                    snackbar.showSnackbar(if (added) "已收藏到在线书架" else "这本书已经在收藏中")
                }
            )
        }
    }
}
