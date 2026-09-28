package com.roadseye.dashcam

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.roadseye.dashcam.settingssection.STAT_CLIPS_SAVED_KEY
import com.roadseye.dashcam.settingssection.STAT_CRASH_CLIPS_SAVED_KEY
import com.roadseye.dashcam.settingssection.STAT_DISTANCE_METERS_KEY
import com.roadseye.dashcam.settingssection.STAT_DRIVES_KEY
import com.roadseye.dashcam.settingssection.STAT_RECORDING_TIME_MS_KEY
import com.roadseye.dashcam.settingssection.STAT_TOP_SPEED_MS_KEY
import com.roadseye.dashcam.settingssection.STAT_VIDEOS_SAVED_KEY
import com.roadseye.dashcam.settingssection.dataStore
import com.roadseye.dashcam.widget.StatsWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Accumulates lifetime driving stats into DataStore while a recording is active. */
class DrivingStatsManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DrivingStatsManager"

        // GPS fixes worse than this are ignored for distance
        private const val MAX_ACCURACY_METERS = 50f

        // Sanity cap (~200 mph) to filter GPS teleport glitches
        private const val MAX_REALISTIC_SPEED_MS = 90.0

        // Distance chain breaks if fixes are more than this far apart
        private const val MAX_FIX_GAP_SECONDS = 10.0

        // A stop then start within this window counts as the same drive.
        private const val DRIVE_RESUME_GRACE_MS = 60_000L

        // Distance/top speed are batched in memory and flushed on this interval
        private const val FLUSH_INTERVAL_MS = 30_000L

        @Volatile
        private var instance: DrivingStatsManager? = null

        @SuppressLint("StaticFieldLeak")
        fun getInstance(context: Context): DrivingStatsManager {
            return instance ?: synchronized(this) {
                instance ?: DrivingStatsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val lock = Any()
    private var lastFix: Location? = null
    private var pendingDistanceMeters = 0.0
    private var pendingTopSpeedMs = 0.0

    init {
        // Counts drives on start and flushes pending stats on stop.
        scope.launch {
            var wasRecording = false
            var lastStopAt = 0L
            RecordingManager.getInstance(context).isRecording.collect { recording ->
                if (recording && !wasRecording) {
                    if (System.currentTimeMillis() - lastStopAt > DRIVE_RESUME_GRACE_MS) {
                        incrementDrives()
                    }
                } else if (!recording && wasRecording) {
                    lastStopAt = System.currentTimeMillis()
                    synchronized(lock) { lastFix = null }
                    flushPending()
                }
                wasRecording = recording
            }
        }

        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushPending()
            }
        }
    }

    /** Called on every GPS fix; only accumulates while a recording is active. */
    fun onLocationUpdate(location: Location) {
        if (!RecordingManager.getInstance(context).isRecording.value) return
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return

        synchronized(lock) {
            val speedMs = location.speed.toDouble().coerceAtLeast(0.0)
            if (speedMs <= MAX_REALISTIC_SPEED_MS && speedMs > pendingTopSpeedMs) {
                pendingTopSpeedMs = speedMs
            }

            val prev = lastFix
            lastFix = location
            if (prev != null) {
                val gapSeconds = (location.time - prev.time) / 1000.0
                if (gapSeconds <= 0 || gapSeconds > MAX_FIX_GAP_SECONDS) return
                val meters = prev.distanceTo(location).toDouble()
                if (meters / gapSeconds <= MAX_REALISTIC_SPEED_MS) {
                    pendingDistanceMeters += meters
                }
            }
        }
    }

    fun onSegmentSaved(durationMs: Long) {
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[STAT_VIDEOS_SAVED_KEY] = (prefs[STAT_VIDEOS_SAVED_KEY] ?: 0) + 1
                    prefs[STAT_RECORDING_TIME_MS_KEY] =
                        (prefs[STAT_RECORDING_TIME_MS_KEY] ?: 0L) + durationMs.coerceAtLeast(0L)
                }
                StatsWidgetProvider.requestUpdate(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record segment stats", e)
            }
        }
    }

    fun onManualClipSaved() {
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[STAT_CLIPS_SAVED_KEY] = (prefs[STAT_CLIPS_SAVED_KEY] ?: 0) + 1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record clip stat", e)
            }
        }
    }

    fun onCrashClipSaved() {
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[STAT_CRASH_CLIPS_SAVED_KEY] = (prefs[STAT_CRASH_CLIPS_SAVED_KEY] ?: 0) + 1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record crash clip stat", e)
            }
        }
    }

    private fun incrementDrives() {
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[STAT_DRIVES_KEY] = (prefs[STAT_DRIVES_KEY] ?: 0) + 1
                }
                StatsWidgetProvider.requestUpdate(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record drive stat", e)
            }
        }
    }

    private fun flushPending() {
        val distance: Double
        val topSpeed: Double
        synchronized(lock) {
            distance = pendingDistanceMeters
            topSpeed = pendingTopSpeedMs
            pendingDistanceMeters = 0.0
            pendingTopSpeedMs = 0.0
        }
        if (distance <= 0.0 && topSpeed <= 0.0) return

        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    if (distance > 0.0) {
                        prefs[STAT_DISTANCE_METERS_KEY] = (prefs[STAT_DISTANCE_METERS_KEY] ?: 0.0) + distance
                    }
                    if (topSpeed > (prefs[STAT_TOP_SPEED_MS_KEY] ?: 0.0)) {
                        prefs[STAT_TOP_SPEED_MS_KEY] = topSpeed
                    }
                }
                StatsWidgetProvider.requestUpdate(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush pending stats", e)
            }
        }
    }
}
