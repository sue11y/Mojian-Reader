package com.mojian.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojian.reader.data.RemoteBook
import com.mojian.reader.data.RemoteChapter
import com.mojian.reader.ui.theme.Forest
import com.mojian.reader.ui.theme.Hairline
import com.mojian.reader.ui.theme.Ink
import com.mojian.reader.ui.theme.InkMuted
import com.mojian.reader.ui.theme.Surface
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceBookDetailSheet(
    remote: RemoteBook,
    onDismiss: () -> Unit,
    onFetchChapters: suspend (sourceId: String, url: String) -> List<RemoteChapter>,
    onReadChapters: (List<RemoteChapter>, Int) -> Unit,
    onImport: suspend (RemoteBook, List<RemoteChapter>) -> Unit,
    onFavorite: suspend (RemoteBook, List<RemoteChapter>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var chapters by remember(remote.url) { mutableStateOf<List<RemoteChapter>?>(null) }
    var loading by remember(remote.url) { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(remote.url) {
        loading = true
        chapters = runCatching { onFetchChapters(remote.source.id, remote.url) }
            .getOrDefault(emptyList())
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
                .padding(bottom = 22.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Forest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = remote.title.take(1),
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = remote.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(remote.author, remote.source.name)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = InkMuted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "目录",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Forest
                    )
                    chapters != null -> Text(
                        text = "${chapters?.size ?: 0} 章",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val chapterList = chapters
            when {
                chapterList == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (loading) "正在读取目录" else "没有解析到目录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }
                }
                chapterList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "这个书源的目录规则为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        itemsIndexed(chapterList, key = { _, chapter -> chapter.url }) { position, chapter ->
                            Text(
                                text = chapter.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onReadChapters(chapterList, position) }
                                    .padding(vertical = 7.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (!importing && !chapterList.isNullOrEmpty()) {
                        importing = true
                        scope.launch {
                            onImport(remote, chapterList)
                            importing = false
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                enabled = !importing && !chapterList.isNullOrEmpty(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (importing) "正在导入" else "导入全部章节")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    if (!importing && !chapterList.isNullOrEmpty()) {
                        scope.launch {
                            onFavorite(remote, chapterList)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                enabled = !importing && !chapterList.isNullOrEmpty(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("收藏到在线书架")
            }
        }
    }
}

data class RemoteReadSession(
    val remote: RemoteBook,
    val chapters: List<RemoteChapter>,
    val initialIndex: Int,
    val initialOffset: Int = 0,
    val initialOffsetPx: Int = 0,
    val initialPageMode: com.mojian.reader.data.RemotePageMode = com.mojian.reader.data.RemotePageMode.VERTICAL,
    val favoriteId: String? = null
)
