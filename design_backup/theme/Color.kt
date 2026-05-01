package com.goonvpn.app.ui.theme

import androidx.compose.ui.graphics.Color

// Sky palette — light, soft, paper-airplane-friendly
val Background = Color(0xFFF4F8FE)         // very pale sky, fallback under gradient
val Surface = Color(0xFFFFFFFF)            // pure white
val CardBackground = Color(0xFFFFFFFF)     // white card
val CardBackgroundSelected = Color(0xFFE6F0FF) // pale sky tint for selection

val AccentBlue = Color(0xFF2F7DF6)         // crisp sky blue
val AccentBlueDark = Color(0xFF1B5FD6)
val AccentPurple = Color(0xFFFF7E6B)       // warm coral (used as warm accent)

val ConnectedGreen = Color(0xFF1FB261)
val ConnectedGreenDim = Color(0xFFD4F1DF)
val DisconnectedRed = Color(0xFFE5484D)
val ConnectingYellow = Color(0xFFFFB627)

val TextPrimary = Color(0xFF1A2238)        // deep slate
val TextSecondary = Color(0xFF5C6B86)
val TextHint = Color(0xFF94A3B8)

val Divider = Color(0xFFEAF0F8)
val BorderColor = Color(0xFFDDE6F1)

// Sky gradient stops — used by AnimatedBackground
val SkyTop = Color(0xFFFFFAF0)             // warm cream at top
val SkyMid = Color(0xFFEAF4FF)             // light sky
val SkyBottom = Color(0xFFD3E5FA)          // a touch deeper at the bottom
val CloudColor = Color(0xFFFFFFFF)
