package com.pranshulgg.recordmaster.core.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val audioFocusHandler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null
    private var audioFocusHeld = false

    /**
     * Audio focus is the interruption signal — no intrusive phone-state
     * permission required. A recorder has no volume to duck, so every loss
     * means "pause the take". Focus gain deliberately does NOT auto-resume:
     * restarting the microphone after a call without an explicit tap could
     * capture a private conversation.
     */
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val controller = RecordingController.instance
                val current = controller?.state?.value
                if (current != null && current.isRecording && !current.isPaused) {
                    controller.pause(PauseReason.INTERRUPTION)
                    refreshNow()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> Unit
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        requestAudioFocus()
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

        val initialState = RecordingController.instance?.state?.value
            ?: RecordingController.RecorderState(isRecording = true)
        val notification = buildNotification(initialState)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
        abandonAudioFocus()
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun requestAudioFocus() {
        if (audioFocusHeld) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                // A recorder can't duck — make even MAY_DUCK losses visible so
                // we pause instead of capturing another app's audio.
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener, audioFocusHandler)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        audioFocusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!audioFocusHeld && focusRequest == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        focusRequest = null
        audioFocusHeld = false
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
                notify(buildNotification(state))
                delay(1000)
            }
        }
    }

    private fun refreshNow() {
        val state = RecordingController.instance?.state?.value ?: run {
            stopSelf()
            return
        }
        notify(buildNotification(state))
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

    private fun buildNotification(state: RecordingController.RecorderState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val elapsed = state.elapsedMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val isPaused = state.isPaused
        val interrupted = state.pauseReason == PauseReason.INTERRUPTION
        val title = when {
            interrupted -> "Interrupted • "
            isPaused -> "Paused • "
            else -> "Recording • "
        } + formatMillis(elapsed)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(
                getString(
                    if (interrupted) R.string.recording_paused_interruption
                    else R.string.recording_notification_text
                )
            )
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
