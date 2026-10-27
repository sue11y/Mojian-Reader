package com.mojian.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojian.reader.data.Book
import com.mojian.reader.data.BookSource
import com.mojian.reader.data.RemoteBook
import com.mojian.reader.data.SearchHit
import com.mojian.reader.data.SearchKind
import com.mojian.reader.ui.theme.Forest
import com.mojian.reader.ui.theme.Hairline
import com.mojian.reader.ui.theme.Ink
import com.mojian.reader.ui.theme.InkMuted
import com.mojian.reader.ui.theme.Paper
import com.mojian.reader.ui.theme.Surface
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    books: List<Book>,
    sources: List<BookSource>,
    onSearch: suspend (String) -> List<SearchHit>,
    onSourceSearch: suspend (String) -> List<RemoteBook>,
    onOpenBook: (bookId: String, chapterIndex: Int?) -> Unit,
    onOpenSourceBook: (RemoteBook) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("local") }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var sourceResults by remember { mutableStateOf<List<RemoteBook>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var sourceSearching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val recentBooks = remember(books) {
        books.sortedByDescending { it.lastOpenedAt }.take(8)
    }

    LaunchedEffect(query, mode) {
        if (query.isBlank()) {
            results = emptyList()
            sourceResults = emptyList()
            searching = false
            sourceSearching = false
            return@LaunchedEffect
        }
        if (mode == "source") {
            sourceSearching = true
            delay(220)
            sourceResults = onSourceSearch(query)
            sourceSearching = false
        } else {
            searching = true
            delay(220)
            results = onSearch(query)
            searching = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 26.dp)
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ink
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "在 ${books.size} 本书中查找",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
                Spacer(Modifier.height(20.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("书名、作者、分类或正文") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = InkMuted
                        )
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    tint = InkMuted
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface,
                        focusedIndicatorColor = Forest,
                        unfocusedIndicatorColor = Hairline,
                        cursorColor = Forest
                    )
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchModeChip("本地", mode == "local") { mode = "local" }
                    SearchModeChip("书源", mode == "source") { mode = "source" }
                }
            }
        }

        if (query.isBlank()) {
            item {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)
                ) {
                    Text(
                        text = "最近阅读",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
            if (recentBooks.isEmpty()) {
                item {
                    SearchEmpty(
                        title = "还没有可搜索的书",
                        subtitle = "导入书籍后，书名和正文都能在这里找到"
                    )
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(recentBooks, key = { it.id }) { book ->
                            RecentBook(book = book, onClick = {
                                onOpenBook(book.id, book.chapterIndex)
                            })
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp)
                ) {
                    Text(
                        text = "快速查找",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("桃花", "境界", "岳阳楼").forEach { suggestion ->
                            Surface(
                                modifier = Modifier.clickable { query = suggestion },
                                shape = CircleShape,
                                color = Forest.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = suggestion,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Forest
                                )
                            }
                        }
                    }
                }
            }
        } else if (mode == "source") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (sourceSearching) "正在搜索书源" else "书源结果",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (!sourceSearching) {
                        Text(
                            text = "${sourceResults.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Forest
                        )
                    }
                }
            }
            if (!sourceSearching && sourceResults.isEmpty()) {
                item {
                    SearchEmpty(
                        title = "书源没有返回结果",
                        subtitle = "检查书源规则是否支持搜索，或换一个关键词"
                    )
                }
            } else {
                items(
                    items = sourceResults,
                    key = { it.url }
                ) { remote ->
                    SourceResultRow(
                        remote = remote,
                        onClick = { onOpenSourceBook(remote) }
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searching) "正在查找" else "搜索结果",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (!searching) {
                        Text(
                            text = "${results.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Forest
                        )
                    }
                }
            }
            if (!searching && results.isEmpty()) {
                item {
                    SearchEmpty(
                        title = "没有找到“$query”",
                        subtitle = "试试更短的关键词或搜索正文内容"
                    )
                }
            } else {
                items(
                    items = results,
                    key = { hit ->
                        "${hit.book.id}-${hit.kind}-${hit.chapterIndex ?: -1}"
                    }
                ) { hit ->
                    SearchResultRow(
                        hit = hit,
                        query = query,
                        onClick = { onOpenBook(hit.book.id, hit.chapterIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentBook(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(94.dp)
            .clickable(onClick = onClick)
    ) {
        BookCover(book = book, compact = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, query: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(
            book = hit.book,
            compact = true,
            modifier = Modifier.width(48.dp)
        )
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hit.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (hit.kind == SearchKind.CONTENT) {
                        Forest.copy(alpha = 0.08f)
                    } else {
                        Color(0xFFF4E7DB)
                    }
                ) {
                    Text(
                        text = if (hit.kind == SearchKind.CONTENT) "正文" else "书籍",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hit.kind == SearchKind.CONTENT) Forest else InkMuted
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    hit.snippet != null -> {
                        val chapter = hit.chapterTitle?.let { "$it · " }.orEmpty()
                        "$chapter${hit.snippet}"
                    }
                    else -> "${hit.book.author} · ${hit.book.category} · ${hit.book.format.label}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = Hairline,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SearchModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) Forest else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Hairline)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else InkMuted
        )
    }
}

@Composable
private fun SourceResultRow(remote: RemoteBook, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = Forest
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = remote.title.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = remote.title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOf(
                    remote.author,
                    remote.source.name
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!remote.latestChapter.isNullOrBlank()) {
                Text(
                    text = remote.latestChapter,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = Hairline,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SearchEmpty(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Forest.copy(alpha = 0.07f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Forest,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(13.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted
        )
    }
}

@Suppress("unused")
private fun highlighted(text: String, query: String): AnnotatedString = buildAnnotatedString {
    val start = text.indexOf(query, ignoreCase = true)
    if (start < 0) {
        append(text)
    } else {
        append(text.substring(0, start))
        pushStyle(
            SpanStyle(
                color = Forest,
                fontWeight = FontWeight.Bold,
                background = Forest.copy(alpha = 0.08f)
            )
        )
        append(text.substring(start, start + query.length))
        pop()
        append(text.substring(start + query.length))
    }
}
