package com.mojian.reader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class RemoteCacheRepository(context: Context) {
    private val root = context.filesDir.resolve("remote_cache")
    private val _sizeBytes = MutableStateFlow(calculateSize())
    val sizeBytes: StateFlow<Long> = _sizeBytes.asStateFlow()

    suspend fun get(sourceId: String, url: String): String? = withContext(Dispatchers.IO) {
        file(sourceId, url).takeIf { it.exists() }?.readText()
    }

    suspend fun put(sourceId: String, url: String, content: String) = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext
        root.mkdirs()
        val file = file(sourceId, url)
        file.parentFile?.mkdirs()
        file.writeText(content)
        _sizeBytes.value = calculateSize()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (root.exists()) root.deleteRecursively()
        _sizeBytes.value = 0L
    }

    private fun file(sourceId: String, url: String): File {
        val safeSource = sourceId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
        val hash = url.hashCode().toUInt().toString(16)
        return root.resolve(safeSource).resolve("$hash.txt")
    }

    private fun calculateSize(): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
