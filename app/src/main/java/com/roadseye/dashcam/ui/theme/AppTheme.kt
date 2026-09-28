package com.roadseye.dashcam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadseye.dashcam.settingssection.APPEARANCE_KEY
import com.roadseye.dashcam.settingssection.dataStore
import kotlinx.coroutines.flow.map

/** Semantic colors that follow the Appearance setting. */
data class AppColors(
    val isDark: Boolean,
    val background: Color,      // full-screen background
    val surface: Color,         // cards / settings section background
    val control: Color,         // rows, segmented controls, toggles background
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val accent: Color = Color(0xFF007AFF)
)

val DarkAppColors = AppColors(
    isDark = true,
    background = Color(0xFF1C2526),
    surface = Color(0xFF2D383A),
    control = Color(0xFF3A4547),
    textPrimary = Color.White,
    textSecondary = Color(0xFFB0BEC5),
    divider = Color(0x4D808080)
)

val LightAppColors = AppColors(
    isDark = false,
    background = Color(0xFFF2F4F5),
    surface = Color.White,
    control = Color(0xFFE7EBEC),
    textPrimary = Color(0xFF1C2526),
    textSecondary = Color(0xFF5C6B70),
    divider = Color(0x33808080)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

private val AppDarkColorScheme = darkColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    background = Color(0xFF1C2526),
    onBackground = Color.White,
    surface = Color(0xFF2D383A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF3A4547),
    onSurfaceVariant = Color(0xFFB0BEC5),
    primaryContainer = Color(0xFF004A99),
    onPrimaryContainer = Color.White
)

private val AppLightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    background = Color(0xFFF2F4F5),
    onBackground = Color(0xFF1C2526),
    surface = Color.White,
    onSurface = Color(0xFF1C2526),
    surfaceVariant = Color(0xFFE7EBEC),
    onSurfaceVariant = Color(0xFF5C6B70),
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF00335C)
)

/** App-wide theme driven by the Appearance setting. */
@Composable
fun RoadsEyeAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearance by context.dataStore.data
        .map { it[APPEARANCE_KEY] ?: "System" }
        .collectAsStateWithLifecycle(initialValue = "System")

    val darkTheme = when (appearance) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
