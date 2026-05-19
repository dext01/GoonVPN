package com.goonvpn.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goonvpn.app.data.SettingsRepository
import com.goonvpn.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val cs = MaterialTheme.colorScheme

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var disallowed by remember { mutableStateOf(settings.disallowedApps) }
    var showSystem by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadInstalledApps(context.packageManager) }
        loading = false
    }

    val filtered = apps
        .filter { showSystem || !it.isSystem }
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<AppEntry> { it.packageName in disallowed }.thenBy { it.label.lowercase() })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад",
                    tint = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Приложения вне VPN",
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Трафик этих приложений пойдёт напрямую",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск", color = cs.onSurfaceVariant.copy(alpha = 0.5f)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = cs.outline,
                focusedTextColor = cs.onBackground,
                unfocusedTextColor = cs.onBackground,
                cursorColor = AccentBlue,
                focusedContainerColor = cs.surfaceContainer,
                unfocusedContainerColor = cs.surfaceContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Показать системные",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showSystem,
                onCheckedChange = { showSystem = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }

        if (disallowed.isNotEmpty()) {
            Text(
                text = "Выбрано: ${disallowed.size}. Изменения применятся при следующем подключении.",
                style = MaterialTheme.typography.bodySmall,
                color = AccentBlue,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val isDisallowed = app.packageName in disallowed
                    AppRow(
                        app = app,
                        isDisallowed = isDisallowed,
                        onToggle = {
                            disallowed = if (isDisallowed) disallowed - app.packageName
                                        else disallowed + app.packageName
                            settings.disallowedApps = disallowed
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, isDisallowed: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDisallowed) AccentBlueDim else cs.surfaceContainer)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(bitmap = app.icon.asImageBitmap(), contentDescription = null,
                modifier = Modifier.size(36.dp))
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge,
                color = if (isDisallowed) androidx.compose.ui.graphics.Color.White else cs.onBackground,
                maxLines = 1)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall,
                color = if (isDisallowed) androidx.compose.ui.graphics.Color.White.copy(0.7f) else cs.onSurfaceVariant.copy(0.6f),
                maxLines = 1)
        }
        Checkbox(
            checked = isDisallowed,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AccentBlue,
                uncheckedColor = cs.outline
            )
        )
    }
}

private fun loadInstalledApps(pm: PackageManager): List<AppEntry> {
    val flags = PackageManager.GET_META_DATA
    val all = pm.getInstalledApplications(flags)
    return all.mapNotNull { info ->
        val label = pm.getApplicationLabel(info).toString()
        val icon = try { pm.getApplicationIcon(info).toBitmap() } catch (e: Exception) { null }
        AppEntry(
            packageName = info.packageName,
            label = label,
            icon = icon,
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        )
    }
}

private fun Drawable.toBitmap(size: Int = 96): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = if (intrinsicWidth <= 0) size else intrinsicWidth
    val h = if (intrinsicHeight <= 0) size else intrinsicHeight
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
