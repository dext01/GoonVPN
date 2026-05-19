package com.goonvpn.app.ui.components

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goonvpn.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AddMode { MENU, MANUAL, SUBSCRIPTION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerBottomSheet(
    onDismiss: () -> Unit,
    onUrlAdded: (String) -> Unit,
    onMultipleUrlsAdded: (List<String>) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AddMode.MENU) }
    var textInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainerHigh,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.outline)
            )
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                if (mode != AddMode.MENU) {
                    IconButton(onClick = { mode = AddMode.MENU; textInput = ""; errorMsg = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    when (mode) {
                        AddMode.MENU         -> "Добавить сервер"
                        AddMode.MANUAL       -> "Ручной ввод"
                        AddMode.SUBSCRIPTION -> "Подписка"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            when (mode) {
                AddMode.MENU -> {
                    BottomSheetOption(Icons.Filled.ContentPaste, "Вставить из буфера", "Ссылка vless:// из буфера обмена") {
                        val text = clipboard.getText()?.text ?: ""
                        if (text.startsWith("vless://")) {
                            onUrlAdded(text)
                            onDismiss()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BottomSheetOption(Icons.Filled.Edit, "Ручной ввод", "Вставить ссылку vless:// вручную") {
                        mode = AddMode.MANUAL
                    }
                    Spacer(Modifier.height(8.dp))
                    BottomSheetOption(Icons.Filled.Link, "Добавить подписку", "URL-подписка — список серверов") {
                        mode = AddMode.SUBSCRIPTION
                    }
                }

                AddMode.MANUAL -> {
                    Text("Ссылка vless://", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp))
                    UrlTextField(value = textInput, onValueChange = { textInput = it },
                        placeholder = "vless://uuid@host:port?...", maxLines = 4)
                    Spacer(Modifier.height(12.dp))
                    ActionButtons(
                        onBack = { mode = AddMode.MENU; textInput = "" },
                        onConfirm = {
                            if (textInput.startsWith("vless://")) {
                                onUrlAdded(textInput)
                                onDismiss()
                            }
                        },
                        confirmEnabled = textInput.startsWith("vless://"),
                        confirmLabel = "Добавить"
                    )
                }

                AddMode.SUBSCRIPTION -> {
                    Text("URL подписки", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp))
                    UrlTextField(value = textInput, onValueChange = { textInput = it; errorMsg = null },
                        placeholder = "https://example.com/sub?token=...", maxLines = 2)
                    Spacer(Modifier.height(8.dp))

                    // Объяснение: сервер возвращает base64-кодированный список vless:// ссылок
                    Text(
                        "Сервер вернёт список серверов в формате Base64 или текст с vless:// ссылками",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    errorMsg?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = DisconnectedRed, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentBlue)
                        }
                    } else {
                        ActionButtons(
                            onBack = { mode = AddMode.MENU; textInput = "" },
                            onConfirm = {
                                scope.launch {
                                    isLoading = true
                                    errorMsg = null
                                    val urls = withContext(Dispatchers.IO) {
                                        fetchSubscription(textInput)
                                    }
                                    isLoading = false
                                    if (urls.isEmpty()) {
                                        errorMsg = "Серверы не найдены или ошибка загрузки"
                                    } else {
                                        onMultipleUrlsAdded(urls)
                                        onDismiss()
                                    }
                                }
                            },
                            confirmEnabled = textInput.startsWith("http"),
                            confirmLabel = "Загрузить"
                        )
                    }
                }
            }
        }
    }
}

// Загружаем подписку: поддерживаем Base64 (стандарт большинства провайдеров) и plain-text
private fun fetchSubscription(url: String): List<String> {
    return try {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "GoonVPN/1.0 (Android)")
        val raw = conn.inputStream.bufferedReader().readText().trim()
        conn.disconnect()

        val text = try {
            String(Base64.decode(raw, Base64.DEFAULT))
        } catch (_: Exception) { raw }

        text.lines().map { it.trim() }.filter { it.startsWith("vless://") }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun UrlTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, maxLines: Int) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = cs.onSurfaceVariant.copy(0.5f), style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlue, unfocusedBorderColor = cs.outline,
            focusedTextColor = cs.onBackground, unfocusedTextColor = cs.onBackground,
            cursorColor = AccentBlue,
            focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer
        ),
        maxLines = maxLines
    )
}

@Composable
private fun ActionButtons(onBack: () -> Unit, onConfirm: () -> Unit, confirmEnabled: Boolean, confirmLabel: String) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onBack, modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline)
        ) { Text("Назад") }
        Button(
            onClick = onConfirm, modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            enabled = confirmEnabled
        ) { Text(confirmLabel, color = Color.White) }
    }
}

@Composable
private fun BottomSheetOption(
    icon: ImageVector, title: String, subtitle: String,
    badge: String? = null, accentColor: Color = AccentBlue,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) {
            Text(badge, style = MaterialTheme.typography.labelMedium, color = accentColor,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accentColor.copy(0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp))
        } else {
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(20.dp))
        }
    }
}
