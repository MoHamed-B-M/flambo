package com.pranshulgg.recordmaster.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    }

    val selectedFontFlow: Flow<AppFont> =
        context.fontPreferencesDataStore.data.map { prefs ->
            AppFont.fromId(prefs[Keys.SELECTED_FONT])
        }

    suspend fun selectedFont(): AppFont = selectedFontFlow.first()

    suspend fun setSelectedFont(font: AppFont) {
        context.fontPreferencesDataStore.edit { prefs ->
            prefs[Keys.SELECTED_FONT] = font.id
        }
    }
}
