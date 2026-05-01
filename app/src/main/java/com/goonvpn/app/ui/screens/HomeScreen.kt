package com.goonvpn.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goonvpn.app.model.Server
import com.goonvpn.app.ui.components.AddServerBottomSheet
import com.goonvpn.app.ui.theme.*
import com.goonvpn.app.viewmodel.VpnState

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L               -> "$bytes Б"
    bytes < 1024L * 1024        -> "%.1f КБ".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024))
    else                        -> "%.2f ГБ".format(bytes / (1024.0 * 1024 * 1024))
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
    @Suppress("UNUSED_PARAMETER") isDark: Boolean = true,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onServerSelect: (Server) -> Unit,
    onServerAdded: (String) -> Unit,
    onDeleteServer: (Server) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showAddSheet    by remember { mutableStateOf(false) }
    var longPressServer by remember { mutableStateOf<Server?>(null) }

    val isConnected  = vpnState == VpnState.CONNECTED
    val isConnecting = vpnState == VpnState.CONNECTING

    val statusText = when (vpnState) {
        VpnState.CONNECTED    -> "Защищено"
        VpnState.CONNECTING   -> "Подключение..."
        VpnState.DISCONNECTED -> "Не защищено"
        VpnState.ERROR        -> "Ошибка соединения"
    }
    val stateColor = when (vpnState) {
        VpnState.CONNECTED    -> ConnectedGreen
        VpnState.CONNECTING   -> ConnectingYellow
        VpnState.DISCONNECTED -> TextHint
        VpnState.ERROR        -> DisconnectedRed
    }

    // Пульс при подключении
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "ps"
    )
    val ringAlpha by pulseAnim.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing), RepeatMode.Restart
        ), label = "ra"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Фоновое свечение под кнопкой — Canvas с радиальным градиентом (без прямоугольника)
        if (isConnected) {
            Canvas(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 80.dp)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ConnectedGreen.copy(alpha = 0.18f),
                            ConnectedGreen.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        radius = size.minDimension / 2
                    )
                )
            }
        }

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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "GoonVPN",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                    ) {
                        Icon(Icons.Filled.Settings, null,
                            tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { showAddSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentBlue)
                    ) {
                        Icon(Icons.Filled.Add, null,
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Центральная зона — кнопка ─────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Кнопка подключения
                    Box(contentAlignment = Alignment.Center) {
                        // Расширяющееся кольцо при подключении
                        if (isConnecting) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .border(
                                        1.dp,
                                        ConnectingYellow.copy(ringAlpha),
                                        CircleShape
                                    )
                            )
                        }
                        // Внешнее кольцо
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isConnected) 2.dp else 1.dp,
                                    color = stateColor.copy(if (isConnected) 0.8f else 0.3f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Внутренняя кнопка
                            Box(
                                modifier = Modifier
                                    .size(148.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnected)
                                            Brush.radialGradient(
                                                listOf(
                                                    ConnectedGreen.copy(0.15f),
                                                    SurfaceCard
                                                )
                                            )
                                        else
                                            Brush.radialGradient(
                                                listOf(SurfaceElevated, SurfaceCard)
                                            )
                                    )
                                    .border(1.dp, BorderColor, CircleShape)
                                    .clickable {
                                        if (isConnected) onDisconnect()
                                        else if (!isConnecting) onConnect()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PowerSettingsNew,
                                    contentDescription = if (isConnected) "Отключить" else "Подключить",
                                    tint = if (isConnected) ConnectedGreen
                                           else if (isConnecting) ConnectingYellow
                                           else TextSecondary,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }
                    }

                    // Статус
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(targetState = statusText, label = "st") { text ->
                            Text(
                                text,
                                style = MaterialTheme.typography.titleMedium,
                                color = stateColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (isConnected && connectionTime.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                connectionTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Нижний блок ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Трафик
                if (isConnected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TrafficCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.ArrowDownward,
                            label = "Получено",
                            value = formatBytes(downloadBytes),
                            color = AccentBlue
                        )
                        TrafficCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.ArrowUpward,
                            label = "Отправлено",
                            value = formatBytes(uploadBytes),
                            color = AccentBlue
                        )
                    }
                }

                // Карточка сервера
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { selectedServer?.let { longPressServer = it } }
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedServer?.flag ?: "🌐",
                        fontSize = 26.sp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Активный сервер",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextHint
                        )
                        Text(
                            selectedServer?.let { "${it.country} · ${it.name}" }
                                ?: "Нет серверов — нажмите +",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (selectedServer != null) {
                        PingBadge(pingMs = selectedServer.pingMs, connected = isConnected)
                    }
                }

                // Быстрый переключатель серверов
                if (servers.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            .clickable {
                                val idx = servers.indexOfFirst { it.id == selectedServer?.id }
                                onServerSelect(servers[(idx + 1) % servers.size])
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.SwapHoriz, null,
                            tint = AccentBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Следующий сервер  (${servers.size} доступно)",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ChevronRight, null,
                            tint = TextHint, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Long-press меню сервера
    longPressServer?.let { server ->
        ModalBottomSheet(
            onDismissRequest = { longPressServer = null },
            containerColor = SurfaceElevated,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)) {
                Text(server.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold)
                Text(server.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 20.dp))

                ServerAction(Icons.Filled.CheckCircle, "Выбрать", AccentBlue) {
                    onServerSelect(server); longPressServer = null
                }
                ServerAction(Icons.Filled.Delete, "Удалить", DisconnectedRed) {
                    onDeleteServer(server); longPressServer = null
                }
            }
        }
    }

    if (showAddSheet) {
        AddServerBottomSheet(
            onDismiss          = { showAddSheet = false },
            onUrlAdded         = { onServerAdded(it); showAddSheet = false },
            onMultipleUrlsAdded = { urls -> urls.forEach { onServerAdded(it) }; showAddSheet = false }
        )
    }
}

@Composable
private fun TrafficCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextHint)
            Text(value, style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PingBadge(pingMs: Int, connected: Boolean) {
    val (color, text) = when {
        !connected || pingMs < 0 -> Pair(TextHint, "—")
        pingMs < 80  -> Pair(ConnectedGreen, "${pingMs}мс")
        pingMs < 200 -> Pair(ConnectingYellow, "${pingMs}мс")
        else         -> Pair(DisconnectedRed, "${pingMs}мс")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ServerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = color,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium)
    }
}
