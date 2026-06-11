package at.aau.pulverfass.app.ui.map

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
import kotlin.math.cos
import kotlin.math.sin

/*
 * Zeitachse der Clash-Animation. Die Phasen laufen nacheinander beziehungsweise
 * leicht überlappend: erst wachsen die Angriffslinien zur Mitte, dann zündet
 * die Explosion, während die Verlustlabels neben den Truppen-Chips aufsteigen.
 */
private const val LINE_DURATION_MS = 350
private const val BURST_DURATION_MS = 500
private const val LABEL_DELAY_MS = 150
private const val LABEL_DURATION_MS = 700

private const val PARTICLE_COUNT = 12
private const val FULL_CIRCLE_DEGREES = 360f
private val BurstMaxRadius = 42.dp
private val ParticleRadius = 3.dp
private val LineWidth = 3.dp
private val LabelRiseDistance = 34.dp
private val LabelSideOffset = 18.dp

private val BurstCoreColor = Color(0xFFFFF3B0)
private val BurstGlowColor = Color(0xFFFF9800)
private val ShockwaveColor = Color(0xFFFFCC80)
private val DamageLabelColor = Color(0xFFFF5252)

/**
 * Beschreibt einen abzuspielenden Kampf-Effekt auf der Karte.
 *
 * Die [attackId] stammt aus dem Serverereignis und stellt sicher, dass jeder
 * Kampf genau einmal animiert wird, auch wenn der zugrunde liegende UI-State
 * mehrfach rekomponiert. [sourceTroopsBefore] und [targetTroopsBefore] halten
 * die sichtbaren Truppen-Chips für die Dauer der Animation auf dem
 * Vorkampfstand; der echte Spielzustand bleibt davon unberührt.
 */
data class AttackVfxRequest(
    val attackId: Long,
    val fromRegionId: String,
    val toRegionId: String,
    val attackerLosses: Int,
    val defenderLosses: Int,
    val sourceTroopsBefore: Int,
    val targetTroopsBefore: Int,
)

/**
 * Spielt eingehende [AttackVfxRequest]s nacheinander ab.
 *
 * Schnell aufeinanderfolgende Kämpfe werden über eine Queue serialisiert statt
 * sich gegenseitig zu überschreiben. Bereits gesehene [AttackVfxRequest.attackId]s
 * werden ignoriert, damit derselbe Kampf nie doppelt animiert wird.
 */
internal class AttackVfxController {
    private val queue = mutableStateListOf<AttackVfxRequest>()
    private val seenAttackIds = mutableSetOf<Long>()

    val lineProgress = Animatable(0f)
    val burstProgress = Animatable(0f)
    val labelProgress = Animatable(0f)

    /** Aktuell abgespielter Kampf oder `null`, wenn keine Animation läuft. */
    val activeRequest: AttackVfxRequest?
        get() = queue.firstOrNull()

    fun enqueue(request: AttackVfxRequest) {
        if (seenAttackIds.add(request.attackId)) {
            queue.add(request)
        }
    }

    suspend fun playHead() {
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
        queue.removeAt(0)
    }
}

/**
 * Erstellt den Controller und füttert ihn mit neuen Kampf-Ergebnissen.
 *
 * Das Abspielen hängt am jeweiligen Queue-Kopf: Sobald ein Kampf fertig
 * animiert und entfernt ist, startet der nächste automatisch.
 */
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

/**
 * Nicht blockierender VFX-Layer über der Karte.
 *
 * Zeichnet Angriffslinien von beiden Territorien zur Streckenmitte, dort eine
 * Canvas-Explosion aus Shockwave, Glow und Partikeln sowie aufsteigende
 * Verlustlabels neben den Truppen-Chips. Die Positionen werden pro Frame aus
 * den Kartenankern projiziert und folgen damit Zoom und Pan.
 */
@Composable
internal fun AttackVfxOverlay(
    controller: AttackVfxController,
    fromAnchor: MapPoint,
    toAnchor: MapPoint,
    attackerColor: Color,
    defenderColor: Color,
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
        if (!fromOffset.isValid() || !toOffset.isValid()) {
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
            attackerColor = attackerColor,
            defenderColor = defenderColor,
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
    attackerColor: Color,
    defenderColor: Color,
    scale: Float,
) {
    if (lineProgress <= 0f) {
        return
    }

    /*
     * Die Linien wachsen von beiden Chips zur Mitte und blenden aus, sobald die
     * Explosion übernimmt. So bleibt der Fokus immer auf genau einer Phase.
     */
    val alpha = (1f - burstProgress).coerceIn(0f, 1f)
    if (alpha <= 0f) {
        return
    }

    val strokeWidth = LineWidth.toPx() * scale
    listOf(
        Triple(fromOffset, attackerColor, lineProgress),
        Triple(toOffset, defenderColor, lineProgress),
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
    if (progress <= 0f || progress >= 1f) {
        return
    }

    val maxRadius = BurstMaxRadius.toPx() * scale
    val fade = 1f - progress

    /*
     * Canvas-Fallback statt Frame-Assets: expandierender Shockwave-Ring, ein
     * weicher Glow-Kern und radial davonfliegende Partikel ergeben zusammen
     * einen lesbaren Einschlag ohne zusätzliche Bitmaps.
     */
    drawCircle(
        color = ShockwaveColor.copy(alpha = fade),
        radius = maxRadius * progress,
        center = midpoint,
        style = Stroke(width = LineWidth.toPx() * scale),
    )
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(BurstCoreColor.copy(alpha = fade), Color.Transparent),
                center = midpoint,
                radius = (maxRadius * 0.6f).coerceAtLeast(1f),
            ),
        radius = maxRadius * 0.6f,
        center = midpoint,
    )

    val particleTravel = maxRadius * progress
    val particleRadius = ParticleRadius.toPx() * scale * fade
    for (index in 0 until PARTICLE_COUNT) {
        val angleDegrees = index * (FULL_CIRCLE_DEGREES / PARTICLE_COUNT)
        val angleRadians = Math.toRadians(angleDegrees.toDouble())
        val particleCenter =
            midpoint +
                Offset(
                    x = (cos(angleRadians) * particleTravel).toFloat(),
                    y = (sin(angleRadians) * particleTravel).toFloat(),
                )
        val color = if (index % 2 == 0) BurstGlowColor else BurstCoreColor
        drawCircle(
            color = color.copy(alpha = fade),
            radius = particleRadius,
            center = particleCenter,
        )
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

    /*
     * Schnelles Einblenden, langsames Ausblenden: Das Label ist sofort lesbar
     * und verschwindet, bevor der Chip auf den echten Stand synchronisiert.
     */
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

private fun Offset.isValid(): Boolean = isSpecified() && x.isFinite() && y.isFinite()

private fun Offset.isSpecified(): Boolean = this != Offset.Unspecified
