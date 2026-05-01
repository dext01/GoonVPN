package com.goonvpn.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.goonvpn.app.R
import kotlin.math.PI
import kotlin.math.sin

// Each plane: baseY, size, driftMs, phase, angle, bobAmp, bobMs, alpha
private data class Plane(
    val baseY: Float, val size: Float, val driftMs: Int,
    val phase: Float, val angle: Float, val bobAmp: Float,
    val bobMs: Int, val alpha: Float
)

private val P0 = Plane(0.10f, 36f, 22000, 0.05f, -14f, 8f,  4000, 0.90f)
private val P1 = Plane(0.28f, 24f, 30000, 0.40f,   5f, 5f,  5000, 0.70f)
private val P2 = Plane(0.52f, 44f, 20000, 0.65f, -10f, 10f, 3000, 0.95f)
private val P3 = Plane(0.72f, 20f, 35000, 0.15f,   6f,  4f, 6000, 0.55f)
private val P4 = Plane(0.85f, 32f, 18000, 0.80f, -16f,  7f, 4000, 0.80f)

@Composable
fun AnimatedBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val tr = rememberInfiniteTransition(label = "bg")

    // Explicit `by` for each plane so Compose tracks each state individually
    val d0 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P0.driftMs, easing = LinearEasing), RepeatMode.Restart), "d0")
    val d1 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P1.driftMs, easing = LinearEasing), RepeatMode.Restart), "d1")
    val d2 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P2.driftMs, easing = LinearEasing), RepeatMode.Restart), "d2")
    val d3 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P3.driftMs, easing = LinearEasing), RepeatMode.Restart), "d3")
    val d4 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P4.driftMs, easing = LinearEasing), RepeatMode.Restart), "d4")

    val b0 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P0.bobMs, easing = LinearEasing), RepeatMode.Restart), "b0")
    val b1 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P1.bobMs, easing = LinearEasing), RepeatMode.Restart), "b1")
    val b2 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P2.bobMs, easing = LinearEasing), RepeatMode.Restart), "b2")
    val b3 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P3.bobMs, easing = LinearEasing), RepeatMode.Restart), "b3")
    val b4 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(P4.bobMs, easing = LinearEasing), RepeatMode.Restart), "b4")

    val drifts = listOf(d0, d1, d2, d3, d4)
    val bobs   = listOf(b0, b1, b2, b3, b4)
    val planes = listOf(P0, P1, P2, P3, P4)

    Box(modifier = modifier) {
        Image(
            painter = painterResource(if (isDark) R.drawable.sky_night else R.drawable.sky_day),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = if (isDark)
                androidx.compose.ui.Alignment.TopCenter
            else
                androidx.compose.ui.Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            planes.forEachIndexed { i, p ->
                val drift = (p.phase + drifts[i]) % 1f
                val x = w + 80f - (w + 160f) * drift
                val y = h * p.baseY + sin(bobs[i] * 2f * PI.toFloat()) * p.bobAmp
                translate(x, y) {
                    rotate(p.angle, Offset(0f, 0f)) {
                        drawPlane(p.size, p.alpha, isDark)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPlane(s: Float, alpha: Float, dark: Boolean) {
    val topColor = if (dark) Color(0xFFCCE0FF) else Color(0xFFFFFFFF)
    val botColor = if (dark) Color(0xFF8AADD4) else Color(0xFFCCE4F5)

    // Nose at (-s, 0) = pointing LEFT, matching direction of travel
    val top = Path().apply {
        moveTo(-s, 0f); lineTo(s * 0.88f, -s * 0.50f); lineTo(s * 0.15f, -s * 0.04f); close()
    }
    val bot = Path().apply {
        moveTo(-s, 0f); lineTo(s * 0.88f,  s * 0.50f); lineTo(s * 0.15f,  s * 0.04f); close()
    }

    translate(s * 0.05f, s * 0.08f) {
        drawPath(top, Color.Black.copy(alpha * 0.15f))
        drawPath(bot, Color.Black.copy(alpha * 0.10f))
    }

    drawPath(top, topColor.copy(alpha))
    drawPath(bot, botColor.copy(alpha * 0.85f))

    drawLine(Color.White.copy(alpha * 0.95f), Offset(-s, 0f), Offset(s * 0.15f, 0f),
        1.6f, cap = StrokeCap.Round)

    val outlineColor = if (dark) Color(0xFF4A7AB5).copy(alpha * 0.55f)
                       else Color(0xFF7AAEC8).copy(alpha * 0.60f)
    drawPath(top, outlineColor, style = Stroke(1.1f))
    drawPath(bot, outlineColor.copy(alpha * 0.50f), style = Stroke(1.0f))
}
