package com.roadseye.dashcam.settingssection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadseye.dashcam.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.datastore.preferences.core.edit

@Composable
fun RecordingSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    // Local state synced from DataStore
    var segmentDuration by remember { mutableStateOf("1m") }
    var maxStorageMB by remember { mutableStateOf(2000) }
    var crashDetectionEnabled by remember { mutableStateOf(false) }
    var autoRecordEnabled by remember { mutableStateOf(false) }
    var speedThreshold by remember { mutableStateOf(5f) }
    var durationThreshold by remember { mutableStateOf(5f) }
    var speedUnit by remember { mutableStateOf("MPH") }

    LaunchedEffect(Unit) {
        context.dataStore.data.collect { prefs ->
            segmentDuration = prefs[SEGMENT_DURATION_KEY] ?: "1m"
            maxStorageMB = prefs[MAX_STORAGE_MB_KEY] ?: 2000
            crashDetectionEnabled = prefs[CRASH_DETECTION_KEY] ?: false
            autoRecordEnabled = prefs[AUTO_RECORD_KEY] ?: false
            speedThreshold = prefs[SPEED_THRESHOLD_KEY] ?: 5f
            durationThreshold = prefs[DURATION_THRESHOLD_KEY] ?: 5f
            speedUnit = prefs[SPEED_UNITS_KEY] ?: "MPH"
        }
    }

    var showCrashDetectionAlert by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {

        // Recording segment length
        Text(
            text = "Recording Segment Length",
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SegmentedOptionView(
            title = "Each segment is automatically saved",
            options = listOf("1m", "2m", "3m"),
            selectedOption = segmentDuration,
            onOptionSelected = { new ->
                segmentDuration = new
                scope.launch {
                    context.dataStore.edit { it[SEGMENT_DURATION_KEY] = new }
                }
            }
        )

        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        // Maximum storage
        Text(
            text = "Maximum Storage",
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        SegmentedOptionView(
            title = "Oldest clips are auto-deleted when limit reached",
            options = listOf("2GB", "5GB", "10GB"),
            selectedOption = when (maxStorageMB) {
                2000 -> "2GB"
                5000 -> "5GB"
                10000 -> "10GB"
                else -> "2GB"
            },
            onOptionSelected = { new ->
                val mbValue = when (new) {
                    "2GB" -> 2000
                    "5GB" -> 5000
                    "10GB" -> 10000
                    else -> 2000
                }
                maxStorageMB = mbValue
                scope.launch {
                    context.dataStore.edit { it[MAX_STORAGE_MB_KEY] = mbValue }
                }
            }
        )

        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        // Crash Detection
        ToggleRow(
            title = "Crash Detection (BETA)",
            isChecked = crashDetectionEnabled,
            onCheckedChange = { newValue ->
                if (crashDetectionEnabled && !newValue) {
                    showCrashDetectionAlert = true
                } else {
                    crashDetectionEnabled = newValue
                    scope.launch {
                        context.dataStore.edit { it[CRASH_DETECTION_KEY] = newValue }
                    }
                }
            }
        )

        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        // Auto Record
        ToggleRow(
            title = "Auto Record",
            isChecked = autoRecordEnabled,
            onCheckedChange = { newValue ->
                autoRecordEnabled = newValue
                scope.launch {
                    context.dataStore.edit { it[AUTO_RECORD_KEY] = newValue }
                }
            }
        )

        if (autoRecordEnabled) {
            HorizontalDivider(color = colors.divider, thickness = 1.dp)

            SliderRow(
                title = "Speed Threshold",
                value = if (speedUnit == "MPH") speedThreshold else (speedThreshold * 1.60934f).coerceIn(1f, 10f),
                valueRange = 1f..10f,
                onValueChange = { new ->
                    scope.launch {
                        context.dataStore.edit { prefs ->
                            val stored = if (speedUnit == "MPH") new else new / 1.60934f
                            prefs[SPEED_THRESHOLD_KEY] = stored.coerceIn(1f, 10f)
                        }
                    }
                },
                unit = speedUnit
            )

            HorizontalDivider(color = colors.divider, thickness = 1.dp)

            SliderRow(
                title = "Duration Threshold",
                value = durationThreshold,
                valueRange = 1f..10f,
                onValueChange = { new ->
                    durationThreshold = new.roundToInt().toFloat()
                    scope.launch {
                        context.dataStore.edit { it[DURATION_THRESHOLD_KEY] = durationThreshold }
                    }
                },
                unit = "sec"
            )

            Text(
                text = "Recording starts automatically when speed exceeds threshold for the specified duration.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (showCrashDetectionAlert) {
        AlertDialog(
            onDismissRequest = { showCrashDetectionAlert = false },
            title = { Text("Disable Crash Detection?") },
            text = { Text("Are you sure you want to turn off Crash Detection?") },
            confirmButton = {
                TextButton(onClick = {
                    crashDetectionEnabled = false
                    scope.launch { context.dataStore.edit { it[CRASH_DETECTION_KEY] = false } }
                    showCrashDetectionAlert = false
                }) { Text("Disable") }
            },
            dismissButton = { TextButton(onClick = { showCrashDetectionAlert = false }) { Text("Cancel", color = colors.textSecondary) } },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary
        )
    }
}

// SegmentedOptionView
@Composable
fun SegmentedOptionView(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    val colors = LocalAppColors.current

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.control, shape = RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = selectedOption == option

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSelected) colors.accent else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onOptionSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else colors.textSecondary,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (index < options.lastIndex) Spacer(modifier = Modifier.width(1.dp))
            }
        }
    }
}

// SliderRow
@Composable
fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    unit: String
) {
    val colors = LocalAppColors.current

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Header: title on the left, current value in a pill on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f, fill = false)
            )

            Box(
                modifier = Modifier
                    .background(colors.accent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${value.roundToInt()} $unit",
                    color = colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Slider(
            value = value,
            onValueChange = { raw ->
                val snapped = raw.roundToInt().toFloat()
                onValueChange(snapped.coerceIn(valueRange))
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.control
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )

        // Range endpoints under the slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${valueRange.start.roundToInt()} $unit",
                fontSize = 10.sp,
                color = colors.textSecondary
            )
            Text(
                text = "${valueRange.endInclusive.roundToInt()} $unit",
                fontSize = 10.sp,
                color = colors.textSecondary
            )
        }
    }
}
