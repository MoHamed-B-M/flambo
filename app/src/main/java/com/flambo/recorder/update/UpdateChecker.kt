package com.flambo.recorder.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ApkAsset(val name: String, val url: String)

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val version: String, // e.g. 1.0.6 or 1.0.6-beta
    val buildNumber: Int, // parsed from "(#N)" in the preview title, 0 if absent
    val pageUrl: String,
    val apkAssets: List<ApkAsset>
) {
    // Releases carry one APK per ABI (arm64 / armv7) — pick this device's.
    fun bestApk(): ApkAsset? {
        if (apkAssets.isEmpty()) return null
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        val want = when {
            abis.any { it.contains("arm64", ignoreCase = true) } -> "arm64"
            abis.any { it.contains("armeabi", ignoreCase = true) || it.contains("armv7", ignoreCase = true) } -> "armv7"
            else -> null
        }
        if (want != null) apkAssets.firstOrNull { "-$want." in it.name }?.let { return it }
        return apkAssets.first()
    }

    // Back-compat for callers that just need any APK link.
    val apkUrl: String? get() = bestApk()?.url
}

data class WhatsNewItem(val title: String, val body: String)

// Pulls the "## What's in …" bullet list from the latest stable release
// notes, so the in-app What's New sheet always matches GitHub.
// Returns null when offline or unparsable — callers fall back to bundled text.
object ReleaseNotes {

    suspend fun fetchWhatsNew(): List<WhatsNewItem>? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$API/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) return@withContext null
            parseWhatsNew(JSONObject(conn.inputStream.bufferedReader().readText()).optString("body", ""))
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun parseWhatsNew(body: String): List<WhatsNewItem> {
        val items = mutableListOf<WhatsNewItem>()
        var inSection = false
        for (raw in body.lines()) {
            val line = raw.trim()
            if (line.startsWith("## ")) {
                inSection = "what's in" in line.lowercase()
                continue
            }
            if (!inSection || !line.startsWith("- ")) continue
            val text = line.removePrefix("- ").trim()
            // "Playback: waveform scrubber…" → title + body; plain sentences
            // stay body-only so nothing reads awkwardly.
            val split = Regex("^([^:]{2,32}):\\s+(.+)$").find(text)
            if (split != null) items += WhatsNewItem(split.groupValues[1], split.groupValues[2])
            else items += WhatsNewItem("", text)
        }
        return items
    }

    private const val API = "https://api.github.com/repos/MoHamed-B-M/flambo/releases"
}

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

    // Mirrors BETA_CODE_OFFSET in build.yaml: beta codes sit above any
    // stable build so installing a beta over stable always counts as an
    // update. Keep the two in sync if the workflow value ever changes.
    const val BETA_CODE_OFFSET = 100000L

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
            // Recover the CI run number for beta-vs-beta comparison: beta
            // codes are BETA_CODE_OFFSET + run, stable codes are the build itself.
            val installedBuild = if (installedCode >= BETA_CODE_OFFSET) {
                installedCode - BETA_CODE_OFFSET
            } else {
                (installedCode - 1).coerceAtLeast(0)
            }
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
            ?: Regex("(\\d+\\.\\d+\\.\\d+(?:-beta|-dev)?)").find(name)?.groupValues?.get(1)
            ?: tag.removePrefix("v")
        val build = Regex("#(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val apks = mutableListOf<ApkAsset>()
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (assetName.startsWith("Flambo-") && assetName.endsWith(".apk") && url.isNotBlank()) {
                    apks += ApkAsset(assetName, url)
                }
            }
        }
        return ReleaseInfo(tag, name, version, build, json.optString("html_url", ""), apks)
    }

    // Triplet compare; a stable release beats a beta/dev of the same triplet.
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String): List<Int> =
            (v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)).take(3)
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0..2) {
            if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        }
        fun pre(v: String) = v.contains("-beta") || v.contains("-dev")
        val aPre = pre(a)
        val bPre = pre(b)
        return when {
            aPre == bPre -> 0
            aPre -> -1
            else -> 1
        }
    }
}
