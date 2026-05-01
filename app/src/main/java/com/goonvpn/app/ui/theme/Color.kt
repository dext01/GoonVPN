package com.goonvpn.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Backgrounds ───────────────────────────────────────────────────────────────
val Background     = Color(0xFF0B0F1A)   // основной фон — почти чёрный с синим
val SurfaceCard    = Color(0xFF131929)   // карточки
val SurfaceElevated = Color(0xFF1C2538) // поднятые элементы (bottom sheet и т.д.)
val BorderColor    = Color(0xFF1E2D4A)  // тонкая граница карточек

// ── Accent ───────────────────────────────────────────────────────────────────
val AccentBlue     = Color(0xFF4F86F7)  // основной акцент — доверительный синий
val AccentBlueDim  = Color(0xFF1A3A6E) // заглушённый синий (фоны)

// ── State ────────────────────────────────────────────────────────────────────
val ConnectedGreen    = Color(0xFF30D158)
val ConnectedGreenDim = Color(0xFF0C2B1E)
val DisconnectedRed   = Color(0xFFFF453A)
val ConnectingYellow  = Color(0xFFFFD60A)

// ── Text ─────────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E9BB5)
val TextHint      = Color(0xFF3D4D6A)

// ── Misc ─────────────────────────────────────────────────────────────────────
val Divider     = Color(0xFF161E30)
val NavBar      = Color(0xFF0E1422)

// Legacy aliases (чтобы не ломать неизменённые файлы)
val Surface              = SurfaceElevated
val CardBackground       = SurfaceCard
val CardBackgroundSelected = AccentBlueDim
val AccentPurple         = Color(0xFF7B61FF)
val AccentBlueDark       = Color(0xFF2A5FD4)
val ConnectedGreenDimOld = Color(0xFFD4F1DF)
val TextPrimaryDark      = Color(0xFF1A2238)
