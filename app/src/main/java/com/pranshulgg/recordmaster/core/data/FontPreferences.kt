package com.pranshulgg.recordmaster.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pranshulgg.recordmaster.core.domain.AppFont
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.fontPreferencesDataStore by preferencesDataStore(name = "recordmaster_font_prefs")

/** Persists the user's app-wide typography choice. */
class FontPreferences(private val context: Context) {

    private object Keys {
        val SELECTED_FONT = stringPreferencesKey("selected_font")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val CUSTOM_FONT_GENERATION = longPreferencesKey("custom_font_generation")
    }

    val selectedFontFlow: Flow<AppFont> =
        context.fontPreferencesDataStore.data.map { prefs ->
            AppFont.fromId(prefs[Keys.SELECTED_FONT])
        }

    /** Display name of the imported font, retained when a built-in is active. */
    val customFontNameFlow: Flow<String?> =
        context.fontPreferencesDataStore.data.map { prefs ->
            prefs[Keys.CUSTOM_FONT_NAME]
        }

    /** Bumped on each import so replacing a same-named file still refreshes the theme. */
    val customFontGenerationFlow: Flow<Long> =
        context.fontPreferencesDataStore.data.map { prefs ->
            prefs[Keys.CUSTOM_FONT_GENERATION] ?: 0L
        }

    suspend fun selectedFont(): AppFont = selectedFontFlow.first()

    suspend fun setSelectedFont(font: AppFont) {
        context.fontPreferencesDataStore.edit { prefs ->
            prefs[Keys.SELECTED_FONT] = font.id
        }
    }

    /** Imports a name and selects the custom font in one DataStore update. */
    suspend fun setCustomFont(displayName: String) {
        context.fontPreferencesDataStore.edit { prefs ->
            prefs[Keys.SELECTED_FONT] = AppFont.CUSTOM.id
            prefs[Keys.CUSTOM_FONT_NAME] = displayName
            prefs[Keys.CUSTOM_FONT_GENERATION] =
                (prefs[Keys.CUSTOM_FONT_GENERATION] ?: 0L) + 1
        }
    }
}
