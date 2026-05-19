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

    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = cs.onSurfaceVariant)
            }
            Text(
                "Логи",
                style = MaterialTheme.typography.titleLarge,
                color = cs.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Filled.Refresh, null, tint = AccentBlue)
            }
            IconButton(onClick = {
                File(context.filesDir, "hev.log").delete()
                File(context.filesDir, "xray.log").delete()
                refreshTrigger++
            }) {
                Icon(Icons.Filled.Delete, null, tint = DisconnectedRed)
            }
        }

        Text(
            "Логи HEV-tunnel и Xray, конфиги. Обновляется при нажатии ↻",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // Log content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(cs.surfaceContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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
                    color = cs.onBackground
                )
            }
        }
    }
}

private fun readLogs(filesDir: File): String {
    val sb = StringBuilder()

    fun appendFile(title: String, file: File, maxChars: Int = 6000) {
        if (!file.exists() || file.length() == 0L) return
        val content = file.readText()
        sb.append("════════ $title ════════\n")
        sb.append(if (content.length > maxChars) "...(обрезано)...\n" + content.takeLast(maxChars) else content)
        sb.append("\n\n")
    }

    appendFile("Xray Runtime Log", File(filesDir, "xray.log"))
    appendFile("HEV Socks5 Tunnel", File(filesDir, "hev.log"))
    appendFile("Xray Config", File(filesDir, "xray_config.json"), maxChars = 4000)
    appendFile("HEV Config", File(filesDir, "hev_config.yml"), maxChars = 2000)

    return if (sb.isEmpty()) {
        "Логи пусты.\n\nПодключитесь к VPN — после этого здесь появятся:\n" +
        "• Xray Runtime Log — вывод ядра Xray\n" +
        "• HEV Socks5 Tunnel — лог туннеля TUN→SOCKS5\n" +
        "• Xray Config — сгенерированный конфиг VLESS/Reality\n" +
        "• HEV Config — конфиг HEV-туннеля"
    } else sb.toString()
}

// viewer: show xray + hev logs
