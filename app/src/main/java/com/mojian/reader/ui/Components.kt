package com.mojian.reader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojian.reader.data.Book
import com.mojian.reader.data.ReadingStatus
import com.mojian.reader.ui.theme.Charcoal
import com.mojian.reader.ui.theme.Clay
import com.mojian.reader.ui.theme.DustyBlue
import com.mojian.reader.ui.theme.Forest
import com.mojian.reader.ui.theme.Hairline
import com.mojian.reader.ui.theme.Ink
import com.mojian.reader.ui.theme.InkMuted
import com.mojian.reader.ui.theme.Mustard
import com.mojian.reader.ui.theme.Paper
import com.mojian.reader.ui.theme.Plum
import com.mojian.reader.ui.theme.Surface
import kotlin.math.roundToInt

private val coverColors = listOf(Forest, DustyBlue, Clay, Mustard, Plum, Charcoal)

@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val background = coverColors[book.accentIndex.mod(coverColors.size)]
    val bitmap = remember(book.coverPath) {
        book.coverPath?.let { path ->
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "${book.title}封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (compact) 4.dp else 6.dp)
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(start = 10.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (compact) 12.dp else 17.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = book.title,
                    color = Color(0xFFFFFBF2),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 16.sp else 21.sp,
                    lineHeight = if (compact) 21.sp else 28.sp,
                    maxLines = if (compact) 3 else 4,
                    overflow = TextOverflow.Ellipsis
                )
                Column {
                    Box(
                        modifier = Modifier
                            .width(if (compact) 18.dp else 28.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.45f))
                    )
                    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                    Text(
                        text = book.author,
                        color = Color.White.copy(alpha = 0.82f),
                        fontFamily = FontFamily.Serif,
                        fontSize = if (compact) 10.sp else 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun BookGridItem(
    book: Book,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box {
            BookCover(
                book = book,
                compact = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.24f)
            ) {
                IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "书籍选项",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.author,
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    book.status == ReadingStatus.FINISHED -> "读完"
                    book.progressPercent > 0 -> "${book.progressPercent}%"
                    else -> book.format.label
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (book.progressPercent > 0) Forest else InkMuted
            )
        }
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = { book.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Forest,
            trackColor = Hairline
        )
    }
}

@Composable
fun ContinueReadingCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Forest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "继续阅读",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = book.title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (book.chapterCount > 0) {
                        "第 ${(book.chapterIndex + 1).coerceAtMost(book.chapterCount)} / ${book.chapterCount} 章"
                    } else {
                        book.author
                    },
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { book.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFE4B474),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${book.progressPercent}%",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = "继续阅读",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SearchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索书名、作者或正文"
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
        }
    }
}

@Composable
fun AppBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = Surface,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selected == AppTab.SHELF,
            onClick = { onSelect(AppTab.SHELF) },
            icon = {
                Icon(Icons.Rounded.LibraryBooks, contentDescription = null)
            },
            label = { Text("书架") },
            colors = navigationColors()
        )
        NavigationBarItem(
            selected = selected == AppTab.SOURCES,
            onClick = { onSelect(AppTab.SOURCES) },
            icon = {
                Icon(Icons.Rounded.RssFeed, contentDescription = null)
            },
            label = { Text("书源") },
            colors = navigationColors()
        )
        NavigationBarItem(
            selected = selected == AppTab.SEARCH,
            onClick = { onSelect(AppTab.SEARCH) },
            icon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            label = { Text("搜索") },
            colors = navigationColors()
        )
        NavigationBarItem(
            selected = selected == AppTab.SETTINGS,
            onClick = { onSelect(AppTab.SETTINGS) },
            icon = {
                Icon(Icons.Rounded.Settings, contentDescription = null)
            },
            label = { Text("设置") },
            colors = navigationColors()
        )
    }
}

@Composable
private fun navigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Forest,
    selectedTextColor = Forest,
    indicatorColor = Forest.copy(alpha = 0.1f),
    unselectedIconColor = InkMuted,
    unselectedTextColor = InkMuted
)

@Composable
fun AddCategoryChip(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color.Transparent,
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "新建分类",
                modifier = Modifier.size(16.dp),
                tint = InkMuted
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "分类",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted
            )
        }
    }
}

fun formatBookCount(count: Int): String = "$count 本"

fun formatWordCount(count: Int): String = when {
    count >= 10000 -> "${(count / 1000f).roundToInt() / 10f} 万字"
    count >= 1000 -> "${(count / 100f).roundToInt() / 10f} 千字"
    else -> "$count 字"
}

val AppBackground = Paper

enum class AppTab {
    SHELF,
    SOURCES,
    SEARCH,
    SETTINGS
}
