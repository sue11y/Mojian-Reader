package com.mojian.reader.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mojian.reader.data.Book
import com.mojian.reader.data.Chapter
import com.mojian.reader.data.ReaderPalette
import com.mojian.reader.data.ReaderPreferences
import com.mojian.reader.data.ReaderThemeStyle
import com.mojian.reader.data.readerPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    preferences: ReaderPreferences,
    onBack: () -> Unit,
    onLoadChapters: suspend (String) -> List<Chapter>,
    onProgress: (chapterIndex: Int, chapterProgress: Float) -> Unit,
    onToggleBookmark: (chapterIndex: Int, title: String, preview: String) -> Unit,
    onThemeChange: (ReaderThemeStyle) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onSerifChange: (Boolean) -> Unit
) {
    var chapters by remember(book.id) { mutableStateOf<List<Chapter>?>(null) }
    var chapterIndex by remember(book.id) {
        mutableIntStateOf(book.chapterIndex.coerceIn(0, (book.chapterCount - 1).coerceAtLeast(0)))
    }
    var showTypography by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var inBookQuery by remember { mutableStateOf("") }
    val palette = readerPalette(preferences.theme)
    val listState = rememberLazyListState()
    val localProgress by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) 0f else {
                (listState.firstVisibleItemIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(book.id) {
        chapters = onLoadChapters(book.id)
        chapterIndex = chapterIndex.coerceIn(
            0,
            ((chapters?.size ?: 1) - 1).coerceAtLeast(0)
        )
    }
    LaunchedEffect(chapterIndex) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(book.id, chapterIndex) {
        snapshotFlow { localProgress }
            .debounce(450)
            .collect { progress ->
                onProgress(chapterIndex, progress)
            }
    }
    ReaderSystemBars(theme = preferences.theme)

    val loadedChapters = chapters
    val currentChapter = loadedChapters?.getOrNull(chapterIndex)
    val isBookmarked = book.bookmarks.any { it.chapterIndex == chapterIndex }

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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回书架",
                    tint = palette.text
                )
            }
            Text(
                text = book.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = {
                    currentChapter?.let { chapter ->
                        onToggleBookmark(
                            chapterIndex,
                            chapter.title,
                            chapter.content.take(120)
                        )
                    }
                }
            ) {
                Icon(
                    imageVector = if (isBookmarked) {
                        Icons.Rounded.Bookmark
                    } else {
                        Icons.Rounded.BookmarkBorder
                    },
                    contentDescription = if (isBookmarked) "移除书签" else "添加书签",
                    tint = if (isBookmarked) palette.accent else palette.muted
                )
            }
            IconButton(onClick = { showSearch = true }) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "在书中搜索",
                    tint = palette.muted
                )
            }
            IconButton(onClick = { showToc = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.List,
                    contentDescription = "目录",
                    tint = palette.muted
                )
            }
            IconButton(onClick = { showTypography = true }) {
                Icon(
                    imageVector = Icons.Rounded.TextFields,
                    contentDescription = "阅读设置",
                    tint = palette.muted
                )
            }
        }

        Divider(color = palette.rule, thickness = 0.5.dp)

        Box(modifier = Modifier.weight(1f)) {
            when {
                loadedChapters == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp),
                        color = palette.accent,
                        strokeWidth = 2.dp
                    )
                }
                currentChapter == null -> {
                    Text(
                        text = "这一章没有可显示的内容",
                        modifier = Modifier.align(Alignment.Center),
                        color = palette.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    ChapterContent(
                        chapter = currentChapter,
                        preferences = preferences,
                        palette = palette,
                        listState = listState,
                        hasNext = chapterIndex < loadedChapters.lastIndex,
                        onNext = {
                            if (chapterIndex < loadedChapters.lastIndex) {
                                chapterIndex += 1
                            }
                        }
                    )
                }
            }
        }

        ReaderFooter(
            palette = palette,
            chapterIndex = chapterIndex,
            chapterCount = loadedChapters?.size ?: book.chapterCount,
            overallProgress = if (loadedChapters.isNullOrEmpty()) {
                book.progress
            } else {
                ((chapterIndex + localProgress) / loadedChapters.size).coerceIn(0f, 1f)
            },
            onPrevious = {
                if (chapterIndex > 0) chapterIndex -= 1
            },
            onNext = {
                if (chapterIndex < (loadedChapters?.lastIndex ?: 0)) chapterIndex += 1
            }
        )
    }

    if (showTypography) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTypography = false },
            sheetState = sheetState,
            containerColor = palette.background,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            TypographySheet(
                preferences = preferences,
                palette = palette,
                onThemeChange = onThemeChange,
                onFontSizeChange = onFontSizeChange,
                onLineHeightChange = onLineHeightChange,
                onSerifChange = onSerifChange
            )
        }
    }

    if (showToc && loadedChapters != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = sheetState,
            containerColor = palette.background,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "目录",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${loadedChapters.size} 章",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(loadedChapters, key = { it.index }) { chapter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chapterIndex = chapter.index
                                    showToc = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chapter.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (chapter.index == chapterIndex) palette.accent else palette.text,
                                fontWeight = if (chapter.index == chapterIndex) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chapter.index == chapterIndex) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearch && loadedChapters != null) {
        val searchResults = remember(inBookQuery, loadedChapters) {
            val needle = inBookQuery.trim()
            if (needle.length < 1) emptyList()
            else loadedChapters.mapNotNull { chapter ->
                val position = chapter.content.indexOf(needle, ignoreCase = true)
                if (position < 0) null
                else {
                    val start = (position - 30).coerceAtLeast(0)
                    val end = (position + needle.length + 70).coerceAtMost(chapter.content.length)
                    BookSearchResult(
                        chapter = chapter,
                        snippet = chapter.content.substring(start, end).replace('\n', ' ')
                    )
                }
            }.take(30)
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSearch = false },
            sheetState = sheetState,
            containerColor = palette.background,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "在书中搜索",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSearch = false }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = palette.muted
                        )
                    }
                }
                TextField(
                    value = inBookQuery,
                    onValueChange = { inBookQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    placeholder = { Text("输入正文关键词") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = palette.muted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedIndicatorColor = palette.accent,
                        unfocusedIndicatorColor = palette.rule,
                        cursorColor = palette.accent
                    )
                )
                Spacer(Modifier.height(12.dp))
                if (inBookQuery.isBlank()) {
                    Text(
                        text = "输入关键词后会显示所在章节和上下文",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                } else {
                    Text(
                        text = "${searchResults.size} 个结果",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                    LazyColumn {
                        items(searchResults, key = { it.chapter.index }) { result ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        chapterIndex = result.chapter.index
                                        showSearch = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = result.chapter.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.text
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = result.snippet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterContent(
    chapter: Chapter,
    preferences: ReaderPreferences,
    palette: ReaderPalette,
    listState: LazyListState,
    hasNext: Boolean,
    onNext: () -> Unit
) {
    val paragraphs = remember(chapter.content) {
        chapter.content.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val family = if (preferences.useSerif) FontFamily.Serif else FontFamily.SansSerif
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 34.dp, bottom = 44.dp)
    ) {
        item {
            Text(
                text = chapter.title,
                color = palette.text,
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = (preferences.fontSize + 7).sp,
                lineHeight = (preferences.fontSize * 1.55f).sp
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(2.dp)
                    .background(palette.accent)
            )
            Spacer(Modifier.height(26.dp))
        }
        items(paragraphs) { paragraph ->
            Text(
                text = "　　$paragraph",
                color = palette.text,
                fontFamily = family,
                fontSize = preferences.fontSize.sp,
                lineHeight = (preferences.fontSize * preferences.lineHeight).sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
        if (hasNext) {
            item {
                Spacer(Modifier.height(16.dp))
                Divider(color = palette.rule, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNext)
                        .padding(vertical = 22.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "下一章",
                        color = palette.accent,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderFooter(
    palette: ReaderPalette,
    chapterIndex: Int,
    chapterCount: Int,
    overallProgress: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Divider(color = palette.rule, thickness = 0.5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = chapterIndex > 0) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "上一章",
                tint = if (chapterIndex > 0) palette.text else palette.muted.copy(alpha = 0.3f)
            )
        }
        Text(
            text = "${chapterIndex + 1} / $chapterCount",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "${(overallProgress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        IconButton(onClick = onNext, enabled = chapterIndex < chapterCount - 1) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "下一章",
                tint = if (chapterIndex < chapterCount - 1) {
                    palette.text
                } else {
                    palette.muted.copy(alpha = 0.3f)
                }
            )
        }
    }
}

@Composable
private fun TypographySheet(
    preferences: ReaderPreferences,
    palette: ReaderPalette,
    onThemeChange: (ReaderThemeStyle) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onSerifChange: (Boolean) -> Unit
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
        Spacer(Modifier.height(20.dp))
        Text(
            text = "背景",
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderThemeStyle.entries.forEach { theme ->
                ReaderPaletteChoice(
                    theme = theme,
                    selected = preferences.theme == theme,
                    onClick = { onThemeChange(theme) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "字号",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${preferences.fontSize.roundToInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent
            )
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "行距",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format("%.2f", preferences.lineHeight),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent
            )
        }
        Slider(
            value = preferences.lineHeight,
            onValueChange = onLineHeightChange,
            valueRange = 1.35f..2.2f,
            steps = 16,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.rule
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSerifChange(!preferences.useSerif) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "衬线字体",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = preferences.useSerif,
                onCheckedChange = onSerifChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = palette.accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = palette.rule
                )
            )
        }
    }
}

@Composable
private fun ReaderPaletteChoice(
    theme: ReaderThemeStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = readerPalette(theme)
    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(64.dp),
        shape = RoundedCornerShape(8.dp),
        color = palette.background,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) palette.accent else palette.rule
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "文",
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                color = palette.text
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = theme.label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
        }
    }
}

@Composable
private fun ReaderSystemBars(theme: ReaderThemeStyle) {
    val view = LocalView.current
    DisposableEffect(theme) {
        val activity = view.context as? Activity
        val controller = activity?.window?.let { window ->
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

private data class BookSearchResult(
    val chapter: Chapter,
    val snippet: String
)
