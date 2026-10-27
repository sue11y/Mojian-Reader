package com.mojian.reader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class RemoteLibraryRepository(private val context: Context) {
    private val file = context.filesDir.resolve("remote_library.json")
    private val _state = MutableStateFlow(RemoteLibraryState())
    val state: StateFlow<RemoteLibraryState> = _state.asStateFlow()

    suspend fun initialize() {
        if (_state.value.isReady) return
        withContext(Dispatchers.IO) {
            val loaded = if (file.exists()) {
                runCatching { readFavorites() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _state.update { it.copy(favorites = loaded, isReady = true) }
        }
    }

    suspend fun addFavorite(remote: RemoteBook, chapters: List<RemoteChapter>): Boolean =
        withContext(Dispatchers.IO) {
            val exists = _state.value.favorites.any {
                it.sourceId == remote.source.id && it.url == remote.url
            }
            if (exists) return@withContext false
            val favorite = RemoteFavorite(
                id = UUID.randomUUID().toString(),
                sourceId = remote.source.id,
                sourceName = remote.source.name,
                title = remote.title,
                author = remote.author,
                url = remote.url,
                coverUrl = remote.coverUrl,
                latestChapter = remote.latestChapter,
                addedAt = System.currentTimeMillis(),
                chapters = chapters
            )
            _state.update { it.copy(favorites = listOf(favorite) + it.favorites) }
            save()
            true
        }

    suspend fun removeFavorite(id: String) = withContext(Dispatchers.IO) {
        _state.update { it.copy(favorites = it.favorites.filterNot { fav -> fav.id == id }) }
        save()
    }

    suspend fun updateProgress(
        id: String,
        chapterIndex: Int,
        chapterOffset: Int,
        chapterOffsetPx: Int = 0
    ) =
        withContext(Dispatchers.IO) {
            _state.update { current ->
                current.copy(
                    favorites = current.favorites.map { favorite ->
                        if (favorite.id == id) {
                            favorite.copy(
                                chapterIndex = chapterIndex.coerceAtLeast(0),
                                chapterOffset = chapterOffset.coerceAtLeast(0),
                                chapterOffsetPx = chapterOffsetPx.coerceAtLeast(0)
                            )
                        } else {
                            favorite
                        }
                    }
                )
            }
            save()
        }

    suspend fun setPageMode(id: String, horizontal: Boolean) = withContext(Dispatchers.IO) {
        _state.update { current ->
            current.copy(
                favorites = current.favorites.map { favorite ->
                    if (favorite.id == id) favorite.copy(horizontalMode = horizontal) else favorite
                }
            )
        }
        save()
    }

    private fun save() {
        val array = JSONArray()
        _state.value.favorites.forEach { array.put(favoriteToJson(it)) }
        val temp = file.resolveSibling("remote_library.json.tmp")
        temp.writeText(array.toString())
        if (file.exists() && !file.delete()) {
            file.writeText(array.toString())
        } else if (!temp.renameTo(file)) {
            file.writeText(array.toString())
        }
        if (temp.exists()) temp.delete()
    }

    private fun readFavorites(): List<RemoteFavorite> {
        val array = JSONArray(file.readText())
        return List(array.length()) { index -> favoriteFromJson(array.getJSONObject(index)) }
    }

    private fun favoriteToJson(favorite: RemoteFavorite): JSONObject = JSONObject().apply {
        put("id", favorite.id)
        put("sourceId", favorite.sourceId)
        put("sourceName", favorite.sourceName)
        put("title", favorite.title)
        put("author", favorite.author)
        put("url", favorite.url)
        put("coverUrl", favorite.coverUrl)
        put("latestChapter", favorite.latestChapter)
        put("addedAt", favorite.addedAt)
        put("chapterIndex", favorite.chapterIndex)
        put("chapterOffset", favorite.chapterOffset)
        put("chapterOffsetPx", favorite.chapterOffsetPx)
        put("horizontalMode", favorite.horizontalMode)
        put("chapters", JSONArray().apply {
            favorite.chapters.forEach { chapter ->
                put(JSONObject().apply {
                    put("title", chapter.title)
                    put("url", chapter.url)
                    put("fallbackUrl", chapter.fallbackUrl)
                })
            }
        })
    }

    private fun favoriteFromJson(json: JSONObject): RemoteFavorite {
        val chapters = json.optJSONArray("chapters")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                RemoteChapter(
                    title = item.optString("title"),
                    url = item.optString("url"),
                    fallbackUrl = item.optString("fallbackUrl").takeIf { it.isNotBlank() }
                )
            }
        }.orEmpty()
        return RemoteFavorite(
            id = json.getString("id"),
            sourceId = json.optString("sourceId"),
            sourceName = json.optString("sourceName"),
            title = json.optString("title"),
            author = json.optString("author"),
            url = json.optString("url"),
            coverUrl = json.optString("coverUrl").takeIf { it.isNotBlank() },
            latestChapter = json.optString("latestChapter").takeIf { it.isNotBlank() },
            addedAt = json.optLong("addedAt"),
            chapterIndex = json.optInt("chapterIndex"),
            chapterOffset = json.optInt("chapterOffset"),
            chapterOffsetPx = json.optInt("chapterOffsetPx"),
            horizontalMode = json.optBoolean("horizontalMode"),
            chapters = chapters
        )
    }
}
