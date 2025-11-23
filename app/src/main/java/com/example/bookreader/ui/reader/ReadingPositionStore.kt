package com.example.bookreader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reading_prefs")

class ReadingPositionStore(private val context: Context) {

    private fun progressKeyFor(bookId: String) = floatPreferencesKey("progress_$bookId")


    suspend fun getProgress(bookId: String): Float {
        return context.dataStore.data.first()[progressKeyFor(bookId)] ?: 0f
    }

    suspend fun saveProgress(bookId: String, progress: Float) {
        context.dataStore.edit { prefs ->
            prefs[progressKeyFor(bookId)] = progress
        }
    }

    fun getProgressFlow(bookId: String) = context.dataStore.data
        .map { it[progressKeyFor(bookId)] ?: 0f }
}