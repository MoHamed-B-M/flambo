package com.flambo.recorder.record

import android.content.pm.ServiceInfo
import com.flambo.recorder.data.PreferencesManager

enum class AudioSource(val prefValue: String, val label: String) {
    MIC(PreferencesManager.AUDIO_MIC, "Microphone"),
    SYSTEM(PreferencesManager.AUDIO_SYSTEM, "System sound");

    val fgsType: Int
        get() = if (this == SYSTEM) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

    companion object {

        const val SYSTEM_ENABLED = false

        fun fromPref(value: String): AudioSource =
            entries.firstOrNull { it.prefValue == value } ?: MIC
    }
}
