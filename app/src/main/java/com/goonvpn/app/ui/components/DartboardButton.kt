package com.goonvpn.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.goonvpn.app.ui.theme.ConnectedGreen
import com.goonvpn.app.ui.theme.ConnectingYellow
import com.goonvpn.app.ui.theme.DisconnectedRed
import com.goonvpn.app.viewmodel.VpnState
import kotlin.math.cos
import kotlin.math.sin

// Day / Sun palette
private val S_CORE  = Color(0xFFFFFFFF)
private val S_IN1   = Color(0xFFFFFDE7)
private val S_IN2   = Color(0xFFFFF176)
private val S_MID   = Color(0xFFFFD54F)
private val S_OUT   = Color(0xFFFFB300)
private val S_RIM   = Color(0xFFFF8F00)
private val S_GLOW1 = Color(0x80FFD54F)
private val S_GLOW2 = Color(0x40FFB300)
private val S_GLOW3 = Color(0x18FF8F00)

// Night / Moon palette — deep indigo & cool blue
private val N_CORE  = Color(0xFFE8EEFF)   // near-white ice blue
private val N_IN1   = Color(0xFFB0C0F0)   // light periwinkle
private val N_IN2   = Color(0xFF6E88E0)   // medium blue
private val N_MID   = Color(0xFF3A56C8)   // royal blue
private val N_OUT   = Color(0xFF1E3490)   // deep blue
private val N_RIM   = Color(0xFF0D1D5E)   // near-black navy
private val N_RAY   = Color(0xFF5B7FFF)   // blue ray colour
private val N_GLOW1 = Color(0x603A56C8)   // blue inner glow
private val N_GLOW2 = Color(0x301E3490)   // blue outer glow

