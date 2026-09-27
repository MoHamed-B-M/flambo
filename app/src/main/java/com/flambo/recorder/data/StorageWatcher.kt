package com.flambo.recorder.data

import android.os.FileObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debounced [FileObserver] on a plain-filesystem directory. SAF tree URIs
 * cannot be observed, so SAF-backed folders rely on launch/resume re-scans
 * instead (see [RecordingRepository.syncExternalFiles]).
 */
class StorageWatcher(
    private val dir: File,
    private val scope: CoroutineScope,
    private val onChange: () -> Unit
) {
    private var observer: FileObserver? = null
    private var debounce: Job? = null

    fun start() {
        stop()
        if (!dir.isDirectory) return
        observer = object : FileObserver(
            dir.absolutePath,
            FileObserver.CREATE or FileObserver.DELETE or
                FileObserver.MOVED_FROM or FileObserver.MOVED_TO
        ) {
            override fun onEvent(event: Int, path: String?) {
                debounce?.cancel()
                debounce = scope.launch {
                    delay(800)
                    runCatching { onChange() }
                }
            }
        }
        observer?.startWatching()
    }

    fun stop() {
        debounce?.cancel()
        debounce = null
        observer?.stopWatching()
        observer = null
    }
}
