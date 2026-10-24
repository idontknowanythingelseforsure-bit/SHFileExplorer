package com.shfileexplorer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object ShPalette {
    val Teal = Color(0xFF4DD0C8)
    val TealDeep = Color(0xFF1F9E96)
    val Amber = Color(0xFFFFB86B)
    val Background = Color(0xFF0B1116)
    val Surface = Color(0xFF111A22)
    val SurfaceRaised = Color(0xFF17232C)
    val SurfaceHigh = Color(0xFF1D2C36)
    val TextPrimary = Color(0xFFE8F0F2)
    val TextSecondary = Color(0xFF93A6AE)
    val Divider = Color(0xFF24343E)
    val Danger = Color(0xFFFF7B72)
}

private val DarkColors = darkColorScheme(
    primary = ShPalette.Teal,
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF123B38),
    onPrimaryContainer = Color(0xFFB7FFF8),
    secondary = ShPalette.Amber,
    onSecondary = Color(0xFF2A1700),
    secondaryContainer = Color(0xFF3A2A12),
    onSecondaryContainer = Color(0xFFFFDDBB),
    background = ShPalette.Background,
    onBackground = ShPalette.TextPrimary,
    surface = ShPalette.Surface,
    onSurface = ShPalette.TextPrimary,
    surfaceVariant = ShPalette.SurfaceRaised,
    onSurfaceVariant = ShPalette.TextSecondary,
    outline = ShPalette.Divider,
    outlineVariant = ShPalette.SurfaceHigh,
    error = ShPalette.Danger,
    onError = Color(0xFF360001),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2EA),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF7A5118),
    onSecondary = Color.White,
    background = Color(0xFFF5FAFA),
    onBackground = Color(0xFF101C20),
    surface = Color.White,
    onSurface = Color(0xFF101C20),
    surfaceVariant = Color(0xFFE7EFEF),
    onSurfaceVariant = Color(0xFF405257),
    outline = Color(0xFFC6D5D5),
    error = Color(0xFFBA1A1A),
)

@Composable
fun ShFileExplorerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
