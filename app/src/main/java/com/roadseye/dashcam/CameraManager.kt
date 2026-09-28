package com.roadseye.dashcam

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as SystemCameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.FrameLayout
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.ScaleType
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.util.concurrent.ListenableFuture
import com.roadseye.dashcam.settingssection.UI_SHOW_DATE_KEY
import com.roadseye.dashcam.settingssection.UI_SHOW_LOCATION_KEY
import com.roadseye.dashcam.settingssection.UI_SHOW_SPEED_KEY
import com.roadseye.dashcam.settingssection.UI_SHOW_TIME_KEY
import com.roadseye.dashcam.settingssection.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@ExperimentalCamera2Interop
class CameraManager private constructor(
    private val appContext: Context
) {

    companion object {
        @Volatile
        private var instance: CameraManager? = null

        fun getInstance(context: Context): CameraManager {
            return instance ?: synchronized(this) {
                instance ?: CameraManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    internal var overlayView: OverlayView? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val _videoCaptureFlow = MutableStateFlow<VideoCapture<Recorder>?>(null)
    val videoCaptureFlow: StateFlow<VideoCapture<Recorder>?> = _videoCaptureFlow.asStateFlow()

    // Shared background executor for camera and recorder callbacks.
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Draws the speed and time overlay into every recorded frame through CameraX's effects pipeline.
    private var overlayEffect: OverlayEffect? = null

    // Tracks device orientation so saved videos match how the phone is held.
    @Volatile
    private var deviceRotation: Int = Surface.ROTATION_0
    private var orientationListener: OrientationEventListener? = null

    private fun startOrientationTracking() {
        if (orientationListener != null) return
        orientationListener = object : OrientationEventListener(appContext) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (rotation != deviceRotation) {
                    deviceRotation = rotation
                    // Applies to the next recorded segment.
                    videoCapture?.targetRotation = rotation
                    Log.d("CameraManager", "Device rotation changed → targetRotation=$rotation")
                }
            }
        }.also { it.enable() }
    }

    private data class OverlayToggles(
        val showLocation: Boolean = true,
        val showDate: Boolean = true,
        val showTime: Boolean = true,
        val showSpeed: Boolean = true
    )

    @Volatile
    private var overlayToggles = OverlayToggles()

    private val overlayTextPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    private val overlayBgPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        alpha = 140
    }
    private val overlayBgRect = RectF()
    private val overlayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val overlayTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Draws the watermark and Location/Date/Time/Speed block into the top-left corner of the saved video, counter-rotating the canvas to match the final frame. */
    private fun drawDashcamOverlay(frame: Frame) {
        val canvas = frame.overlayCanvas
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val bufferWidth = frame.size.width.toFloat()
        val bufferHeight = frame.size.height.toFloat()
        val rotation = frame.rotationDegrees
        val swapped = rotation == 90 || rotation == 270
        val displayedWidth = if (swapped) bufferHeight else bufferWidth
        val displayedHeight = if (swapped) bufferWidth else bufferHeight

        val forward = Matrix()
        forward.postRotate(rotation.toFloat())
        when (rotation) {
            90 -> forward.postTranslate(bufferHeight, 0f)
            180 -> forward.postTranslate(bufferWidth, bufferHeight)
            270 -> forward.postTranslate(0f, bufferWidth)
        }
        if (frame.isMirroring) {
            forward.postScale(-1f, 1f)
            forward.postTranslate(displayedWidth, 0f)
        }
        val inverse = Matrix()
        forward.invert(inverse)
        canvas.concat(inverse)

        // From here on, coordinates match the final saved video.
        val toggles = overlayToggles
        val locationSpeedManager = LocationSpeedManager.getInstance(appContext)
        val latLon = locationSpeedManager.getCurrentLatLon()
        val speedMs = locationSpeedManager.speed.value
        val speedUnit = locationSpeedManager.speedUnit.value

        val lines = buildList {
            add("Clipped By: Road's Eye")
            if (toggles.showLocation && latLon != null) {
                add("Location: %.5f, %.5f".format(latLon.first, latLon.second))
            }
            if (toggles.showDate) add("Date: ${overlayDateFormat.format(Date())}")
            if (toggles.showTime) add("Time: ${overlayTimeFormat.format(Date())}")
            if (toggles.showSpeed) {
                val displaySpeed = if (speedUnit == "MPH") speedMs * 2.23694 else speedMs * 3.6
                add("Speed: %.0f %s".format(displaySpeed, speedUnit))
            }
        }

        val textSize = minOf(displayedWidth, displayedHeight) * 0.035f
        overlayTextPaint.textSize = textSize
        val padding = textSize * 0.5f
        val lineHeight = textSize * 1.3f
        val maxTextWidth = lines.maxOf { overlayTextPaint.measureText(it) }

        val boxLeft = padding
        val boxRight = boxLeft + maxTextWidth + padding * 2
        val boxTop = padding
        val boxBottom = boxTop + lines.size * lineHeight + padding

        overlayBgRect.set(boxLeft, boxTop, boxRight, boxBottom)
        canvas.drawRoundRect(overlayBgRect, 12f, 12f, overlayBgPaint)

        var y = boxTop + lineHeight
        lines.forEach { line ->
            canvas.drawText(line, boxLeft + padding, y - (lineHeight - textSize) / 2, overlayTextPaint)
            y += lineHeight
        }
    }

    fun getVideoCapture(): VideoCapture<Recorder>? = videoCapture

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCoroutine { cont ->
        addListener({
            try { cont.resume(get()) }
            catch (e: Exception) { cont.resumeWithException(e) }
        }, cameraExecutor)
    }

    private fun buildCameraSelector(zoom: String): CameraSelector {
        if (zoom == "1x") return CameraSelector.DEFAULT_BACK_CAMERA

        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as SystemCameraManager
        val backCameraIds = cameraManager.cameraIdList.filter { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }

        val physicalChars = backCameraIds.map { id ->
            id to cameraManager.getCameraCharacteristics(id)
        }.filter { (_, chars) ->
            chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) != true
        }

        val focalPairs = physicalChars.mapNotNull { (id, chars) ->
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()
                ?.let { it to id }
        }

        if (focalPairs.isEmpty()) return CameraSelector.DEFAULT_BACK_CAMERA

        val sorted = focalPairs.sortedBy { it.first }
        val targetId = when (zoom) {
            "0.5x" -> sorted.first().second
            "2x" -> sorted.last().second
            else -> return CameraSelector.DEFAULT_BACK_CAMERA
        }

        return CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == targetId }
            }
            .build()
    }

    /** Camera preview overlay; text is only drawn into saved videos. */
    class OverlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

        val previewView: PreviewView = PreviewView(context).apply {
            scaleType = ScaleType.FILL_CENTER
        }

        init {
            addView(previewView)
            setWillNotDraw(false)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
        }
    }

    @Composable
    fun CameraPreview(
        modifier: Modifier = Modifier,
        isRecording: Boolean = false
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val localContext = LocalContext.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(localContext) }

        val fov by localContext.dataStore.data
            .map { it[stringPreferencesKey("camera_zoom")] ?: "0.5x" }
            .collectAsStateWithLifecycle("0.5x")

        val fps by localContext.dataStore.data
            .map { it[stringPreferencesKey("frames_per_second")] ?: "30" }
            .collectAsStateWithLifecycle("30")

        val quality by localContext.dataStore.data
            .map { it[stringPreferencesKey("video_quality")] ?: "Medium" }
            .collectAsStateWithLifecycle("Medium")

        val overlayEnabled by localContext.dataStore.data
            .map { it[booleanPreferencesKey("driving_overlay_mode")] ?: true }
            .collectAsStateWithLifecycle(true)

        val overlayAlways by localContext.dataStore.data
            .map { it[booleanPreferencesKey("overlay_always_active")] ?: true }
            .collectAsStateWithLifecycle(false)

        val locationSpeedManager = remember { LocationSpeedManager.getInstance(localContext) }

        val speedMs by locationSpeedManager.speed.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            localContext.dataStore.data.collect { prefs ->
                overlayToggles = OverlayToggles(
                    showLocation = prefs[UI_SHOW_LOCATION_KEY] ?: true,
                    showDate = prefs[UI_SHOW_DATE_KEY] ?: true,
                    showTime = prefs[UI_SHOW_TIME_KEY] ?: true,
                    showSpeed = prefs[UI_SHOW_SPEED_KEY] ?: true
                )
            }
        }

        var isOverlayVisible by rememberSaveable { mutableStateOf(true) }
        var lastTouchTime by rememberSaveable { mutableLongStateOf(0L) }

        // Never cover the PiP window with the driving overlay.
        val isInPip = (localContext as? MainActivity)?.isInPipMode == true

        val shouldShowOverlay by remember(isRecording, speedMs, overlayEnabled, overlayAlways, isInPip) {
            derivedStateOf {
                if (isInPip) false
                else if (!isRecording) false
                else if (overlayAlways) true
                else if (!overlayEnabled) false
                else speedMs >= 0.44704 // ~1 mph
            }
        }

        val overlayAlpha by animateFloatAsState(
            targetValue = if (shouldShowOverlay && isOverlayVisible) 1f else 0f,
            animationSpec = tween(300)
        )

        LaunchedEffect(shouldShowOverlay, isOverlayVisible, lastTouchTime) {
            if (shouldShowOverlay && !isOverlayVisible) {
                val elapsed = System.currentTimeMillis() - lastTouchTime
                if (elapsed < 5000) delay(5000 - elapsed)
                if (System.currentTimeMillis() - lastTouchTime >= 5000) {
                    isOverlayVisible = true
                }
            }
        }

        LaunchedEffect(shouldShowOverlay) {
            if (shouldShowOverlay && lastTouchTime == 0L) isOverlayVisible = true
        }

        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    OverlayView(ctx).also { view ->
                        overlayView = view
                    }
                },
                modifier = Modifier.fillMaxSize().zIndex(-1f)
            )

            // Semi-transparent overlay message when "Driving Overlay Mode" is active
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .background(ComposeColor.Black.copy(alpha = overlayAlpha))
                    .pointerInput(shouldShowOverlay) {
                        if (shouldShowOverlay) {
                            detectTapGestures {
                                isOverlayVisible = false
                                lastTouchTime = System.currentTimeMillis()
                            }
                        }
                    }
            ) {
                if (shouldShowOverlay && isOverlayVisible) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Column(
                            modifier = Modifier
                                .background(ComposeColor.Black.copy(0.7f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Driving Overlay Mode Active",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor.White.copy(0.4f)
                            )
                            Text(
                                "Camera is operational - Keep app in foreground at all times during recording!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = ComposeColor.White.copy(0.4f)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        LaunchedEffect(fov, fps, quality) {
            if (!hasCameraPermission()) return@LaunchedEffect

            startOrientationTracking()

            val provider = cameraProviderFuture.await()
            cameraProvider = provider

            val selector = buildCameraSelector(fov)

            val targetFps = fps.toIntOrNull() ?: 30
            val fpsRange = android.util.Range(targetFps, targetFps)

            val videoQuality = when (quality) {
                "Low" -> Quality.SD
                "Medium" -> Quality.HD
                "High" -> Quality.FHD
                else -> Quality.HD
            }

            val qualitySelector = QualitySelector.from(videoQuality, FallbackStrategy.higherQualityOrLowerThan(videoQuality))
            val bitrate = when (quality) {
                "Low" -> 4_000_000
                "Medium" -> 6_000_000
                "High" -> 8_000_000
                else -> 6_000_000
            }

            val recorder = Recorder.Builder()
                .setExecutor(cameraExecutor)
                .setQualitySelector(qualitySelector)
                .setTargetVideoEncodingBitRate(bitrate)
                .build()

            val videoCaptureBuilder = VideoCapture.Builder(recorder)
            Camera2Interop.Extender(videoCaptureBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)

            val previewBuilder = Preview.Builder()
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)

            val preview = previewBuilder.build().apply {
                setSurfaceProvider(overlayView?.previewView?.surfaceProvider)
            }

            videoCapture = videoCaptureBuilder.build().apply {
                targetRotation = deviceRotation
            }

            _videoCaptureFlow.value = videoCapture
            val useCases = mutableListOf<UseCase>(preview)
            videoCapture?.let { useCases.add(it) }

            overlayEffect?.close()
            val newOverlayEffect = OverlayEffect(
                CameraEffect.VIDEO_CAPTURE,
                /* queueDepth= */ 0,
                Handler(Looper.getMainLooper())
            ) { throwable -> Log.e("CameraManager", "Overlay effect error", throwable) }
            newOverlayEffect.setOnDrawListener { frame ->
                drawDashcamOverlay(frame)
                true
            }
            overlayEffect = newOverlayEffect

            val useCaseGroupBuilder = UseCaseGroup.Builder().addEffect(newOverlayEffect)
            useCases.forEach { useCaseGroupBuilder.addUseCase(it) }

            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroupBuilder.build())
        }

        DisposableEffect(Unit) {
            onDispose {
                overlayEffect?.close()
                overlayEffect = null
            }
        }
    }
}
