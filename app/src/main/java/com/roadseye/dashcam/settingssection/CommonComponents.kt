package com.roadseye.dashcam.settingssection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadseye.dashcam.ui.theme.LocalAppColors

@Composable
fun ToggleRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.control, shape = RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                onCheckedChange(!isChecked)
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f, fill = false)
        )
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accent,
                checkedTrackColor = colors.surface,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.control
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ClipDurationSegmentedView(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    val colors = LocalAppColors.current

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = title, color = colors.textPrimary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.control, shape = RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isSelected = selectedOption == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) colors.accent else Color.Transparent, shape = RoundedCornerShape(8.dp))
                        .clickable { onOptionSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = option, color = if (isSelected) Color.White else colors.textSecondary, fontSize = 16.sp)
                }
                if (option != options.last()) {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }
}
