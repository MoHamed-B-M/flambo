package com.flambo.recorder.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import java.io.File

class UpdateDownloadState {
    var progress by mutableStateOf<Float?>(null)
    var downloadedFile by mutableStateOf<File?>(null)
    var downloadedAssetName by mutableStateOf<String?>(null)
    var job: Job? = null

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
