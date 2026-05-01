package com.goonvpn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SkyColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Surface,
    secondary = AccentPurple,
    onSecondary = Surface,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = DisconnectedRed,
)

@Composable
fun GoonVPNTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SkyColorScheme,
        typography = Typography,
        content = content
    )
}
