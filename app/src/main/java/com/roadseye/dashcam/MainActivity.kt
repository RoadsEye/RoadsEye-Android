package com.roadseye.dashcam

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.util.UnstableApi
import com.roadseye.dashcam.settingssection.CLIP_DURATION_KEY
import com.roadseye.dashcam.settingssection.IS_VOICE_RECORDING_KEY
import com.roadseye.dashcam.settingssection.OVERLAY_ALWAYS_ACTIVE_KEY
import com.roadseye.dashcam.settingssection.PIP_ENABLED_KEY
import com.roadseye.dashcam.settingssection.SettingsMenu
import com.roadseye.dashcam.settingssection.dataStore
import com.roadseye.dashcam.ui.theme.LocalAppColors
import com.roadseye.dashcam.ui.theme.RoadsEyeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Bump when the Terms or Privacy Policy change so every user must accept them again.
private const val CURRENT_TOS_VERSION = 4

// Preference keys
private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
private val TOS_ACCEPTED_KEY = booleanPreferencesKey("tos_and_privacy_accepted") // legacy pre-versioning flag
private val TOS_ACCEPTED_VERSION_KEY = intPreferencesKey("tos_accepted_version")
private val INITIAL_PERMISSIONS_REQUESTED_KEY = booleanPreferencesKey("initial_permissions_requested")
private val NOTIFICATION_PERMISSION_ASKED_KEY = booleanPreferencesKey("notification_permission_asked")
private val NOTIFICATION_PERMANENTLY_DENIED_KEY = booleanPreferencesKey("notification_permanently_denied")
private val WIDGET_PROMO_DISMISSED_KEY = booleanPreferencesKey("widget_promo_dismissed")

@OptIn(UnstableApi::class, ExperimentalCamera2Interop::class)
class MainActivity : ComponentActivity() {

    private val cameraManager by lazy { CameraManager.getInstance(applicationContext) }
    private val recordingManager by lazy { RecordingManager.getInstance(applicationContext) }
    private val crashDetectionManager by lazy { CrashDetectionManager.getInstance(applicationContext) }

    private val voiceCommandsManager by lazy {
        VoiceCommandsManager.getInstance(applicationContext)
    }

