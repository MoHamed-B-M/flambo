package com.flambo.recorder.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flambo.recorder.MainActivity
import com.flambo.recorder.R
import com.flambo.recorder.domain.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "flambo_recording"
        const val NOTIF_ID = 1001
        const val ACTION_PAUSE = "flambo.action.PAUSE"
        const val ACTION_RESUME = "flambo.action.RESUME"
        const val ACTION_STOP = "flambo.action.STOP" // stop & save
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                RecordingController.instance?.pause()
                refreshNow()
                return START_STICKY
            }
            ACTION_RESUME -> {
                RecordingController.instance?.resume()
                refreshNow()
                return START_STICKY
            }
            ACTION_STOP -> {
                RecordingController.instance?.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotification(0L, false))
        startTicker()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Ongoing voice recording" }
        mgr.createNotificationChannel(channel)
    }

    // Ticks once a second so the timer stays live; each update is silent.
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val state = RecordingController.instance?.state?.value
                if (state == null || !state.isRecording) {
                    stopSelf()
                    break
                }
                notify(buildNotification(state.elapsedMs, state.isPaused))
                delay(1000)
            }
        }
    }

    private fun refreshNow() {
        val state = RecordingController.instance?.state?.value ?: run {
            stopSelf()
            return
        }
        notify(buildNotification(state.elapsedMs, state.isPaused))
    }

    private fun notify(notification: Notification) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, notification)
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(elapsedMs: Long, isPaused: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = (if (isPaused) "Paused • " else "Recording • ") + formatDuration(elapsedMs)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(R.drawable.ic_stat_flambo)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
        if (isPaused) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", actionIntent(ACTION_RESUME, 2))
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", actionIntent(ACTION_PAUSE, 1))
        }
        builder.addAction(android.R.drawable.ic_menu_save, "Save", actionIntent(ACTION_STOP, 3))
        return builder.build()
    }
}
