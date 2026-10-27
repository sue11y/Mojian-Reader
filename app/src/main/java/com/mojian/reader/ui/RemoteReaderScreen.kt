package com.mojian.reader.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mojian.reader.data.ReaderPalette
import com.mojian.reader.data.ReaderPreferences
import com.mojian.reader.data.ReaderThemeStyle
import com.mojian.reader.data.RemoteBook
import com.mojian.reader.data.RemoteChapter
import com.mojian.reader.data.RemotePageMode
import com.mojian.reader.data.readerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteReaderScreen(
    remote: RemoteBook,
    chapters: List<RemoteChapter>,
    initialIndex: Int,
    initialOffset: Int = 0,
    initialOffsetPx: Int = 0,
    initialPageMode: RemotePageMode = RemotePageMode.VERTICAL,
    preferences: ReaderPreferences,
    onBack: () -> Unit,
    onFetchContent: suspend (RemoteChapter) -> String,
    onProgress: (chapterIndex: Int, chapterOffset: Int, chapterOffsetPx: Int) -> Unit = { _, _, _ -> },
    onFontSizeChange: (Float) -> Unit = {},
    onThemeChange: (ReaderThemeStyle) -> Unit = {},
    onPageModeChange: (RemotePageMode) -> Unit = {},
    onPrefetch: suspend (List<RemoteChapter>) -> Unit = {}
) {
    var index by remember {
        mutableIntStateOf(initialIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
    }
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var restored by remember { mutableStateOf(false) }
    var pageMode by remember { mutableStateOf(initialPageMode) }
    var showChapters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val palette = readerPalette(preferences.theme)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val horizontalPages = remember(content, preferences.fontSize, preferences.lineHeight) {
        val text = content.orEmpty()
        if (text.isBlank()) {
            emptyList()
        } else {
            val fontSizePx = with(density) { preferences.fontSize.sp.toPx() }
            val lineHeightPx = fontSizePx * preferences.lineHeight
            val widthPx = with(density) { (configuration.screenWidthDp - 48).dp.toPx() }
            val heightPx = with(density) { (configuration.screenHeightDp - 180).dp.toPx() }
            val charsPerLine = (widthPx / fontSizePx).toInt().coerceAtLeast(8)
            val linesPerPage = (heightPx / lineHeightPx).toInt().coerceAtLeast(4)
            paginate(text, charsPerLine * linesPerPage)
        }
    }
    val contentPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { horizontalPages.size }
    )
    val leave = {
        if (pageMode == RemotePageMode.HORIZONTAL) {
            onProgress(index, contentPagerState.currentPage, 0)
        } else {
            onProgress(
                index,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
        onBack()
    }

    LaunchedEffect(index) {
        restored = false
        loading = true
        content = runCatching {
            onFetchContent(chapters[index])
        }.getOrElse { "" }
        loading = false
        val nextChapters = chapters.drop(index + 1).take(2)
        if (nextChapters.isNotEmpty()) {
            scope.launch { onPrefetch(nextChapters) }
        }
        if (pageMode == RemotePageMode.VERTICAL) {
            delay(120)
            val target = if (index == initialIndex) initialOffset.coerceAtLeast(0) else 0
            var guard = 0
            while (listState.layoutInfo.totalItemsCount <= target && guard < 60) {
                delay(50)
                guard += 1
            }
            listState.scrollToItem(
                target.coerceAtMost((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)),
                if (index == initialIndex) initialOffsetPx.coerceAtLeast(0) else 0
            )
        } else {
            delay(120)
            val target = if (index == initialIndex) initialOffset.coerceAtLeast(0) else 0
            val maxPage = (horizontalPages.size - 1).coerceAtLeast(0)
            var guard = 0
            while (contentPagerState.pageCount <= target.coerceAtMost(maxPage) && guard < 60) {
                delay(50)
                guard += 1
            }
            if (contentPagerState.pageCount > 0) {
                val safePage = target.coerceAtMost(contentPagerState.pageCount - 1)
                contentPagerState.scrollToPage(safePage)
            }
        }
        delay(120)
        restored = true
    }

    LaunchedEffect(index, restored, pageMode) {
        if (pageMode == RemotePageMode.VERTICAL && restored) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
                .debounce(400)
                .collect { (offset, offsetPx) ->
                    onProgress(index, offset, offsetPx)
                }
        }
    }

    BackHandler { leave() }
    ReaderSystemBars(preferences.theme)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = leave) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.text
                )
            }
            Text(
                text = remote.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${index + 1}/${chapters.size}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
            IconButton(onClick = { showChapters = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.List,
                    contentDescription = "选择章节",
                    tint = palette.muted
                )
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "阅读设置",
                    tint = palette.muted
                )
            }
        }

        if (pageMode == RemotePageMode.HORIZONTAL) {
            if (loading || horizontalPages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = palette.accent)
                    } else {
                        Text(
                            text = "这一章没有内容",
                            color = palette.muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = contentPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    beyondViewportPageCount = 0
                ) { page ->
                    HorizontalPage(
                        chapter = chapters[index],
                        pageIndex = page,
                        content = horizontalPages.getOrElse(page) { "" },
                        preferences = preferences,
                        palette = palette
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = palette.accent
                    )
                    content.isNullOrBlank() -> Text(
                        text = "这一章没有内容",
                        modifier = Modifier.align(Alignment.Center),
                        color = palette.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> ChapterText(
                        chapter = chapters[index],
                        content = content!!,
                        preferences = preferences,
                        palette = palette,
                        listState = listState
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val previous = index - 1
                    if (previous >= 0) {
                        index = previous
                    }
                },
                enabled = index > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "上一章",
                    tint = if (index > 0) palette.text else palette.muted.copy(alpha = 0.3f)
                )
            }
            Text(
                text = if (pageMode == RemotePageMode.HORIZONTAL) "左右翻页" else remote.source.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = {
                    val next = index + 1
                    if (next < chapters.size) {
                        index = next
                    }
                },
                enabled = index < chapters.lastIndex
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "下一章",
                    tint = if (index < chapters.lastIndex) palette.text else palette.muted.copy(alpha = 0.3f)
                )
            }
        }
    }

    if (showChapters) {
        val chapterListState = rememberLazyListState(initialFirstVisibleItemIndex = index)
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.background,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "选择章节",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.text
                )
                LazyColumn(state = chapterListState) {
                    items(chapters, key = { it.url }) { chapter ->
                        Text(
                            text = chapter.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val target = chapters.indexOf(chapter)
                                    index = target
                                    showChapters = false
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (chapter.url == chapters.getOrNull(index)?.url) {
                                palette.accent
                            } else {
                                palette.text
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.background,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            RemoteReadSettingsSheet(
                preferences = preferences,
                palette = palette,
                pageMode = pageMode,
                onFontSizeChange = onFontSizeChange,
                onThemeChange = onThemeChange,
                onPageModeChange = {
                    pageMode = it
                    onPageModeChange(it)
                }
            )
        }
    }
}

@Composable
private fun ChapterText(
    chapter: RemoteChapter,
    content: String,
    preferences: ReaderPreferences,
    palette: ReaderPalette,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val family = if (preferences.useSerif) FontFamily.Serif else FontFamily.SansSerif
    val paragraphs = remember(content) {
        content.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(content.trim()) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 34.dp)
    ) {
        item {
            Text(
                text = chapter.title,
                color = palette.text,
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = (preferences.fontSize + 6).sp,
                lineHeight = (preferences.fontSize * 1.5f).sp
            )
            Spacer(Modifier.height(22.dp))
        }
        items(paragraphs.size) { position ->
            Text(
                text = "　　${paragraphs[position]}",
                color = palette.text,
                fontFamily = family,
                fontSize = preferences.fontSize.sp,
                lineHeight = (preferences.fontSize * preferences.lineHeight).sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun HorizontalPage(
    chapter: RemoteChapter,
    pageIndex: Int,
    content: String,
    preferences: ReaderPreferences,
    palette: ReaderPalette
) {
    val family = if (preferences.useSerif) FontFamily.Serif else FontFamily.SansSerif
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp)
    ) {
        if (pageIndex == 0) {
            Text(
                text = chapter.title,
                color = palette.text,
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = (preferences.fontSize + 6).sp,
                lineHeight = (preferences.fontSize * 1.5f).sp
            )
            Spacer(Modifier.height(22.dp))
        }
        Text(
            text = if (pageIndex == 0) "　　$content" else content,
            color = palette.text,
            fontFamily = family,
            fontSize = preferences.fontSize.sp,
            lineHeight = (preferences.fontSize * preferences.lineHeight).sp
        )
        if (pageIndex > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${chapter.title} · ${pageIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
        }
    }
}

private fun paginate(text: String, charsPerPage: Int): List<String> {
    val capacity = charsPerPage.coerceAtLeast(80)
    val paragraphs = text.split(Regex("""\n\s*\n"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val pages = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotBlank()) {
            pages += current.toString()
            current.clear()
        }
    }
    paragraphs.forEach { paragraph ->
        if (paragraph.length > capacity) {
            flush()
            paragraph.chunked(capacity).forEach { pages += it }
        } else {
            if (current.length + paragraph.length + 1 > capacity) flush()
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(paragraph)
        }
    }
    flush()
    return pages.ifEmpty { listOf(text) }
}

@Composable
private fun RemoteReadSettingsSheet(
    preferences: ReaderPreferences,
    palette: ReaderPalette,
    pageMode: RemotePageMode,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderThemeStyle) -> Unit,
    onPageModeChange: (RemotePageMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
    ) {
        Text(
            text = "阅读设置",
            style = MaterialTheme.typography.titleLarge,
            color = palette.text
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "背景",
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderThemeStyle.entries.forEach { theme ->
                Surface(
                    modifier = Modifier
                        .width(58.dp)
                        .clickable { onThemeChange(theme) },
                    shape = RoundedCornerShape(8.dp),
                    color = readerPalette(theme).background,
                    border = androidx.compose.foundation.BorderStroke(
                        if (preferences.theme == theme) 2.dp else 1.dp,
                        if (preferences.theme == theme) palette.accent else palette.rule
                    )
                ) {
                    Text(
                        text = theme.label,
                        modifier = Modifier.padding(vertical = 18.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = readerPalette(theme).text,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "字号",
            style = MaterialTheme.typography.titleMedium,
            color = palette.text
        )
        Slider(
            value = preferences.fontSize,
            onValueChange = onFontSizeChange,
            valueRange = 15f..28f,
            steps = 12,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.rule
            )
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "翻页方式",
            style = MaterialTheme.typography.titleMedium,
            color = palette.text
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemotePageMode.entries.forEach { mode ->
                Surface(
                    modifier = Modifier.clickable { onPageModeChange(mode) },
                    shape = CircleShape,
                    color = if (pageMode == mode) palette.accent else Color.Transparent,
                    border = if (pageMode == mode) null else androidx.compose.foundation.BorderStroke(1.dp, palette.rule)
                ) {
                    Text(
                        text = if (mode == RemotePageMode.VERTICAL) "上下滚动" else "左右翻页",
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (pageMode == mode) {
                            readerPalette(ReaderThemeStyle.NIGHT).text
                        } else {
                            palette.muted
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSystemBars(theme: ReaderThemeStyle) {
    val view = LocalView.current
    DisposableEffect(theme) {
        val controller = (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view)
        }
        val light = theme != ReaderThemeStyle.NIGHT && theme != ReaderThemeStyle.AMOLED
        controller?.isAppearanceLightStatusBars = light
        controller?.isAppearanceLightNavigationBars = light
        onDispose {
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }
}
