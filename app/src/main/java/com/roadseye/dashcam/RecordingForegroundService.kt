package com.roadseye.dashcam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecordingForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dashcam_recording_channel"
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val recordingManager = RecordingManager.getInstance(this)
    private val cameraManager = CameraManager.getInstance(this)

    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    startForegroundWithNotification()
                    startRecordingLoop()
                }
            }
            ACTION_STOP_RECORDING -> {
                stopRecordingLoop()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildRecordingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildRecordingNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Road’s Eye Recording")
            .setContentText("Dashcam is actively recording")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dashcam Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while dashcam is recording"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // Only retries until the first startRecording() call succeeds.
    private fun startRecordingLoop() {
        scope.launch {
            while (isServiceRunning && !recordingManager.isRecording.value) {
                val videoCapture = cameraManager.getVideoCapture()
                if (videoCapture != null) {
                    try {
                        recordingManager.startRecording(videoCapture)
                    } catch (e: Exception) {
                        Log.e("RecordingService", "Failed to start recording segment", e)
                    }
                } else {
                    Log.w("RecordingService", "VideoCapture not ready, retrying...")
                }
                delay(1000)
            }
        }
    }

    private fun stopRecordingLoop() {
        isServiceRunning = false
        scope.launch {
            recordingManager.stopAndAlwaysDiscard()
            Log.d("RecordingService", "Recording loop stopped")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingLoop()
        scope.cancel()
        stopForeground(true)
        Log.d("RecordingService", "Foreground service destroyed")
    }
}
