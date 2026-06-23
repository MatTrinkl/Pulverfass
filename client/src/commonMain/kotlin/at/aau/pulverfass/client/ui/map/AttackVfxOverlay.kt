package at.aau.pulverfass.client.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val LINE_DURATION_MS = 350
private const val BURST_DURATION_MS = 500
private const val LABEL_DELAY_MS = 150
private const val LABEL_DURATION_MS = 700

private val BurstMaxRadius = 34.dp
private val LineWidth = 3.dp
private val LabelRiseDistance = 34.dp
private val LabelSideOffset = 18.dp

private val BurstRingWidth = 2.dp
private val BurstSparkRadius = 2.dp
private const val BURST_SPARK_COUNT = 8
private val BurstCoreColor = Color(0xFFFFF3E0)
private val BurstEdgeColor = Color(0xFFFF7043)
private val BurstSparkColor = Color(0xFFFFCA28)

private val DamageLabelColor = Color(0xFFFF5252)

data class AttackVfxRequest(
    val attackId: Long,
    val fromRegionId: String,
    val toRegionId: String,
    val attackerLosses: Int,
    val defenderLosses: Int,
    val sourceTroopsBefore: Int,
    val targetTroopsBefore: Int,
)

data class ClashColors(
    val attacker: Color,
    val defender: Color,
)

internal class AttackVfxController {
    private val queue = mutableStateListOf<AttackVfxRequest>()
    private var lastPlayedAttackId: Long? = null

    val lineProgress = Animatable(0f)
    val burstProgress = Animatable(0f)
    val labelProgress = Animatable(0f)

    val activeRequest: AttackVfxRequest?
        get() = queue.firstOrNull()

    fun enqueue(request: AttackVfxRequest) {
        val alreadyQueuedOrPlayed =
            request.attackId == lastPlayedAttackId ||
                queue.any { it.attackId == request.attackId }
        if (alreadyQueuedOrPlayed) {
            return
        }
        queue.add(request)
    }

    suspend fun playHead() {
        val head = queue.firstOrNull() ?: return
        lineProgress.snapTo(0f)
        burstProgress.snapTo(0f)
        labelProgress.snapTo(0f)

        lineProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(LINE_DURATION_MS, easing = FastOutSlowInEasing),
        )
        coroutineScope {
            launch {
                burstProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(BURST_DURATION_MS, easing = LinearOutSlowInEasing),
                )
            }
            launch {
                delay(LABEL_DELAY_MS.toLong())
                labelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(LABEL_DURATION_MS, easing = LinearOutSlowInEasing),
                )
            }
        }
        lastPlayedAttackId = head.attackId
        queue.removeAt(0)
    }
}

@Composable
internal fun rememberAttackVfxController(request: AttackVfxRequest?): AttackVfxController {
    val controller = remember { AttackVfxController() }

    LaunchedEffect(request) {
        if (request != null) {
            controller.enqueue(request)
        }
    }
    LaunchedEffect(controller.activeRequest) {
        if (controller.activeRequest != null) {
            controller.playHead()
        }
    }
    return controller
}

@Composable
internal fun AttackVfxOverlay(
    controller: AttackVfxController,
    fromAnchor: MapPoint,
    toAnchor: MapPoint,
    colors: ClashColors,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
    modifier: Modifier = Modifier,
) {
    val request = controller.activeRequest ?: return
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DamageLabelColor,
        )

    Canvas(modifier = modifier) {
        val fromOffset =
            mapPointToScreenOffset(fromAnchor, layoutMetrics, viewportState)
        val toOffset =
            mapPointToScreenOffset(toAnchor, layoutMetrics, viewportState)
        if (!fromOffset.isRenderable() || !toOffset.isRenderable()) {
            return@Canvas
        }

        val midpoint = (fromOffset + toOffset) / 2f
        val scale = viewportState.scale

        drawClashLines(
            fromOffset = fromOffset,
            toOffset = toOffset,
            midpoint = midpoint,
            lineProgress = controller.lineProgress.value,
            burstProgress = controller.burstProgress.value,
            colors = colors,
            scale = scale,
        )
        drawClashBurst(
            midpoint = midpoint,
            progress = controller.burstProgress.value,
            scale = scale,
        )
        drawDamageLabels(
            request = request,
            fromOffset = fromOffset,
            toOffset = toOffset,
            progress = controller.labelProgress.value,
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
        )
    }
}

