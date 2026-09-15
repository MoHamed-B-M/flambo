package com.flambo.recorder.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

object SafFolderHelper {

    fun displayName(context: Context, uriString: String): String {
        if (uriString.isBlank()) return ""
        return try {
            val uri = Uri.parse(uriString)
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: uriString
        } catch (_: Exception) { uriString }
    }

    /** Persist permission for the tree URI so it survives reboot. */
    fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) { }
    }

    /**
     * Create a new file inside the SAF tree. Returns the DocumentFile + its content Uri.
     * Caller can then open an FD via contentResolver.openFileDescriptor(uri, "w").
     */
    fun createFile(context: Context, treeUriString: String, displayName: String, mimeType: String): Uri? {
        if (treeUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(treeUriString)
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            // Ensure we have write permission; if not, try to take it
            val created = dir.createFile(mimeType, displayName) ?: return null
            created.uri
        } catch (_: Exception) { null }
    }

    fun isTreeUriValid(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            val persisted = context.contentResolver.persistedUriPermissions.any { it.uri == uri }
            // Even if not persisted, try to see if we can list it
            DocumentFile.fromTreeUri(context, uri)?.canWrite() == true || persisted
        } catch (_: Exception) { false }
    }
}
