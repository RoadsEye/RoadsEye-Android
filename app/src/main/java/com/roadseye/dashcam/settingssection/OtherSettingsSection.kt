package com.roadseye.dashcam.settingssection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadseye.dashcam.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun OtherSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    // Appearance (System / Light / Dark)
    val appearance = context.dataStore.data
        .map { preferences -> preferences[APPEARANCE_KEY] ?: "System" }
        .collectAsStateWithLifecycle(initialValue = "System")

    // Driving Overlay Mode
    val drivingOverlayModeEnabled = context.dataStore.data
        .map { preferences -> preferences[DRIVING_OVERLAY_MODE_KEY] ?: true }
        .collectAsStateWithLifecycle(initialValue = true)

    // Overlay Always Active (defaults to true)
    val overlayAlwaysActive = context.dataStore.data
        .map { preferences -> preferences[OVERLAY_ALWAYS_ACTIVE_KEY] ?: true }
        .collectAsStateWithLifecycle(initialValue = true)

    // Picture-in-Picture
    val pipEnabled = context.dataStore.data
        .map { preferences -> preferences[PIP_ENABLED_KEY] ?: true }
        .collectAsStateWithLifecycle(initialValue = true)

    // Speed Units
    val speedUnits = context.dataStore.data
        .map { preferences -> preferences[SPEED_UNITS_KEY] ?: "MPH" }
        .collectAsStateWithLifecycle(initialValue = "MPH")

    var showInfoDialog by remember { mutableStateOf(false) }

    // Ensure default value is written on first launch
    LaunchedEffect(Unit) {
        context.dataStore.edit { preferences ->
            if (preferences[OVERLAY_ALWAYS_ACTIVE_KEY] == null) {
                preferences[OVERLAY_ALWAYS_ACTIVE_KEY] = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Appearance (System / Light / Dark)
        ClipDurationSegmentedView(
            title = "Appearance",
            options = listOf("System", "Light", "Dark"),
            selectedOption = appearance.value,
            onOptionSelected = { newValue ->
                scope.launch {
                    context.dataStore.edit { preferences ->
                        preferences[APPEARANCE_KEY] = newValue
                    }
                }
            }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        // Driving Overlay Mode with Info Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ToggleRow(
                title = "Driving Overlay Mode",
                isChecked = drivingOverlayModeEnabled.value,
                onCheckedChange = { newValue ->
                    scope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[DRIVING_OVERLAY_MODE_KEY] = newValue
                            if (!newValue) {
                                preferences[OVERLAY_ALWAYS_ACTIVE_KEY] = false
                            }
                        }
                    }
                }
            )

            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Driving Overlay Info",
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        // Overlay Always Active - only show if Driving Overlay Mode is enabled
        if (drivingOverlayModeEnabled.value) {
            ToggleRow(
                title = "Overlay Always Active",
                isChecked = overlayAlwaysActive.value,
                onCheckedChange = { newValue ->
                    scope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[OVERLAY_ALWAYS_ACTIVE_KEY] = newValue
                        }
                    }
                }
            )
            HorizontalDivider(color = colors.divider, thickness = 1.dp)
        }

        // Speed Units
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Speed Units",
                color = colors.textPrimary,
                fontSize = 16.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            context.dataStore.edit { it[SPEED_UNITS_KEY] = "KM/H" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (speedUnits.value == "KM/H") colors.accent else colors.control,
                        contentColor = if (speedUnits.value == "KM/H") Color.White else colors.textPrimary
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("KM/H", fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            context.dataStore.edit { it[SPEED_UNITS_KEY] = "MPH" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (speedUnits.value == "MPH") colors.accent else colors.control,
                        contentColor = if (speedUnits.value == "MPH") Color.White else colors.textPrimary
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("MPH", fontSize = 14.sp)
                }
            }
        }
    }

    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text("Driving Overlay Mode", color = colors.textPrimary)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Driving Overlay Mode provides a visual overlay on your dashcam screen to assist with driving and recording. Here's how it works:",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("1. Driving Overlay Mode", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "When enabled, this mode displays an overlay when the vehicle is in motion (requires location permissions). The overlay hides when the vehicle is stationary, as long as recording is active.",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("2. Overlay Always Active", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "When enabled, the overlay is shown continuously from the start to the stop of recording, regardless of vehicle motion. This does not require location permissions.",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("3. Interacting with the Overlay", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Tap the screen during recording to temporarily hide the overlay.", color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tips for Best Results", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "• Ensure location permissions are granted for Driving Overlay Mode.\n" +
                                "• Use Overlay Always Active for continuous display without motion detection.\n" +
                                "• Adjust settings based on your recording needs.",
                        color = colors.textPrimary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Done", color = colors.accent)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary
        )
    }
}
