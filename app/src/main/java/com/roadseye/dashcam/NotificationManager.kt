package com.roadseye.dashcam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationManager {

    private const val CHANNEL_ID_REGULAR = "dashcam_status_channel"
    private const val CHANNEL_ID_CRASH = "dashcam_crash_channel"
    private const val NOTIFICATION_ID_BASE = 1000

    var notificationsEnabled: Boolean = true
        private set

    fun initialize(context: Context) {
        createNotificationChannels(context)
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val defaultSoundUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val regularChannel = NotificationChannel(
            CHANNEL_ID_REGULAR,
            "Dashcam Status",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Recording status and clip saving"
            setShowBadge(true)
            enableLights(true)
            enableVibration(true)
            setSound(defaultSoundUri, audioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val crashChannel = NotificationChannel(
            CHANNEL_ID_CRASH,
            "Crash Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical crash detection alerts"
            setShowBadge(true)
            enableLights(true)
            enableVibration(true)
            setSound(defaultSoundUri, audioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(regularChannel)
        nm.createNotificationChannel(crashChannel)
    }

    private fun getAppIcon(context: Context): Int {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
            appInfo.icon
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }
    }

    private fun buildNotification(
        context: Context,
        title: String,
        text: String,
        channelId: String = CHANNEL_ID_REGULAR,
        isCritical: Boolean = false,
        autoCancel: Boolean = true
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val vibrationPattern = if (isCritical) {
            longArrayOf(0, 300, 100, 300)
        } else {
            longArrayOf(0, 150, 100, 150)
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getAppIcon(context))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(autoCancel)
            .setTimeoutAfter(if (autoCancel && !isCritical) 4000 else 0)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVibrate(vibrationPattern)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun showRecordingStarted(context: Context) {
        if (!notificationsEnabled) return
        val n = buildNotification(context, "Recording Started", "Dashcam is now recording")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + 1, n)
    }

    fun showRecordingStopped(context: Context) {
        if (!notificationsEnabled) return
        val n = buildNotification(context, "Recording Stopped", "Recording has been paused")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + 2, n)
    }

    fun showRecordingSaving(context: Context) {
        if (!notificationsEnabled) return
        val n = buildNotification(context, "Saving Recording", "Finalizing video segment...")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + 3, n)
    }

    fun showRecordingSaved(context: Context) {
        if (!notificationsEnabled) return
        val n = buildNotification(context, "Recording Saved", "Video segment saved successfully")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + 4, n)
    }

    fun showCrashDetected(context: Context) {
        if (!notificationsEnabled) return
        val n = buildNotification(
            context = context,
            title = "Crash Detected!",
            text = "Possible collision detected",
            channelId = CHANNEL_ID_CRASH,
            isCritical = true,
            autoCancel = false
        )
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + 5, n)
    }

    fun showCrashClipSaved(context: Context) {
        if (!notificationsEnabled) return
        val n = buildNotification(
            context = context,
            title = "Crash Clip Saved",
            text = "Emergency clip has been saved",
            channelId = CHANNEL_ID_CRASH,
            isCritical = true,
            autoCancel = false
        )
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + 6, n)
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    // Enables or disables notifications.
    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
    }
}
