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
import androidx.datastore.preferences.core.edit

@Composable
fun VideoSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Local state — synced from DataStore
    var selectedQuality by remember { mutableStateOf("Medium") }
    var selectedFPS by remember { mutableStateOf("30") }
    var selectedZoom by remember { mutableStateOf("0.5x") }

    LaunchedEffect(Unit) {
        context.dataStore.data.collect { prefs ->
            selectedQuality = prefs[VIDEO_QUALITY_KEY] ?: "Medium"
            selectedFPS = prefs[FPS_KEY] ?: "30"
            selectedZoom = prefs[CAMERA_ZOOM_KEY] ?: "0.5x"
        }
    }

    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        SegmentedControl(
            title = "Video Resolution",
            options = listOf("Low", "Medium", "High"),
            selected = selectedQuality,
            onSelect = { new ->
                selectedQuality = new
                scope.launch {
                    context.dataStore.edit { it[VIDEO_QUALITY_KEY] = new }
                }
            }
        )

        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        SegmentedControl(
            title = "Frames Per Second",
            options = listOf("15", "30", "60"),
            selected = selectedFPS,
            onSelect = { new ->
                selectedFPS = new
                scope.launch {
                    context.dataStore.edit { it[FPS_KEY] = new }
                }
            }
        )

        HorizontalDivider(color = colors.divider, thickness = 1.dp)

        SegmentedControl(
            title = "Camera Zoom",
            options = listOf("0.5x", "1x", "2x"),
            selected = selectedZoom,
            onSelect = { new ->
                selectedZoom = new
                scope.launch {
                    context.dataStore.edit { it[CAMERA_ZOOM_KEY] = new }
                }
            }
        )
    }
}

@Composable
private fun SegmentedControl(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
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
                val isSelected = selected == option

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (isSelected) colors.accent else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else colors.textSecondary,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (index < options.lastIndex) {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }
}
