package com.pranshulgg.recordmaster.core.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single owner for resolving audio locations.
 *
 * A recording's path is normally an absolute file path. `content://` support
 * is kept so the deferred SAF custom-folder work can reuse this layer without
 * touching playback, transcription or enhancement.
 */
object AudioFileStore {

    fun isContentUri(path: String): Boolean = path.startsWith("content://")

    fun exists(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            if (!isContentUri(path)) File(path).exists()
            else DocumentFile.fromSingleUri(context, Uri.parse(path))?.exists() == true
        } catch (_: Exception) {
            false
        }
    }

    fun length(context: Context, path: String): Long {
        if (path.isBlank()) return 0L
        return try {
            if (!isContentUri(path)) File(path).length().takeIf { it > 0 } ?: 0L
            else DocumentFile.fromSingleUri(context, Uri.parse(path))?.length()?.takeIf { it > 0 } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun displayName(context: Context, path: String): String {
        if (path.isBlank()) return ""
        if (!isContentUri(path)) return File(path).name
        return try {
            DocumentFile.fromSingleUri(context, Uri.parse(path))?.name
                ?: Uri.parse(path).lastPathSegment?.substringAfterLast('/') ?: path
        } catch (_: Exception) {
            path
        }
    }

    /** Human-readable parent: directory path for files, tree name for SAF documents. */
    fun locationLabel(context: Context, path: String): String {
        if (path.isBlank()) return ""
        if (!isContentUri(path)) return File(path).parent ?: ""
        return try {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(path))
            doc?.parentFile?.name ?: SafFolderHelper.displayName(context, "")
                .ifBlank { "Picked folder" }
        } catch (_: Exception) {
            "Picked folder"
        }
    }

    fun delete(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            if (!isContentUri(path)) {
                val file = File(path)
                !file.exists() || file.delete()
            } else {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(path)) ?: return false
                if (!doc.exists()) true else doc.delete()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** URI suitable for playback and share intents. */
    fun playableUri(context: Context, path: String): Uri? {
        if (path.isBlank()) return null
        return try {
            if (!isContentUri(path)) {
                val file = File(path)
                if (!file.exists()) null
                else FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } else {
                val uri = Uri.parse(path)
                if (DocumentFile.fromSingleUri(context, uri)?.exists() == true) uri else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns a real [File] for APIs that need one (transcription, enhancement).
     * File paths are returned as-is; SAF documents are copied to cache once.
     */
    suspend fun ensureLocalFile(context: Context, path: String): File? = withContext(Dispatchers.IO) {
        try {
            if (path.isBlank()) return@withContext null
            if (!isContentUri(path)) {
                val file = File(path)
                return@withContext file.takeIf { it.exists() }
            }
            val uri = Uri.parse(path)
            val doc = DocumentFile.fromSingleUri(context, uri) ?: return@withContext null
            if (!doc.exists()) return@withContext null
            val name = (doc.name ?: "audio.m4a").replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val dir = File(context.cacheDir, "audio_local").apply { mkdirs() }
            val cached = File(dir, name)
            if (cached.exists() && cached.length() == doc.length() && doc.length() > 0) {
                return@withContext cached
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            cached.takeIf { it.exists() && it.length() > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies [source] into a SAF tree, uniquifying the display name.
     * Reserved for the deferred custom-folder feature.
     */
    suspend fun copyFileToTree(
        context: Context,
        treeUriString: String,
        source: File,
        mimeType: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (!source.exists() || treeUriString.isBlank()) return@withContext null
            if (!SafFolderHelper.isTreeUriValid(context, treeUriString)) return@withContext null
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return@withContext null
            val uniqueName = uniqueDisplayName(dir, source.name)
            val doc = dir.createFile(mimeType, uniqueName) ?: return@withContext null
            context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: run {
                runCatching { doc.delete() }
                return@withContext null
            }
            doc.uri
        } catch (_: Exception) {
            null
        }
    }

    private fun uniqueDisplayName(dir: DocumentFile, base: String): String {
        if (dir.findFile(base) == null) return base
        val dot = base.lastIndexOf('.')
        val name = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var i = 2
        while (dir.findFile("$name ($i)$ext") != null) i++
        return "$name ($i)$ext"
    }
}
