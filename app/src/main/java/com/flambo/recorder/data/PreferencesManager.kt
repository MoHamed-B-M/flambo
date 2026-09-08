package com.flambo.recorder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flambo.recorder.domain.RecordingQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flambo_prefs")

class PreferencesManager(private val context: Context) {

    private object Keys {
        val QUALITY = stringPreferencesKey("quality")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val RECORDING_REMINDER = booleanPreferencesKey("recording_reminder")
        val DARK_THEME = stringPreferencesKey("dark_theme") // system, light, dark
    }

    val qualityFlow: Flow<RecordingQuality> =
        context.dataStore.data.map { it[Keys.QUALITY]?.let { v -> runCatching { RecordingQuality.valueOf(v) }.getOrNull() } ?: RecordingQuality.HIGH }

    val dynamicColorFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

    val recordingReminderFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.RECORDING_REMINDER] ?: true }

    val darkThemeFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.DARK_THEME] ?: "system" }

    suspend fun setQuality(q: RecordingQuality) {
        context.dataStore.edit { it[Keys.QUALITY] = q.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setRecordingReminder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RECORDING_REMINDER] = enabled }
    }

    suspend fun setDarkTheme(mode: String) {
        context.dataStore.edit { it[Keys.DARK_THEME] = mode }
    }
}
