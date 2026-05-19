package com.goonvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.goonvpn.app.data.SettingsRepository
import com.goonvpn.app.model.Server
import com.goonvpn.app.ui.components.AddServerBottomSheet
import com.goonvpn.app.ui.theme.*

// Режим маршрутизации: глобальный (весь трафик через VPN) или раздельный (только зарубежный)
enum class RoutingMode(val label: String, val description: String) {
    GLOBAL("Глобальный", "Весь трафик через VPN"),
    SPLIT("Раздельный", "Только зарубежный трафик через VPN")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    servers: List<Server>,
    selectedServer: Server?,
    @Suppress("UNUSED_PARAMETER") isDark: Boolean = false,
    onServerSelect: (Server) -> Unit,
    onServerAdded: (String) -> Unit,
    onMultipleServersAdded: (List<String>) -> Unit,
    onDeleteServer: (Server) -> Unit,
    onPingAll: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    val cs      = MaterialTheme.colorScheme
    val bg      = cs.background
    val cardBg  = cs.surfaceContainer
    val textPri = cs.onBackground
    val textSec = cs.onSurfaceVariant
    val accent  = AccentBlue

    var showAddSheet by remember { mutableStateOf(false) }
    var routingMode by remember {
        mutableStateOf(
            RoutingMode.entries.find { it.name == settings.routingMode } ?: RoutingMode.GLOBAL
        )
    }
    var showRoutingDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Прокси", style = MaterialTheme.typography.headlineMedium,
                    color = textPri, fontWeight = FontWeight.Bold)
                Text("${servers.size} серверов", style = MaterialTheme.typography.bodySmall,
                    color = textSec)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Пинг всех серверов
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onPingAll)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Speed, null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    Text("Пинг", style = MaterialTheme.typography.labelSmall, color = accent)
                }
                // Добавить сервер
                IconButton(onClick = { showAddSheet = true }) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Режим маршрутизации
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .clickable { showRoutingDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.AltRoute, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Маршрутизация", style = MaterialTheme.typography.labelMedium, color = textSec)
                Text(routingMode.label, style = MaterialTheme.typography.bodyLarge,
                    color = textPri, fontWeight = FontWeight.Medium)
                Text(routingMode.description, style = MaterialTheme.typography.bodySmall, color = textSec)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = textSec)
        }

        Spacer(Modifier.height(16.dp))

        if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CloudOff, null, tint = textSec, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Нет серверов", color = textSec, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Нажмите + чтобы добавить", color = textSec.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                    ServerRow(
                        server = server,
                        isSelected = server.id == selectedServer?.id,
                        isDark = isDark,
                        onSelect = { onServerSelect(server) },
                        onDelete = { onDeleteServer(server) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    // Диалог выбора маршрутизации
    if (showRoutingDialog) {
        AlertDialog(
            onDismissRequest = { showRoutingDialog = false },
            title = { Text("Режим маршрутизации") },
            text = {
                Column {
                    RoutingMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                routingMode = mode
                                settings.routingMode = mode.name
                                showRoutingDialog = false
                            }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = routingMode == mode,
                                onClick = {
                                routingMode = mode
                                settings.routingMode = mode.name
                                showRoutingDialog = false
                            },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentBlue)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(mode.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showAddSheet) {
        AddServerBottomSheet(
            onDismiss = { showAddSheet = false },
            onUrlAdded = { onServerAdded(it); showAddSheet = false },
            onMultipleUrlsAdded = { urls -> onMultipleServersAdded(urls); showAddSheet = false }
        )
    }
}

@Composable
private fun ServerRow(
    server: Server,
    isSelected: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (isSelected) AccentBlueDim else MaterialTheme.colorScheme.surfaceContainer
    val nameColor = if (isSelected || isDark) TextPrimary else androidx.compose.ui.graphics.Color(0xFF0D1117)
    val subColor  = if (isSelected || isDark) TextSecondary else androidx.compose.ui.graphics.Color(0xFF5A6578)
    val pingColor = when {
        server.pingMs < 0   -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        server.pingMs < 80  -> ConnectedGreen
        server.pingMs < 200 -> ConnectingYellow
        else                -> DisconnectedRed
    }
    val pingText = if (server.pingMs < 0) "— мс" else "${server.pingMs} мс"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(server.flag, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(server.name, style = MaterialTheme.typography.titleMedium,
                color = nameColor, fontWeight = FontWeight.SemiBold)
            Text("${server.address}:${server.port}", style = MaterialTheme.typography.bodySmall,
                color = subColor)
            Text(server.protocol, style = MaterialTheme.typography.labelSmall, color = AccentBlue)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(pingText, style = MaterialTheme.typography.labelSmall, color = pingColor,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, null, tint = ConnectedGreen,
                    modifier = Modifier.size(18.dp))
            } else {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
