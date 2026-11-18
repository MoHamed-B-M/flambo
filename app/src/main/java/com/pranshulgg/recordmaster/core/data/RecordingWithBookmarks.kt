package com.pranshulgg.recordmaster.core.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * The one-to-many view used by the playback and review screens: a recording
 * plus every marker on its timeline, ordered by [Bookmark.timestampMs].
 */
data class RecordingWithBookmarks(
    @Embedded val recording: Recording,
    @Relation(
        parentColumn = "id",
        entityColumn = "recordingId"
    )
    val bookmarks: List<Bookmark> = emptyList()
)
