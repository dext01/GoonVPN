package com.goonvpn.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.goonvpn.app.ui.theme.*
import com.goonvpn.app.viewmodel.VpnState

@Composable
fun ShieldAnimation(
    state: VpnState,
    modifier: Modifier = Modifier
) {
    val shieldColor = when (state) {
        VpnState.CONNECTED -> ConnectedGreen
        VpnState.CONNECTING -> ConnectingYellow
        VpnState.ERROR -> DisconnectedRed
        VpnState.DISCONNECTED -> TextHint
    }

    val infiniteTransition = rememberInfiniteTransition(label = "shield")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VpnState.CONNECTING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    val ringRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_radius"
    )

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        if (state == VpnState.CONNECTED) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val maxRadius = size.minDimension / 2f
                for (i in 0..1) {
                    val offset = i * 0.5f
                    val r = ((ringRadius + offset) % 1f) * maxRadius
                    val alpha = (1f - ((ringRadius + offset) % 1f)) * 0.4f
                    drawCircle(
                        color = ConnectedGreen.copy(alpha = alpha),
                        radius = r,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = "Shield",
            tint = shieldColor,
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
        )
    }
}
