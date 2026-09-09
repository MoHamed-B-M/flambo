package com.flambo.recorder.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val version: String, // e.g. 1.0.6 or 1.0.6-beta
    val buildNumber: Int, // parsed from "(#N)" in the preview title, 0 if absent
    val pageUrl: String,
    val apkUrl: String?
)

data class UpdateCheck(
    val channel: String, // beta | stable
    val installedVersion: String,
    val installedBuild: Long,
    val release: ReleaseInfo?,
    val available: Boolean,
    val error: String? = null
)

// Compares this install against GitHub releases. Beta tracks the rolling
// `beta-latest` prerelease by build number (version tie-break), stable tracks
// versioned `v*` releases by semantic version only.
object UpdateChecker {

    const val CHANNEL_BETA = "beta"
    const val CHANNEL_STABLE = "stable"

    private const val API = "https://api.github.com/repos/MoHamed-B-M/flambo/releases"

    fun installed(context: Context): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        return (info.versionName ?: "1.0.0") to code
    }

    suspend fun check(context: Context, channel: String): UpdateCheck =
        withContext(Dispatchers.IO) {
            val (installedVersion, installedCode) = installed(context)
            // versionCode is 1 + CI run number, so this recovers the build number.
            val installedBuild = (installedCode - 1).coerceAtLeast(0)
            try {
                val endpoint = if (channel == CHANNEL_BETA) "$API/tags/beta-latest" else "$API/latest"
                val release = fetchRelease(endpoint)
                    ?: return@withContext UpdateCheck(
                        channel, installedVersion, installedBuild, null, false,
                        if (channel == CHANNEL_BETA) "No preview published yet." else "No stable release published yet."
                    )
                val available = if (channel == CHANNEL_BETA) {
                    val cmp = compareVersions(release.version, installedVersion)
                    cmp > 0 || (cmp == 0 && release.buildNumber > installedBuild)
                } else {
                    compareVersions(release.version, installedVersion) > 0
                }
                UpdateCheck(channel, installedVersion, installedBuild, release, available)
            } catch (_: Exception) {
                UpdateCheck(
                    channel, installedVersion, installedBuild, null, false,
                    "Couldn't reach GitHub — check your connection and retry."
                )
            }
        }

    private fun fetchRelease(endpoint: String): ReleaseInfo? {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        conn.connect()
        if (conn.responseCode == 404) return null
        require(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val tag = json.optString("tag_name", "")
        val name = json.optString("name", tag)
        val body = json.optString("body", "")
        // Prefer the explicit `Version: \`x\`` line in our release notes,
        // then the title, then the tag itself.
        val version = Regex("Version:\\s*`([^`]+)`").find(body)?.groupValues?.get(1)
            ?: Regex("(\\d+\\.\\d+\\.\\d+(?:-beta)?)").find(name)?.groupValues?.get(1)
            ?: tag.removePrefix("v")
        val build = Regex("#(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        var apk: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.startsWith("Flambo-") && assetName.endsWith(".apk")) {
                    apk = asset.optString("browser_download_url", null)
                    break
                }
            }
        }
        return ReleaseInfo(tag, name, version, build, json.optString("html_url", ""), apk)
    }

    // Triplet compare; a stable release beats a beta of the same triplet.
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String): List<Int> =
            (v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)).take(3)
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0..2) {
            if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        }
        val aBeta = a.contains("-beta")
        val bBeta = b.contains("-beta")
        return when {
            aBeta == bBeta -> 0
            aBeta -> -1
            else -> 1
        }
    }
}
