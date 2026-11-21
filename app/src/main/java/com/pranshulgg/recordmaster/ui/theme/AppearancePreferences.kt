package com.pranshulgg.recordmaster.ui.theme

import com.pranshulgg.recordmaster.helpers.PreferencesHelper

/**
 * Appearance switches live in the same SharedPreferences store as the existing
 * theme settings, so the theme can be resolved synchronously on first compose.
 */
object AppearancePreferences {
    private const val KEY_BLACK_AND_WHITE = "useBlackAndWhite"
    private const val KEY_AMOLED_MODE = "useAmoledMode"

    var blackAndWhite: Boolean
        get() = PreferencesHelper.getBool(KEY_BLACK_AND_WHITE) ?: false
        set(value) {
            PreferencesHelper.setBool(KEY_BLACK_AND_WHITE, value)
        }

    var amoledMode: Boolean
        get() = PreferencesHelper.getBool(KEY_AMOLED_MODE) ?: false
        set(value) {
            PreferencesHelper.setBool(KEY_AMOLED_MODE, value)
        }
}
