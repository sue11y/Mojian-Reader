package com.mojian.reader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mojian.reader.data.Book
import com.mojian.reader.data.ReaderThemeStyle
import com.mojian.reader.ui.theme.Forest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfReaderScreen(
    book: Book,
    sourceFile: File,
    initialPage: Int,
    theme: ReaderThemeStyle,
    onBack: () -> Unit,
    onProgress: (pageIndex: Int, pageProgress: Float) -> Unit
) {
    val pfd = remember(book.id) {
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    val renderer = remember(book.id) { PdfRenderer(pfd) }
    var pageCount by remember { mutableIntStateOf(renderer.pageCount.coerceAtLeast(1)) }
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )
    val dark = theme == ReaderThemeStyle.NIGHT
    val background = if (dark) Color(0xFF191E1C) else Color(0xFFE9E5DC)
    val chrome = if (dark) Color(0xFF252C29) else Color(0xFFF8F4EB)
    val foreground = if (dark) Color(0xFFEDE9DF) else Color(0xFF25302B)

    DisposableEffect(book.id) {
        onDispose {
            renderer.close()
            pfd.close()
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        onProgress(
            pagerState.currentPage,
            (pagerState.currentPage + 1f) / pageCount
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
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
                    contentDescription = "返回",
                    tint = foreground
                )
            }
            Text(
                text = book.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${pagerState.currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = foreground.copy(alpha = 0.62f),
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            beyondViewportPageCount = 0,
            pageSpacing = 16.dp
        ) { page ->
            PdfPage(
                sourceFile = sourceFile,
                pageIndex = page,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            color = chrome,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            pagerState.requestScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    enabled = pagerState.currentPage > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "上一页",
                        tint = if (pagerState.currentPage > 0) foreground else foreground.copy(alpha = 0.25f)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(foreground.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((pagerState.currentPage + 1f) / pageCount)
                            .height(3.dp)
                            .background(Forest, RoundedCornerShape(2.dp))
                    )
                }
                IconButton(
                    onClick = {
                        if (pagerState.currentPage < pageCount - 1) {
                            pagerState.requestScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    enabled = pagerState.currentPage < pageCount - 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "下一页",
                        tint = if (pagerState.currentPage < pageCount - 1) {
                            foreground
                        } else {
                            foreground.copy(alpha = 0.25f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    sourceFile: File,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthPx = with(density) {
        (configuration.screenWidthDp.dp - 28.dp).roundToPx()
    }.coerceAtLeast(320)
    val bitmap by produceState<Bitmap?>(initialValue = null, sourceFile, pageIndex, widthPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(
                    sourceFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                ).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            val height = (widthPx * page.height.toFloat() / page.width)
                                .toInt()
                                .coerceAtLeast(1)
                            val output = Bitmap.createBitmap(
                                widthPx,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            Canvas(output).drawColor(AndroidColor.WHITE)
                            page.render(
                                output,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            output
                        }
                    }
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val pageBitmap = bitmap
        if (pageBitmap == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Forest,
                strokeWidth = 2.dp
            )
        } else {
            Image(
                bitmap = pageBitmap.asImageBitmap(),
                contentDescription = "PDF 第 ${pageIndex + 1} 页",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(pageBitmap.width.toFloat() / pageBitmap.height)
            )
        }
    }
}
