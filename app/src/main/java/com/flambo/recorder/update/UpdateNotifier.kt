package com.flambo.recorder.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.flambo.recorder.MainActivity
import com.flambo.recorder.R

// System ping when the launch-time auto-check finds a newer build.
// Fires once per version (see notifiedUpdateVersion in prefs).
object UpdateNotifier {

    private const val CHANNEL_ID = "flambo_updates"
    private const val NOTIF_ID = 2001

    fun show(context: Context, release: ReleaseInfo) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flambo)
            .setContentTitle("Flambo update ready")
            .setContentText("v${release.version} is available — tap to view")
            .setContentIntent(openApp)
            .setAutoCancel(true)

        val url = release.bestApk()?.url?.ifBlank { null } ?: release.pageUrl
        if (url.isNotBlank()) {
            val download = PendingIntent.getActivity(
                context, 1,
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Download", download)
        }

        mgr.notify(NOTIF_ID, builder.build())
    }
}