    private val supportsPiP: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private val supportsAutoEnterPiP: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private var pipEnabled by mutableStateOf(true)

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.containsKey(Manifest.permission.POST_NOTIFICATIONS)) {
            handleNotificationPermissionResult(permissions[Manifest.permission.POST_NOTIFICATIONS] == true)
        }

        permissionRefreshKey = System.currentTimeMillis()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handleNotificationPermissionResult(isGranted)

        if (isGranted) {
            Toast.makeText(this, "Notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notifications are disabled. You can enable them in Settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleNotificationPermissionResult(isGranted: Boolean) {
        lifecycleScope.launch {
            dataStore.edit { prefs ->
                prefs[NOTIFICATION_PERMISSION_ASKED_KEY] = true
                if (!isGranted) {
                    val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    if (permanentlyDenied) prefs[NOTIFICATION_PERMANENTLY_DENIED_KEY] = true
                }
            }
        }

        if (isGranted) {
            NotificationManager.setNotificationsEnabled(true)
        }
    }

    private var permissionRefreshKey by mutableStateOf(0L)

    internal fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    internal fun showSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun updatePipParams() {
        if (!supportsPiP) return

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
            // Only auto-enter PiP while a recording is active
            builder.setAutoEnterEnabled(pipEnabled && recordingManager.isRecording.value)
        }

        cameraManager.overlayView?.previewView?.let { previewView ->
            val rect = android.graphics.Rect()
            previewView.getGlobalVisibleRect(rect)
            builder.setSourceRectHint(rect)
        }

        try {
            setPictureInPictureParams(builder.build())
        } catch (e: Exception) {
            Log.w("PiP", "Failed to set PictureInPictureParams", e)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (supportsPiP && recordingManager.isRecording.value && pipEnabled && !isInPictureInPictureMode) {
            try {
                updatePipParams()
                enterPictureInPictureMode()
            } catch (e: Exception) {
                Log.w("PiP", "Failed to enter PiP", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (recordingManager.isRecording.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        voiceCommandsManager.stopListening()

        if (supportsPiP && recordingManager.isRecording.value && pipEnabled && !isInPictureInPictureMode) {
            try {
                updatePipParams()
                enterPictureInPictureMode()
            } catch (e: Exception) {
                Log.w("PiP", "Failed to enter PiP from onPause", e)
            }
        }

    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)

        isInPipMode = isInPictureInPictureMode

        if (!isInPictureInPictureMode && recordingManager.isRecording.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Only relaunch full screen if the PiP window was dismissed while recording.
            window.decorView.postDelayed({
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    recordingManager.isRecording.value
                ) {
                    try {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.w("PiP", "Failed to return to full screen", e)
                    }
                }
            }, 500)
        }
    }

    // Lets the UI show only the camera preview while in PiP.
    var isInPipMode by mutableStateOf(false)
        private set

    // Set when launched from the Quick Record widget.
    private var pendingWidgetStartRecording by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == com.roadseye.dashcam.widget.QuickRecordWidgetProvider.ACTION_START_RECORDING) {
            pendingWidgetStartRecording = true
        }
    }

    private var computedStartDestination by mutableStateOf("onboarding")
    private var isSplashReady by mutableStateOf(false)
    private var showNavHost by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        if (supportsPiP) {
            isInPipMode = isInPictureInPictureMode
        }

        if (intent?.action == com.roadseye.dashcam.widget.QuickRecordWidgetProvider.ACTION_START_RECORDING) {
            pendingWidgetStartRecording = true
        }

        enableEdgeToEdge()
        lifecycle.addObserver(crashDetectionManager)

        lifecycleScope.launch {
            dataStore.data.collect { prefs ->
                val userPref = prefs[PIP_ENABLED_KEY] ?: true
                pipEnabled = if (supportsAutoEnterPiP) userPref else false
                if (supportsPiP) updatePipParams()
            }
        }

        // Only auto-enter PiP while a recording is active.
        lifecycleScope.launch {
            recordingManager.isRecording.collect {
                if (supportsPiP) updatePipParams()
            }
        }

        if (supportsPiP) {
            updatePipParams()
        }

        splashScreen.setKeepOnScreenCondition { !isSplashReady }

        lifecycleScope.launch {
            try {
                val prefs = dataStore.data.first()
                val onboardingDone = prefs[ONBOARDING_COMPLETED_KEY] ?: false
                // Users who accepted an older Terms version must accept again.
                val tosDone = (prefs[TOS_ACCEPTED_VERSION_KEY] ?: 0) >= CURRENT_TOS_VERSION

                computedStartDestination = when {
                    !onboardingDone -> "onboarding"
                    !tosDone -> "terms_and_privacy"
                    else -> "main"
                }
            } catch (e: Exception) {
                Log.w("Startup", "Failed to read initial navigation state", e)
                computedStartDestination = "onboarding"
            } finally {
                isSplashReady = true
                showNavHost = true
            }
        }

        setContent {
            RoadsEyeAppTheme {
                if (!showNavHost) {
                    Box(modifier = Modifier.fillMaxSize())
                    return@RoadsEyeAppTheme
                }

                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()

                NavHost(
                    navController = navController,
                    startDestination = computedStartDestination
                ) {
                    composable("onboarding") {
                        LaunchedEffect(Unit) {
                        }

                        OnboardingScreen(
                            onComplete = {
                                coroutineScope.launch {
                                    dataStore.edit { preferences ->
                                        preferences[ONBOARDING_COMPLETED_KEY] = true
                                    }
                                }
                                navController.navigate("terms_and_privacy") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("terms_and_privacy") {
                        LaunchedEffect(Unit) {
                        }

                        TermsAndPrivacyScreen(
                            onAccept = {
                                coroutineScope.launch {
                                    dataStore.edit { preferences ->
                                        preferences[TOS_ACCEPTED_KEY] = true
                                        preferences[TOS_ACCEPTED_VERSION_KEY] = CURRENT_TOS_VERSION
                                    }
                                }
                                navController.navigate("main") {
                                    popUpTo("terms_and_privacy") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("main") {
                        LaunchedEffect(Unit) {

                            NotificationManager.initialize(this@MainActivity)

                            val prefs = dataStore.data.first()
                            val permissionsAlreadyRequested = prefs[INITIAL_PERMISSIONS_REQUESTED_KEY] ?: false

                            // Ensure Overlay Always Active defaults to true on first launch
                            if (prefs[OVERLAY_ALWAYS_ACTIVE_KEY] == null) {
                                dataStore.edit { preferences ->
                                    preferences[OVERLAY_ALWAYS_ACTIVE_KEY] = true
                                }
                            }

                            if (!permissionsAlreadyRequested) {
                                requestPermissions()
                                dataStore.edit { preferences ->
                                    preferences[INITIAL_PERMISSIONS_REQUESTED_KEY] = true
                                }
                            }
                        }

                        // Open source announcement, shown until the user picks "Don't show again".
                        var showOpenSourceNotice by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            val prefs = dataStore.data.first()
                            if (prefs[HIDE_OPEN_SOURCE_NOTICE_KEY] != true &&
                                !OpenSourceInfo.dismissedThisSession
                            ) {
                                showOpenSourceNotice = true
                            }
                        }

                        if (showOpenSourceNotice) {
                            OpenSourceNoticeDialog(
                                onDismiss = {
                                    showOpenSourceNotice = false
                                    OpenSourceInfo.dismissedThisSession = true
                                },
                                onDontShowAgain = {
                                    showOpenSourceNotice = false
                                    OpenSourceInfo.dismissedThisSession = true
                                    lifecycleScope.launch {
                                        dataStore.edit { it[HIDE_OPEN_SOURCE_NOTICE_KEY] = true }
                                    }
                                }
                            )
                        }

                        // Widget promo, shown until the user picks "Don't Show Again".
                        var showWidgetPromo by remember { mutableStateOf(false) }

                        LaunchedEffect(showOpenSourceNotice) {
                            if (showOpenSourceNotice) return@LaunchedEffect
                            delay(1200)
                            val prefs = dataStore.data.first()
                            val dismissedForever = prefs[WIDGET_PROMO_DISMISSED_KEY] ?: false
                            if (!dismissedForever &&
                                !recordingManager.isRecording.value && !pendingWidgetStartRecording
                            ) {
                                showWidgetPromo = true
                            }
                        }

                        if (showWidgetPromo) {
                            WidgetPromoDialog(
                                onClose = {
                                    showWidgetPromo = false
                                },
                                onDontShowAgain = {
                                    showWidgetPromo = false
                                    lifecycleScope.launch {
                                        dataStore.edit { it[WIDGET_PROMO_DISMISSED_KEY] = true }
                                    }
                                }
                            )
                        }

                        MainScreen(
                            key = permissionRefreshKey,
                            cameraManager = cameraManager,
                            recordingManager = recordingManager,
                            crashDetectionManager = crashDetectionManager,
                            voiceCommandsManager = voiceCommandsManager,
                            onSettingsClick = { navController.navigate("settings") },
                            onRequestPermission = { requestPermissions() },
                            autoStartRecording = pendingWidgetStartRecording,
                            onAutoStartConsumed = { pendingWidgetStartRecording = false },
                            onStartRecording = {
                                lifecycleScope.launch {
                                    val prefs = dataStore.data.first()
                                    val alreadyAsked = prefs[NOTIFICATION_PERMISSION_ASKED_KEY] ?: false
                                    val permanentlyDenied = prefs[NOTIFICATION_PERMANENTLY_DENIED_KEY] ?: false

                                    if (!alreadyAsked && !permanentlyDenied) {
                                        requestNotificationPermissionIfNeeded()
                                    }
                                }
                            }
                        )
                    }

                    composable("settings") {
                        LaunchedEffect(Unit) {
                        }
                        SettingsMenu(onDismiss = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    // Permissions

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Notification permission is requested together with the other permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionRequest.launch(permissionsToRequest.toTypedArray())
        } else {
            permissionRefreshKey = System.currentTimeMillis()
            Toast.makeText(this, "All required permissions already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun updateRecordingState(isRecording: Boolean) {
        crashDetectionManager.updateRecordingState(isRecording)
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()

        // Resync PiP state in case the PiP exit callback was missed.
        if (supportsPiP) {
            isInPipMode = isInPictureInPictureMode
        }

        if (recordingManager.isRecording.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (supportsPiP && recordingManager.isRecording.value) {
            updatePipParams()
        }
    }

    override fun onDestroy() {
        voiceCommandsManager.stopListening()

        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP_RECORDING
        }
        stopService(stopIntent)

        super.onDestroy()
    }
}

// Compose UI

@Composable
private fun WidgetPromoDialog(
    onClose: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    val colors = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Record Faster with Widgets",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Add Road's Eye widgets to your home screen:",
                    color = colors.textPrimary,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "🔴 Quick Record — start a dashcam recording with a single tap.",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "📊 Driving Stats — distance, time recorded, top speed, and drives at a glance.",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Long-press your home screen → Widgets → RoadsEye.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDontShowAgain) {
                Text("Don't Show Again", color = colors.textSecondary)
            }
        }
    )
}

@OptIn(UnstableApi::class, ExperimentalCamera2Interop::class)
@Composable
fun MainScreen(
    key: Long = 0L,
    cameraManager: CameraManager,
    recordingManager: RecordingManager,
    crashDetectionManager: CrashDetectionManager,
    voiceCommandsManager: VoiceCommandsManager,
    onSettingsClick: () -> Unit,
    onRequestPermission: () -> Unit,
    autoStartRecording: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onStartRecording: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? MainActivity

    val isRecording by recordingManager.isRecording.collectAsState()

    var showStopConfirmation by rememberSaveable { mutableStateOf(false) }
    var showWakePhraseAlert by remember { mutableStateOf(false) }

    val hasCameraPermission = remember(key) { mutableStateOf(cameraManager.hasCameraPermission()) }

    var isCameraReady by remember { mutableStateOf(false) }
    val videoCaptureState by cameraManager.videoCaptureFlow.collectAsStateWithLifecycle()

    val clipDuration by context.dataStore.data
        .map { preferences -> preferences[CLIP_DURATION_KEY] ?: "1m" }
        .collectAsStateWithLifecycle(initialValue = "1m")

    val isVoiceRecordingEnabled by context.dataStore.data
        .map { preferences -> preferences[IS_VOICE_RECORDING_KEY] ?: false }
        .collectAsStateWithLifecycle(initialValue = false)

    val isVoiceActive by voiceCommandsManager.isActive.collectAsStateWithLifecycle()

    val autoRecordManager = remember { AutoRecordManager.getInstance(context) }

    LaunchedEffect(videoCaptureState) {
        isCameraReady = videoCaptureState != null
    }

    // Quick Record widget: start recording as soon as the camera is ready
    LaunchedEffect(autoStartRecording, isCameraReady) {
        if (autoStartRecording && isCameraReady && !isRecording && hasCameraPermission.value) {
            onAutoStartConsumed()
            onStartRecording()
            cameraManager.getVideoCapture()?.let { recordingManager.startRecording(it) }
        } else if (autoStartRecording && isRecording) {
            // Already recording — nothing to start
            onAutoStartConsumed()
        }
    }

    LaunchedEffect(isCameraReady, hasCameraPermission.value) {
        if (isCameraReady && hasCameraPermission.value) {
            autoRecordManager.startMonitoring()
        }
    }

    DisposableEffect(Unit) {
        onDispose { autoRecordManager.stopMonitoring() }
    }

    LaunchedEffect(isRecording) {
        crashDetectionManager.updateRecordingState(isRecording)
        if (isRecording) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(isVoiceRecordingEnabled) {
        if (isVoiceRecordingEnabled) {
            voiceCommandsManager.startListening()
        } else {
            voiceCommandsManager.stopListening()
        }
    }

    DisposableEffect(Unit) {
        voiceCommandsManager.onWakePhraseDetected = {
            showWakePhraseAlert = true
            scope.launch { delay(3000); showWakePhraseAlert = false }
        }

        voiceCommandsManager.onCommandDetected = { command ->
            when (command.lowercase()) {
                "clip it", "clip recording", "clip" -> {
                    scope.launch {
                        Toast.makeText(context, "Creating $clipDuration clip...", Toast.LENGTH_SHORT).show()
                        val (success, _) = recordingManager.performManualClip(clipDuration)
                        if (success) {
                            Toast.makeText(context, "Clip saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clip failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "start recording" -> {
                    onStartRecording()
                    if (!isRecording && isCameraReady) {
                        scope.launch {
                            cameraManager.getVideoCapture()?.let { recordingManager.startRecording(it) }
                        }
                    }
                }
                "stop recording" -> if (isRecording) showStopConfirmation = true
                "yes" -> if (showStopConfirmation) {
                    scope.launch {
                        recordingManager.stopAndAlwaysDiscard()
                        showStopConfirmation = false
                        Toast.makeText(context, "Recording stopped", Toast.LENGTH_SHORT).show()
                    }
                }
                "no", "cancel" -> showStopConfirmation = false
                else -> Toast.makeText(context, "Voice command: $command", Toast.LENGTH_SHORT).show()
            }
        }

        onDispose {
            voiceCommandsManager.onWakePhraseDetected = null
            voiceCommandsManager.onCommandDetected = null
            voiceCommandsManager.stopListening()
        }
    }

    SideEffect {
        if (isRecording) activity?.hideSystemBars() else activity?.showSystemBars()
    }

    DisposableEffect(Unit) {
        onDispose { activity?.showSystemBars() }
    }

    // While in PiP only the camera preview is shown.
    val isInPip = activity?.isInPipMode == true

    Box(modifier = Modifier.fillMaxSize()) {

        // Camera Preview
        Box(modifier = Modifier.fillMaxSize().zIndex(-1f)) {
            if (hasCameraPermission.value) {
                cameraManager.CameraPreview(modifier = Modifier.fillMaxSize(), isRecording = isRecording)
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF6200EE).copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("Permissions Required", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Camera, microphone and location access are needed.", color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6200EE)), modifier = Modifier.height(56.dp)) {
                            Text("Grant Permissions", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // UI Overlays (hidden entirely while in PiP mode)
        if (!isInPip) Box(modifier = Modifier.fillMaxSize().zIndex(0f)) {

            crashDetectionManager.CrashAlertDialog()

            if (showStopConfirmation) {
                AlertDialog(
                    onDismissRequest = { showStopConfirmation = false },
                    title = { Text("Stop Recording?") },
                    text = { Text("Do you want to stop recording? Current segment will be saved.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                recordingManager.stopAndAlwaysDiscard()
                                showStopConfirmation = false
                                Toast.makeText(context, "Recording stopped", Toast.LENGTH_SHORT).show()
                            }
                        }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                            Text("Stop")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStopConfirmation = false }) { Text("Cancel") }
                    }
                )
            }

            AnimatedVisibility(
                visible = showWakePhraseAlert,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).zIndex(5f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Green.copy(0.9f), RoundedCornerShape(12.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        .padding(24.dp, 12.dp)
                ) {
                    Text("Listening...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(
                visible = isVoiceRecordingEnabled && isVoiceActive && !showWakePhraseAlert,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).zIndex(4f)
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(0.6f), RoundedCornerShape(12.dp))
                        .padding(16.dp, 8.dp)
                ) {
                    Text("Voice commands active", color = Color.White.copy(0.8f), fontSize = 14.sp)
                }
            }

            if (hasCameraPermission.value) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 55.dp)
                        .zIndex(7f)
                ) {
                    val (greyBox, mainButton, settingsButton, clipButton) = createRefs()

                    if (isRecording) {
                        Image(
                            painter = painterResource(id = R.drawable.capture_icon),
                            contentDescription = "Clip Recording",
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF007AFF))
                                .border(2.dp, Color.White, RoundedCornerShape(20.dp))
                                .clickable {
                                    scope.launch {
                                        Toast.makeText(context, "Creating clip...", Toast.LENGTH_SHORT).show()
                                        val (success, _) = recordingManager.performManualClip(clipDuration)
                                        if (success) {
                                            Toast.makeText(context, "Clip saved!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Clip failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .zIndex(3f)
                                .constrainAs(clipButton) {
                                    bottom.linkTo(mainButton.top, margin = 12.dp)
                                    start.linkTo(mainButton.start)
                                    end.linkTo(mainButton.end)
                                }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFCC0000))
                            .border(1.dp, Color.Black, CircleShape)
                            .clickable(enabled = isCameraReady) {
                                if (!isRecording) {
                                    onStartRecording()
                                    scope.launch {
                                        cameraManager.getVideoCapture()?.let { recordingManager.startRecording(it) }
                                    }
                                } else {
                                    showStopConfirmation = true
                                }
                            }
                            .zIndex(2f)
                            .constrainAs(mainButton) {
                                start.linkTo(parent.start)
                                bottom.linkTo(parent.bottom)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = if (isRecording) R.drawable.stop_icon else R.drawable.play_icon),
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                            modifier = Modifier.size(90.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 73.dp)
                            .background(Color.Gray.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                            .zIndex(1f)
                            .constrainAs(greyBox) {
                                start.linkTo(mainButton.end, margin = (-20).dp)
                                centerVerticallyTo(mainButton)
                            }
                    )

                    Image(
                        painter = painterResource(id = R.drawable.settings_icon),
                        contentDescription = "Settings",
                        modifier = Modifier
                            .size(55.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRecording) Color.Gray else Color(0xFFE0E0E0))
                            .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                            .alpha(if (isRecording) 0.5f else 1f)
                            .clickable(enabled = !isRecording) { onSettingsClick() }
                            .zIndex(2f)
                            .constrainAs(settingsButton) {
                                start.linkTo(greyBox.start, margin = 27.dp)
                                top.linkTo(greyBox.top, margin = 8.dp)
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun TermsAndPrivacyScreen(onAccept: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scrollState = rememberScrollState()

    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    val bothAccepted = termsAccepted && privacyAccepted

    // True for existing users who accepted an earlier version of the policies
    val isReacceptance by context.dataStore.data
        .map { it[TOS_ACCEPTED_KEY] ?: false }
        .collectAsStateWithLifecycle(initialValue = false)

    val termsText = remember {
        try { context.assets.open("terms_of_service.txt").bufferedReader().use { it.readText() } }
        catch (e: Exception) { "Error loading Terms of Service." }
    }

    val privacyText = remember {
        try { context.assets.open("privacy_policy.txt").bufferedReader().use { it.readText() } }
        catch (e: Exception) { "Error loading Privacy Policy." }
    }

    val colors = LocalAppColors.current

    Box(modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // Header icon badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                if (isReacceptance) "We've Updated Our Policies" else "Welcome to Road's Eye",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (isReacceptance)
                    "Our Terms of Service and Privacy Policy have changed. Please review and accept the updated policies to continue."
                else
                    "Please review and accept our policies to continue.",
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            PolicyCard(
                title = "Terms of Service",
                icon = Icons.Default.Description,
                body = termsText
            )

            Spacer(modifier = Modifier.height(16.dp))

            PolicyCard(
                title = "Privacy Policy",
                icon = Icons.Default.Shield,
                body = privacyText
            )

            Spacer(modifier = Modifier.height(28.dp))

            PolicyAcceptRow(
                label = "I accept the Terms of Service",
                checked = termsAccepted,
                onToggle = { termsAccepted = !termsAccepted }
            )

            Spacer(modifier = Modifier.height(10.dp))

            PolicyAcceptRow(
                label = "I accept the Privacy Policy",
                checked = privacyAccepted,
                onToggle = { privacyAccepted = !privacyAccepted }
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    onAccept()
                },
                enabled = bothAccepted,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.control,
                    disabledContentColor = colors.textSecondary
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Continue", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PolicyCard(
    title: String,
    icon: ImageVector,
    body: String
) {
    val colors = LocalAppColors.current

    Card(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.divider),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.divider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun PolicyAcceptRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                1.dp,
                if (checked) colors.accent else colors.divider,
                RoundedCornerShape(14.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                checkmarkColor = Color.White,
                uncheckedColor = colors.textSecondary
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TermsAndPrivacyScreenPreview() {
    MaterialTheme { TermsAndPrivacyScreen(onAccept = {}) }
}
