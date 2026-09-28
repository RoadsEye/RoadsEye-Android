package com.roadseye.dashcam

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.roadseye.dashcam.settingssection.dataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.File

class RecordingManager private constructor(private val context: Context) {

    companion object {
        private var instance: RecordingManager? = null

        @SuppressLint("StaticFieldLeak")
        fun getInstance(context: Context): RecordingManager {
            return instance ?: synchronized(this) {
                instance ?: RecordingManager(context.applicationContext).also { instance = it }
            }
        }

        private val SEGMENT_DURATION_KEY = stringPreferencesKey("segment_duration")
        private val MAX_STORAGE_MB_KEY = intPreferencesKey("max_storage_mb") // 0 = unlimited
    }

    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentRecordingUri: Uri? = null
    var lastSavedUri: Uri? = null

    private var recordingStartTimeMs: Long? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var segmentDurationMinutes = 3
    private var maxStorageMB = 2000 // 2GB default

    var onSaved: () -> Unit = {}

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        scope.launch {
            context.dataStore.data.collect { prefs ->
                val rawDuration = prefs[SEGMENT_DURATION_KEY] ?: "1m"
                segmentDurationMinutes = rawDuration.removeSuffix("m").toIntOrNull() ?: 1

                maxStorageMB = prefs[MAX_STORAGE_MB_KEY] ?: 2000

                Log.d("RecordingManager",
                    "Preferences loaded → segment=${segmentDurationMinutes}m, maxStorage=${maxStorageMB}MB")
            }
        }
    }

    /** Starts continuous recording through the foreground service. */
    fun startRecording(videoCapture: VideoCapture<Recorder>) {
        if (activeRecording != null || loopJob?.isActive == true) {
            Log.w("RecordingManager", "Recording loop already running")
            return
        }

        this.videoCapture = videoCapture
        _isRecording.value = true
        NotificationManager.showRecordingStarted(context)
        com.roadseye.dashcam.widget.QuickRecordWidgetProvider.updateAll(context)

        // Start the foreground service to keep recording alive during calls/background
        val serviceIntent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START_RECORDING
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        startSegmentLoop()
    }

    private fun startSegmentLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                recordSingleSegment()
                delay(1000)
            }
        }
    }

    private suspend fun recordSingleSegment() {
        val durationMs = segmentDurationMinutes * 60 * 1000L

        // Free up space before recording by deleting the oldest loop videos.
        withContext(Dispatchers.IO) {
            ensureSpaceForNextSegment()
        }

        val name = "roadsEye_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RoadsEye")
        }

        val videoCollection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        currentRecordingUri = context.contentResolver.insert(videoCollection, contentValues) ?: return

        val outputOptions = MediaStoreOutputOptions.Builder(context.contentResolver, videoCollection)
            .setContentValues(contentValues)
            .build()

        val pendingRecording = videoCapture?.output?.prepareRecording(context, outputOptions) ?: return

        val segmentStartMs = System.currentTimeMillis()
        recordingStartTimeMs = segmentStartMs

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
                currentRecordingUri?.let { uri ->
                    if (!event.hasError()) {
                        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                        lastSavedUri = uri
                        onSaved.invoke()
                        Log.d("RecordingManager", "Segment saved: $uri")
                        DrivingStatsManager.getInstance(context)
                            .onSegmentSaved(System.currentTimeMillis() - segmentStartMs)
                        enforceStorageLimit()
                    } else {
                        context.contentResolver.delete(uri, null, null)
                        Log.e("RecordingManager", "Segment recording failed")
                    }
                }
                currentRecordingUri = null
            }
        }

        delay(durationMs)
        activeRecording?.stop()
        activeRecording = null
    }

    /** Loop recordings in Movies/RoadsEye, excluding Clips/ and Crashes/, oldest first. */
    private fun getLoopVideosOldestFirst(): List<File> {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val roadsEyeDir = File(moviesDir, "RoadsEye")

        if (!roadsEyeDir.exists() || !roadsEyeDir.isDirectory) return emptyList()

        return roadsEyeDir.walkTopDown()
            .filter { file ->
                file.isFile &&
                        file.extension.lowercase() in listOf("mp4", "mov") &&
                        !file.parentFile?.name.equals("Clips", ignoreCase = true) &&
                        !file.parentFile?.name.equals("Crashes", ignoreCase = true)
            }
            .sortedBy { it.lastModified() }
            .toList()
    }

    /** Deletes a loop video and its MediaStore entry. */
    private fun deleteLoopVideo(file: File): Boolean {
        val path = file.absolutePath
        val deleted = file.delete()
        if (deleted) {
            try {
                context.contentResolver.delete(
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${MediaStore.Video.Media.DATA}=?",
                    arrayOf(path)
                )
            } catch (e: Exception) {
                Log.w("RecordingManager", "Could not remove MediaStore entry for $path", e)
            }
        }
        return deleted
    }

    /** Deletes the oldest loop videos until there is room for the next segment. */
    private fun ensureSpaceForNextSegment() {
        try {
            val files = getLoopVideosOldestFirst().toMutableList()

            // Estimate the next segment's size generously (~10 Mbps + margin)
            val estimatedSegmentBytes =
                (segmentDurationMinutes * 60L * 10_000_000L / 8).coerceAtLeast(100L * 1024 * 1024)

            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            var usedBytes = files.sumOf { it.length() }
            val limitBytes = if (maxStorageMB > 0) maxStorageMB * 1024L * 1024L else Long.MAX_VALUE

            while (files.isNotEmpty() &&
                (usedBytes + estimatedSegmentBytes > limitBytes ||
                        moviesDir.usableSpace < estimatedSegmentBytes)
            ) {
                val oldest = files.removeAt(0)
                val size = oldest.length()
                if (deleteLoopVideo(oldest)) {
                    usedBytes -= size
                    Log.d("RecordingManager", "Rotated out oldest video for new segment: ${oldest.name}")
                } else {
                    Log.w("RecordingManager", "Failed to delete old video: ${oldest.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("RecordingManager", "Error freeing space for next segment", e)
        }
    }

    private fun enforceStorageLimit() {
        if (maxStorageMB <= 0) return

        scope.launch(Dispatchers.IO) {
            try {
                val files = getLoopVideosOldestFirst().toMutableList()

                if (files.isEmpty()) {
                    Log.d("RecordingManager", "No eligible files to enforce storage limit on")
                    return@launch
                }

                var totalSizeMB = files.sumOf { it.length() } / (1024 * 1024)

                while (totalSizeMB > maxStorageMB && files.isNotEmpty()) {
                    val oldest = files.removeAt(0)
                    val sizeMB = oldest.length() / (1024 * 1024)
                    if (deleteLoopVideo(oldest)) {
                        Log.d("RecordingManager", "Deleted old clip to enforce storage limit: ${oldest.name}")
                        totalSizeMB -= sizeMB
                    } else {
                        Log.w("RecordingManager", "Failed to delete old clip: ${oldest.name}")
                    }
                }

                Log.d("RecordingManager", "Storage enforcement finished. Used: ${totalSizeMB}MB / Limit: ${maxStorageMB}MB")
            } catch (e: Exception) {
                Log.e("RecordingManager", "Error enforcing storage limit", e)
            }
        }
    }

    /** Stops recording and the foreground service. */
    suspend fun stopAndAlwaysDiscard() = withContext(Dispatchers.Main) {
        Log.d("RecordingManager", "stopAndAlwaysDiscard() — stopping rolling recording")

        // Stop the foreground service
        val stopIntent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP_RECORDING
        }
        context.stopService(stopIntent)

        loopJob?.cancel()
        loopJob = null
        activeRecording?.stop()
        activeRecording = null
        _isRecording.value = false
        NotificationManager.showRecordingStopped(context)
        com.roadseye.dashcam.widget.QuickRecordWidgetProvider.updateAll(context)
    }

    suspend fun performManualClip(duration: String): Pair<Boolean, Uri?> = withContext(Dispatchers.Main) {
        if (!isRecording.value) {
            Log.w("RecordingManager", "Cannot clip - recording is not active")
            return@withContext false to null
        }

        Log.d("RecordingManager", "Manual clip requested — stopping current recording...")

        stopAndAlwaysDiscard()
        delay(800)

        val clippingManager = ClippingManager.getInstance(context)
        val sourceUri = lastSavedUri ?: currentRecordingUri

        if (sourceUri == null) {
            Log.e("RecordingManager", "No source URI available for manual clip")
            videoCapture?.let { startRecording(it) }
            return@withContext false to null
        }

        val (success, clipUri) = clippingManager.clipVideo(
            videoUri = sourceUri,
            durationInMinutes = duration,
            locationSpeedManager = null,
            recordingStartMs = recordingStartTimeMs
        )

        videoCapture?.let {
            Log.d("RecordingManager", "Restarting recording after manual clip")
            startRecording(it)
        }

        if (success && clipUri != null) {
            Log.d("RecordingManager", "Manual clip saved successfully: $clipUri")
            DrivingStatsManager.getInstance(context).onManualClipSaved()
        } else {
            Log.e("RecordingManager", "Manual clip failed")
        }

        success to clipUri
    }

    suspend fun performCrashClip(locationSpeedManager: LocationSpeedManager? = null): Pair<Boolean, Uri?> = withContext(Dispatchers.Main) {
        val clippingManager = ClippingManager.getInstance(context)

        var sourceUri = lastSavedUri

        if (sourceUri == null && currentRecordingUri != null && isRecording.value) {
            Log.w("RecordingManager", "No finalized segment for crash — forcing stop of current recording")
            stopAndAlwaysDiscard()
            delay(1200)
            sourceUri = lastSavedUri ?: currentRecordingUri
        }

        if (sourceUri == null) {
            Log.e("RecordingManager", "No clip found for crash clip — no source URI available")
            videoCapture?.let { startRecording(it) }
            return@withContext false to null
        }

        Log.d("RecordingManager", "Performing crash clip from source: $sourceUri")

        val (success, clipUri) = clippingManager.clipCrashVideo(
            videoUri = sourceUri,
            locationSpeedManager = locationSpeedManager,
            recordingStartMs = recordingStartTimeMs
        )

        if (videoCapture != null) {
            Log.d("RecordingManager", "Restarting recording after crash clip")
            startRecording(videoCapture!!)
        }

        if (success && clipUri != null) {
            Log.d("RecordingManager", "Crash clip saved: $clipUri")
            DrivingStatsManager.getInstance(context).onCrashClipSaved()
        } else {
            Log.e("RecordingManager", "Crash clip failed")
        }

        success to clipUri
    }

    fun getCurrentRecordingUri(): Uri? = currentRecordingUri

    fun getVideoCapture(): VideoCapture<Recorder>? = videoCapture

    fun clearLastSavedUri() {
        lastSavedUri = null
    }

    fun getRecordingStartTimeMs(): Long? = recordingStartTimeMs
}