private fun DrawScope.drawClashLines(
    fromOffset: Offset,
    toOffset: Offset,
    midpoint: Offset,
    lineProgress: Float,
    burstProgress: Float,
    colors: ClashColors,
    scale: Float,
) {
    if (lineProgress <= 0f) {
        return
    }

    val alpha = (1f - burstProgress).coerceIn(0f, 1f)
    if (alpha <= 0f) {
        return
    }

    val strokeWidth = LineWidth.toPx() * scale
    listOf(
        Triple(fromOffset, colors.attacker, lineProgress),
        Triple(toOffset, colors.defender, lineProgress),
    ).forEach { (start, color, progress) ->
        val end = start + (midpoint - start) * progress
        drawLine(
            color = color.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = strokeWidth,
        )
    }
}

private fun DrawScope.drawClashBurst(
    midpoint: Offset,
    progress: Float,
    scale: Float,
) {
    if (progress <= 0f) {
        return
    }

    val maxRadius = BurstMaxRadius.toPx() * scale
    val fade = (1f - progress).coerceIn(0f, 1f)

    val coreRadius = (maxRadius * (0.4f + progress * 0.6f)).coerceAtLeast(1f)
    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        BurstCoreColor.copy(alpha = fade * 0.36f),
                        BurstEdgeColor.copy(alpha = fade * 0.18f),
                        Color.Transparent,
                    ),
                center = midpoint,
                radius = coreRadius,
            ),
        radius = coreRadius,
        center = midpoint,
    )

    val ringRadius = maxRadius * (0.3f + progress)
    drawCircle(
        color = BurstCoreColor.copy(alpha = fade * 0.45f),
        radius = ringRadius,
        center = midpoint,
        style = Stroke(width = (BurstRingWidth.toPx() * scale) * (1f - progress * 0.5f)),
    )

    val sparkDistance = maxRadius * (0.5f + progress)
    val sparkRadius = (BurstSparkRadius.toPx() * scale) * fade
    if (sparkRadius > 0f) {
        repeat(BURST_SPARK_COUNT) { index ->
            val angle = (2.0 * PI * index / BURST_SPARK_COUNT).toFloat()
            val sparkCenter =
                midpoint + Offset(cos(angle), sin(angle)) * sparkDistance
            drawCircle(
                color = BurstSparkColor.copy(alpha = fade * 0.55f),
                radius = sparkRadius,
                center = sparkCenter,
            )
        }
    }
}

private fun DrawScope.drawDamageLabels(
    request: AttackVfxRequest,
    fromOffset: Offset,
    toOffset: Offset,
    progress: Float,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    if (progress <= 0f) {
        return
    }

    val alpha = (progress * 4f).coerceAtMost(1f) * (1f - progress)
    val rise = LabelRiseDistance.toPx() * progress
    val sideOffset = LabelSideOffset.toPx()

    listOf(
        request.attackerLosses to fromOffset,
        request.defenderLosses to toOffset,
    ).forEach { (losses, anchor) ->
        if (losses > 0) {
            val layoutResult =
                textMeasurer.measure(AnnotatedString("-$losses"), style = labelStyle)
            drawText(
                textLayoutResult = layoutResult,
                topLeft =
                    Offset(
                        x = anchor.x + sideOffset,
                        y = anchor.y - rise - layoutResult.size.height / 2f,
                    ),
                alpha = alpha,
            )
        }
    }
}

private fun Offset.isRenderable(): Boolean = isSpecified() && x.isFinite() && y.isFinite()

private fun Offset.isSpecified(): Boolean = this != Offset.Unspecified
