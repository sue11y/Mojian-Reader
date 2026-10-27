package com.mojian.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mojian.reader.data.Bookmark
import com.mojian.reader.data.ImportProgress
import com.mojian.reader.data.ReadingStatus
import com.mojian.reader.ui.theme.Forest
import com.mojian.reader.ui.theme.ForestSoft
import com.mojian.reader.ui.theme.Hairline
import com.mojian.reader.ui.theme.Ink
import com.mojian.reader.ui.theme.InkMuted
import com.mojian.reader.ui.theme.Surface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    importing: ImportProgress?,
    onDismiss: () -> Unit,
    onPickFiles: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 30.dp)
        ) {
            Text(
                text = "导入书籍",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "文件会复制到本机书架，原文件不受影响",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
            Spacer(Modifier.height(22.dp))
            if (importing != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ForestSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = Forest
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "正在解析 ${importing.fileName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${importing.current}/${importing.total}",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkMuted
                            )
                        }
                        Spacer(Modifier.height(13.dp))
                        LinearProgressIndicator(
                            progress = {
                                importing.current.toFloat() / importing.total.coerceAtLeast(1)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = Forest,
                            trackColor = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = importing == null, onClick = onPickFiles),
                color = Forest,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.FileOpen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "从手机文件中选择",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "一次可以导入多本书",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "支持格式",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted
            )
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("EPUB", "TXT", "PDF", "DOCX", "HTML", "MD").forEach { format ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF2EDE3),
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Text(
                            text = format,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "TXT 会自动识别 UTF-8 和 GB18030 编码，并按章节标题拆分。超大 PDF 首次渲染可能需要几秒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailSheet(
    book: Book,
    categories: List<String>,
    onDismiss: () -> Unit,
    onCategoryChange: (String) -> Unit,
    onStatusChange: (ReadingStatus) -> Unit,
    onDelete: () -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                BookCover(
                    book = book,
                    modifier = Modifier.width(88.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp,
                        lineHeight = 32.sp,
                        color = Ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetadataTag(book.format.label)
                        MetadataTag(book.category)
                        MetadataTag("${book.progressPercent}%")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = InkMuted
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Divider(color = Hairline)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "阅读状态",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingStatus.entries.forEach { status ->
                    ChoiceChip(
                        label = status.label,
                        selected = book.status == status,
                        onClick = { onStatusChange(status) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "分类",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    ChoiceChip(
                        label = category,
                        selected = book.category == category,
                        onClick = { onCategoryChange(category) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "书籍信息",
                style = MaterialTheme.typography.titleMedium,
                color = Ink
            )
            Spacer(Modifier.height(8.dp))
            InfoRow("章节", "${book.chapterCount} 章")
            InfoRow("字数", formatWordCount(book.wordCount))
            InfoRow("文件", book.sourceName)
            InfoRow("添加时间", formatDate(book.addedAt))

            if (book.bookmarks.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "书签",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(Modifier.height(4.dp))
                book.bookmarks.sortedBy { it.chapterIndex }.forEach { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = Forest,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookmark.chapterTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink
                            )
                            Text(
                                text = bookmark.preview,
                                style = MaterialTheme.typography.labelMedium,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveBookmark(bookmark) }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "删除书签",
                                tint = InkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Divider(color = Hairline)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF8EAE7),
                    contentColor = Color(0xFFA23D34)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text("从书架删除")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除《${book.title}》？") },
            text = { Text("书库中的正文、封面和阅读进度都会从这台设备移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = Color(0xFFA23D34))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = Surface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun MetadataTag(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF2EDE3)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) Forest else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Hairline)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else InkMuted
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.width(74.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "未知"
    return SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE).format(Date(timestamp))
}
