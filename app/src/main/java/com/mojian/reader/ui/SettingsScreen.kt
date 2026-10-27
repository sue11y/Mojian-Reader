package com.mojian.reader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mojian.reader.data.Book
import com.mojian.reader.data.ReaderPreferences
import com.mojian.reader.data.ReaderThemeStyle
import com.mojian.reader.data.readerPalette
import com.mojian.reader.ui.theme.Forest
import com.mojian.reader.ui.theme.Hairline
import com.mojian.reader.ui.theme.Ink
import com.mojian.reader.ui.theme.InkMuted
import com.mojian.reader.ui.theme.Paper
import com.mojian.reader.ui.theme.Surface
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    books: List<Book>,
    preferences: ReaderPreferences,
    cacheSizeBytes: Long,
    onThemeChange: (ReaderThemeStyle) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onClearCache: () -> Unit
) {
    val totalWords = books.sumOf { it.wordCount }
    val bookmarks = books.sumOf { it.bookmarks.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 34.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineLarge,
            color = Ink
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            StatItem(
                value = books.size.toString(),
                label = "藏书",
                modifier = Modifier.weight(1f)
            )
            StatItem(
                value = formatWordCount(totalWords),
                label = "文字",
                modifier = Modifier.weight(1f)
            )
            StatItem(
                value = bookmarks.toString(),
                label = "书签",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(30.dp))
        SettingsSectionTitle("阅读外观")
        Spacer(Modifier.height(15.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ReaderThemeStyle.entries.forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = preferences.theme == theme,
                    onClick = { onThemeChange(theme) },
                    modifier = Modifier.width(78.dp)
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "正文字号",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = "${preferences.fontSize.roundToInt()} pt",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
            }
            Text(
                text = "Aa",
                fontFamily = FontFamily.Serif,
                fontSize = preferences.fontSize.sp,
                color = Ink
            )
        }
        Slider(
            value = preferences.fontSize,
            onValueChange = onFontSizeChange,
            valueRange = 15f..28f,
            steps = 12,
            colors = SliderDefaults.colors(
                thumbColor = Forest,
                activeTrackColor = Forest,
                inactiveTrackColor = Hairline
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "行间距",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = "更舒展的阅读节奏",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
            }
            Text(
                text = String.format("%.2f", preferences.lineHeight),
                style = MaterialTheme.typography.labelLarge,
                color = Forest
            )
        }
        Slider(
            value = preferences.lineHeight,
            onValueChange = onLineHeightChange,
            valueRange = 1.35f..2.2f,
            steps = 16,
            colors = SliderDefaults.colors(
                thumbColor = Forest,
                activeTrackColor = Forest,
                inactiveTrackColor = Hairline
            )
        )

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSerifChange(!preferences.useSerif) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "衬线正文",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = "长文阅读更接近纸书",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
            }
            Switch(
                checked = preferences.useSerif,
                onCheckedChange = onSerifChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Forest,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Hairline
                )
            )
        }

        Spacer(Modifier.height(30.dp))
        SettingsSectionTitle("数据")
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClearCache),
            shape = RoundedCornerShape(8.dp),
            color = Surface,
            border = BorderStroke(1.dp, Hairline)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = Forest.copy(alpha = 0.08f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = Forest,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "清理在线缓存",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    Text(
                        text = "当前 ${formatCacheSize(cacheSizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExport),
            shape = RoundedCornerShape(8.dp),
            color = Surface,
            border = BorderStroke(1.dp, Hairline)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = Forest.copy(alpha = 0.08f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            tint = Forest,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "导出完整备份",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    Text(
                        text = "包含书籍、封面、进度和书签",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "所有数据只保存在这台设备上。建议定期把备份文件移到网盘或电脑。",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(30.dp))
        Divider(color = Hairline)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(8.dp),
                color = Forest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFFF7EBD3),
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "墨间",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = "版本 2.6.1 · 本地阅读器",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted
                )
            }
        }
    }
}

private fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    return when {
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024f / 1024f)
        else -> "${(bytes / 1024f).toInt()} KB"
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
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
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = Ink
    )
}

@Composable
private fun ThemeSwatch(
    theme: ReaderThemeStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = readerPalette(theme)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = palette.background,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) Forest else Hairline
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "阅",
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = palette.text
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = theme.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (theme == ReaderThemeStyle.AMOLED) Color.White else Forest,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
