package com.goonvpn.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import com.goonvpn.app.model.Server
import com.goonvpn.app.ui.components.AddServerBottomSheet
import com.goonvpn.app.ui.components.AnimatedBackground
import com.goonvpn.app.ui.components.DartboardButton
import com.goonvpn.app.ui.theme.*
import com.goonvpn.app.viewmodel.VpnState

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L              -> "$bytes Б"
    bytes < 1024L * 1024       -> "%.1f КБ".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024))
    else                       -> "%.2f ГБ".format(bytes / (1024.0 * 1024 * 1024))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vpnState: VpnState,
    servers: List<Server>,
    selectedServer: Server?,
    connectionTime: String,
    downloadBytes: Long = 0L,
    uploadBytes: Long = 0L,
    isDark: Boolean = false,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onServerSelect: (Server) -> Unit,
    onServerAdded: (String) -> Unit,
    onDeleteServer: (Server) -> Unit,
    onSettingsClick: () -> Unit
) {
    val glassColor  = if (isDark) Color(0xCC0D2137) else Color(0xBBFFFFFF)
    val glassBorder = if (isDark) Color(0x44FFFFFF)  else Color(0x66FFFFFF)
    val labelColor  = if (isDark) Color(0xFF90A4AE)  else TextSecondary
    val titleColor  = if (isDark) Color.White         else TextPrimary
    var showAddSheet by remember { mutableStateOf(false) }
    var longPressServer by remember { mutableStateOf<Server?>(null) }

    val isConnected  = vpnState == VpnState.CONNECTED
    val isConnecting = vpnState == VpnState.CONNECTING
    val statusText   = when (vpnState) {
        VpnState.CONNECTED    -> "Защищено"
        VpnState.CONNECTING   -> "Подключение..."
        VpnState.DISCONNECTED -> "Не защищено"
        VpnState.ERROR        -> "Нет соединения"
    }
    val statusColor = when (vpnState) {
        VpnState.CONNECTED    -> ConnectedGreen
        VpnState.CONNECTING   -> ConnectingYellow
        VpnState.DISCONNECTED -> TextSecondary
        VpnState.ERROR        -> DisconnectedRed
    }

    Box(modifier = Modifier.fillMaxSize()) {

        AnimatedBackground(modifier = Modifier.fillMaxSize(), isDark = isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, null, tint = Color.White,
                        modifier = Modifier.size(24.dp))
                }
                Text(
                    "GoonVPN",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showAddSheet = true }) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Color.White,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Central area — status + button ────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(targetState = statusText, label = "st") { text ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(glassColor)
                            .border(1.dp, glassBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.Shield, null,
                            tint = statusColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text, color = statusColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold)
                            if (isConnected && connectionTime.isNotBlank()) {
                                Text(connectionTime, color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                DartboardButton(
                    vpnState = vpnState,
                    isDark   = isDark,
                    onClick = {
                        if (isConnected) onDisconnect()
                        else if (!isConnecting) onConnect()
                    }
                )
            }

            // ── Bottom info cards ─────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Selected server card (long-press for context menu)
                GlassCard(
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = {},
                        onLongClick = { selectedServer?.let { longPressServer = it } }
                    ),
                    bg = glassColor, border = glassBorder
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedServer?.flag ?: "🌐", fontSize = 28.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Выбранный сервер",
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor
                            )
                            Text(
                                selectedServer?.let { "${it.country}, ${it.name}" } ?: "Нет серверов — нажмите +",
                                style = MaterialTheme.typography.titleMedium,
                                color = titleColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (selectedServer != null) {
                                Text(selectedServer.protocol,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentBlue)
                            }
                        }
                        SignalBars(pingMs = selectedServer?.pingMs ?: -1, connected = isConnected)
                    }
                }

                // Stats row — показываем реальный трафик от HEV-socks5-tunnel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassCard(modifier = Modifier.weight(1f), bg = glassColor, border = glassBorder) {
                        Text("↓ Получено", style = MaterialTheme.typography.labelSmall,
                            color = labelColor)
                        Text(
                            if (isConnected) formatBytes(downloadBytes) else "0 МБ",
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("↑ ${if (isConnected) formatBytes(uploadBytes) else "0 МБ"}",
                            style = MaterialTheme.typography.labelSmall, color = labelColor)
                        Spacer(Modifier.height(2.dp))
                        MiniWaveLine(active = isConnected, color = AccentBlue)
                    }
                    GlassCard(modifier = Modifier.weight(1f), bg = glassColor, border = glassBorder) {
                        Text("Протокол", style = MaterialTheme.typography.labelSmall,
                            color = labelColor)
                        Text("VLESS",
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Авто-реконнект",
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.size(8.dp).clip(RoundedCornerShape(50))
                                    .background(ConnectedGreen)
                            )
                        }
                    }
                }

                // Quick select — переключает на следующий сервер
                if (servers.size > 1) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val currentIndex = servers.indexOfFirst { it.id == selectedServer?.id }
                            val next = servers[(currentIndex + 1) % servers.size]
                            onServerSelect(next)
                        },
                        bg = glassColor, border = glassBorder
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, null,
                                tint = ConnectingYellow, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Быстрый выбор  (${servers.size} серверов)", color = titleColor,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = if (isDark) Color(0xFF546E7A) else TextHint)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }

    // Long-press: управление сервером
    longPressServer?.let { server ->
        ModalBottomSheet(
            onDismissRequest = { longPressServer = null },
            containerColor = Surface
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp))
                Text(server.address, style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary, modifier = Modifier.padding(bottom = 16.dp))

                ServerActionItem(Icons.Filled.Speed, "Пинг", TextPrimary) {
                    longPressServer = null
                }
                ServerActionItem(Icons.Filled.CheckCircle, "Выбрать", AccentBlue) {
                    onServerSelect(server)
                    longPressServer = null
                }
                ServerActionItem(Icons.Filled.Delete, "Удалить", DisconnectedRed) {
                    onDeleteServer(server)
                    longPressServer = null
                }
            }
        }
    }

    if (showAddSheet) {
        AddServerBottomSheet(
            onDismiss = { showAddSheet = false },
            onUrlAdded = { onServerAdded(it); showAddSheet = false },
            onMultipleUrlsAdded = { urls ->
                urls.forEach { onServerAdded(it) }
                showAddSheet = false
            }
        )
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    bg: Color = Color(0xBBFFFFFF),
    border: Color = Color(0x66FFFFFF),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
private fun SignalBars(pingMs: Int, connected: Boolean) {
    val color = when {
        !connected   -> TextHint
        pingMs < 0   -> TextHint
        pingMs < 80  -> ConnectedGreen
        pingMs < 200 -> ConnectingYellow
        else         -> DisconnectedRed
    }
    Row(verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(6, 10, 14, 18).forEachIndexed { i, height ->
            val filled = connected && pingMs >= 0 && when (i) {
                0 -> true
                1 -> pingMs < 300
                2 -> pingMs < 150
                3 -> pingMs < 80
                else -> false
            }
            Box(
                Modifier.width(4.dp).height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (filled) color else TextHint.copy(0.3f))
            )
        }
    }
}

@Composable
private fun MiniWaveLine(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1200), RepeatMode.Restart), "wph")
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(24.dp)
    ) {
        if (!active) {
            drawLine(color.copy(0.3f),
                androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 1.5f)
            return@Canvas
        }
        val path = androidx.compose.ui.graphics.Path()
        val pts = 40
        for (i in 0..pts) {
            val x = size.width * i / pts
            val y = size.height / 2 + size.height * 0.38f *
                    sin((i.toFloat() / pts * 2 * PI.toFloat()) + phase * 2 * PI.toFloat()).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
private fun ServerActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, color: Color, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = color,
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
