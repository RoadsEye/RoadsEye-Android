package com.roadseye.dashcam.settingssection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadseye.dashcam.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsMenu(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = General, 1 = Account

    val colors = LocalAppColors.current

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = colors.textPrimary, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
                    .padding(paddingValues)
            ) {
                // Segmented Tab Bar
                SegmentedTabBar(
                    tabs = listOf("General", "Account"),
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                // Tab Content
                when (selectedTab) {
                    0 -> GeneralSettingsTab()
                    1 -> AccountTab()
                }
            }
        }
    )
}

// Segmented tab bar
@Composable
fun SegmentedTabBar(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) colors.accent else colors.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (isSelected) Color.White else colors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// General tab
@Composable
fun GeneralSettingsTab() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text("Video Settings", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        VideoSettingsSection()

        Text("Audio Settings", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        AudioSettingsSection()

        Text("Recording Settings", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        RecordingSettingsSection()

        Text("On-Screen Display", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        UserInterfaceSection()

        OtherSettingsSection()

        Text("More Information", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        MoreInformationSection()

        // Legal & Safety Notices
        Text(
            text = "Legal & Safety Notices",
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        var expanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Hide Disclaimers" else "View Important Disclaimers & Safety Information",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = colors.textPrimary
                    )
                }

                if (expanded) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DisclaimerText(
                            "Use of this application is at your own risk. The developer assumes no liability for any damages, injuries, or legal consequences arising from its use. Ensure compliance with all applicable local, state, and federal laws, including those related to distracted driving."
                        )

                        DisclaimerText(
                            "All information provided by this app — including but not limited to location, date, time, speed, and other data — is approximate, may contain errors, and is provided 'AS IS' without any warranty of accuracy or reliability. Do NOT rely on this information for any critical decisions or purposes."
                        )

                        DisclaimerText(
                            "Not every option or feature may be supported depending on the device being used. Some cameras, sensors, or hardware capabilities may be required for full functionality."
                        )

                        DisclaimerText(
                            "During any mode — including Picture-in-Picture (PiP) mode — we cannot guarantee that every feature will work as intended."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Please read the full Terms of Service (accepted during onboarding) for complete details.",
                            color = colors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

/** Disclaimer paragraph. */
@Composable
private fun DisclaimerText(text: String) {
    Text(
        text = text,
        color = LocalAppColors.current.textSecondary,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
}

// Account tab (storage, cache and driving stats)
@Composable
fun AccountTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    var usedMB by remember { mutableStateOf(0L) }
    var maxStorageMB by remember { mutableStateOf(2000) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var deleteInProgress by remember { mutableStateOf(false) }

    var cacheBytes by remember { mutableStateOf(0L) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var clearCacheInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        context.dataStore.data.collect { prefs ->
            maxStorageMB = prefs[MAX_STORAGE_MB_KEY] ?: 2000
        }
    }

    LaunchedEffect(maxStorageMB) {
        usedMB = calculateRoadsEyeStorageUsage(context)
    }

    LaunchedEffect(Unit) {
        cacheBytes = calculateCacheSize(context)
    }

    val progress = (usedMB.toFloat() / maxStorageMB).coerceIn(0f, 1f)

    val usedText = formatMB(usedMB)

    val limitText = when (maxStorageMB) {
        1000 -> "1 GB"
        2000 -> "2 GB"
        5000 -> "5 GB"
        10000 -> "10 GB"
        else -> "2 GB"
    }

    val remainingText = formatMB((maxStorageMB - usedMB).coerceAtLeast(0L))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Storage Usage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Storage Usage", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)

                Spacer(modifier = Modifier.height(20.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(colors.control, RoundedCornerShape(7.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(14.dp)
                            .background(colors.accent, RoundedCornerShape(7.dp))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Used", color = Color(0xFF4FC3F7))
                    Text(usedText, color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Limit", color = colors.textPrimary)
                    Text(limitText, color = colors.textPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Remaining", color = Color(0xFF81C784))
                    Text(remainingText, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons: Open Folder + Delete All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { openRoadsEyeFolder(context) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Videos Folder")
                    }

                    Button(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                        enabled = !deleteInProgress
                    ) {
                        if (deleteInProgress) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text("Delete All")
                        }
                    }
                }
            }
        }

        // Cache
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Cache", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "Temporary files left behind while trimming and saving clips. Clearing this never touches your recordings.",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cached data", color = colors.textPrimary)
                    Text(formatBytes(cacheBytes), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showClearCacheDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    enabled = !clearCacheInProgress
                ) {
                    if (clearCacheInProgress) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Cache")
                    }
                }
            }
        }

        // Fun Stats
        FunStatsCard()
    }

    // Clear cache confirmation dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Delete Cache?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This clears all of the app's temporary files (${formatBytes(cacheBytes)}).\n\n" +
                            "Your recordings, clips and settings are not affected. Avoid doing this while a clip is still saving.",
                    color = colors.textPrimary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        clearCacheInProgress = true
                        scope.launch(Dispatchers.IO) {
                            val freed = cacheBytes
                            val success = clearAppCache(context)
                            val remaining = calculateCacheSize(context)
                            withContext(Dispatchers.Main) {
                                clearCacheInProgress = false
                                cacheBytes = remaining
                                val message = if (success) "Cache cleared (${formatBytes(freed - remaining)} freed)"
                                else "Some cached files could not be deleted"
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accent)
                ) {
                    Text("Delete Cache", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary
        )
    }

    // Delete all confirmation dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    "Delete All Recordings?",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will permanently delete ALL videos in the RoadsEye folder (including crash clips and manual clips).\n\n" +
                            "This action cannot be undone. Are you sure?",
                    color = colors.textPrimary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        deleteInProgress = true
                        scope.launch(Dispatchers.IO) {
                            val success = deleteAllRoadsEyeFiles(context)
                            withContext(Dispatchers.Main) {
                                deleteInProgress = false
                                usedMB = calculateRoadsEyeStorageUsage(context) // Refresh usage
                                val message = if (success) "All RoadsEye videos have been deleted"
                                else "Some files could not be deleted"
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Yes, Delete All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.surface,
            titleContentColor = Color.Red,
            textContentColor = colors.textPrimary
        )
    }
}

// Driving stats tracked by DrivingStatsManager
@Composable
private fun FunStatsCard() {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    val prefs by context.dataStore.data.collectAsStateWithLifecycle(initialValue = null)

    val speedUnit = prefs?.get(SPEED_UNITS_KEY) ?: "MPH"
    val distanceMeters = prefs?.get(STAT_DISTANCE_METERS_KEY) ?: 0.0
    val recordingMs = prefs?.get(STAT_RECORDING_TIME_MS_KEY) ?: 0L
    val topSpeedMs = prefs?.get(STAT_TOP_SPEED_MS_KEY) ?: 0.0
    val drives = prefs?.get(STAT_DRIVES_KEY) ?: 0
    val videosSaved = prefs?.get(STAT_VIDEOS_SAVED_KEY) ?: 0
    val clipsSaved = (prefs?.get(STAT_CLIPS_SAVED_KEY) ?: 0) + (prefs?.get(STAT_CRASH_CLIPS_SAVED_KEY) ?: 0)

    val speedMultiplier = if (speedUnit == "MPH") 2.23694 else 3.6

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Fun Stats", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Your Road's Eye journey so far",
                fontSize = 13.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile("🛣️", formatDistance(distanceMeters, speedUnit), "Distance Driven", Modifier.weight(1f).fillMaxHeight())
                StatTile("⏱️", formatStatDuration(recordingMs), "Time Recorded", Modifier.weight(1f).fillMaxHeight())
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile("🚀", "%.0f %s".format(topSpeedMs * speedMultiplier, speedUnit), "Top Speed", Modifier.weight(1f).fillMaxHeight())
                StatTile("🚗", drives.toString(), "Drives", Modifier.weight(1f).fillMaxHeight())
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile("🎬", videosSaved.toString(), "Videos Recorded", Modifier.weight(1f).fillMaxHeight())
                StatTile("✂️", clipsSaved.toString(), "Clips Saved", Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun StatTile(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier
            .background(colors.control, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatDistance(meters: Double, speedUnit: String): String {
    return if (speedUnit == "MPH") {
        val miles = meters / 1609.344
        if (miles >= 100) "%,.0f mi".format(miles) else "%.1f mi".format(miles)
    } else {
        val km = meters / 1000.0
        if (km >= 100) "%,.0f km".format(km) else "%.1f km".format(km)
    }
}

private fun formatStatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        totalMinutes > 0 -> "${minutes}m"
        else -> "${(ms / 1000).coerceAtLeast(0)}s"
    }
}

private fun formatMB(mb: Long): String {
    val gb = mb / 1024.0
    return if (gb >= 1.0) {
        "${"%.1f".format(gb)} GB"
    } else {
        "$mb MB"
    }
}

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024 * 1024 -> "${"%.1f".format(safe / (1024.0 * 1024 * 1024))} GB"
        safe >= 1024L * 1024 -> "${"%.1f".format(safe / (1024.0 * 1024))} MB"
        safe >= 1024L -> "${safe / 1024} KB"
        else -> "$safe B"
    }
}

/** Every cache location the app owns: internal, code cache, and any external caches. */
private fun cacheRoots(context: Context): List<File> =
    (listOf(context.cacheDir, context.codeCacheDir) + context.externalCacheDirs.filterNotNull())
        .distinctBy { it.absolutePath }

private suspend fun calculateCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
    try {
        cacheRoots(context).sumOf { root ->
            if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        }
    } catch (e: Exception) {
        Log.e("Cache", "Failed to calculate cache size", e)
        0L
    }
}

/** Empties the app's cache directories but keeps the directories themselves. */
private suspend fun clearAppCache(context: Context): Boolean = withContext(Dispatchers.IO) {
    var allDeleted = true
    cacheRoots(context).forEach { root ->
        try {
            root.listFiles()?.forEach { child ->
                if (!child.deleteRecursively()) {
                    Log.w("Cache", "Failed to delete: ${child.absolutePath}")
                    allDeleted = false
                }
            }
        } catch (e: Exception) {
            Log.e("Cache", "Error while clearing ${root.absolutePath}", e)
            allDeleted = false
        }
    }
    allDeleted
}

private suspend fun calculateRoadsEyeStorageUsage(context: Context): Long = withContext(Dispatchers.IO) {
    try {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RoadsEye")
        if (!dir.exists()) return@withContext 0L
        dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in listOf("mp4", "mov") }
            .sumOf { it.length() } / (1024 * 1024)
    } catch (e: Exception) {
        Log.e("Storage", "Failed to calculate usage", e)
        0L
    }
}

/** Deletes all files and subfolders inside Movies/RoadsEye only. */
private suspend fun deleteAllRoadsEyeFiles(context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val roadsEyeDir = File(moviesDir, "RoadsEye")

        if (!roadsEyeDir.exists() || !roadsEyeDir.isDirectory) {
            Log.w("Storage", "RoadsEye folder does not exist")
            return@withContext true
        }

        var allDeleted = true

        // Delete all video files (including in subfolders like Crashes)
        roadsEyeDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.lowercase() in listOf("mp4", "mov")) {
                if (!file.delete()) {
                    Log.w("Storage", "Failed to delete: ${file.absolutePath}")
                    allDeleted = false
                } else {
                    Log.d("Storage", "Deleted: ${file.name}")
                }
            }
        }

        // Clean up empty directories (bottom-up)
        roadsEyeDir.walkBottomUp().forEach { file ->
            if (file.isDirectory && (file.listFiles()?.isEmpty() == true)) {
                file.delete()
            }
        }

        allDeleted
    } catch (e: Exception) {
        Log.e("Storage", "Error while deleting RoadsEye files", e)
        false
    }
}

private fun openRoadsEyeFolder(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/external/video/media"), "vnd.android.cursor.dir/video")
            putExtra("android.provider.extra.INITIAL_URI", "Movies/RoadsEye")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open folder", Toast.LENGTH_SHORT).show()
    }
}
