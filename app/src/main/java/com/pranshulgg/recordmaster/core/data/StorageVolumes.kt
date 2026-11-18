package com.pranshulgg.recordmaster.core.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import java.io.File

data class StorageVolume(
    val id: String,
    val label: String,
    val dir: File
)

/**
 * Storage choices. The default is a "Recorder" folder in the primary shared
 * storage root (auto-created) so recordings are easy to find in file managers.
 * Internal storage and removable volumes remain available as options.
 */
object StorageVolumes {
    const val ID_DEFAULT = "default"
    const val ID_INTERNAL = "internal"

    fun list(context: Context): List<StorageVolume> {
        val out = mutableListOf<StorageVolume>()

        out += StorageVolume(ID_DEFAULT, "Phone storage (Recorder)", defaultRecorderDir(context))
        out += StorageVolume(
            ID_INTERNAL,
            "Internal storage",
            File(context.filesDir, "recordings")
        )

        val dirs = ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_MUSIC)
        dirs.drop(1).forEachIndexed { index, dir ->
            if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                out += StorageVolume("sdcard-$index", "SD card", dir)
            }
        }
        return out
    }

    fun resolveDir(context: Context, id: String): File = when {
        id == ID_INTERNAL -> File(context.filesDir, "recordings").apply { mkdirs() }
        id.startsWith("sdcard-") -> {
            val match = list(context).firstOrNull { it.id == id }?.dir
            (match ?: appMusicFallback(context)).apply { mkdirs() }
        }
        else -> defaultRecorderDir(context).apply { mkdirs() }
    }

    fun defaultRecorderDir(context: Context): File {
        val shared = runCatching {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStorageDirectory(), "Recorder")
        }.getOrNull()
        if (shared != null) {
            val ready = runCatching { shared.mkdirs() || shared.isDirectory }.getOrDefault(false)
            if (ready) return shared
        }
        return appMusicFallback(context)
    }

    private fun appMusicFallback(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir

    /**
     * Best-effort resolution of a SAF tree URI to a writable filesystem directory.
     * This keeps the existing File-based recording/library pipeline working for
     * folders the user picks on primary/shared storage.
     */
    fun resolveTreeUriToDir(context: Context, uriString: String): File? {
        if (uriString.isBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val dir = when {
                docId.startsWith("primary:") -> {
                    val rel = docId.removePrefix("primary:")
                    @Suppress("DEPRECATION")
                    val base = Environment.getExternalStorageDirectory()
                    if (rel.isEmpty()) base else File(base, rel)
                }
                docId.startsWith("raw:") -> File(docId.removePrefix("raw:"))
                else -> null
            }
            dir?.let {
                val ready = runCatching { it.mkdirs() || it.isDirectory }.getOrDefault(false)
                if (ready) it else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun folderDisplayName(context: Context, uriString: String): String {
        val resolved = resolveTreeUriToDir(context, uriString)
        if (resolved != null) return resolved.absolutePath
        return SafFolderHelper.displayName(context, uriString).ifBlank { "Selected folder" }
    }
}
