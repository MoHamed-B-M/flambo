package com.pranshulgg.recordmaster.ui.theme

import com.pranshulgg.recordmaster.helpers.PreferencesHelper

/**
 * The three appearance choices exposed in Settings.
 *
 * The value is stored in the existing "AppTheme" preference so installs that
 * used the old dialog keep their selection. "dark_theme" is only read as a
 * fallback for installs that predate "AppTheme".
 */
enum class AppThemeMode(val storageValue: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        private const val KEY_APP_THEME = "AppTheme"
        private const val KEY_DARK_THEME = "dark_theme"

        fun current(): AppThemeMode {
            val stored = PreferencesHelper.getString(KEY_APP_THEME)
            if (stored != null) {
                return entries.firstOrNull { it.storageValue == stored } ?: SYSTEM
            }
            return if (PreferencesHelper.getBool(KEY_DARK_THEME) == true) DARK else LIGHT
        }

        fun persist(mode: AppThemeMode, systemInDarkTheme: Boolean) {
            PreferencesHelper.setString(KEY_APP_THEME, mode.storageValue)
            // Keep the legacy boolean in sync for anything that still reads it.
            PreferencesHelper.setBool(KEY_DARK_THEME, mode.isDark(systemInDarkTheme))
        }
    }
}
