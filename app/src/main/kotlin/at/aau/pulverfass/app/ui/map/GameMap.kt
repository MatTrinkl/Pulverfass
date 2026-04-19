package at.aau.pulverfass.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_MAP_ASPECT_RATIO = 16f / 9f
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

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
 */
object PulverfassMapDefaults {
    val regions: List<GameMapRegion> =
        listOf(
            GameMapRegion(
                id = "northwest",
                name = "Nordwest",
                polygon =
                    listOf(
                        MapPoint(0.06f, 0.15f),
                        MapPoint(0.27f, 0.10f),
                        MapPoint(0.32f, 0.26f),
                        MapPoint(0.18f, 0.35f),
                        MapPoint(0.07f, 0.27f),
                    ),
                labelAnchor = MapPoint(0.18f, 0.21f),
            ),
            GameMapRegion(
                id = "northeast",
                name = "Nordost",
                polygon =
                    listOf(
                        MapPoint(0.37f, 0.11f),
                        MapPoint(0.59f, 0.08f),
                        MapPoint(0.72f, 0.17f),
                        MapPoint(0.68f, 0.33f),
                        MapPoint(0.45f, 0.30f),
                    ),
                labelAnchor = MapPoint(0.56f, 0.20f),
            ),
            GameMapRegion(
                id = "central",
                name = "Zentrum",
                polygon =
                    listOf(
                        MapPoint(0.26f, 0.31f),
                        MapPoint(0.49f, 0.27f),
                        MapPoint(0.60f, 0.42f),
                        MapPoint(0.52f, 0.58f),
                        MapPoint(0.31f, 0.55f),
                        MapPoint(0.22f, 0.42f),
                    ),
                labelAnchor = MapPoint(0.41f, 0.42f),
            ),
            GameMapRegion(
                id = "southwest",
                name = "Südwest",
                polygon =
                    listOf(
                        MapPoint(0.08f, 0.46f),
                        MapPoint(0.27f, 0.51f),
                        MapPoint(0.29f, 0.73f),
                        MapPoint(0.17f, 0.87f),
                        MapPoint(0.05f, 0.77f),
                    ),
                labelAnchor = MapPoint(0.17f, 0.65f),
            ),
            GameMapRegion(
                id = "southeast",
                name = "Südost",
                polygon =
                    listOf(
                        MapPoint(0.57f, 0.46f),
                        MapPoint(0.75f, 0.40f),
                        MapPoint(0.91f, 0.53f),
                        MapPoint(0.86f, 0.79f),
                        MapPoint(0.68f, 0.88f),
                        MapPoint(0.54f, 0.73f),
                    ),
                labelAnchor = MapPoint(0.73f, 0.63f),
            ),
        )
}

/**
 * Rendert die interaktive Spielkarte mit sichtbarem Kartenlayer,
 * Polygon-Hitflächen und sichtbaren Regionsbuttons.
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
                    .pointerInput(regions, layoutMetrics) {
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
                    selectedRegionId = selectedRegionId,
                    layoutMetrics = layoutMetrics,
                    viewportState = viewportState,
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
                    FilledTonalButton(
                        onClick = { onRegionSelected(region) },
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    IntOffset(
                                        x = (labelPosition.x - 56.dp.toPx()).roundToInt(),
                                        y = (labelPosition.y - 20.dp.toPx()).roundToInt(),
                                    )
                                }
                                .testTag("region_button_${region.id}"),
                    ) {
                        Text(text = region.name)
                    }
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
    modifier: Modifier = Modifier,
) {
    if (selectedRegion == null) {
        return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = "Ausgewählt: ${selectedRegion.name}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Berechnet die sichtbare Kartenfläche innerhalb des verfügbaren Viewports.
 *
 * Die Karte wird immer mit festem Seitenverhältnis eingepasst und zentriert.
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
            val height = viewportHeight
            Size(width = height * aspectRatio, height = height)
        } else {
            val width = viewportWidth
            Size(width = width, height = width / aspectRatio)
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

    val maxOffsetX = max((scaledWidth - layoutMetrics.viewportSize.width) / 2f, 0f)
    val maxOffsetY = max((scaledHeight - layoutMetrics.viewportSize.height) / 2f, 0f)

    return Offset(-maxOffsetX, -maxOffsetY) to Offset(maxOffsetX, maxOffsetY)
}

private fun DrawScope.drawGameMap(
    regions: List<GameMapRegion>,
    selectedRegionId: String?,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
) {
    drawRect(
        brush =
            Brush.linearGradient(
                colors =
                    listOf(
                        Color(0xFF0E2A47),
                        Color(0xFF1A4C71),
                        Color(0xFF3A6A71),
                    ),
            ),
        size = layoutMetrics.viewportSize,
    )

    if (layoutMetrics.mapSize == Size.Zero) {
        return
    }

    drawPlaceholderWorldMap(layoutMetrics = layoutMetrics, viewportState = viewportState)

    val strokeBase = (2.2f * viewportState.scale).coerceAtMost(6f)

    regions.forEach { region ->
        val path =
            region.toScreenPath(
                layoutMetrics = layoutMetrics,
                viewportState = viewportState,
            )

        val isSelected = region.id == selectedRegionId

        drawPath(
            path = path,
            color = if (isSelected) Color(0xFFE0A84D) else Color(0xFFBFC9A7),
            style = Fill,
            alpha = if (isSelected) 0.95f else 0.82f,
        )
        drawPath(
            path = path,
            color = if (isSelected) Color(0xFF2F1800) else Color(0xFF263238),
            style = Stroke(width = strokeBase),
        )
    }
}

private fun DrawScope.drawPlaceholderWorldMap(
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
) {
    val topLeft =
        mapPointToScreenOffset(
            point = MapPoint(0f, 0f),
            layoutMetrics = layoutMetrics,
            viewportState = viewportState,
        )

    val worldFramePath =
        Path().apply {
            addRoundRect(
                RoundRect(
                    left = topLeft.x,
                    top = topLeft.y,
                    right = topLeft.x + (layoutMetrics.mapSize.width * viewportState.scale),
                    bottom = topLeft.y + (layoutMetrics.mapSize.height * viewportState.scale),
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

        drawPath(
            path = path,
            color = continentFill,
            style = Fill,
            alpha = 0.7f,
        )
        drawPath(
            path = path,
            color = continentStroke,
            style = Stroke(width = (1.4f * viewportState.scale).coerceAtMost(4f)),
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
    x in -140f..(size.width + 140f) && y in -56f..(size.height + 56f)
