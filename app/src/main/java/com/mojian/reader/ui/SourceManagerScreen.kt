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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojian.reader.data.BookSource
import com.mojian.reader.data.SourceHealth
import com.mojian.reader.data.SourceUiState
import com.mojian.reader.ui.theme.Forest
import com.mojian.reader.ui.theme.Hairline
import com.mojian.reader.ui.theme.Ink
import com.mojian.reader.ui.theme.InkMuted
import com.mojian.reader.ui.theme.Paper
import com.mojian.reader.ui.theme.Surface

private enum class SourceSort(val label: String) {
    SMART("智能"),
    WEIGHT("权重"),
    HEALTH("健康"),
    NAME("名称")
}

@Composable
fun SourceManagerScreen(
    state: SourceUiState,
    onBack: () -> Unit,
    onToggleEnabled: (BookSource, Boolean) -> Unit,
    onToggleExplore: (BookSource, Boolean) -> Unit,
    onWeightChange: (BookSource, Int) -> Unit,
    onDelete: (BookSource) -> Unit,
    onTest: (BookSource) -> Unit,
    onRefreshAll: () -> Unit,
    onImport: () -> Unit,
    onAdd: (String, String, String, Int, String) -> Unit
) {
    var selectedGroup by rememberSaveable { mutableStateOf("全部") }
    var sortMode by rememberSaveable { mutableStateOf(SourceSort.SMART) }
    var autoSort by rememberSaveable { mutableStateOf(true) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val groups = remember(state.sources) {
        buildList {
            add("全部")
            addAll(state.sources.map { it.group }.distinct().filter { it.isNotBlank() })
        }
    }
    val filtered = remember(state.sources, selectedGroup, sortMode, autoSort) {
        val subset = if (selectedGroup == "全部") {
            state.sources
        } else {
            state.sources.filter { it.group == selectedGroup }
        }
        if (autoSort || sortMode == SourceSort.SMART) {
            subset.sortedWith(
                compareByDescending<BookSource> { it.enabled }
                    .thenByDescending { healthRank(it.health) }
                    .thenByDescending { it.weight }
                    .thenBy { it.name }
            )
        } else {
            when (sortMode) {
                SourceSort.WEIGHT -> subset.sortedByDescending { it.weight }
                SourceSort.HEALTH -> subset.sortedByDescending { it.successRate }
                else -> subset.sortedBy { it.name }
            }
        }
    }
    val healthy = state.sources.count { it.health == SourceHealth.GOOD }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = Ink
                )
            }
            Text(
                text = "书源",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onImport) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = "导入书源",
                    tint = Forest
                )
            }
            if (state.refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Forest
                )
            } else {
                IconButton(onClick = onRefreshAll) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "检测全部书源",
                        tint = Forest
                    )
                }
            }
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "添加书源",
                    tint = Forest
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SourceStat(state.sources.size, "全部")
            SourceStat(state.sources.count { it.enabled }, "启用")
            SourceStat(healthy, "正常")
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            groups.forEach { group ->
                Surface(
                    modifier = Modifier.clickable { selectedGroup = group },
                    shape = CircleShape,
                    color = if (selectedGroup == group) Forest else Color.Transparent,
                    border = if (selectedGroup == group) null else BorderStroke(1.dp, Hairline)
                ) {
                    Text(
                        text = group,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedGroup == group) Color.White else InkMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "自动排序",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink
                )
                Text(
                    text = "按启用、健康、权重自动排列",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
            }
            Switch(
                checked = autoSort,
                onCheckedChange = { autoSort = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Forest,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Hairline
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "排序",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted
            )
            Spacer(Modifier.width(2.dp))
            SourceSort.entries.forEach { mode ->
                TextButton(
                    onClick = {
                        sortMode = mode
                        if (mode != SourceSort.SMART) autoSort = false
                    },
                    enabled = !autoSort || mode == SourceSort.SMART
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sortMode == mode) Forest else InkMuted,
                        fontWeight = if (sortMode == mode) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 42.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = InkMuted,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (state.sources.isEmpty()) "还没有书源" else "这个分组没有书源",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { source ->
                    SourceCard(
                        source = source,
                        testing = state.testingId == source.id,
                        onToggleEnabled = { onToggleEnabled(source, it) },
                        onToggleExplore = { onToggleExplore(source, it) },
                        onWeightChange = { onWeightChange(source, it) },
                        onDelete = { onDelete(source) },
                        onTest = { onTest(source) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, group, weight, searchUrl ->
                onAdd(name, url, group, weight, searchUrl)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SourceCard(
    source: BookSource,
    testing: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleExplore: (Boolean) -> Unit,
    onWeightChange: (Int) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Surface,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = healthColor(source.health).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = source.health.label,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = healthColor(source.health)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = source.group,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除书源",
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.url.ifBlank { "未填写地址" },
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "启用",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = source.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Forest,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Hairline
                    )
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "发现",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = source.enabledExplore,
                    onCheckedChange = onToggleExplore,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Forest,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Hairline
                    )
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onWeightChange(source.weight - 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "降低权重",
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = source.weight.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Forest
                )
                IconButton(onClick = { onWeightChange(source.weight + 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "提高权重",
                        tint = Forest,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Forest
                    )
                } else {
                    IconButton(onClick = onTest, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "测试书源",
                            tint = Forest,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (source.lastError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = source.lastError,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFA23D34)
                )
            }
        }
    }
}

@Composable
private fun SourceStat(value: Int, label: String) {
    Column {
        Text(
            text = value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp,
            color = Ink
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted
        )
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, group: String, weight: Int, searchUrl: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var searchUrl by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加书源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("站点地址") }, singleLine = true)
                OutlinedTextField(value = searchUrl, onValueChange = { searchUrl = it }, label = { Text("搜索路径") }, singleLine = true)
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("分组") }, singleLine = true)
                OutlinedTextField(value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '-' }.take(5) }, label = { Text("权重") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        url,
                        group,
                        weight.toIntOrNull() ?: 0,
                        searchUrl
                    )
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = Surface,
        shape = RoundedCornerShape(8.dp)
    )
}

private fun healthColor(health: SourceHealth): Color = when (health) {
    SourceHealth.GOOD -> Color(0xFF3C7A4B)
    SourceHealth.WARN -> Color(0xFFB07724)
    SourceHealth.BAD -> Color(0xFFA23D34)
    SourceHealth.UNKNOWN -> InkMuted
}

private fun healthRank(health: SourceHealth): Int = when (health) {
    SourceHealth.GOOD -> 3
    SourceHealth.UNKNOWN -> 2
    SourceHealth.WARN -> 1
    SourceHealth.BAD -> 0
}
