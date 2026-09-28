package com.roadseye.dashcam

import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.roadseye.dashcam.settingssection.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.sqrt

class CrashDetectionManager private constructor(private val context: Context) : LifecycleEventObserver {

    companion object {
        @Volatile
        private var INSTANCE: CrashDetectionManager? = null

        fun getInstance(context: Context): CrashDetectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CrashDetectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // Linear acceleration excludes gravity, which is what the 1.75g threshold is calibrated against.
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var sensorListener: SensorEventListener? = null
    private var lastCrashResolvedTime: Long = 0L
    private val crashCooldownMs = 30_000L

    private var isRecording = false

    private val _isCrashDetectionEnabled = MutableStateFlow(true)
    val isCrashDetectionEnabled: StateFlow<Boolean> = _isCrashDetectionEnabled.asStateFlow()

    private val _showCrashAlert = MutableStateFlow(false)
    val showCrashAlert: StateFlow<Boolean> = _showCrashAlert.asStateFlow()

    private val _crashTimerProgress = MutableStateFlow(1.0f)
    val crashTimerProgress: StateFlow<Float> = _crashTimerProgress.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val clippingManager = ClippingManager.getInstance(context)
    private val recordingManager = RecordingManager.getInstance(context)

    init {
        loadCrashDetectionPreference()
    }

    private fun loadCrashDetectionPreference() {
        scope.launch {
            val enabled = context.dataStore.data
                .map { it[booleanPreferencesKey("isCrashDetectionEnabled")] ?: true }
                .first()
            _isCrashDetectionEnabled.value = enabled
            if (enabled) startMonitoring()
        }
    }

    fun setCrashDetectionEnabled(enabled: Boolean) {
        _isCrashDetectionEnabled.value = enabled
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[booleanPreferencesKey("isCrashDetectionEnabled")] = enabled
            }
        }
        if (enabled) startMonitoring() else stopMonitoring()
    }

    fun updateRecordingState(recording: Boolean) {
        this.isRecording = recording
        Log.d("CrashDetectionManager", "Recording state updated: $recording")
    }

    private fun startMonitoring() {
        if (accelerometer == null) {
            Log.e("CrashDetectionManager", "Accelerometer not available")
            return
        }

        sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return
                if (!isRecording || !_isCrashDetectionEnabled.value) return
                if (_showCrashAlert.value) return
                if (System.currentTimeMillis() - lastCrashResolvedTime < crashCooldownMs) return

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

                if (gForce >= 1.75) {
                    Log.d("CrashDetectionManager", "CRASH DETECTED: ${"%.2f".format(gForce)}g")
                    scope.launch { triggerCrashAlert() }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        Log.d("CrashDetectionManager", "Crash detection monitoring started")
    }

    private fun stopMonitoring() {
        sensorListener?.let { sensorManager.unregisterListener(it) }
        sensorListener = null
        Log.d("CrashDetectionManager", "Crash detection monitoring stopped")
    }

    private suspend fun triggerCrashAlert() {
        if (_showCrashAlert.value) return

        NotificationManager.showCrashDetected(context)

        _showCrashAlert.value = true
        _crashTimerProgress.value = 1.0f
        Log.d("CrashDetectionManager", "Crash alert shown — 30 second countdown started")

        for (i in 0..299) {
            if (!_showCrashAlert.value) return
            delay(100)
            _crashTimerProgress.value = 1.0f - (i + 1) / 300f
        }

        if (_showCrashAlert.value) {
            Log.d("CrashDetectionManager", "30s countdown finished — auto-saving crash clip")
            saveCrashClip()
        }
    }

    fun cancelCrash() {
        if (!_showCrashAlert.value) return
        _showCrashAlert.value = false
        _crashTimerProgress.value = 1.0f
        lastCrashResolvedTime = System.currentTimeMillis()
        Log.d("CrashDetectionManager", "Crash alert cancelled by user")
    }

    private fun saveCrashClip() {
        _showCrashAlert.value = false
        _crashTimerProgress.value = 1.0f
        lastCrashResolvedTime = System.currentTimeMillis()

        scope.launch {
            // Prefer finalized segment first
            var sourceUri = recordingManager.lastSavedUri

            // If no finalized segment, force-stop current one
            if (sourceUri == null && recordingManager.isRecording.value) {
                Log.w("CrashDetectionManager", "No finalized segment → forcing stop for crash clip")
                recordingManager.stopAndAlwaysDiscard()
                delay(1200) // Give time for finalize + IS_PENDING = 0
                sourceUri = recordingManager.lastSavedUri ?: recordingManager.getCurrentRecordingUri()
            }

            if (sourceUri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No recording available for crash clip", Toast.LENGTH_LONG).show()
                }
                Log.e("CrashDetectionManager", "No URI available for crash clip")
                // Restart recording so dashcam doesn't stay stopped
                recordingManager.getVideoCapture()?.let { recordingManager.startRecording(it) }
                return@launch
            }

            Log.d("CrashDetectionManager", "Saving crash clip from: $sourceUri")

            val (success, clippedUri) = clippingManager.clipCrashVideo(
                videoUri = sourceUri,
                locationSpeedManager = null,
                recordingStartMs = recordingManager.getRecordingStartTimeMs()
            )

            withContext(Dispatchers.Main) {
                if (success && clippedUri != null) {
                    Toast.makeText(context, "Crash clip saved to Crashes/ folder", Toast.LENGTH_LONG).show()
                    NotificationManager.showCrashClipSaved(context)
                    Log.d("CrashDetectionManager", "Crash clip saved: $clippedUri")
                } else {
                    Toast.makeText(context, "Failed to save crash clip", Toast.LENGTH_LONG).show()
                    Log.e("CrashDetectionManager", "Crash clip creation failed")
                }
            }

            // Always restart recording after crash handling
            recordingManager.getVideoCapture()?.let { recordingManager.startRecording(it) }
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            stopMonitoring()
            scope.cancel()
            INSTANCE = null
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CrashAlertDialog() {
        val showAlert by showCrashAlert.collectAsState()
        val progress by crashTimerProgress.collectAsState()

        if (showAlert) {
            AlertDialog(
                onDismissRequest = { },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                    .border(3.dp, Color.Red, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Possible Crash Detected!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Video will auto-save in ${"%.0f".format(progress * 30)} seconds",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = Color.Red,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { cancelCrash() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
