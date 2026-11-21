package com.pranshulgg.recordmaster.core.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Stores one imported typeface in app-private storage.
 *
 * Copying the file avoids depending on a persisted SAF permission, so the font
 * keeps working after a reboot or if the source document moves.
 */
class CustomFontStore(private val context: Context) {

    val fontFile: File
        get() = File(File(context.filesDir, FONT_DIRECTORY), FONT_FILE)

    suspend fun importFont(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "Custom font"
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("The selected font could not be opened")

            val directory = fontFile.parentFile
                ?: error("The font directory could not be resolved")
            if (!directory.exists() && !directory.mkdirs()) {
                error("The font directory could not be created")
            }

            val staging = File(directory, "$FONT_FILE.staging")
            inputStream.use { input ->
                FileOutputStream(staging).use { output ->
                    input.copyTo(output)
                }
            }

            if (staging.length() == 0L) {
                staging.delete()
                error("The selected font is empty")
            }
            if (runCatching { Typeface.createFromFile(staging.absolutePath) }.isFailure) {
                staging.delete()
                error("The selected file is not a supported font")
            }

            try {
                if (fontFile.exists()) fontFile.delete()
                if (!staging.renameTo(fontFile)) {
                    staging.copyTo(fontFile, overwrite = true)
                }
            } finally {
                staging.delete()
            }

            displayName
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        return try {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)
                    ?.substringAfterLast('/')
                    ?.takeIf { name -> name.isNotBlank() }
                    ?.take(MAX_NAME_LENGTH)
            } else {
                null
            }
        } finally {
            cursor.close()
        }
    }

    private companion object {
        const val FONT_DIRECTORY = "fonts"
        const val FONT_FILE = "app_custom_font"
        const val MAX_NAME_LENGTH = 120
    }
}
