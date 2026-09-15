package com.flambo.recorder.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import java.io.File

/**
 * Update download state that outlives the Settings screen.
 *
 * Remembered in the nav graph (not in SettingsScreen) so closing Settings
 * mid-download neither loses progress nor allows a second download to
 * start. A finished download stays on disk until the user taps Install
 * (or deletes it) — reopening Settings shows Install, not Download again.
 */
class UpdateDownloadState {
    var progress by mutableStateOf<Float?>(null)
    var downloadedFile by mutableStateOf<File?>(null)
    var downloadedAssetName by mutableStateOf<String?>(null)
    var job: Job? = null

    // The saved file only counts for the release it was downloaded from.
    fun fileFor(assetName: String?): File? =
        downloadedFile?.takeIf { it.exists() && assetName != null && it.name == assetName }

    fun clear() {
        job?.cancel()
        job = null
        progress = null
        downloadedFile = null
        downloadedAssetName = null
    }
}
