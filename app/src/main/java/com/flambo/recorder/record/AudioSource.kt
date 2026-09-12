package com.flambo.recorder.record

import android.content.pm.ServiceInfo
import com.flambo.recorder.data.PreferencesManager

// Where the audio comes from. SYSTEM captures device playback (music, video,
// anything marked capturable) via AudioPlaybackCapture — Android 10+ only.
enum class AudioSource(val prefValue: String, val label: String) {
    MIC(PreferencesManager.AUDIO_MIC, "Microphone"),
    SYSTEM(PreferencesManager.AUDIO_SYSTEM, "System sound");

    // Foreground-service type actually claimed at runtime. The manifest keeps
    // the union as the allowed superset; each recording narrows to what it
    // uses so mic takes never ride on the projection type.
    // (ServiceInfo constants are compile-time inlined ints — safe on old APIs;
    // the 3-arg startForeground call itself stays behind a Q+ guard.)
    val fgsType: Int
        get() = if (this == SYSTEM) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

    companion object {
        // System capture is parked until its projection bugs are fixed
        // (dropped grants, silent takes on some skins). The engine stays
        // intact behind this flag so it can come back without a rewrite.
        const val SYSTEM_ENABLED = false

        fun fromPref(value: String): AudioSource =
            entries.firstOrNull { it.prefValue == value } ?: MIC
    }
}
