package com.goonvpn.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.goonvpn.app.data.SettingsRepository
import com.goonvpn.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: Int = 0,
    isDark: Boolean = true,
    onThemeMode: (Int) -> Unit = {},
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }

    var autoReconnect   by remember { mutableStateOf(settings.autoReconnect) }
    var useMux          by remember { mutableStateOf(settings.useMux) }
    var useFragment     by remember { mutableStateOf(settings.useFragmentation) }
    var blockBindToTun  by remember { mutableStateOf(settings.blockBindToTun) }
    var allowLan        by remember { mutableStateOf(settings.allowLan) }
    var dnsChoice       by remember { mutableStateOf(settings.dnsChoice) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDnsDialog   by remember { mutableStateOf(false) }

    val bg                = if (isDark) Background    else Color(0xFFF2F5FA)
    val cardBg            = if (isDark) SurfaceCard   else Color(0xFFFFFFFF)
    val textPri           = if (isDark) TextPrimary   else Color(0xFF0D1117)
    val textSec           = if (isDark) TextSecondary else Color(0xFF5A6578)
    val border            = if (isDark) BorderColor   else Color(0xFFD0D9E8)
    val switchUnchecked   = if (isDark) Color(0xFF3A4A5E) else Color(0xFFCDD5E0)

    val dnsLabels = listOf("Google (8.8.8.8)", "Cloudflare (1.1.1.1)", "AdGuard (94.140.14.14)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = textSec)
            }
            Spacer(Modifier.width(8.dp))
            Text("Настройки", style = MaterialTheme.typography.headlineMedium, color = textPri)
        }

        // ── ИНТЕРФЕЙС ──────────────────────────────────────────────────────
        SectionLabel("ИНТЕРФЕЙС", textSec)
        SettingsCard(cardBg) {
            // Theme selector row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Palette, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Тема оформления", color = textPri,
                        style = MaterialTheme.typography.bodyMedium)
                }
                // 0 = Светлая, 1 = Тёмная
                val themes = listOf("Светлая", "Тёмная")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    themes.forEachIndexed { idx, label ->
                        val selected = themeMode == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AccentBlue else cardBg)
                                .clickable { onThemeMode(idx) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) Color.White else textSec,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── ТУННЕЛЬ ────────────────────────────────────────────────────────
        SectionLabel("ТУННЕЛЬ", textSec)
        SettingsCard(cardBg) {
            SettingItem(Icons.Filled.Apps, "Приложения вне VPN",
                if (settings.disallowedApps.isEmpty()) "Все приложения через VPN"
                else "${settings.disallowedApps.size} исключено",
                textPri = textPri,
                onClick = onOpenApps
            )
            HorizontalDivider(color = border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            SettingItem(Icons.Filled.Dns, "DNS-сервер",
                dnsLabels[dnsChoice.coerceIn(0, dnsLabels.lastIndex)],
                textPri = textPri
            ) { showDnsDialog = true }
            HorizontalDivider(color = border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            SettingToggle(Icons.AutoMirrored.Filled.CallSplit, "Фрагментирование",
                "Разбивать пакеты для обхода DPI",
                textPri = textPri, textSec = textSec, uncheckedTrackColor = switchUnchecked,
                checked = useFragment) { useFragment = it; settings.useFragmentation = it }
            HorizontalDivider(color = border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            SettingToggle(Icons.AutoMirrored.Filled.CompareArrows, "Использовать MUX",
                "Мультиплексирование — несколько потоков в одном TCP",
                textPri = textPri, textSec = textSec, uncheckedTrackColor = switchUnchecked,
                checked = useMux) { useMux = it; settings.useMux = it }
            HorizontalDivider(color = border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            SettingToggle(Icons.Filled.Lock, "Блок привязки к туннелю",
                "Запретить системным приложениям обходить VPN",
                textPri = textPri, textSec = textSec, uncheckedTrackColor = switchUnchecked,
                checked = blockBindToTun) { blockBindToTun = it; settings.blockBindToTun = it }
        }

        Spacer(Modifier.height(20.dp))

        // ── РАСШИРЕННЫЕ ────────────────────────────────────────────────────
        SectionLabel("РАСШИРЕННЫЕ", textSec)
        SettingsCard(cardBg) {
            SettingItem(Icons.Filled.Shield, "Always-on VPN", "Открыть системные настройки",
                textPri = textPri) {
                try { context.startActivity(Intent("android.net.vpn.SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            HorizontalDivider(color = border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
            SettingToggle(Icons.Filled.Refresh, "Авто-переподключение",
                "Восстановить VPN если убит процесс",
                textPri = textPri, textSec = textSec, uncheckedTrackColor = switchUnchecked,
                checked = autoReconnect) { autoReconnect = it; settings.autoReconnect = it }
            HorizontalDivider(color = border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
            SettingToggle(Icons.Filled.Wifi, "Разрешить подключения из LAN",
                "Открыть прокси для локальной сети",
                textPri = textPri, textSec = textSec, uncheckedTrackColor = switchUnchecked,
                checked = allowLan) { allowLan = it; settings.allowLan = it }
        }

        Spacer(Modifier.height(20.dp))

        // ── ДРУГИЕ ─────────────────────────────────────────────────────────
        SectionLabel("ДРУГИЕ", textSec)
        SettingsCard(cardBg) {
            SettingItem(Icons.AutoMirrored.Filled.Notes, "Логи", "Просмотр журнала HEV и Xray",
                textPri = textPri, onClick = onOpenLogs)
            HorizontalDivider(color = border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
            SettingItem(Icons.Filled.RestartAlt, "Сброс настроек", "Вернуть к заводским",
                textPri = textPri, color = DisconnectedRed) { showResetDialog = true }
        }

        Spacer(Modifier.height(20.dp))

        // ── ИНФОРМАЦИЯ ─────────────────────────────────────────────────────
        SectionLabel("ИНФОРМАЦИЯ", textSec)
        SettingsCard(cardBg) {
            InfoRow("Протокол", "VLESS + Reality", textPri, textSec)
            InfoRow("Ядро", "Xray-core 25.9.11", textPri, textSec)
            InfoRow("Туннель", "HEV-socks5-tunnel", textPri, textSec)
            InfoRow("Версия", "1.6.0", textPri, textSec)
        }

        Spacer(Modifier.height(24.dp))
    }

    // Диалог выбора DNS
    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = { Text("DNS-сервер") },
            text = {
                Column {
                    Text(
                        "DNS влияет на приватность и защиту от DNS-утечек. Cloudflare и AdGuard не передают данные Google.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    listOf("Google (8.8.8.8 / 8.8.4.4)", "Cloudflare (1.1.1.1 / 1.0.0.1)", "AdGuard (94.140.14.14 / 94.140.15.15)")
                        .forEachIndexed { idx, label ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { dnsChoice = idx; settings.dnsChoice = idx; showDnsDialog = false }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = dnsChoice == idx,
                                    onClick = { dnsChoice = idx; settings.dnsChoice = idx; showDnsDialog = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentBlue)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // Диалог сброса настроек
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить настройки?") },
            text = { Text("Все настройки и список серверов будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.resetAll()
                    showResetDialog = false
                }) {
                    Text("Сбросить", color = DisconnectedRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Отмена")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun SectionLabel(text: String, textSec: Color = TextSecondary) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = textSec,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
}

@Composable
private fun SettingsCard(
    background: Color = SurfaceCard,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textPri: Color = TextPrimary,
    color: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (color == TextPrimary) AccentBlue else color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (color == TextPrimary) textPri else color,
                style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextHint)
    }
}

@Composable
private fun SettingToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    textPri: Color = TextPrimary,
    textSec: Color = TextSecondary,
    uncheckedTrackColor: Color = Color(0xFF3A4A5E),
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textPri, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = textSec, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentBlue,
                uncheckedTrackColor = uncheckedTrackColor,
                uncheckedThumbColor = Color.White
            )
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, textPri: Color = TextPrimary, textSec: Color = TextSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = textSec, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = textPri, style = MaterialTheme.typography.bodyMedium)
    }
}
