package com.flambo.recorder.data

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

// Where new recordings land. Only app-private volumes are offered, so every
// path stays a plain File — the player, transcriber, enhancer and share sheet
// all keep working with zero permission prompts. Existing recordings keep
// their absolute paths and keep working wherever they are.
data class StorageVolume(
    val id: String,
    val label: String,
    val dir: File,
    val freeBytes: Long
)

object StorageVolumes {
    const val ID_DEFAULT = "default"
    const val ID_INTERNAL = "internal"

    fun list(context: Context): List<StorageVolume> {
        val out = mutableListOf<StorageVolume>()
        // Legacy behavior first: recordings used to go straight into the
        // primary external app dir, and that path must keep working.
        val primary = context.getExternalFilesDir(null) ?: context.filesDir
        out += StorageVolume(ID_DEFAULT, "Phone storage", primary, freeOf(primary))
        out += StorageVolume(
            ID_INTERNAL, "Internal storage",
            File(context.filesDir, "recordings"), freeOf(context.filesDir)
        )
        ContextCompat.getExternalFilesDirs(context, null)
            .filterNotNull()
            .drop(1)
            .forEachIndexed { index, dir ->
                if (Environment.isExternalStorageRemovable(dir)) {
                    out += StorageVolume(
                        "sdcard-$index", "SD card",
                        File(dir, "recordings"), freeOf(dir)
                    )
                }
            }
        return out
    }

    fun resolveDir(context: Context, id: String): File = when {
        id == ID_INTERNAL -> File(context.filesDir, "recordings")
        id.startsWith("sdcard-") -> {
            val match = list(context).firstOrNull { it.id == id }?.dir
            match ?: (context.getExternalFilesDir(null) ?: context.filesDir)
        }
        // ID_DEFAULT and anything unknown (e.g. ejected SD card) fall back here.
        else -> context.getExternalFilesDir(null) ?: context.filesDir
    }

    private fun freeOf(dir: File): Long {
        var probe = dir
        while (!probe.exists()) probe = probe.parentFile ?: return 0L
        return probe.freeSpace.takeIf { it > 0 } ?: 0L
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        if (gb >= 1) return String.format("%.1f GB free", gb)
        val mb = bytes / 1024.0 / 1024.0
        return String.format("%d MB free", mb.toInt())
    }
}
