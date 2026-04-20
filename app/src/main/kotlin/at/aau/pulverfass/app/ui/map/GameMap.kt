package at.aau.pulverfass.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val DEFAULT_MAP_ASPECT_RATIO = 2540f / 1346f
private const val MAP_CONTENT_OFFSET_X = 470f / 2540f
private const val MAP_CONTENT_OFFSET_Y = 249f / 1346f
private const val MAP_CONTENT_SCALE_X = 1600f / 2540f
private const val MAP_CONTENT_SCALE_Y = 848f / 1346f
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private val MapOverlaySurfaceColor = Color.White
private val MapOverlayBorderColor = Color.Black
private val MapOverlayContentColor = Color.Black
private val MapOverlayInverseColor = Color.White

/**
 * Normalisierter Punkt im Kartenkoordinatensystem.
 *
 * Die Koordinaten liegen unabhängig von der Bildschirmgröße im Bereich `0..1`
 * und referenzieren Positionen relativ zur Kartenfläche.
 */
data class MapPoint(
    val x: Float,
    val y: Float,
)

private fun MapPoint.shiftedBy(
    dx: Float = 0f,
    dy: Float = 0f,
): MapPoint = MapPoint(x = x + dx, y = y + dy)

private fun List<MapPoint>.shiftedBy(
    dx: Float = 0f,
    dy: Float = 0f,
): List<MapPoint> = map { point -> point.shiftedBy(dx = dx, dy = dy) }

private fun MapPoint.fromMapContentToCanvas(): MapPoint =
    MapPoint(
        x = MAP_CONTENT_OFFSET_X + (x * MAP_CONTENT_SCALE_X),
        y = MAP_CONTENT_OFFSET_Y + (y * MAP_CONTENT_SCALE_Y),
    )

private fun GameMapRegion.fromMapContentToCanvas(): GameMapRegion =
    copy(
        polygon = polygon.map { point -> point.fromMapContentToCanvas() },
        labelAnchor = labelAnchor.fromMapContentToCanvas(),
    )

/**
 * Beschreibt eine interaktive Region auf der Spielkarte.
 *
 * Das Polygon definiert die klickbare Fläche. [labelAnchor] markiert die
 * bevorzugte Position für sichtbare UI-Elemente über der Karte.
 */
data class GameMapRegion(
    val id: String,
    val name: String,
    val polygon: List<MapPoint>,
    val labelAnchor: MapPoint,
)

/**
 * Hält den reinen Frontend-Zustand einer Region für die Demoansicht.
 */
data class GameMapRegionState(
    val ownerPlayerId: String,
    val ownerName: String,
    val troopCount: Int,
    val accentColor: Color,
)

/**
 * Aktueller Viewport der Karte aus Zoomstufe und Verschiebung.
 */
data class MapViewportState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

/**
 * Abgeleitete Layoutdaten für die Kartenprojektion im aktuellen Canvas.
 */
data class MapLayoutMetrics(
    val viewportSize: Size,
    val mapSize: Size,
    val mapOrigin: Offset,
)

/**
 * Statische Platzhalterdaten für die erste interaktive Kartenansicht.
 *
 * Die Regionen sind bewusst im App-Modul gehalten, bis fachliche Map-Daten
 * zwischen App und Server über ein gemeinsames Modell geteilt werden.
 * Die Rohkoordinaten beziehen sich auf den inneren Karteninhalt und werden auf
 * die erweiterte Bildleinwand mit UI-Rand projiziert.
 */
