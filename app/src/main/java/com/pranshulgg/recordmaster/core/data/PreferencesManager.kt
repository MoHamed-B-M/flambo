package com.pranshulgg.recordmaster.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pranshulgg.recordmaster.core.domain.AnimationSpeed
import com.pranshulgg.recordmaster.core.domain.AutoSaveInterval
import com.pranshulgg.recordmaster.core.domain.MicrophoneInput
import com.pranshulgg.recordmaster.core.domain.RecordingConfig
import com.pranshulgg.recordmaster.core.domain.RecordingFormat
import com.pranshulgg.recordmaster.core.domain.RecordingChannels
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recordMasterDataStore by preferencesDataStore(name = "recordmaster_prefs")

/**
 * Feature preferences introduced by the 2.0 merge. Theme keys stay in
 * [com.pranshulgg.recordmaster.helpers.PreferencesHelper] so RecordMaster's
 * existing appearance settings remain the single source of truth.
 */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val QUALITY = stringPreferencesKey("quality")
        val RECORDING_FORMAT = stringPreferencesKey("recording_format")
        val STEREO_RECORDING = booleanPreferencesKey("stereo_recording")
        val MICROPHONE_INPUT_ID = stringPreferencesKey("microphone_input_id")
        val LOCATION_TAGGING = booleanPreferencesKey("location_tagging")
        val STT_LANGUAGE = stringPreferencesKey("stt_language")
        val STT_MODEL_ID = stringPreferencesKey("stt_model_id")
        val STT_IMPORTED_MODELS = stringPreferencesKey("stt_imported_models_json")
        val NOISE_REDUCTION = booleanPreferencesKey("noise_reduction")
        val ENHANCE_STRENGTH = stringPreferencesKey("enhance_strength")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original")
        val RECORDINGS_VOLUME = stringPreferencesKey("recordings_volume")
        val RECORDINGS_FOLDER_URI = stringPreferencesKey("recordings_folder_uri")
        val RECORDING_PREFIX = stringPreferencesKey("recording_prefix")
        val RECORDING_NAME_PATTERN = stringPreferencesKey("recording_name_pattern")
        val TRASH_AUTO_PURGE = booleanPreferencesKey("trash_auto_purge")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val LIST_FILTER = stringPreferencesKey("list_filter")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val ANIMATION_SPEED = stringPreferencesKey("animation_speed")
        val AUTO_SAVE_INTERVAL = stringPreferencesKey("auto_save_interval")
        val LAST_RUN_VERSION = stringPreferencesKey("last_run_version")
        val HAS_SEEN_2_0_ANNOUNCEMENT = booleanPreferencesKey("has_seen_2_0_announcement")
    }

    // ---- Version / announcements ----

    /** Version last stamped when an announcement was dismissed. */
    val lastRunVersionFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.LAST_RUN_VERSION] ?: "" }

    /** Empty until the version has been stamped after an announcement. */
    suspend fun lastRunVersion(): String =
        context.recordMasterDataStore.data.map { it[Keys.LAST_RUN_VERSION] ?: "" }.first()

    suspend fun setLastRunVersion(version: String) {
        context.recordMasterDataStore.edit { it[Keys.LAST_RUN_VERSION] = version }
    }

    val hasSeen2_0AnnouncementFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.HAS_SEEN_2_0_ANNOUNCEMENT] ?: false }

    suspend fun hasSeen2_0Announcement(): Boolean =
        context.recordMasterDataStore.data.map {
            it[Keys.HAS_SEEN_2_0_ANNOUNCEMENT] ?: false
        }.first()

    suspend fun setHasSeen2_0Announcement(seen: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.HAS_SEEN_2_0_ANNOUNCEMENT] = seen }
    }

    // ---- Recording ----

    val qualityFlow: Flow<RecordingQuality> =
        context.recordMasterDataStore.data.map { prefs ->
            prefs[Keys.QUALITY]?.let { value ->
                runCatching { RecordingQuality.valueOf(value) }.getOrNull()
            } ?: RecordingQuality.HIGH
        }

    suspend fun setQuality(quality: RecordingQuality) {
        context.recordMasterDataStore.edit { it[Keys.QUALITY] = quality.name }
    }

    private suspend fun quality(): RecordingQuality =
        context.recordMasterDataStore.data.map { prefs ->
            prefs[Keys.QUALITY]?.let { value ->
                runCatching { RecordingQuality.valueOf(value) }.getOrNull()
            } ?: RecordingQuality.HIGH
        }.first()

    val noiseReductionFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.NOISE_REDUCTION] ?: true }

    suspend fun setNoiseReduction(enabled: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.NOISE_REDUCTION] = enabled }
    }

    val recordingFormatFlow: Flow<RecordingFormat> =
        context.recordMasterDataStore.data.map { prefs ->
            prefs[Keys.RECORDING_FORMAT]
                ?.let { RecordingFormat.fromStoredValue(it) }
                ?: RecordingFormat.DEFAULT
        }

    suspend fun recordingFormat(): RecordingFormat =
        context.recordMasterDataStore.data.map { prefs ->
            prefs[Keys.RECORDING_FORMAT]
                ?.let { RecordingFormat.fromStoredValue(it) }
                ?: RecordingFormat.DEFAULT
        }.first()

    suspend fun setRecordingFormat(format: RecordingFormat) {
        context.recordMasterDataStore.edit { it[Keys.RECORDING_FORMAT] = format.name }
    }

    val stereoRecordingFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.STEREO_RECORDING] ?: false }

    suspend fun stereoRecording(): Boolean =
        context.recordMasterDataStore.data.map { it[Keys.STEREO_RECORDING] ?: false }.first()

    suspend fun setStereoRecording(enabled: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.STEREO_RECORDING] = enabled }
    }

    val microphoneInputIdFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.MICROPHONE_INPUT_ID] ?: MicrophoneInput.DEFAULT_ID }

    suspend fun microphoneInputId(): String =
        context.recordMasterDataStore.data.map {
            it[Keys.MICROPHONE_INPUT_ID] ?: MicrophoneInput.DEFAULT_ID
        }.first()

    suspend fun setMicrophoneInputId(id: String) {
        context.recordMasterDataStore.edit {
            it[Keys.MICROPHONE_INPUT_ID] = id.trim().ifBlank { MicrophoneInput.DEFAULT_ID }
        }
    }

    val locationTaggingFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.LOCATION_TAGGING] ?: false }

    suspend fun locationTaggingEnabled(): Boolean =
        context.recordMasterDataStore.data.map { it[Keys.LOCATION_TAGGING] ?: false }.first()

    suspend fun setLocationTaggingEnabled(enabled: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.LOCATION_TAGGING] = enabled }
    }

    /** Snapshot used by the recorder so live setting changes cannot alter an active take. */
    suspend fun recordingConfig(): RecordingConfig = RecordingConfig(
        format = recordingFormat(),
        quality = quality(),
        channels = if (stereoRecording()) RecordingChannels.STEREO else RecordingChannels.MONO,
        input = MicrophoneInput.fromStoredId(microphoneInputId()),
        locationTaggingEnabled = locationTaggingEnabled()
    )

    val recordingPrefixFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.RECORDING_PREFIX] ?: DEFAULT_PREFIX }

    suspend fun recordingPrefix(): String =
        context.recordMasterDataStore.data.map { it[Keys.RECORDING_PREFIX] ?: DEFAULT_PREFIX }.first()

    suspend fun setRecordingPrefix(prefix: String) {
        context.recordMasterDataStore.edit {
            it[Keys.RECORDING_PREFIX] = prefix.trim().ifBlank { DEFAULT_PREFIX }
        }
    }

    val recordingNamePatternFlow: Flow<String> =
        context.recordMasterDataStore.data.map {
            it[Keys.RECORDING_NAME_PATTERN] ?: DEFAULT_NAME_PATTERN
        }

    suspend fun recordingNamePattern(): String =
        context.recordMasterDataStore.data.map {
            it[Keys.RECORDING_NAME_PATTERN] ?: DEFAULT_NAME_PATTERN
        }.first()

    suspend fun setRecordingNamePattern(pattern: String) {
        context.recordMasterDataStore.edit {
            it[Keys.RECORDING_NAME_PATTERN] = pattern.trim().ifBlank { DEFAULT_NAME_PATTERN }
        }
    }

    // ---- Playback / enhancement ----

    /** Default speed used by the playback engine on the next app start. */
    val playbackSpeedFlow: Flow<Float> =
        context.recordMasterDataStore.data.map {
            (it[Keys.PLAYBACK_SPEED] ?: 1f).coerceIn(0.5f, 2f)
        }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.recordMasterDataStore.edit {
            it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2f)
        }
    }

    // ---- Motion / accessibility ----

    /** User override that shortens or removes motion across the app. */
    val reduceAnimationsFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.REDUCE_ANIMATIONS] ?: false }

    suspend fun setReduceAnimations(enabled: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.REDUCE_ANIMATIONS] = enabled }
    }

    /** Multiplier applied to Flambo's springs and transitions. */
    val animationSpeedFlow: Flow<AnimationSpeed> =
        context.recordMasterDataStore.data.map { prefs ->
            prefs[Keys.ANIMATION_SPEED]
                ?.let { stored -> AnimationSpeed.entries.firstOrNull { it.name == stored } }
                ?: AnimationSpeed.Default
        }

    suspend fun setAnimationSpeed(speed: AnimationSpeed) {
        context.recordMasterDataStore.edit { it[Keys.ANIMATION_SPEED] = speed.name }
    }

    // ---- Reliability ----

    /** Checkpoint cadence for long recordings; [AutoSaveInterval.Default] until opted in. */
    val autoSaveIntervalFlow: Flow<AutoSaveInterval> =
        context.recordMasterDataStore.data.map { prefs ->
            prefs[Keys.AUTO_SAVE_INTERVAL]
                ?.let { stored -> runCatching { AutoSaveInterval.valueOf(stored) }.getOrNull() }
                ?: AutoSaveInterval.Default
        }

    suspend fun autoSaveInterval(): AutoSaveInterval =
        context.recordMasterDataStore.data.map { prefs ->
            prefs[Keys.AUTO_SAVE_INTERVAL]
                ?.let { stored -> runCatching { AutoSaveInterval.valueOf(stored) }.getOrNull() }
                ?: AutoSaveInterval.Default
        }.first()

    suspend fun setAutoSaveInterval(interval: AutoSaveInterval) {
        context.recordMasterDataStore.edit { it[Keys.AUTO_SAVE_INTERVAL] = interval.name }
    }

    val enhanceStrengthFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.ENHANCE_STRENGTH] ?: "balanced" }

    suspend fun setEnhanceStrength(strength: String) {
        context.recordMasterDataStore.edit { it[Keys.ENHANCE_STRENGTH] = strength }
    }

    val keepOriginalFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.KEEP_ORIGINAL] ?: true }

    suspend fun setKeepOriginal(keep: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.KEEP_ORIGINAL] = keep }
    }

    // ---- Storage ----

    val recordingsVolumeFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.RECORDINGS_VOLUME] ?: "default" }

    suspend fun recordingsVolume(): String =
        context.recordMasterDataStore.data.map { it[Keys.RECORDINGS_VOLUME] ?: "default" }.first()

    suspend fun setRecordingsVolume(id: String) {
        context.recordMasterDataStore.edit {
            it[Keys.RECORDINGS_VOLUME] = id.ifBlank { "default" }
        }
    }

    /** SAF tree URI for a user-picked exact recordings folder. Empty means use a volume/default. */
    val recordingsFolderUriFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.RECORDINGS_FOLDER_URI] ?: "" }

    suspend fun recordingsFolderUri(): String =
        context.recordMasterDataStore.data.map { it[Keys.RECORDINGS_FOLDER_URI] ?: "" }.first()

    suspend fun setRecordingsFolderUri(uri: String) {
        context.recordMasterDataStore.edit { it[Keys.RECORDINGS_FOLDER_URI] = uri }
    }

    val trashAutoPurgeFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.TRASH_AUTO_PURGE] ?: false }

    suspend fun setTrashAutoPurge(enabled: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.TRASH_AUTO_PURGE] = enabled }
    }

    val trashRetentionDaysFlow: Flow<Int> =
        context.recordMasterDataStore.data.map { it[Keys.TRASH_RETENTION_DAYS] ?: 7 }

    suspend fun trashRetentionDays(): Int =
        context.recordMasterDataStore.data.map { it[Keys.TRASH_RETENTION_DAYS] ?: 7 }.first()

    suspend fun setTrashRetentionDays(days: Int) {
        context.recordMasterDataStore.edit { it[Keys.TRASH_RETENTION_DAYS] = days.coerceIn(1, 365) }
    }

    // ---- Library sorting / filtering ----

    val sortOrderFlow: Flow<RecordingSort> =
        context.recordMasterDataStore.data.map { prefs ->
            runCatching { RecordingSort.valueOf(prefs[Keys.SORT_ORDER] ?: "") }
                .getOrDefault(RecordingSort.DATE_DESC)
        }

    suspend fun setSortOrder(order: RecordingSort) {
        context.recordMasterDataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    val listFilterFlow: Flow<RecordingFilter> =
        context.recordMasterDataStore.data.map { prefs ->
            runCatching { RecordingFilter.valueOf(prefs[Keys.LIST_FILTER] ?: "") }
                .getOrDefault(RecordingFilter.ALL)
        }

    suspend fun setListFilter(filter: RecordingFilter) {
        context.recordMasterDataStore.edit { it[Keys.LIST_FILTER] = filter.name }
    }

    // ---- Speech-to-text (Whisper, on-device) ----

    val sttLanguageFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.STT_LANGUAGE] ?: "" }

    suspend fun setSttLanguage(tag: String) {
        context.recordMasterDataStore.edit { it[Keys.STT_LANGUAGE] = tag }
    }

    /** Id of the selected speech model; empty means the built-in default. */
    val sttModelIdFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.STT_MODEL_ID] ?: "" }

    suspend fun sttModelId(): String =
        context.recordMasterDataStore.data.map { it[Keys.STT_MODEL_ID] ?: "" }.first()

    suspend fun setSttModelId(id: String) {
        context.recordMasterDataStore.edit { it[Keys.STT_MODEL_ID] = id }
    }

    /** JSON array of user-imported models. Built-ins are not stored here. */
    val sttImportedModelsFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.STT_IMPORTED_MODELS] ?: "" }

    suspend fun sttImportedModelsJson(): String =
        context.recordMasterDataStore.data.map { it[Keys.STT_IMPORTED_MODELS] ?: "" }.first()

    suspend fun setSttImportedModelsJson(json: String) {
        context.recordMasterDataStore.edit { it[Keys.STT_IMPORTED_MODELS] = json }
    }

    companion object {
        /** Keeps RecordMaster's existing `User_###` naming as the default. */
        const val DEFAULT_PREFIX = "User"
        /** Default pattern preserves legacy prefix_counter behavior (e.g. User_001). */
        const val DEFAULT_NAME_PATTERN = "{prefix}_{counter}"
        /** The release whose welcome announcement is gated by these preferences. */
        const val ANNOUNCEMENT_VERSION = "2.0.0"
        /** Version the Debug setting restores so the announcement can be replayed. */
        const val DEBUG_RESET_VERSION = "1.7.4"
    }
}
