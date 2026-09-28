package com.roadseye.dashcam.settingssection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadseye.dashcam.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit

@Composable
fun AudioSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    var voiceRecordingEnabled by remember { mutableStateOf(false) }

    // Load saved value
    LaunchedEffect(Unit) {
        context.dataStore.data.collect { prefs ->
            voiceRecordingEnabled = prefs[IS_VOICE_RECORDING_KEY] ?: false
        }
    }

    var showInfoDialog by remember { mutableStateOf(false) }

    // Info dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Voice Commands", color = colors.textPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Voice commands allows you to control your dashcam hands-free using spoken instructions. Follow these steps to use voice commands effectively:",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("1. Activate Voice commands", color = colors.textPrimary, fontSize = 16.sp)
                    Text(
                        text = "Ensure 'Voice commands' is enabled in the Audio Settings. The app will listen for a wake phrase to start processing commands.",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("2. Use a Wake Phrase", color = colors.textPrimary, fontSize = 16.sp)
                    Text(
                        text = "Say one of the following phrases to activate command listening:\n" +
                                "• Hey Road's Eye\n" +
                                "• Hi Road's Eye\n\n" +
                                "After saying a wake phrase, the app will listen for your command for up to 5 seconds.",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("3. Give a Command", color = colors.textPrimary, fontSize = 16.sp)
                    Text(
                        text = "After the wake phrase, say one of these commands clearly:\n" +
                                "• Clip it or Clip recording: Save a short video clip.\n" +
                                "• Start recording: Begin continuous recording.\n" +
                                "• Stop recording: Stop the current recording (requires confirmation).\n" +
                                "• Yes: Confirm stopping a recording.\n" +
                                "• No or Cancel: Cancel the stop recording action.",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("4. Stop Recording Confirmation", color = colors.textPrimary, fontSize = 16.sp)
                    Text(
                        text = "When you say 'Stop recording,' the app will wait for you to confirm with 'Yes,' 'No,' or 'Cancel' to prevent accidental stops.",
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tips for Best Results", color = colors.textPrimary, fontSize = 16.sp)
                    Text(
                        text = "• Speak clearly and at a normal pace.\n" +
                                "• Use voice commands in a quiet environment for better recognition.\n" +
                                "• Ensure microphone permissions are granted.\n" +
                                "• Voice commands may pause during phone calls or when other audio is playing.",
                        color = colors.textPrimary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Done", color = colors.accent)
                }
            },
            dismissButton = { },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary
        )
    }

    // Main UI
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        fun setVoiceRecording(newValue: Boolean) {
            voiceRecordingEnabled = newValue
            scope.launch {
                context.dataStore.edit { preferences ->
                    preferences[IS_VOICE_RECORDING_KEY] = newValue
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.control, shape = RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    setVoiceRecording(!voiceRecordingEnabled)
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Voice Commands (ALPHA)",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            Switch(
                checked = voiceRecordingEnabled,
                onCheckedChange = { newValue -> setVoiceRecording(newValue) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.accent,
                    checkedTrackColor = colors.surface,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.control
                ),
                modifier = Modifier.padding(start = 8.dp)
            )

            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Voice Commands Info",
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = "Note: Enabling voice commands may interfere with external audio sources.",
            color = colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