object PulverfassMapDefaults {
    val regions: List<GameMapRegion> =
        listOf(
            GameMapRegion(
                id = "north_america",
                name = "Nordamerika",
                polygon =
                    listOf(
                        MapPoint(0.002f, 0.142f),
                        MapPoint(0.010f, 0.126f),
                        MapPoint(0.024f, 0.114f),
                        MapPoint(0.050f, 0.112f),
                        MapPoint(0.082f, 0.118f),
                        MapPoint(0.118f, 0.119f),
                        MapPoint(0.146f, 0.105f),
                        MapPoint(0.169f, 0.100f),
                        MapPoint(0.184f, 0.114f),
                        MapPoint(0.204f, 0.105f),
                        MapPoint(0.231f, 0.105f),
                        MapPoint(0.245f, 0.125f),
                        MapPoint(0.240f, 0.149f),
                        MapPoint(0.223f, 0.160f),
                        MapPoint(0.211f, 0.182f),
                        MapPoint(0.211f, 0.212f),
                        MapPoint(0.228f, 0.243f),
                        MapPoint(0.241f, 0.279f),
                        MapPoint(0.238f, 0.317f),
                        MapPoint(0.223f, 0.344f),
                        MapPoint(0.216f, 0.381f),
                        MapPoint(0.208f, 0.418f),
                        MapPoint(0.212f, 0.456f),
                        MapPoint(0.203f, 0.485f),
                        MapPoint(0.186f, 0.491f),
                        MapPoint(0.172f, 0.466f),
                        MapPoint(0.152f, 0.437f),
                        MapPoint(0.133f, 0.407f),
                        MapPoint(0.116f, 0.378f),
                        MapPoint(0.104f, 0.343f),
                        MapPoint(0.089f, 0.317f),
                        MapPoint(0.071f, 0.290f),
                        MapPoint(0.050f, 0.270f),
                        MapPoint(0.030f, 0.250f),
                        MapPoint(0.015f, 0.220f),
                        MapPoint(0.004f, 0.188f),
                    ).shiftedBy(dy = 0.012f),
                labelAnchor = MapPoint(0.132f, 0.248f).shiftedBy(dy = 0.012f),
            ),
            GameMapRegion(
                id = "south_america",
                name = "Südamerika",
                polygon =
                    listOf(
                        MapPoint(0.124f, 0.500f),
                        MapPoint(0.152f, 0.507f),
                        MapPoint(0.177f, 0.518f),
                        MapPoint(0.197f, 0.540f),
                        MapPoint(0.205f, 0.569f),
                        MapPoint(0.204f, 0.608f),
                        MapPoint(0.199f, 0.646f),
                        MapPoint(0.198f, 0.690f),
                        MapPoint(0.192f, 0.733f),
                        MapPoint(0.184f, 0.778f),
                        MapPoint(0.176f, 0.818f),
                        MapPoint(0.171f, 0.858f),
                        MapPoint(0.164f, 0.893f),
                        MapPoint(0.151f, 0.938f),
                        MapPoint(0.141f, 0.967f),
                        MapPoint(0.134f, 0.945f),
                        MapPoint(0.132f, 0.904f),
                        MapPoint(0.131f, 0.861f),
                        MapPoint(0.127f, 0.818f),
                        MapPoint(0.123f, 0.778f),
                        MapPoint(0.121f, 0.734f),
                        MapPoint(0.120f, 0.687f),
                        MapPoint(0.118f, 0.643f),
                        MapPoint(0.118f, 0.597f),
                        MapPoint(0.120f, 0.550f),
                    ).shiftedBy(dx = 0.018f, dy = 0.014f),
                labelAnchor = MapPoint(0.161f, 0.699f).shiftedBy(dx = 0.018f, dy = 0.014f),
            ),
            GameMapRegion(
                id = "greenland",
                name = "Grönland",
                polygon =
                    listOf(
                        MapPoint(0.265f, 0.038f),
                        MapPoint(0.286f, 0.021f),
                        MapPoint(0.322f, 0.015f),
                        MapPoint(0.354f, 0.013f),
                        MapPoint(0.386f, 0.013f),
                        MapPoint(0.419f, 0.027f),
                        MapPoint(0.415f, 0.053f),
                        MapPoint(0.402f, 0.081f),
                        MapPoint(0.399f, 0.112f),
                        MapPoint(0.387f, 0.142f),
                        MapPoint(0.365f, 0.162f),
                        MapPoint(0.337f, 0.171f),
                        MapPoint(0.309f, 0.162f),
                        MapPoint(0.291f, 0.141f),
                        MapPoint(0.282f, 0.112f),
                        MapPoint(0.277f, 0.080f),
                    ),
                labelAnchor = MapPoint(0.344f, 0.091f),
            ),
            GameMapRegion(
                id = "europe",
                name = "Europa",
                polygon =
                    listOf(
                        MapPoint(0.440f, 0.139f),
                        MapPoint(0.455f, 0.126f),
                        MapPoint(0.475f, 0.125f),
                        MapPoint(0.494f, 0.127f),
                        MapPoint(0.514f, 0.125f),
                        MapPoint(0.536f, 0.137f),
                        MapPoint(0.548f, 0.157f),
                        MapPoint(0.549f, 0.176f),
                        MapPoint(0.544f, 0.195f),
                        MapPoint(0.531f, 0.205f),
                        MapPoint(0.517f, 0.203f),
                        MapPoint(0.506f, 0.193f),
                        MapPoint(0.495f, 0.186f),
                        MapPoint(0.486f, 0.198f),
                        MapPoint(0.470f, 0.204f),
                        MapPoint(0.456f, 0.195f),
                        MapPoint(0.448f, 0.176f),
                    ).shiftedBy(dy = 0.018f),
                labelAnchor = MapPoint(0.500f, 0.167f).shiftedBy(dy = 0.018f),
            ),
            GameMapRegion(
                id = "africa",
                name = "Afrika",
                polygon =
                    listOf(
                        MapPoint(0.446f, 0.259f),
                        MapPoint(0.469f, 0.248f),
                        MapPoint(0.498f, 0.249f),
                        MapPoint(0.526f, 0.252f),
                        MapPoint(0.547f, 0.265f),
                        MapPoint(0.567f, 0.291f),
                        MapPoint(0.579f, 0.324f),
                        MapPoint(0.585f, 0.362f),
                        MapPoint(0.590f, 0.403f),
                        MapPoint(0.592f, 0.444f),
                        MapPoint(0.590f, 0.487f),
                        MapPoint(0.585f, 0.531f),
                        MapPoint(0.577f, 0.575f),
                        MapPoint(0.565f, 0.620f),
                        MapPoint(0.548f, 0.664f),
                        MapPoint(0.531f, 0.702f),
                        MapPoint(0.513f, 0.719f),
                        MapPoint(0.499f, 0.691f),
                        MapPoint(0.490f, 0.648f),
                        MapPoint(0.479f, 0.607f),
                        MapPoint(0.470f, 0.563f),
                        MapPoint(0.464f, 0.516f),
                        MapPoint(0.459f, 0.472f),
                        MapPoint(0.454f, 0.425f),
                        MapPoint(0.450f, 0.378f),
                        MapPoint(0.447f, 0.327f),
                        MapPoint(0.444f, 0.286f),
                    ).shiftedBy(dy = 0.022f),
                labelAnchor = MapPoint(0.528f, 0.460f).shiftedBy(dy = 0.022f),
            ),
            GameMapRegion(
                id = "asia",
                name = "Asien",
                polygon =
                    listOf(
                        MapPoint(0.522f, 0.131f),
                        MapPoint(0.554f, 0.128f),
                        MapPoint(0.582f, 0.124f),
                        MapPoint(0.612f, 0.128f),
                        MapPoint(0.646f, 0.117f),
                        MapPoint(0.678f, 0.104f),
                        MapPoint(0.710f, 0.092f),
                        MapPoint(0.742f, 0.086f),
                        MapPoint(0.772f, 0.101f),
                        MapPoint(0.806f, 0.093f),
                        MapPoint(0.839f, 0.098f),
                        MapPoint(0.873f, 0.107f),
                        MapPoint(0.910f, 0.109f),
                        MapPoint(0.946f, 0.120f),
                        MapPoint(0.979f, 0.124f),
                        MapPoint(0.995f, 0.145f),
                        MapPoint(0.987f, 0.164f),
                        MapPoint(0.966f, 0.176f),
                        MapPoint(0.948f, 0.197f),
                        MapPoint(0.923f, 0.216f),
                        MapPoint(0.900f, 0.240f),
                        MapPoint(0.887f, 0.266f),
                        MapPoint(0.878f, 0.296f),
                        MapPoint(0.878f, 0.331f),
                        MapPoint(0.872f, 0.367f),
                        MapPoint(0.853f, 0.389f),
                        MapPoint(0.830f, 0.389f),
                        MapPoint(0.810f, 0.369f),
                        MapPoint(0.792f, 0.346f),
                        MapPoint(0.780f, 0.320f),
                        MapPoint(0.760f, 0.307f),
                        MapPoint(0.740f, 0.318f),
                        MapPoint(0.724f, 0.348f),
                        MapPoint(0.708f, 0.383f),
                        MapPoint(0.689f, 0.395f),
                        MapPoint(0.671f, 0.378f),
                        MapPoint(0.659f, 0.343f),
                        MapPoint(0.650f, 0.312f),
                        MapPoint(0.633f, 0.279f),
                        MapPoint(0.618f, 0.247f),
                        MapPoint(0.602f, 0.221f),
                        MapPoint(0.579f, 0.208f),
                        MapPoint(0.554f, 0.190f),
                        MapPoint(0.536f, 0.166f),
                    ).shiftedBy(dy = 0.018f),
                labelAnchor = MapPoint(0.778f, 0.225f).shiftedBy(dy = 0.018f),
            ),
            GameMapRegion(
                id = "australia",
                name = "Australien",
                polygon =
                    listOf(
                        MapPoint(0.785f, 0.680f),
                        MapPoint(0.815f, 0.670f),
                        MapPoint(0.844f, 0.676f),
                        MapPoint(0.872f, 0.693f),
                        MapPoint(0.894f, 0.718f),
                        MapPoint(0.901f, 0.753f),
                        MapPoint(0.896f, 0.790f),
                        MapPoint(0.884f, 0.827f),
                        MapPoint(0.862f, 0.846f),
                        MapPoint(0.835f, 0.838f),
                        MapPoint(0.811f, 0.823f),
                        MapPoint(0.792f, 0.793f),
                        MapPoint(0.781f, 0.757f),
                        MapPoint(0.778f, 0.718f),
                    ).shiftedBy(dy = 0.010f),
                labelAnchor = MapPoint(0.840f, 0.756f).shiftedBy(dy = 0.010f),
            ),
        ).map { region -> region.fromMapContentToCanvas() }
}

