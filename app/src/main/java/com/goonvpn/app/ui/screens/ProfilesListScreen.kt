package com.goonvpn.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goonvpn.app.ui.theme.*

@Composable
fun ProfilesListScreen(
    vlessUrl: String,
    isDark: Boolean = false,
    onEditProfile: () -> Unit
) {
    val bg      = if (isDark) androidx.compose.ui.graphics.Color(0xFF06111C) else Background
    val textPri = if (isDark) androidx.compose.ui.graphics.Color.White else TextPrimary
    val textSec = if (isDark) androidx.compose.ui.graphics.Color(0xFF90A4AE) else TextSecondary
    val textHnt = if (isDark) androidx.compose.ui.graphics.Color(0xFF546E7A) else TextHint
    val cardBg  = if (isDark) androidx.compose.ui.graphics.Color(0xFF0D2137) else CardBackgroundSelected
    val accent  = if (isDark) androidx.compose.ui.graphics.Color(0xFF90CAF9) else AccentBlue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Профили", style = MaterialTheme.typography.headlineMedium,
                color = textPri, fontWeight = FontWeight.Bold)
            IconButton(onClick = onEditProfile) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (vlessUrl.isNotBlank()) {
            val hostPart = vlessUrl.substringAfter("@").substringBefore("?").substringBefore("#")
            val namePart = vlessUrl.substringAfterLast("#", "Сервер").let {
                try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .clickable(onClick = onEditProfile)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = ConnectedGreen, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(namePart, color = textPri, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(hostPart, color = textSec, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = textHnt)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нет профилей", color = textSec, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Нажми + чтобы добавить", color = textHnt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
