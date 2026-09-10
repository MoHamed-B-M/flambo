package com.flambo.recorder.record

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

// Holds the one-shot screen-capture consent so the recorder can build a
// MediaProjection when a system-sound recording starts. The grant survives
// while the process lives; after a restart the user re-confirms in Settings.
object MediaProjectionHolder {

    private var resultCode: Int = 0
    private var data: Intent? = null

    @Synchronized
    fun grant(code: Int, intent: Intent) {
        resultCode = code
        data = Intent(intent)
    }

    @Synchronized
    fun hasGrant(): Boolean = data != null

    @Synchronized
    fun take(context: Context): MediaProjection? {
        val intent = data ?: return null
        return try {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mgr.getMediaProjection(resultCode, intent)
        } catch (_: Exception) {
            null
        }
    }
}
