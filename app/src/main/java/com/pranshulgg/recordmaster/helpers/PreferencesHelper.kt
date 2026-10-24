package com.pranshulgg.recordmaster.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * RecordMaster's appearance preferences. Only the string/bool accessors used by
 * the existing settings screens are kept; feature preferences live in
 * [com.pranshulgg.recordmaster.core.data.PreferencesManager].
 */
object PreferencesHelper {

    private const val PREF_NAME = "app_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun setString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun setBool(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun getBool(key: String): Boolean? =
        if (prefs.contains(key)) prefs.getBoolean(key, false) else null
}
