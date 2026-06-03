package at.aau.pulverfass.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val GLOW_LAYERS = 5
private const val PARTICLE_COUNT = 12

/**
 * Mehrere konzentrische goldene Ringe mit abnehmender Alpha erzeugen
 * einen Leuchteffekt ohne Blur-API.
 */
@Composable
fun GoldenGlowRing(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        drawGlowLayers(this, PulverfassColors.GoldBright)
    }
}

private fun drawGlowLayers(
    scope: DrawScope,
    color: Color,
) {
    val baseRadius = scope.size.minDimension / 2f
    repeat(GLOW_LAYERS) { layer ->
        val extra = (layer + 1) * (baseRadius * 0.12f)
        val alpha = 0.22f - layer * 0.04f
        scope.drawCircle(
            color = color.copy(alpha = alpha),
            radius = baseRadius + extra,
        )
    }
    scope.drawCircle(
        color = PulverfassColors.GoldBright.copy(alpha = 0.55f),
        radius = baseRadius,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.value),
    )
}

/**
 * Animierte Funkelpartikel rund um einen Mittelpunkt.
 * Jedes Partikel pulsiert unabhängig mit versetztem Start-Delay.
 */
@Composable
fun SparkleParticles(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val alphas = remember { (0 until PARTICLE_COUNT).map { Animatable(0f) } }
    val radii = remember { (0 until PARTICLE_COUNT).map { Animatable(0f) } }

    LaunchedEffect(Unit) {
        alphas.forEachIndexed { i, anim ->
            val delay = i * (1200 / PARTICLE_COUNT)
            launch {
                anim.animateTo(
                    1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(1200, delayMillis = delay, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                )
            }
            launch {
                radii[i].animateTo(
                    1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(1200, delayMillis = delay, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                )
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxOrbit = size.minDimension / 2f
            alphas.forEachIndexed { i, alphaAnim ->
                val angle = (360f / PARTICLE_COUNT) * i
                val rad = Math.toRadians(angle.toDouble())
                val orbit = maxOrbit * (0.65f + radii[i].value * 0.35f)
                val x = cx + (cos(rad) * orbit).toFloat()
                val y = cy + (sin(rad) * orbit).toFloat()
                drawCircle(
                    color = color.copy(alpha = alphaAnim.value * 0.85f),
                    radius = 4.dp.toPx() * (0.5f + alphaAnim.value * 0.5f),
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }
    }
}
