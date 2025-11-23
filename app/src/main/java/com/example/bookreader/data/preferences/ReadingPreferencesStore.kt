package com.example.bookreader.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_preferences")

enum class ReaderTheme {
    SYSTEM, LIGHT, DARK, SEPIA
}

data class ReaderPreferences(
    val fontScale: Float = 1.0f,
    val lineHeight: Float = 1.4f,
    val theme: ReaderTheme = ReaderTheme.SYSTEM
)

class ReadingPreferencesStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.dataStore

    val preferencesFlow: Flow<ReaderPreferences> = dataStore.data.map { prefs ->
        ReaderPreferences(
            fontScale = prefs[FONT_SCALE] ?: 1.0f,
            lineHeight = prefs[LINE_HEIGHT] ?: 1.4f,
            theme = prefs[READER_THEME]?.let { ReaderTheme.valueOf(it) } ?: ReaderTheme.SYSTEM
        )
    }

    fun progressFlow(bookId: String): Flow<Float> = dataStore.data.map { prefs ->
        prefs[stringPreferencesKey(progressKey(bookId))]?.toFloatOrNull() ?: 0f
    }

    suspend fun setFontScale(scale: Float) {
        dataStore.edit { prefs -> prefs[FONT_SCALE] = scale.coerceIn(0.8f, 2.0f) }
    }

    suspend fun setLineHeight(lineHeight: Float) {
        dataStore.edit { prefs -> prefs[LINE_HEIGHT] = lineHeight.coerceIn(1.0f, 2.0f) }
    }

    suspend fun setTheme(theme: ReaderTheme) {
        dataStore.edit { prefs -> prefs[READER_THEME] = theme.name }
    }

    suspend fun setProgress(bookId: String, progress: Float) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(progressKey(bookId))] = progress.coerceIn(0f, 1f).toString()
        }
    }

    private fun progressKey(bookId: String) = "progress_$bookId"

    companion object {
        private val FONT_SCALE = floatPreferencesKey("font_scale")
        private val LINE_HEIGHT = floatPreferencesKey("line_height")
        private val READER_THEME = stringPreferencesKey("reader_theme")
    }
}