/**
 * Rendert die interaktive Spielkarte mit sichtbarem Kartenbild,
 * Polygon-Hitflächen und sichtbaren Regions-Overlays.
 *
 * Pan und Pinch-Zoom orientieren sich direkt am Finger-Centroid. Dadurch bleibt
 * die Kartenbewegung nahe am erwarteten Verhalten klassischer Map-UIs.
 */
@Composable
fun InteractiveGameMap(
    regions: List<GameMapRegion>,
    selectedRegionId: String?,
    onRegionSelected: (GameMapRegion) -> Unit,
    modifier: Modifier = Modifier,
    regionStates: Map<String, GameMapRegionState> = emptyMap(),
    backgroundPainter: Painter? = null,
    aspectRatio: Float = DEFAULT_MAP_ASPECT_RATIO,
) {
    var viewportState by remember { mutableStateOf(MapViewportState()) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val layoutMetrics =
        remember(viewportSize, aspectRatio) {
            createMapLayoutMetrics(viewportSize = viewportSize, aspectRatio = aspectRatio)
        }

    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clipToBounds(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(regions, layoutMetrics, viewportState) {
                        detectTapGestures { tapOffset ->
                            val tappedRegion =
                                findRegionAtScreenPoint(
                                    regions = regions,
                                    tapPoint = tapOffset,
                                    layoutMetrics = layoutMetrics,
                                    viewportState = viewportState,
                                )

                            if (tappedRegion != null) {
                                onRegionSelected(tappedRegion)
                            }
                        }
                    }
                    .pointerInput(layoutMetrics) {
                        detectTransformGestures(
                            panZoomLock = false,
                        ) { centroid, pan, zoom, _ ->
                            viewportState =
                                updateViewportForGesture(
                                    current = viewportState,
                                    centroid = centroid,
                                    pan = pan,
                                    zoomChange = zoom,
                                    layoutMetrics = layoutMetrics,
                                )
                        }
                    },
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = "Interaktive Spielkarte"
                        }
                        .testTag("game_map_canvas"),
            ) {
                drawGameMap(
                    regions = regions,
                    regionStates = regionStates,
                    selectedRegionId = selectedRegionId,
                    layoutMetrics = layoutMetrics,
                    viewportState = viewportState,
                    backgroundPainter = backgroundPainter,
                )
            }

            regions.forEach { region ->
                val labelPosition =
                    mapPointToScreenOffset(
                        point = region.labelAnchor,
                        layoutMetrics = layoutMetrics,
                        viewportState = viewportState,
                    )

                if (
                    labelPosition.isFinite() &&
                    labelPosition.isWithin(layoutMetrics.viewportSize)
                ) {
                    RegionStatusButton(
                        region = region,
                        state = regionStates[region.id],
                        isSelected = region.id == selectedRegionId,
                        onClick = { onRegionSelected(region) },
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    IntOffset(
                                        x = (labelPosition.x - 18.dp.toPx()).roundToInt(),
                                        y = (labelPosition.y - 18.dp.toPx()).roundToInt(),
                                    )
                                }
                                .testTag("region_button_${region.id}"),
                    )
                }
            }
        }
    }
}