@Composable
fun DartboardButton(
    vpnState: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(0.55f, 900f), label = "ps"
    )

    val tr = rememberInfiniteTransition(label = "btn")

    val glowPulse by tr.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse), "gp")

    val rayRpm = when (vpnState) {
        VpnState.CONNECTING -> 900
        VpnState.CONNECTED  -> 12000
        else                -> 30000
    }
    val rayAngle by tr.animateFloat(0f, 360f,
        infiniteRepeatable(tween(rayRpm, easing = LinearEasing), RepeatMode.Restart), "ra")

    val ringColor by animateColorAsState(
        when (vpnState) {
            VpnState.CONNECTED  -> ConnectedGreen
            VpnState.CONNECTING -> if (isDark) Color(0xFF7BA7FF) else Color(0xFFFFB300)
            VpnState.ERROR      -> DisconnectedRed
            else                -> if (isDark) Color(0xFF3A56C8) else Color(0xFFFF8F00)
        }, tween(400), label = "rc"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .scale(pressScale)
            .clickable(interactionSource, null,
                enabled = vpnState != VpnState.CONNECTING,
                onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val cx   = size.width  / 2f
            val cy   = size.height / 2f
            val disc = size.minDimension / 2f * 0.62f
            val gp   = glowPulse

            if (isDark) {
                // ── Night: cool blue glow ──────────────────────────────
                drawCircle(Brush.radialGradient(listOf(N_GLOW2, Color.Transparent),
                    Offset(cx,cy), disc*3.5f), disc*3.5f, Offset(cx,cy))
                drawCircle(Brush.radialGradient(
                    listOf(N_GLOW1.copy(alpha=0.30f+gp*0.15f), Color.Transparent),
                    Offset(cx,cy), disc*2.0f), disc*2.0f, Offset(cx,cy))

                // Night rays — short, dim, blue
                rotate(rayAngle, Offset(cx, cy)) {
                    for (i in 0..11) {
                        val a    = Math.toRadians(i * 30.0)
                        val long = i % 3 == 0
                        val r1   = disc * 1.18f
                        val r2   = disc * (if (long) 1.80f + gp * 0.15f else 1.50f + gp * 0.10f)
                        drawLine(
                            color = N_RAY.copy(if (long) 0.45f else 0.22f),
                            start = Offset(cx + (r1*cos(a)).toFloat(), cy + (r1*sin(a)).toFloat()),
                            end   = Offset(cx + (r2*cos(a)).toFloat(), cy + (r2*sin(a)).toFloat()),
                            strokeWidth = if (long) 3.5f else 1.8f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Night disc — deep indigo gradient
                drawCircle(Color(0x35000000), disc + 4f, Offset(cx, cy + 8f))
                drawCircle(
                    Brush.radialGradient(
                        listOf(N_CORE, N_IN1, N_IN2, N_MID, N_OUT, N_RIM),
                        Offset(cx, cy - disc * 0.10f), disc
                    ), disc, Offset(cx, cy)
                )

            } else {
                // ── Day: warm golden sun ───────────────────────────────
                drawCircle(Brush.radialGradient(listOf(S_GLOW3, Color.Transparent),
                    Offset(cx,cy), disc*4.8f), disc*4.8f, Offset(cx,cy))
                drawCircle(Brush.radialGradient(
                    listOf(S_GLOW2.copy(alpha=0.28f+gp*0.12f), Color.Transparent),
                    Offset(cx,cy), disc*3.2f), disc*3.2f, Offset(cx,cy))
                drawCircle(Brush.radialGradient(
                    listOf(S_GLOW1.copy(alpha=0.42f+gp*0.18f), Color.Transparent),
                    Offset(cx,cy), disc*2.0f), disc*2.0f, Offset(cx,cy))

                // Day rays — long, golden
                rotate(rayAngle, Offset(cx, cy)) {
                    for (i in 0..15) {
                        val a    = Math.toRadians(i * 22.5)
                        val long = i % 2 == 0
                        val r1   = disc * 1.22f
                        val r2   = disc * (if (long) 2.35f + gp * 0.25f else 1.68f + gp * 0.18f)
                        drawLine(
                            color = S_MID.copy(if (long) 0.65f else 0.38f),
                            start = Offset(cx + (r1*cos(a)).toFloat(), cy + (r1*sin(a)).toFloat()),
                            end   = Offset(cx + (r2*cos(a)).toFloat(), cy + (r2*sin(a)).toFloat()),
                            strokeWidth = if (long) 4.5f else 2.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Day disc — white-hot centre → orange rim
                drawCircle(Color(0x25000000), disc + 3f, Offset(cx, cy + 8f))
                drawCircle(
                    Brush.radialGradient(
                        listOf(S_CORE, S_IN1, S_IN2, S_MID, S_OUT, S_RIM),
                        Offset(cx, cy - disc * 0.08f), disc
                    ), disc, Offset(cx, cy)
                )
            }

            // Status ring (both themes)
            drawCircle(ringColor.copy(0.75f), disc - 2f, Offset(cx, cy), style = Stroke(3.5f))

            // Specular highlight
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(if (isDark) 0.40f else 0.90f), Color.Transparent),
                    Offset(cx - disc*0.30f, cy - disc*0.30f), disc*0.30f
                ), disc*0.30f, Offset(cx - disc*0.30f, cy - disc*0.30f)
            )

            // Power icon — white on both themes
            val iconR = disc * 0.40f
            drawArc(
                color = Color.White.copy(0.95f),
                startAngle = -210f, sweepAngle = 240f, useCenter = false,
                topLeft = Offset(cx - iconR, cy - iconR),
                size = Size(iconR * 2f, iconR * 2f),
                style = Stroke(iconR * 0.20f, cap = StrokeCap.Round)
            )
            drawLine(Color.White.copy(0.95f),
                Offset(cx, cy - iconR * 0.42f), Offset(cx, cy - iconR * 1.05f),
                iconR * 0.20f, cap = StrokeCap.Round)
        }
    }
}
