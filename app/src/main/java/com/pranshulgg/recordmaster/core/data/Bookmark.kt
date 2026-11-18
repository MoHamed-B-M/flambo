package com.pranshulgg.recordmaster.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-placed point of interest on a recording's timeline.
 *
 * One [Recording] owns many bookmarks. The foreign key cascades so permanently
 * deleting a recording also removes its markers. [timestampMs] is an offset
 * from the start of the audio file, which keeps a marker meaningful both for
 * normal playback and for chunked review.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = Recording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recordingId"])]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val timestampMs: Long,
    val note: String? = null
)
