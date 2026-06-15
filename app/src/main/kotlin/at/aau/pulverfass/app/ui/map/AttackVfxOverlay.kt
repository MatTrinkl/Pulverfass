package at.aau.pulverfass.app.ui.map

import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Zeitachse der Clash-Animation. Die Phasen laufen nacheinander beziehungsweise
 * leicht überlappend: erst wachsen die Angriffslinien zur Mitte, dann zündet
 * die Explosion, während die Verlustlabels neben den Truppen-Chips aufsteigen.
 */
private const val LINE_DURATION_MS = 350
private const val BURST_DURATION_MS = 500
private const val LABEL_DELAY_MS = 150
private const val LABEL_DURATION_MS = 700

private val BurstMaxRadius = 42.dp
private val LineWidth = 3.dp
private val LabelRiseDistance = 34.dp
private val LabelSideOffset = 18.dp

/** Sichtbare Explosion ~1.82x des Burst-Radius (Durchmesser); 30 % kleiner als zuvor. */
private const val EXPLOSION_SIZE_FACTOR = 1.82f

private val DamageLabelColor = Color(0xFFFF5252)

/** Log-Tag zur Diagnose der Clash-Animation (siehe [AttackVfxController]). */
internal const val VFX_LOG_TAG = "AttackVfx"

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

/** Akzentfarben der beiden Kampfparteien für die Clash-Animation. */
data class ClashColors(
    val attacker: Color,
    val defender: Color,
)

/**
 * Spielt eingehende [AttackVfxRequest]s nacheinander ab.
 *
 * Schnell aufeinanderfolgende Kämpfe werden über eine Queue serialisiert statt
 * sich gegenseitig zu überschreiben. Eine erneut eingereihte [AttackVfxRequest.attackId]
 * wird nur ignoriert, solange sie bereits in der Queue liegt oder gerade zuletzt
 * abgespielt wurde -- so wird derselbe Kampf nie doppelt animiert, ein echter neuer
 * Angriff aber auch nie fälschlich verschluckt.
 */
internal class AttackVfxController {
    private val queue = mutableStateListOf<AttackVfxRequest>()

    /*
     * Es wird nur der zuletzt fertig abgespielte Kampf unterdrückt, nicht die
     * gesamte Historie. So wird ein erneut emittiertes Ergebnis (Recomposition
     * oder State-Catch-up) nicht doppelt animiert, ein echter neuer Angriff aber
     * niemals verschluckt -- selbst wenn seine attackId eine frühere zufällig
     * wiederholt (etwa nach einem Reset des stateVersion-Fallbacks im Reducer).
     */
    private var lastPlayedAttackId: Long? = null

    val lineProgress = Animatable(0f)
    val burstProgress = Animatable(0f)
    val labelProgress = Animatable(0f)

    /** Aktuell abgespielter Kampf oder `null`, wenn keine Animation läuft. */
    val activeRequest: AttackVfxRequest?
        get() = queue.firstOrNull()

    fun enqueue(request: AttackVfxRequest) {
        val skipReason =
            when {
                request.attackId == lastPlayedAttackId -> "zuletzt abgespielt"
                queue.any { it.attackId == request.attackId } -> "bereits in Queue"
                else -> null
            }
        if (skipReason != null) {
            Log.d(VFX_LOG_TAG, "skip attackId=${request.attackId}: $skipReason")
            return
        }
        Log.d(
            VFX_LOG_TAG,
            "enqueue attackId=${request.attackId} " +
                "${request.fromRegionId}->${request.toRegionId}",
        )
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

    // Einzelframes der Explosion; der Burst-Fortschritt wählt den Frame aus.
    val explosionFrames =
        listOf(
            ImageBitmap.imageResource(id = R.drawable.explosion_01_vfx),
            ImageBitmap.imageResource(id = R.drawable.explosion_02_vfx),
            ImageBitmap.imageResource(id = R.drawable.explosion_03_vfx),
            ImageBitmap.imageResource(id = R.drawable.explosion_04_vfx),
            ImageBitmap.imageResource(id = R.drawable.explosion_05_vfx),
            ImageBitmap.imageResource(id = R.drawable.explosion_06_vfx),
            ImageBitmap.imageResource(id = R.drawable.explosion_07_vfx),
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
        drawExplosionFrame(
            frames = explosionFrames,
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

/**
 * Zeichnet die Explosion als Frame-Sequenz: Der Burst-Fortschritt (0..1) wählt
 * den passenden Frame aus den [frames]; das Bild wird unter Beibehaltung seines
 * Seitenverhältnisses mittig auf [midpoint] gezeichnet und folgt über [scale]
 * dem Zoom.
 */
private fun DrawScope.drawExplosionFrame(
    frames: List<ImageBitmap>,
    midpoint: Offset,
    progress: Float,
    scale: Float,
) {
    if (progress <= 0f || frames.isEmpty()) {
        return
    }

    val frameIndex = (progress * frames.size).toInt().coerceIn(0, frames.lastIndex)
    val image = frames[frameIndex]

    val dstWidth = (BurstMaxRadius.toPx() * EXPLOSION_SIZE_FACTOR * scale).coerceAtLeast(1f)
    val dstHeight = dstWidth * (image.height.toFloat() / image.width.toFloat())
    val widthInt = dstWidth.toInt()
    val heightInt = dstHeight.toInt()

    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset =
            IntOffset(
                x = (midpoint.x - widthInt / 2f).toInt(),
                y = (midpoint.y - heightInt / 2f).toInt(),
            ),
        dstSize = IntSize(widthInt, heightInt),
    )
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

private fun Offset.isRenderable(): Boolean = isSpecified() && x.isFinite() && y.isFinite()

private fun Offset.isSpecified(): Boolean = this != Offset.Unspecified