/**
 * Zeigt die aktuell ausgewählte Region als kompaktes Overlay an.
 */
@Composable
fun MapSelectionOverlay(
    selectedRegion: GameMapRegion?,
    selectedRegionState: GameMapRegionState?,
    modifier: Modifier = Modifier,
) {
    if (selectedRegion == null) {
        return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        color = MapOverlaySurfaceColor,
        contentColor = MapOverlayContentColor,
        border = BorderStroke(1.dp, MapOverlayBorderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Ausgewählt",
                style = MaterialTheme.typography.labelSmall,
                color = MapOverlayContentColor,
            )
            Text(
                text = selectedRegion.name,
                style = MaterialTheme.typography.titleSmall,
                color = MapOverlayContentColor,
            )
            if (selectedRegionState != null) {
                Text(
                    text = "${selectedRegionState.ownerName} · ${selectedRegionState.troopCount} Truppen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MapOverlayContentColor,
                )
            }
        }
    }
}

/**
 * Berechnet die sichtbare Kartenfläche innerhalb des verfügbaren Viewports.
 *
 * Die Karte wird immer mit festem Seitenverhältnis so skaliert, dass sie den
 * gesamten Viewport abdeckt.
 */
internal fun createMapLayoutMetrics(
    viewportSize: IntSize,
    aspectRatio: Float,
): MapLayoutMetrics {
    if (viewportSize.width == 0 || viewportSize.height == 0) {
        return MapLayoutMetrics(
            viewportSize = Size.Zero,
            mapSize = Size.Zero,
            mapOrigin = Offset.Zero,
        )
    }

    val viewportWidth = viewportSize.width.toFloat()
    val viewportHeight = viewportSize.height.toFloat()
    val viewportRatio = viewportWidth / viewportHeight

    val mapSize =
        if (viewportRatio > aspectRatio) {
            val width = viewportWidth
            Size(width = width, height = width / aspectRatio)
        } else {
            val height = viewportHeight
            Size(width = height * aspectRatio, height = height)
        }

    return MapLayoutMetrics(
        viewportSize = Size(viewportWidth, viewportHeight),
        mapSize = mapSize,
        mapOrigin =
            Offset(
                x = (viewportWidth - mapSize.width) / 2f,
                y = (viewportHeight - mapSize.height) / 2f,
            ),
    )
}

