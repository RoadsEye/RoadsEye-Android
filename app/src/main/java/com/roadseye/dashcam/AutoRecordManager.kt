package com.roadseye.dashcam

import android.content.Context
import android.util.Log
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import com.roadseye.dashcam.settingssection.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")
@ExperimentalCamera2Interop
@OptIn(ExperimentalCamera2Interop::class)
class AutoRecordManager private constructor(context: Context) {

    companion object {
        private var instance: AutoRecordManager? = null

        fun getInstance(context: Context): AutoRecordManager {
            return instance ?: synchronized(this) {
                instance ?: AutoRecordManager(context.applicationContext).also { instance = it }
            }
        }

        const val TAG = "AutoRecordManager"
        private val AUTO_RECORD_KEY = booleanPreferencesKey("auto_record")
        private val SPEED_THRESHOLD_KEY = floatPreferencesKey("speed_threshold") // stored in MPH
        private val DURATION_THRESHOLD_KEY = floatPreferencesKey("duration_threshold")
    }

    private val appContext: Context = context.applicationContext

    private val recordingManager = RecordingManager.getInstance(appContext)
    private val locationSpeedManager = LocationSpeedManager.getInstance(appContext)
    private val cameraManager = CameraManager.getInstance(appContext)

    private var autoRecordJob: Job? = null
    private var speedAboveThresholdStartTime: Long? = null

    fun startMonitoring() {
        if (autoRecordJob?.isActive == true) return

        autoRecordJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                appContext.dataStore.data.map { it[AUTO_RECORD_KEY] ?: false },
                appContext.dataStore.data.map { it[SPEED_THRESHOLD_KEY] ?: 5f },
                appContext.dataStore.data.map { it[DURATION_THRESHOLD_KEY] ?: 3f },
                locationSpeedManager.displaySpeed,
                locationSpeedManager.speedUnit
            ) { autoRecordEnabled, thresholdMph, durationSec, currentSpeedDisplay, unit ->
                Triple(autoRecordEnabled, thresholdMph, Triple(durationSec, currentSpeedDisplay, unit))
            }.collect { (autoRecordEnabled, thresholdMph, config) ->
                val (durationSec, currentSpeedDisplay, unit) = config

                if (!autoRecordEnabled) {
                    resetTrackingState()
                    Log.d(TAG, "Auto record disabled - resetting state")
                    return@collect
                }

                val currentSpeedMph = if (unit == "MPH") currentSpeedDisplay else currentSpeedDisplay * 0.621371f
                val threshold = thresholdMph.toDouble()

                if (currentSpeedMph >= threshold) {
                    if (speedAboveThresholdStartTime == null) {
                        speedAboveThresholdStartTime = System.currentTimeMillis()
                        Log.d(TAG, "Speed above threshold ($currentSpeedMph MPH >= $threshold MPH). Starting timer...")
                    }

                    val elapsedSeconds = (System.currentTimeMillis() - speedAboveThresholdStartTime!!) / 1000.0
                    if (elapsedSeconds >= durationSec) {
                        if (!recordingManager.isRecording.value) {
                            Log.d(TAG, "Auto-starting recording: speed sustained for ${elapsedSeconds.toInt()}s")

                            NotificationManager.showRecordingStarted(appContext)

                            startRecordingIfPossible()
                        }
                        speedAboveThresholdStartTime = null // Prevent repeated triggers
                    }
                } else {
                    if (speedAboveThresholdStartTime != null) {
                        Log.d(TAG, "Speed dropped below threshold ($currentSpeedMph MPH < $threshold MPH). Resetting timer.")
                        speedAboveThresholdStartTime = null
                    }
                }
            }
        }

        locationSpeedManager.startLocationUpdates(appContext)
        Log.d(TAG, "AutoRecord monitoring started")
    }

    fun stopMonitoring() {
        autoRecordJob?.cancel()
        autoRecordJob = null
        resetTrackingState()
        locationSpeedManager.stopLocationUpdates()
        Log.d(TAG, "AutoRecord monitoring stopped")
    }

    private fun resetTrackingState() {
        speedAboveThresholdStartTime = null
    }

    private suspend fun startRecordingIfPossible() {
        withContext(Dispatchers.Main) {
            val videoCapture = cameraManager.getVideoCapture()
            if (videoCapture != null && !recordingManager.isRecording.value) {
                recordingManager.startRecording(videoCapture)
                Log.d(TAG, "Auto record started successfully")
            } else {
                Log.w(TAG, "Cannot auto-start recording: videoCapture=null or already recording")
            }
        }
    }
}