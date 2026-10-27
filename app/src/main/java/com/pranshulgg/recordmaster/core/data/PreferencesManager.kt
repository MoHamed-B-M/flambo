package com.pranshulgg.recordmaster.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        val STT_LANGUAGE = stringPreferencesKey("stt_language")
        val NOISE_REDUCTION = booleanPreferencesKey("noise_reduction")
        val ENHANCE_STRENGTH = stringPreferencesKey("enhance_strength")
        val KEEP_ORIGINAL = booleanPreferencesKey("keep_original")
        val RECORDINGS_VOLUME = stringPreferencesKey("recordings_volume")
        val RECORDING_PREFIX = stringPreferencesKey("recording_prefix")
        val TRASH_AUTO_PURGE = booleanPreferencesKey("trash_auto_purge")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
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

    val noiseReductionFlow: Flow<Boolean> =
        context.recordMasterDataStore.data.map { it[Keys.NOISE_REDUCTION] ?: true }

    suspend fun setNoiseReduction(enabled: Boolean) {
        context.recordMasterDataStore.edit { it[Keys.NOISE_REDUCTION] = enabled }
    }

    val recordingPrefixFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.RECORDING_PREFIX] ?: DEFAULT_PREFIX }

    suspend fun recordingPrefix(): String =
        context.recordMasterDataStore.data.map { it[Keys.RECORDING_PREFIX] ?: DEFAULT_PREFIX }.first()

    suspend fun setRecordingPrefix(prefix: String) {
        context.recordMasterDataStore.edit {
            it[Keys.RECORDING_PREFIX] = prefix.trim().ifBlank { DEFAULT_PREFIX }
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

    // ---- Speech-to-text (Whisper, on-device) ----

    val sttLanguageFlow: Flow<String> =
        context.recordMasterDataStore.data.map { it[Keys.STT_LANGUAGE] ?: "" }

    suspend fun setSttLanguage(tag: String) {
        context.recordMasterDataStore.edit { it[Keys.STT_LANGUAGE] = tag }
    }

    companion object {
        /** Keeps RecordMaster's existing `User_###` naming as the default. */
        const val DEFAULT_PREFIX = "User"
    }
}
