package com.goonvpn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GoonDarkScheme = darkColorScheme(
    primary              = AccentBlue,
    onPrimary            = Color.White,
    secondary            = ConnectedGreen,
    onSecondary          = Color.White,
    background           = Background,
    onBackground         = TextPrimary,
    surface              = SurfaceCard,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceElevated,
    onSurfaceVariant     = TextSecondary,
    outline              = BorderColor,
    error                = DisconnectedRed,
    onError              = Color.White,
    surfaceContainerLow  = NavBar,
    surfaceContainer     = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,
)

private val GoonLightScheme = lightColorScheme(
    primary              = AccentBlue,
    onPrimary            = Color.White,
    secondary            = ConnectedGreen,
    onSecondary          = Color.White,
    background           = Color(0xFFF2F5FA),
    onBackground         = Color(0xFF0D1117),
    surface              = Color(0xFFFFFFFF),
    onSurface            = Color(0xFF0D1117),
    surfaceVariant       = Color(0xFFE8EDF5),
    onSurfaceVariant     = Color(0xFF5A6578),
    outline              = Color(0xFFD0D9E8),
    error                = DisconnectedRed,
    onError              = Color.White,
    surfaceContainerLow  = Color(0xFFEBEFF7),
    surfaceContainer     = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8EDF5),
)

@Composable
fun GoonVPNTheme(isDark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark) GoonDarkScheme else GoonLightScheme,
        typography  = Typography,
        content     = content
    )
}
