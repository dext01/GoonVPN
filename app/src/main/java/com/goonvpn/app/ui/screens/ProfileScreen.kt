package com.goonvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goonvpn.app.vpn.XrayConfig
import com.goonvpn.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vlessUrl: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    val params = remember(vlessUrl) {
        if (vlessUrl.startsWith("vless://")) XrayConfig.parseVlessUrl(vlessUrl) else null
    }

    var name       by remember { mutableStateOf(params?.serverName?.ifBlank { "Мой сервер" } ?: "Новый сервер") }
    var host       by remember { mutableStateOf(params?.host ?: "") }
    var port       by remember { mutableStateOf(params?.port?.toString() ?: "443") }
    var uuid       by remember { mutableStateOf(params?.userId ?: "") }
    var flow       by remember { mutableStateOf(params?.flow ?: "") }
    var security   by remember { mutableStateOf(params?.security ?: "reality") }
    var network    by remember { mutableStateOf(params?.network ?: "tcp") }
    var httpHost   by remember { mutableStateOf("") }
    var path       by remember { mutableStateOf(params?.spiderX ?: "") }
    var sni        by remember { mutableStateOf(params?.serverName ?: "") }
    var fingerprint by remember { mutableStateOf(params?.fingerprint ?: "chrome") }
    var alpn       by remember { mutableStateOf("") }
    var publicKey  by remember { mutableStateOf(params?.publicKey ?: "") }
    var shortId    by remember { mutableStateOf(params?.shortId ?: "") }
    var spiderX    by remember { mutableStateOf(params?.spiderX ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TextSecondary)
            }
            Text("Профиль", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                // Reconstruct URL from fields
                val rebuilt = buildVlessUrl(uuid, host, port.toIntOrNull() ?: 443,
                    flow, security, network, sni, fingerprint, alpn, publicKey, shortId, spiderX, name)
                onSave(rebuilt)
                onBack()
            }) {
                Text("Сохранить", color = AccentBlue, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── ОСНОВНЫЕ ──────────────────────────────────────────────────────
        ProfileSectionLabel("НАСТРОЙКИ ПРОТОКОЛА")
        ProfileCard {
            ProfileField("Имя", name) { name = it }
            ProfileField("Адрес сервера", host) { host = it }
            ProfileField("Порт", port) { port = it }
            ProfileField("UUID", uuid, mono = true) { uuid = it }
            ProfileDropdownField("Поток (Flow)", flow, listOf("", "xtls-rprx-vision")) { flow = it }
            ProfileDropdownField("Безопасность", security, listOf("reality", "tls", "none")) { security = it }
        }

        Spacer(Modifier.height(16.dp))

        ProfileSectionLabel("ДРУГИЕ ПАРАМЕТРЫ")
        ProfileCard {
            ProfileDropdownField("Сеть", network, listOf("tcp", "ws", "grpc", "httpupgrade")) { network = it }
            ProfileField("HTTP Host", httpHost) { httpHost = it }
            ProfileField("Path", path) { path = it }
        }

        Spacer(Modifier.height(16.dp))

        ProfileSectionLabel("НАСТРОЙКИ TLS / REALITY")
        ProfileCard {
            ProfileDropdownField("TLS", security, listOf("reality", "tls", "none")) { security = it }
            ProfileField("SNI", sni) { sni = it }
            ProfileDropdownField("Fingerprint", fingerprint,
                listOf("chrome", "firefox", "safari", "ios", "android", "edge", "qq", "random")) { fingerprint = it }
            ProfileField("ALPN", alpn) { alpn = it }
            ProfileField("PublicKey", publicKey, mono = true) { publicKey = it }
            ProfileField("ShortId", shortId, mono = true) { shortId = it }
            ProfileField("SpiderX", spiderX) { spiderX = it }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = AccentBlue,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
}

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content
    )
}

@Composable
private fun ProfileField(label: String, value: String, mono: Boolean = false, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        BasicEditText(value = value, onValueChange = onValueChange, mono = mono, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
}

@Composable
private fun ProfileDropdownField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    value.ifBlank { "—" },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.background(Surface)) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.ifBlank { "—" }, color = TextPrimary) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
}

@Composable
private fun BasicEditText(value: String, onValueChange: (String) -> Unit, mono: Boolean, modifier: Modifier) {
    val style = if (mono)
        MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    else
        MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        modifier = modifier,
        singleLine = true,
        decorationBox = { inner ->
            if (value.isEmpty()) Text("—", color = TextHint, style = style)
            inner()
        }
    )
}

@Suppress("DEPRECATION")
@Composable
private fun BasicTextField(
    value: String, onValueChange: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle, modifier: Modifier,
    singleLine: Boolean,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onValueChange,
        textStyle = textStyle, modifier = modifier,
        singleLine = singleLine, decorationBox = decorationBox
    )
}

private fun buildVlessUrl(
    uuid: String, host: String, port: Int, flow: String, security: String,
    network: String, sni: String, fp: String, alpn: String, pbk: String, sid: String,
    spx: String, name: String
): String {
    val sb = StringBuilder("vless://$uuid@$host:$port?")
    sb.append("type=$network")
    sb.append("&encryption=none")
    if (security.isNotEmpty()) sb.append("&security=$security")
    if (pbk.isNotEmpty())   sb.append("&pbk=$pbk")
    if (fp.isNotEmpty())    sb.append("&fp=$fp")
    if (sni.isNotEmpty())   sb.append("&sni=$sni")
    if (sid.isNotEmpty())   sb.append("&sid=$sid")
    if (spx.isNotEmpty())   sb.append("&spx=${java.net.URLEncoder.encode(spx, "UTF-8")}")
    if (flow.isNotEmpty())  sb.append("&flow=$flow")
    if (alpn.isNotEmpty())  sb.append("&alpn=$alpn")
    sb.append("#${java.net.URLEncoder.encode(name, "UTF-8")}")
    return sb.toString()
}
