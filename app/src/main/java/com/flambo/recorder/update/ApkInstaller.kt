package com.flambo.recorder.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Downloads a release APK into app cache, hands it to the system installer,
// and cleans up afterwards. APKs never touch shared storage.
object ApkInstaller {

    fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    // Streams the file with progress (0..1). Caller drives UI state.
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val dest = File(updatesDir(context), fileName)
        // Resume nothing — release APKs change every build; stale file goes first.
        runCatching { if (dest.exists()) dest.delete() }
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                conn.connect()
                require(conn.responseCode in 200..299) { "Download failed (HTTP ${conn.responseCode})." }
                val total = conn.contentLengthLong.takeIf { it > 0 }
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total != null) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            onProgress(1f)
            Result.success(dest)
        } catch (e: Exception) {
            runCatching { dest.delete() }
            Result.failure(IllegalStateException(e.message ?: "Download failed."))
        }
    }

    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun installIntent(context: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    // Deletes the APK handed to the installer on the previous run plus any
    // download older than a week. Never touches anything else.
    suspend fun cleanupStale(context: Context, pendingName: String?) =
        withContext(Dispatchers.IO) {
            val dir = updatesDir(context)
            val now = System.currentTimeMillis()
            dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }?.forEach { f ->
                val isPending = f.name == pendingName
                val isOld = now - f.lastModified() > 7L * 24 * 60 * 60 * 1000
                if (isPending || isOld) runCatching { f.delete() }
            }
        }
}
