package com.pranshulgg.recordmaster.core.data

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

data class StorageVolume(
    val id: String,
    val label: String,
    val dir: File
)

/**
 * Storage choices align with RecordMaster's existing layout: the default
 * volume is the app's external `Music` directory, so existing recordings are
 * never moved on first run. Internal storage and removable volumes are opt-in.
 */
object StorageVolumes {
    const val ID_DEFAULT = "default"
    const val ID_INTERNAL = "internal"

    fun list(context: Context): List<StorageVolume> {
        val out = mutableListOf<StorageVolume>()
        val dirs = ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_MUSIC)
        val primary = dirs.getOrNull(0) ?: context.filesDir

        out += StorageVolume(ID_DEFAULT, "Phone storage", primary)
        out += StorageVolume(
            ID_INTERNAL,
            "Internal storage",
            File(context.filesDir, "recordings")
        )

        dirs.drop(1).forEachIndexed { index, dir ->
            if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                out += StorageVolume("sdcard-$index", "SD card", dir)
            }
        }
        return out
    }

    fun resolveDir(context: Context, id: String): File = when {
        id == ID_INTERNAL -> File(context.filesDir, "recordings")
        id.startsWith("sdcard-") -> {
            val match = list(context).firstOrNull { it.id == id }?.dir
            match ?: (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir)
        }
        else -> context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
    }
}
