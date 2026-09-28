package com.roadseye.dashcam.settingssection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.roadseye.dashcam.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val UI_SHOW_LOCATION_KEY = booleanPreferencesKey("ui_location")
val UI_SHOW_DATE_KEY = booleanPreferencesKey("ui_date")
val UI_SHOW_TIME_KEY = booleanPreferencesKey("ui_time")
val UI_SHOW_SPEED_KEY = booleanPreferencesKey("ui_speed")

@Composable
fun UserInterfaceSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    val locationEnabled = context.dataStore.data
        .map { preferences -> preferences[UI_SHOW_LOCATION_KEY] ?: true }
        .collectAsState(initial = true)
    val dateEnabled = context.dataStore.data
        .map { preferences -> preferences[UI_SHOW_DATE_KEY] ?: true }
        .collectAsState(initial = true)
    val timeEnabled = context.dataStore.data
        .map { preferences -> preferences[UI_SHOW_TIME_KEY] ?: true }
        .collectAsState(initial = true)
    val speedEnabled = context.dataStore.data
        .map { preferences -> preferences[UI_SHOW_SPEED_KEY] ?: true }
        .collectAsState(initial = true)

    fun onToggle(key: Preferences.Key<Boolean>, newValue: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[key] = newValue
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        ToggleRow(
            title = "Location",
            isChecked = locationEnabled.value,
            onCheckedChange = { newValue: Boolean -> onToggle(UI_SHOW_LOCATION_KEY, newValue) }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        ToggleRow(
            title = "Date",
            isChecked = dateEnabled.value,
            onCheckedChange = { newValue: Boolean -> onToggle(UI_SHOW_DATE_KEY, newValue) }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        ToggleRow(
            title = "Time",
            isChecked = timeEnabled.value,
            onCheckedChange = { newValue: Boolean -> onToggle(UI_SHOW_TIME_KEY, newValue) }
        )
        HorizontalDivider(color = colors.divider, thickness = 1.dp)
        ToggleRow(
            title = "Speed",
            isChecked = speedEnabled.value,
            onCheckedChange = { newValue: Boolean -> onToggle(UI_SHOW_SPEED_KEY, newValue) }
        )
    }
}
