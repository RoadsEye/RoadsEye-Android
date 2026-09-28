package com.roadseye.dashcam

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.roadseye.dashcam.settingssection.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
class ClippingManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ClippingManager? = null

        @SuppressLint("StaticFieldLeak")
        fun getInstance(context: Context): ClippingManager {
            return instance ?: synchronized(this) {
                instance ?: ClippingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var isClippingInProgress = false

    // Manual clip
    suspend fun clipVideo(
        videoUri: Uri,
        durationInMinutes: String,
        outputUri: Uri? = null,
        locationSpeedManager: LocationSpeedManager? = null,
        recordingStartMs: Long? = null
    ): Pair<Boolean, Uri?> = withContext(Dispatchers.Main) {
        synchronized(this@ClippingManager) {
            if (isClippingInProgress) {
                Log.w("ClippingManager", "Clipping already in progress — ignoring new request")
                return@withContext false to null
            }
            isClippingInProgress = true
        }

        NotificationManager.showRecordingSaving(context)
        Log.d("ClippingManager", "Starting CLEAN MANUAL clip: $durationInMinutes from $videoUri")

        var effectiveUri = videoUri
        var tempFile: File? = null

        // URI access with retry + temp fallback
        var pfd: ParcelFileDescriptor? = null
        var attempts = 0
        val maxAttempts = 10
        var backoffMs = 200L

        while (pfd == null && attempts < maxAttempts) {
            try {
                pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
                if (pfd != null) Log.d("ClippingManager", "Source URI accessible after $attempts attempts")
            } catch (e: Exception) {
                Log.d("ClippingManager", "Access attempt ${attempts + 1} failed: ${e.message}")
                delay(backoffMs)
                backoffMs *= 2
                attempts++
            }
        }

        if (pfd == null) {
            Log.w("ClippingManager", "Direct URI access failed — copying to temp file")
            tempFile = File(context.cacheDir, "clip_source_${System.currentTimeMillis()}.mp4")
            var copySuccess = false
            attempts = 0
            backoffMs = 200L

            while (!copySuccess && attempts < maxAttempts) {
                try {
                    context.contentResolver.openInputStream(videoUri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    copySuccess = true
                    Log.d("ClippingManager", "Temp source created: ${tempFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w("ClippingManager", "Temp copy attempt ${attempts + 1} failed: ${e.message}")
                    delay(backoffMs)
                    backoffMs *= 2
                    attempts++
                }
            }

            if (!copySuccess) {
                Log.e("ClippingManager", "Failed to access source even after fallback")
                isClippingInProgress = false
                return@withContext false to null
            }
            effectiveUri = Uri.fromFile(tempFile)
        }

        val videoFormat = getVideoFormat(effectiveUri)
        if (videoFormat.mimeType.isNullOrEmpty() || !videoFormat.hasVideo) {
            Log.e("ClippingManager", "Invalid video format")
            isClippingInProgress = false
            tempFile?.delete()
            return@withContext false to null
        }

        val requestedDurationSeconds = when (durationInMinutes.lowercase()) {
            "30s" -> 30.0
            "1m" -> 60.0
            "2m" -> 120.0
            "3m" -> 180.0
            else -> 60.0
        }

        val videoDurationSeconds = getVideoDuration(effectiveUri)
        if (videoDurationSeconds <= 0) {
            Log.e("ClippingManager", "Invalid video duration")
            isClippingInProgress = false
            tempFile?.delete()
            return@withContext false to null
        }

        val actualStartSeconds = kotlin.math.max(0.0, videoDurationSeconds - requestedDurationSeconds)

        val mediaItem = MediaItem.Builder()
            .setUri(effectiveUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((actualStartSeconds * 1000).toLong())
                    .setEndPositionMs((videoDurationSeconds * 1000).toLong())
                    .build()
            )
            .build()

        val fps = withContext(Dispatchers.IO) {
            context.dataStore.data.map { it[stringPreferencesKey("frames_per_second")] ?: "30" }
                .firstOrNull()?.toIntOrNull() ?: 30
        }
        val quality = withContext(Dispatchers.IO) {
            context.dataStore.data.map { it[stringPreferencesKey("video_quality")] ?: "Medium" }
                .firstOrNull() ?: "Medium"
        }
        val bitrate = when (quality) {
            "Low" -> 2_000_000
            "Medium" -> 4_000_000
            "High" -> 6_000_000
            else -> 4_000_000
        }

        val outputFileUri = outputUri ?: createManualClipOutputUri()

        try {
            val effects = Effects(emptyList(), emptyList())

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setFrameRate(fps)
                .setEffects(effects)
                .build()

            val success = exportWithOverlay(
                context = context,
                editedMediaItem = editedMediaItem,
                outputUri = outputFileUri,
                bitrate = bitrate,
                videoMimeType = videoFormat.codec ?: "video/avc"
            )

            if (success) {
                context.contentResolver.update(outputFileUri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)

                NotificationManager.showRecordingSaved(context)
                Log.d("ClippingManager", "CLEAN Manual clip saved to Clips/: $outputFileUri")
                true to outputFileUri
            } else {
                context.contentResolver.delete(outputFileUri, null, null)
                Log.e("ClippingManager", "Export failed")
                false to null
            }

        } catch (e: Exception) {
            Log.e("ClippingManager", "Manual clip error", e)
            false to null
        } finally {
            isClippingInProgress = false
            tempFile?.delete()
        }
    }

    // Crash clip
    suspend fun clipCrashVideo(
        videoUri: Uri,
        outputUri: Uri? = null,
        locationSpeedManager: LocationSpeedManager? = null,
        recordingStartMs: Long? = null
    ): Pair<Boolean, Uri?> = withContext(Dispatchers.Main) {
        synchronized(this@ClippingManager) {
            if (isClippingInProgress) return@withContext false to null
            isClippingInProgress = true
        }

        NotificationManager.showRecordingSaving(context)
        Log.d("ClippingManager", "Starting CLEAN CRASH clip from $videoUri")

        val fps = withContext(Dispatchers.IO) {
            context.dataStore.data.map { it[stringPreferencesKey("frames_per_second")] ?: "30" }
                .firstOrNull()?.toIntOrNull() ?: 30
        }
        val quality = withContext(Dispatchers.IO) {
            context.dataStore.data.map { it[stringPreferencesKey("video_quality")] ?: "Medium" }
                .firstOrNull() ?: "Medium"
        }
        val bitrate = when (quality) {
            "Low" -> 4_000_000
            "Medium" -> 6_000_000
            "High" -> 8_000_000
            else -> 6_000_000
        }

        val durationSeconds = 180.0
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs((durationSeconds * 1000).toLong())
                    .build()
            )
            .build()

        try {
            val effects = Effects(emptyList(), emptyList())

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setFrameRate(fps)
                .setEffects(effects)
                .build()

            val finalOutputUri = outputUri ?: createCrashOutputUri()

            val success = exportWithOverlay(
                context = context,
                editedMediaItem = editedMediaItem,
                outputUri = finalOutputUri,
                bitrate = bitrate,
                videoMimeType = "video/avc"
            )

            if (success) {
                context.contentResolver.update(finalOutputUri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
                NotificationManager.showCrashClipSaved(context)
                Log.d("ClippingManager", "Clean crash clip saved to Crashes/: $finalOutputUri")
                true to finalOutputUri
            } else {
                context.contentResolver.delete(finalOutputUri, null, null)
                false to null
            }

        } catch (e: Exception) {
            Log.e("ClippingManager", "Crash clip error", e)
            false to null
        } finally {
            isClippingInProgress = false
        }
    }

    private suspend fun exportWithOverlay(
        context: Context,
        editedMediaItem: EditedMediaItem,
        outputUri: Uri,
        bitrate: Int,
        videoMimeType: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val tempFile = File(context.cacheDir, "clipped_temp_${System.currentTimeMillis()}.mp4")

        try {
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                .setEnableFallback(true)
                .build()

            val transformer = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(videoMimeType)
                .build()

            val handler = Handler(Looper.getMainLooper())
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, result: ExportResult) {
                    handler.post {
                        val success = try {
                            context.contentResolver.openOutputStream(outputUri, "w")?.use { out ->
                                FileInputStream(tempFile).use { input ->
                                    input.copyTo(out)
                                }
                            }
                            true
                        } catch (e: Exception) {
                            Log.e("ExportDebug", "Copy failed", e)
                            false
                        }
                        tempFile.delete()
                        continuation.resume(success)
                    }
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    handler.post {
                        Log.e("ExportDebug", "Export error", exception)
                        tempFile.delete()
                        continuation.resume(false)
                    }
                }
            })

            transformer.start(editedMediaItem, tempFile.absolutePath)

        } catch (e: Exception) {
            Log.e("ExportDebug", "Exception in exportWithOverlay", e)
            tempFile.delete()
            continuation.resume(false)
        }
    }

    private fun createManualClipOutputUri(): Uri {
        val name = "clipped_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RoadsEye/Clips")
        }
        return context.contentResolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: throw IllegalStateException("Failed to create manual clip output URI")
    }

    private fun createCrashOutputUri(): Uri {
        val name = "crash_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RoadsEye/Crashes")
        }
        return context.contentResolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: throw IllegalStateException("Failed to create crash output URI")
    }

    suspend fun getVideoDuration(uri: Uri): Double = withContext(Dispatchers.IO) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            durationMs / 1000.0
        } catch (e: Exception) {
            Log.e("ClippingManager", "Error getting duration: ${e.message}", e)
            0.0
        }
    }

    private data class VideoFormat(val mimeType: String?, val codec: String?, val hasVideo: Boolean)

    private fun getVideoFormat(uri: Uri): VideoFormat {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val hasVideo = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                ?.equals("yes", ignoreCase = true) ?: false
            val codec = when (mimeType) {
                "video/mp4", "video/3gpp" -> "video/avc"
                "video/hevc" -> "video/hevc"
                else -> "video/avc"
            }
            retriever.release()
            VideoFormat(mimeType, codec, hasVideo)
        } catch (e: Exception) {
            Log.e("ClippingManager", "Error getting video format: ${e.message}", e)
            VideoFormat(null, null, false)
        }
    }
}
