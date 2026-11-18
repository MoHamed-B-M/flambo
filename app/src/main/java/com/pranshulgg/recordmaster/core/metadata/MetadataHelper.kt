package com.pranshulgg.recordmaster.core.metadata

import com.pranshulgg.recordmaster.core.data.AudioFileStore
import com.pranshulgg.recordmaster.core.data.Recording
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Writes user-editable metadata (title/author) into the physical audio file.
 * Best-effort: unsupported formats, content URIs, and IO/tagging failures are ignored.
 */
object MetadataHelper {

    init {
        runCatching {
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        }
    }

    fun writeTags(recording: Recording): Boolean {
        val path = recording.filePath
        if (path.isBlank() || AudioFileStore.isContentUri(path)) return false
        val file = File(path)
        if (!file.exists() || !file.isFile) return false

        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }

            val title = recording.customName.ifBlank { recording.title }.ifBlank { recording.displayTitle }
            if (title.isNotBlank()) {
                runCatching { tag.setField(FieldKey.TITLE, title) }
            } else {
                runCatching { tag.deleteField(FieldKey.TITLE) }
            }

            val artist = recording.authorName.trim()
            if (artist.isNotBlank()) {
                runCatching { tag.setField(FieldKey.ARTIST, artist) }
            } else {
                runCatching { tag.deleteField(FieldKey.ARTIST) }
            }

            audioFile.commit()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
