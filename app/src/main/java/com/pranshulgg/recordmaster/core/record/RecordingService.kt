package com.pranshulgg.recordmaster.core.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pranshulgg.recordmaster.MainActivity
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.utils.formatMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground notification for an active recording, with Pause/Resume/Save
 * actions. The controller owns the MediaRecorder; this service only mirrors
 * its state.
 */
class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recordmaster_recording"
        const val NOTIF_ID = 1001
        const val ACTION_PAUSE = "com.pranshulgg.recordmaster.action.PAUSE"
        const val ACTION_RESUME = "com.pranshulgg.recordmaster.action.RESUME"
        const val ACTION_STOP = "com.pranshulgg.recordmaster.action.STOP"
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

        val notification = buildNotification(0L, false)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing voice recording"
        }
        manager.createNotificationChannel(channel)
    }

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
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, notification)
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(elapsedMs: Long, isPaused: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val elapsed = elapsedMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val title = (if (isPaused) "Paused • " else "Recording • ") + formatMillis(elapsed)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)

        if (isPaused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                actionIntent(ACTION_RESUME, 2)
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionIntent(ACTION_PAUSE, 1)
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_save,
            "Save",
            actionIntent(ACTION_STOP, 3)
        )
        return builder.build()
    }
}
