package com.flambo.recorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val tags: String = "",
    // stored as comma-separated amplitudes (normalized 0..1) for waveform preview
    val amplitudePeaks: String = "",
    val quality: String = "HIGH",
    // saved speech-to-text result (empty = not transcribed yet)
    val transcriptText: String = ""
) {
    val tagList: List<String>
        get() = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val peakList: List<Float>
        get() = if (amplitudePeaks.isBlank()) emptyList()
        else amplitudePeaks.split(",").mapNotNull { it.toFloatOrNull() }
}
