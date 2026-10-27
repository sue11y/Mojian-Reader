package com.mojian.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojian.reader.data.Book
import com.mojian.reader.data.RemoteFavorite
import com.mojian.reader.data.ReadingStatus
import com.mojian.reader.ui.theme.Forest
import com.mojian.reader.ui.theme.Hairline
import com.mojian.reader.ui.theme.Ink
import com.mojian.reader.ui.theme.InkMuted
import com.mojian.reader.ui.theme.Paper
import com.mojian.reader.ui.theme.Surface
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ShelfScreen(
    books: List<Book>,
    categories: List<String>,
    remoteFavorites: List<RemoteFavorite>,
    onOpenBook: (String) -> Unit,
    onOpenRemoteFavorite: (RemoteFavorite) -> Unit,
    onBookOptions: (String) -> Unit,
    onImport: () -> Unit,
    onSearch: () -> Unit,
    onAddCategory: (String) -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf("全部") }
    var showCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }

    val categoryOptions = remember(categories, books) {
        buildList {
            add("全部")
            add("在读")
            addAll(categories.distinct())
            addAll(books.map { it.category }.filter { it.isNotBlank() }.distinct())
        }.distinct()
    }
    val filteredBooks = remember(books, selectedCategory) {
        when (selectedCategory) {
            "全部" -> books
            "在读" -> books.filter { it.status == ReadingStatus.READING }
            else -> books.filter { it.category == selectedCategory }
        }.sortedWith(compareByDescending<Book> { it.lastOpenedAt }.thenByDescending { it.addedAt })
    }
    val continueBook = remember(books) {
        books.filter { it.status == ReadingStatus.READING && it.progress > 0f }
            .maxByOrNull { it.lastOpenedAt }
            ?: books.maxByOrNull { it.lastOpenedAt }?.takeIf { it.progress > 0f }
    }
    val totalWords = remember(books) { books.sumOf { it.wordCount } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Forest
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFFF9EED8),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "墨间",
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = todayLabel(),
                                color = InkMuted,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clickable(onClick = onImport),
                            shape = CircleShape,
                            color = Surface,
                            border = BorderStroke(1.dp, Hairline)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.FileOpen,
                                    contentDescription = "导入书籍",
                                    tint = Forest,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                    Text(
                        text = greeting(),
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "我的书架",
                        color = Ink,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        lineHeight = 44.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${formatBookCount(books.size)}  ·  ${formatWordCount(totalWords)}",
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(22.dp))
                    SearchEntry(onClick = onSearch)
                }
            }

            if (continueBook != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueReadingCard(
                        book = continueBook,
                        onClick = { onOpenBook(continueBook.id) }
                    )
                }
            }

            if (remoteFavorites.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            text = "在线收藏",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            remoteFavorites.forEach { favorite ->
                                RemoteFavoriteCard(
                                    favorite = favorite,
                                    onClick = { onOpenRemoteFavorite(favorite) }
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedCategory == "全部") "全部藏书" else selectedCategory,
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${filteredBooks.size} 本",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categoryOptions.forEach { category ->
                            CategoryChip(
                                label = category,
                                selected = category == selectedCategory,
                                onClick = { selectedCategory = category }
                            )
                        }
                        AddCategoryChip(onClick = { showCategoryDialog = true })
                    }
                }
            }

            if (filteredBooks.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyShelf(
                        hasQuery = selectedCategory != "全部",
                        onImport = onImport
                    )
                }
            } else {
                items(filteredBooks, key = { it.id }) { book ->
                    BookGridItem(
                        book = book,
                        onClick = { onOpenBook(book.id) },
                        onMore = { onBookOptions(book.id) }
                    )
                }
            }
        }
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it.take(12) },
                    label = { Text("分类名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCategory(newCategory)
                        newCategory = ""
                        showCategoryDialog = false
                    },
                    enabled = newCategory.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun RemoteFavoriteCard(favorite: RemoteFavorite, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Forest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = favorite.title,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = favorite.sourceName,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (favorite.chapters.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${favorite.chapterIndex + 1}/${favorite.chapters.size} 章",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) Forest else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Hairline)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyShelf(hasQuery: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = Forest.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    tint = Forest,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (hasQuery) "这个分类还没有书" else "书架还是空的",
            style = MaterialTheme.typography.titleMedium,
            color = Ink
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (hasQuery) "从其他分类移动一本书到这里" else "导入第一本书，开始你的阅读",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted
        )
        if (!hasQuery) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onImport,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入书籍")
            }
        }
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..10 -> "早上好，今天读点什么"
        in 11..13 -> "午间好，翻开一页吧"
        in 14..18 -> "下午好，留一点安静时间"
        else -> "晚上好，灯下好读书"
    }
}

private fun todayLabel(): String {
    val formatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
    return LocalDate.now().format(formatter)
}
