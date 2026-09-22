package com.flambo.recorder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flambo.recorder.domain.RecordingQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flambo_prefs")

class PreferencesManager(private val context: Context) {

    private object Keys {
        val QUALITY = stringPreferencesKey("quality")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val RECORDING_REMINDER = booleanPreferencesKey("recording_reminder")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val STT_LANGUAGE = stringPreferencesKey("stt_language")
        val STT_ENGINE = stringPreferencesKey("stt_engine")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val PENDING_APK_DELETE = stringPreferencesKey("pending_apk_delete")
        val NOTIFIED_UPDATE_VERSION = stringPreferencesKey("notified_update_version")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val NOISE_REDUCTION = booleanPreferencesKey("noise_reduction")
        val ENHANCE_STRENGTH = stringPreferencesKey("enhance_strength")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original")
        val RECORDINGS_VOLUME = stringPreferencesKey("recordings_volume")
        val THEME_SEED = stringPreferencesKey("theme_seed")
        val LAST_SEEN_VERSION_CODE = longPreferencesKey("last_seen_version_code")
        val TIP_INDEX = intPreferencesKey("tip_index")
        val TIPS_ENABLED = booleanPreferencesKey("tips_enabled")
        val RECORDING_PREFIX = stringPreferencesKey("recording_prefix")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val CUSTOM_FOLDER_URI = stringPreferencesKey("custom_folder_uri")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val GESTURE_ENABLED = booleanPreferencesKey("gesture_enabled")
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

    val sttLanguageFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.STT_LANGUAGE] ?: "" }

    suspend fun setSttLanguage(tag: String) {
        context.dataStore.edit { it[Keys.STT_LANGUAGE] = tag }
    }

    val sttEngineFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.STT_ENGINE] ?: "vosk" }

    suspend fun setSttEngine(engine: String) {
        context.dataStore.edit { it[Keys.STT_ENGINE] = if (engine == "whisper") "whisper" else "vosk" }
    }

    val updateChannelFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.UPDATE_CHANNEL] ?: "beta" }

    suspend fun setUpdateChannel(channel: String) {
        context.dataStore.edit { it[Keys.UPDATE_CHANNEL] = channel }
    }

    val autoUpdateCheckFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_UPDATE_CHECK] ?: false }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_CHECK] = enabled }
    }

    suspend fun setPendingApkDelete(fileName: String) {
        context.dataStore.edit { it[Keys.PENDING_APK_DELETE] = fileName }
    }

    suspend fun clearPendingApkDelete() {
        context.dataStore.edit { it.remove(Keys.PENDING_APK_DELETE) }
    }

    suspend fun notifiedUpdateVersion(): String? =
        context.dataStore.data.map { it[Keys.NOTIFIED_UPDATE_VERSION] }.first()

    suspend fun setNotifiedUpdateVersion(key: String) {
        context.dataStore.edit { it[Keys.NOTIFIED_UPDATE_VERSION] = key }
    }

    suspend fun clearNotifiedUpdateVersion() {
        context.dataStore.edit { it.remove(Keys.NOTIFIED_UPDATE_VERSION) }
    }

    suspend fun pendingApkDelete(): String? =
        context.dataStore.data.map { it[Keys.PENDING_APK_DELETE] }.first()

    val onboardingDoneFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    val audioSourceFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.AUDIO_SOURCE] ?: AUDIO_MIC }

    suspend fun setAudioSource(source: String) {
        context.dataStore.edit { it[Keys.AUDIO_SOURCE] = source }
    }

    val noiseReductionFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOISE_REDUCTION] ?: true }

    suspend fun setNoiseReduction(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOISE_REDUCTION] = enabled }
    }

    val enhanceStrengthFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.ENHANCE_STRENGTH] ?: "balanced" }

    suspend fun setEnhanceStrength(strength: String) {
        context.dataStore.edit { it[Keys.ENHANCE_STRENGTH] = strength }
    }

    val keepOriginalFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.KEEP_ORIGINAL] ?: true }

    suspend fun setKeepOriginal(keep: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_ORIGINAL] = keep }
    }

    val recordingsVolumeFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.RECORDINGS_VOLUME] ?: "default" }

    suspend fun recordingsVolume(): String =
        context.dataStore.data.map { it[Keys.RECORDINGS_VOLUME] ?: "default" }.first()

    suspend fun setRecordingsVolume(id: String) {
        context.dataStore.edit { it[Keys.RECORDINGS_VOLUME] = id }
    }

    val themeSeedFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.THEME_SEED] ?: "ember" }

    suspend fun setThemeSeed(id: String) {
        context.dataStore.edit { it[Keys.THEME_SEED] = id }
    }

    suspend fun lastSeenVersionCode(): Long =
        context.dataStore.data.map { it[Keys.LAST_SEEN_VERSION_CODE] ?: 0L }.first()

    suspend fun setLastSeenVersionCode(code: Long) {
        context.dataStore.edit { it[Keys.LAST_SEEN_VERSION_CODE] = code }
    }

    val tipIndexFlow: Flow<Int> =
        context.dataStore.data.map { it[Keys.TIP_INDEX] ?: 0 }

    suspend fun setTipIndex(index: Int) {
        context.dataStore.edit { it[Keys.TIP_INDEX] = index }
    }

    val tipsEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.TIPS_ENABLED] ?: true }

    suspend fun setTipsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TIPS_ENABLED] = enabled }
    }

    val recordingPrefixFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.RECORDING_PREFIX] ?: "Recording" }

    suspend fun setRecordingPrefix(prefix: String) {
        context.dataStore.edit { it[Keys.RECORDING_PREFIX] = prefix.trim().ifBlank { "Recording" } }
    }

    suspend fun recordingPrefix(): String =
        context.dataStore.data.map { it[Keys.RECORDING_PREFIX] ?: "Recording" }.first()

    val homeLayoutFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.HOME_LAYOUT] ?: "list" }

    suspend fun setHomeLayout(layout: String) {
        context.dataStore.edit { it[Keys.HOME_LAYOUT] = if (layout == "grid") "grid" else "list" }
    }

    val customFolderUriFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_FOLDER_URI] ?: "" }

    suspend fun customFolderUri(): String =
        context.dataStore.data.map { it[Keys.CUSTOM_FOLDER_URI] ?: "" }.first()

    suspend fun setCustomFolderUri(uri: String) {
        context.dataStore.edit { it[Keys.CUSTOM_FOLDER_URI] = uri }
    }

    suspend fun clearCustomFolderUri() {
        context.dataStore.edit { it.remove(Keys.CUSTOM_FOLDER_URI) }
    }

    val colorSchemeFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.COLOR_SCHEME] ?: "TONAL_SPOT" }

    suspend fun setColorScheme(scheme: String) {
        val valid = setOf("TONAL_SPOT", "EXPRESSIVE", "VIBRANT", "NEUTRAL", "MONOCHROME", "DYNAMIC", "SPRITZ", "RAINBOW", "FRUIT_SALAD")
        context.dataStore.edit { it[Keys.COLOR_SCHEME] = if (scheme.uppercase() in valid) scheme.uppercase() else "TONAL_SPOT" }
    }

    val gestureEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.GESTURE_ENABLED] ?: true }

    suspend fun setGestureEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GESTURE_ENABLED] = enabled }
    }

    companion object {
        const val AUDIO_MIC = "mic"
        const val AUDIO_SYSTEM = "system"
    }
}
