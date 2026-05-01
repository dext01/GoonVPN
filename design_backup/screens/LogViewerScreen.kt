package com.goonvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goonvpn.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("Загрузка...") }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        logText = withContext(Dispatchers.IO) { readLogs(context.filesDir) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextSecondary)
            }
            Text(
                "Логи",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Filled.Refresh, null, tint = AccentBlue)
            }
            IconButton(onClick = {
                // Очистить лог-файлы
                File(context.filesDir, "hev.log").delete()
                refreshTrigger++
            }) {
                Icon(Icons.Filled.Delete, null, tint = DisconnectedRed)
            }
        }

        Text(
            "Лог HEV-socks5-tunnel и конфиг Xray. Обновляется при нажатии ↻",
            style = MaterialTheme.typography.bodySmall,
            color = TextHint,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // Log content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(CardBackground, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}

private fun readLogs(filesDir: File): String {
    val sb = StringBuilder()

    val hevLog = File(filesDir, "hev.log")
    if (hevLog.exists() && hevLog.length() > 0) {
        sb.append("════════ HEV Socks5 Tunnel ════════\n")
        // Берём последние 6000 символов чтобы не перегружать UI
        val content = hevLog.readText()
        sb.append(if (content.length > 6000) "...(обрезано)...\n" + content.takeLast(6000) else content)
        sb.append("\n")
    }

    val xrayCfg = File(filesDir, "xray_config.json")
    if (xrayCfg.exists()) {
        sb.append("════════ Xray Config ════════\n")
        sb.append(xrayCfg.readText())
        sb.append("\n")
    }

    val hevCfg = File(filesDir, "hev_config.yml")
    if (hevCfg.exists()) {
        sb.append("════════ HEV Config ════════\n")
        sb.append(hevCfg.readText())
    }

    return if (sb.isEmpty()) {
        "Логи пусты.\n\nПодключитесь к VPN — после этого здесь появятся:\n" +
        "• Лог HEV-socks5-tunnel (туннель TUN→SOCKS5)\n" +
        "• Конфиг Xray (VLESS/Reality)\n" +
        "• Конфиг HEV-туннеля"
    } else sb.toString()
}
