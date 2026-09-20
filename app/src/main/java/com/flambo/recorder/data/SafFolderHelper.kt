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

    fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) { }
    }

    fun createFile(context: Context, treeUriString: String, displayName: String, mimeType: String): Uri? {
        if (treeUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(treeUriString)
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return null

            val created = dir.createFile(mimeType, displayName) ?: return null
            created.uri
        } catch (_: Exception) { null }
    }

    fun isTreeUriValid(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            val persisted = context.contentResolver.persistedUriPermissions.any { it.uri == uri }

            DocumentFile.fromTreeUri(context, uri)?.canWrite() == true || persisted
        } catch (_: Exception) { false }
    }
}
