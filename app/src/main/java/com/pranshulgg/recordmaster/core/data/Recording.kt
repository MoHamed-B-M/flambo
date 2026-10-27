package com.pranshulgg.recordmaster.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room is an index over RecordMaster's real file tree. The audio file is the
 * durable recording; this row caches metadata and feature state.
 *
 * [filePath] is unique so a rescan can never duplicate an existing take.
 */
@Entity(
    tableName = "recordings",
    indices = [Index(value = ["filePath"], unique = true)]
)
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
    val amplitudePeaks: String = "",
    val quality: String = "HIGH",
    val transcriptText: String = "",
    val enhancedPath: String = ""
) {
    val tagList: List<String>
        get() = if (tags.isBlank()) emptyList()
        else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val peakList: List<Float>
        get() = if (amplitudePeaks.isBlank()) emptyList()
        else amplitudePeaks.split(",").mapNotNull { it.toFloatOrNull() }
}
