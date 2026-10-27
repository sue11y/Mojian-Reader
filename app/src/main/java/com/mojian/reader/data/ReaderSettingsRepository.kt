package com.mojian.reader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ReaderSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ReaderPreferences> = _state.asStateFlow()

    fun setTheme(theme: ReaderThemeStyle) {
        update(_state.value.copy(theme = theme))
    }

    fun setFontSize(size: Float) {
        update(_state.value.copy(fontSize = size.coerceIn(15f, 28f)))
    }

    fun setLineHeight(height: Float) {
        update(_state.value.copy(lineHeight = height.coerceIn(1.35f, 2.2f)))
    }

    fun setSerif(enabled: Boolean) {
        update(_state.value.copy(useSerif = enabled))
    }

    private fun update(value: ReaderPreferences) {
        _state.update { value }
        preferences.edit()
            .putString("theme", value.theme.name)
            .putFloat("font_size", value.fontSize)
            .putFloat("line_height", value.lineHeight)
            .putBoolean("serif", value.useSerif)
            .apply()
    }

    private fun load(): ReaderPreferences {
        val theme = runCatching {
            ReaderThemeStyle.valueOf(
                preferences.getString("theme", ReaderThemeStyle.PAPER.name)
                    ?: ReaderThemeStyle.PAPER.name
            )
        }.getOrDefault(ReaderThemeStyle.PAPER)
        return ReaderPreferences(
            theme = theme,
            fontSize = preferences.getFloat("font_size", 19f),
            lineHeight = preferences.getFloat("line_height", 1.75f),
            useSerif = preferences.getBoolean("serif", true)
        )
    }
}