/**
 * Überführt eine Transform-Geste in einen neuen Karten-Viewport.
 *
 * Die Berechnung hält den Kartenpunkt unter dem Gesture-Centroid beim Zoomen
 * möglichst stabil. Das reduziert das typische "Wegspringen" der Karte.
 */
internal fun updateViewportForGesture(
    current: MapViewportState,
    centroid: Offset,
    pan: Offset,
    zoomChange: Float,
    layoutMetrics: MapLayoutMetrics,
): MapViewportState {
    if (layoutMetrics.mapSize == Size.Zero) {
        return current
    }

    val oldScale = current.scale
    val newScale = (oldScale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val scaleFactor = newScale / oldScale
    val relativeCentroid = centroid - layoutMetrics.mapOrigin
    val rawOffset =
        relativeCentroid -
            ((relativeCentroid - current.offset) * scaleFactor) +
            pan

    return MapViewportState(
        scale = newScale,
        offset = clampOffset(rawOffset, layoutMetrics = layoutMetrics, scale = newScale),
    )
}

/**
 * Ermittelt die erste Region, deren Polygon den gegebenen Bildschirmpunkt
 * enthält.
 */
internal fun findRegionAtScreenPoint(
    regions: List<GameMapRegion>,
    tapPoint: Offset,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
): GameMapRegion? {
    val normalizedPoint =
        screenOffsetToNormalizedMapPoint(
            screenPoint = tapPoint,
            layoutMetrics = layoutMetrics,
            viewportState = viewportState,
        ) ?: return null

    return regions.firstOrNull { region ->
        regionContainsPoint(region = region, point = normalizedPoint)
    }
}

/**
 * Transformiert einen Bildschirmpunkt in normalisierte Kartenkoordinaten.
 */
internal fun screenOffsetToNormalizedMapPoint(
    screenPoint: Offset,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
): MapPoint? {
    if (layoutMetrics.mapSize == Size.Zero) {
        return null
    }

    val localPoint =
        (screenPoint - layoutMetrics.mapOrigin - viewportState.offset) / viewportState.scale

    val normalizedX = localPoint.x / layoutMetrics.mapSize.width
    val normalizedY = localPoint.y / layoutMetrics.mapSize.height

    if (normalizedX !in 0f..1f || normalizedY !in 0f..1f) {
        return null
    }

    return MapPoint(normalizedX, normalizedY)
}

/**
 * Punkt-in-Polygon-Test für Regions-Hitdetection.
 */
internal fun regionContainsPoint(
    region: GameMapRegion,
    point: MapPoint,
): Boolean {
    var inside = false
    val polygon = region.polygon

    for (index in polygon.indices) {
        val current = polygon[index]
        val next = polygon[(index + 1) % polygon.size]

        val crossesEdge =
            (current.y > point.y) != (next.y > point.y) &&
                point.x <
                ((next.x - current.x) * (point.y - current.y) / ((next.y - current.y) + 0.00001f)) +
                current.x

        if (crossesEdge) {
            inside = !inside
        }
    }

    return inside
}

/**
 * Transformiert einen normalisierten Kartenpunkt in Bildschirmkoordinaten.
 */
internal fun mapPointToScreenOffset(
    point: MapPoint,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
): Offset {
    if (layoutMetrics.mapSize == Size.Zero) {
        return Offset.Unspecified
    }

    return Offset(
        x =
            layoutMetrics.mapOrigin.x +
                viewportState.offset.x +
                (point.x * layoutMetrics.mapSize.width * viewportState.scale),
        y =
            layoutMetrics.mapOrigin.y +
                viewportState.offset.y +
                (point.y * layoutMetrics.mapSize.height * viewportState.scale),
    )
}

/**
 * Begrenzt die Kartenverschiebung auf den sichtbaren Kartenbereich.
 *
 * Dadurch bleibt die Karte innerhalb ihrer fachlich definierten Grenzen und
 * kann nicht aus dem Viewport "herausgezogen" werden.
 */
internal fun clampOffset(
    offset: Offset,
    layoutMetrics: MapLayoutMetrics,
    scale: Float,
): Offset {
    val bounds = calculateOffsetBounds(layoutMetrics, scale)
    return Offset(
        x = offset.x.coerceIn(bounds.first.x, bounds.second.x),
        y = offset.y.coerceIn(bounds.first.y, bounds.second.y),
    )
}

/**
 * Liefert die erlaubten Minimal- und Maximalwerte für den Kartenoffset.
 */
internal fun calculateOffsetBounds(
    layoutMetrics: MapLayoutMetrics,
    scale: Float,
): Pair<Offset, Offset> {
    val scaledWidth = layoutMetrics.mapSize.width * scale
    val scaledHeight = layoutMetrics.mapSize.height * scale

    val horizontalBounds =
        calculateAxisOffsetBounds(
            scaledMapSize = scaledWidth,
            viewportSize = layoutMetrics.viewportSize.width,
            mapOrigin = layoutMetrics.mapOrigin.x,
        )
    val verticalBounds =
        calculateAxisOffsetBounds(
            scaledMapSize = scaledHeight,
            viewportSize = layoutMetrics.viewportSize.height,
            mapOrigin = layoutMetrics.mapOrigin.y,
        )

    return Offset(horizontalBounds.first, verticalBounds.first) to
        Offset(horizontalBounds.second, verticalBounds.second)
}

private fun calculateAxisOffsetBounds(
    scaledMapSize: Float,
    viewportSize: Float,
    mapOrigin: Float,
): Pair<Float, Float> {
    if (scaledMapSize <= viewportSize) {
        return 0f to 0f
    }

    val minOffset = viewportSize - mapOrigin - scaledMapSize
    val maxOffset = -mapOrigin
    return minOffset to maxOffset
}

@Composable
private fun RegionStatusButton(
    region: GameMapRegion,
    state: GameMapRegionState?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) MapOverlayContentColor else MapOverlaySurfaceColor
    val contentColor = if (isSelected) MapOverlayInverseColor else MapOverlayContentColor

    FilledTonalButton(
        onClick = onClick,
        modifier =
            modifier
                .size(36.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.5.dp, MapOverlayBorderColor),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
    ) {
        Text(
            text = state?.troopCount?.toString() ?: "0",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun DrawScope.drawGameMap(
    regions: List<GameMapRegion>,
    regionStates: Map<String, GameMapRegionState>,
    selectedRegionId: String?,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
    backgroundPainter: Painter?,
) {
    val hasBackgroundImage = backgroundPainter != null

    if (!hasBackgroundImage) {
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color(0xFF0B1B29),
                            Color(0xFF183B54),
                            Color(0xFF2C6172),
                        ),
                ),
            size = layoutMetrics.viewportSize,
        )
    }

    if (layoutMetrics.mapSize == Size.Zero) {
        return
    }

    withTransform({
        translate(
            left = layoutMetrics.mapOrigin.x + viewportState.offset.x,
            top = layoutMetrics.mapOrigin.y + viewportState.offset.y,
        )
        scale(
            scaleX = viewportState.scale,
            scaleY = viewportState.scale,
            pivot = Offset.Zero,
        )
    }) {
        if (backgroundPainter != null) {
            with(backgroundPainter) {
                draw(size = layoutMetrics.mapSize, alpha = 0.96f)
            }
        } else {
            drawPlaceholderWorldMap(mapSize = layoutMetrics.mapSize)
        }
    }

    val strokeBase = (2.1f * viewportState.scale).coerceAtMost(5.4f)

    regions.forEach { region ->
        val path =
            region.toScreenPath(
                layoutMetrics = layoutMetrics,
                viewportState = viewportState,
            )
        val regionState = regionStates[region.id]
        val isSelected = region.id == selectedRegionId
        val fillColor = regionState?.accentColor ?: Color(0xFFD8D0B7)
        val fillAlpha =
            if (hasBackgroundImage) {
                if (isSelected) 0.58f else 0.28f
            } else {
                if (isSelected) 0.92f else 0.76f
            }

        drawPath(
            path = path,
            color = fillColor,
            style = Fill,
            alpha = fillAlpha,
        )
        drawPath(
            path = path,
            color = if (isSelected) Color(0xFFFCE0A4) else Color(0xFF102230),
            style = Stroke(width = strokeBase),
            alpha = if (isSelected) 0.95f else 0.78f,
        )
    }
}

private fun DrawScope.drawPlaceholderWorldMap(
    mapSize: Size,
) {
    val worldFramePath =
        Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = mapSize.width,
                    bottom = mapSize.height,
                    radiusX = 18f,
                    radiusY = 18f,
                ),
            )
        }

    drawPath(
        path = worldFramePath,
        brush =
            Brush.linearGradient(
                colors =
                    listOf(
                        Color(0xFF173D63),
                        Color(0xFF285D85),
                        Color(0xFF4E8AA1),
                    ),
            ),
        style = Fill,
        alpha = 0.92f,
    )

    val continentFill = Color(0xFFE5D3A1)
    val continentStroke = Color(0xFF6B5837)

    listOf(
        listOf(
            MapPoint(0.08f, 0.23f),
            MapPoint(0.16f, 0.12f),
            MapPoint(0.24f, 0.14f),
            MapPoint(0.28f, 0.24f),
            MapPoint(0.23f, 0.34f),
            MapPoint(0.18f, 0.44f),
            MapPoint(0.21f, 0.58f),
            MapPoint(0.16f, 0.76f),
            MapPoint(0.10f, 0.63f),
            MapPoint(0.08f, 0.43f),
        ),
        listOf(
            MapPoint(0.31f, 0.18f),
            MapPoint(0.42f, 0.13f),
            MapPoint(0.56f, 0.14f),
            MapPoint(0.66f, 0.21f),
            MapPoint(0.69f, 0.31f),
            MapPoint(0.61f, 0.38f),
            MapPoint(0.52f, 0.34f),
            MapPoint(0.49f, 0.44f),
            MapPoint(0.42f, 0.42f),
            MapPoint(0.37f, 0.31f),
        ),
        listOf(
            MapPoint(0.53f, 0.46f),
            MapPoint(0.61f, 0.49f),
            MapPoint(0.68f, 0.58f),
            MapPoint(0.65f, 0.74f),
            MapPoint(0.57f, 0.82f),
            MapPoint(0.50f, 0.73f),
            MapPoint(0.49f, 0.58f),
        ),
        listOf(
            MapPoint(0.73f, 0.62f),
            MapPoint(0.79f, 0.66f),
            MapPoint(0.82f, 0.74f),
            MapPoint(0.75f, 0.79f),
            MapPoint(0.70f, 0.71f),
        ),
        listOf(
            MapPoint(0.78f, 0.18f),
            MapPoint(0.91f, 0.16f),
            MapPoint(0.95f, 0.24f),
            MapPoint(0.91f, 0.35f),
            MapPoint(0.84f, 0.38f),
            MapPoint(0.76f, 0.31f),
        ),
    ).forEach { polygon ->
        val path =
            Path().apply {
                polygon.forEachIndexed { index, point ->
                    val screenPoint =
                        Offset(
                            x = point.x * mapSize.width,
                            y = point.y * mapSize.height,
                        )
                    if (index == 0) {
                        moveTo(screenPoint.x, screenPoint.y)
                    } else {
                        lineTo(screenPoint.x, screenPoint.y)
                    }
                }
                close()
            }

        drawPath(
            path = path,
            color = continentFill,
            style = Fill,
            alpha = 0.7f,
        )
        drawPath(
            path = path,
            color = continentStroke,
            style = Stroke(width = 1.4f),
            alpha = 0.8f,
        )
    }
}

private fun GameMapRegion.toScreenPath(
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
): Path =
    Path().apply {
        polygon.forEachIndexed { index, point ->
            val screenPoint =
                mapPointToScreenOffset(
                    point = point,
                    layoutMetrics = layoutMetrics,
                    viewportState = viewportState,
                )

            if (index == 0) {
                moveTo(screenPoint.x, screenPoint.y)
            } else {
                lineTo(screenPoint.x, screenPoint.y)
            }
        }
        close()
    }

private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

private fun Offset.isWithin(size: Size): Boolean =
    x in -150f..(size.width + 150f) && y in -60f..(size.height + 60f)
