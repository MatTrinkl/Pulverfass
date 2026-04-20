package at.aau.pulverfass.app.ui.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import kotlin.math.roundToInt

private const val MAP_IMAGE_WIDTH_PX = 2540
private const val MAP_IMAGE_HEIGHT_PX = 1346
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val TERRITORY_OVERLAY_ALPHA = 0.64f
private val MapOverlaySurfaceColor = Color.White
private val MapOverlayBorderColor = Color.Black
private val MapOverlayContentColor = Color.Black
private val MapOverlayInverseColor = Color.White

/**
 * Normalisierter Punkt im Kartenkoordinatensystem.
 *
 * Die Koordinaten liegen unabhängig von der Bildschirmgröße im Bereich `0..1`
 * und referenzieren Positionen relativ zur kompletten Kartenbildfläche.
 */
data class MapPoint(
    val x: Float,
    val y: Float,
)

/**
 * Beschreibt eine Territory-Region der Spielkarte.
 *
 * [maskResId] verweist auf die transparente Territory-Maske. [idMapColorRgb]
 * ist die exakte RGB-Farbe aus `map_region_id.png`, die für Hitdetection
 * verwendet wird. Andere Farben werden bewusst ignoriert.
 */
data class GameMapRegion(
    val id: String,
    val name: String,
    @param:DrawableRes val maskResId: Int,
    val idMapColorRgb: Int,
    val fallbackAnchor: MapPoint = MapPoint(0.5f, 0.5f),
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

private data class TerritoryRenderAssets(
    val overlay: ImageBitmap,
    val anchors: Map<String, MapPoint>,
)

/**
 * Statische Frontend-Daten für die aktuelle Demo-Karte.
 *
 * Die Territories liegen bewusst im App-Modul, bis fachliche Kartendaten aus
 * den anderen Modulen kommen. Die präzisen Grenzen kommen jetzt aus Bildern
 * statt aus hart codierten Polygonen.
 */
object PulverfassMapDefaults {
    val aspectRatio: Float =
        MAP_IMAGE_WIDTH_PX.toFloat() / MAP_IMAGE_HEIGHT_PX.toFloat()

    val regions: List<GameMapRegion> =
        listOf(
            GameMapRegion(
                "america",
                "Amerika",
                R.drawable.territory_america,
                rgbKey(0xE6, 0x19, 0x4B),
            ),
            GameMapRegion(
                "canada",
                "Kanada",
                R.drawable.territory_canada,
                rgbKey(0x3C, 0xB4, 0x4B),
            ),
            GameMapRegion(
                "mexico",
                "Mexiko",
                R.drawable.territory_mexico,
                rgbKey(0xFF, 0xE1, 0x19),
            ),
            GameMapRegion(
                "greenland",
                "Grönland",
                R.drawable.territory_greenland,
                rgbKey(0x43, 0x63, 0xD8),
            ),
            GameMapRegion(
                "british_islands",
                "Britische Inseln",
                R.drawable.territory_british_islands,
                rgbKey(0xF5, 0x82, 0x31),
            ),
            GameMapRegion(
                "scandinavia",
                "Skandinavien",
                R.drawable.territory_scandinavia,
                rgbKey(0x91, 0x1E, 0xB4),
            ),
            GameMapRegion(
                "west_europe",
                "Westeuropa",
                R.drawable.territory_west_europe,
                rgbKey(0x46, 0xF0, 0xF0),
            ),
            GameMapRegion(
                "central_europe",
                "Mitteleuropa",
                R.drawable.territory_central_europe,
                rgbKey(0xF0, 0x32, 0xE6),
            ),
            GameMapRegion(
                "russia",
                "Russland",
                R.drawable.territory_russia,
                rgbKey(0xBC, 0xF6, 0x0C),
            ),
            GameMapRegion(
                "siberia",
                "Sibirien",
                R.drawable.territory_siberia,
                rgbKey(0xFA, 0xBE, 0xBE),
            ),
            GameMapRegion(
                "east_siberia",
                "Ostsibirien",
                R.drawable.territory_east_siberia,
                rgbKey(0x00, 0x80, 0x80),
            ),
            GameMapRegion(
                "china",
                "China",
                R.drawable.territory_china,
                rgbKey(0xE6, 0xBE, 0xFF),
            ),
            GameMapRegion(
                "japan",
                "Japan",
                R.drawable.territory_japan,
                rgbKey(0x9A, 0x63, 0x24),
            ),
            GameMapRegion(
                "orient",
                "Orient",
                R.drawable.territory_orient,
                rgbKey(0xFF, 0xFA, 0xC8),
            ),
            GameMapRegion(
                "middle_east",
                "Mittlerer Osten",
                R.drawable.territory_middle_east,
                rgbKey(0x80, 0x00, 0x00),
            ),
            GameMapRegion(
                "egypt",
                "Ägypten",
                R.drawable.territory_egypt,
                rgbKey(0xAA, 0xFF, 0xC3),
            ),
            GameMapRegion(
                "west_africa",
                "Westafrika",
                R.drawable.territory_west_africa,
                rgbKey(0x80, 0x80, 0x00),
            ),
            GameMapRegion(
                "central_africa",
                "Zentralafrika",
                R.drawable.territory_central_africa,
                rgbKey(0xFF, 0xD8, 0xB1),
            ),
            GameMapRegion(
                "south_africa",
                "Südafrika",
                R.drawable.territory_south_africa,
                rgbKey(0x00, 0x00, 0x75),
            ),
            GameMapRegion(
                "brazil",
                "Brasilien",
                R.drawable.territory_brazil,
                rgbKey(0xA9, 0xA9, 0xA9),
            ),
            GameMapRegion(
                "andean_community",
                "Andengemeinschaft",
                R.drawable.territory_andean_community,
                rgbKey(0xFF, 0x8C, 0x00),
            ),
            GameMapRegion(
                "argentina",
                "Argentinien",
                R.drawable.territory_argentina,
                rgbKey(0x8B, 0x00, 0x8B),
            ),
            GameMapRegion(
                "australia",
                "Australien",
                R.drawable.territory_australia,
                rgbKey(0x00, 0xCE, 0xD1),
            ),
            GameMapRegion(
                "oceania",
                "Ozeanien",
                R.drawable.territory_oceania,
                rgbKey(0xDC, 0x14, 0x3C),
            ),
        )
}

/**
 * Rendert die interaktive Spielkarte mit Hintergrundbild, einfärbbaren
 * Territory-Masken, Region-ID-Hitmap und Truppenzählern.
 */
@Composable
fun InteractiveGameMap(
    regions: List<GameMapRegion>,
    selectedRegionId: String?,
    onRegionSelected: (GameMapRegion) -> Unit,
    modifier: Modifier = Modifier,
    regionStates: Map<String, GameMapRegionState> = emptyMap(),
    backgroundPainter: Painter? = null,
    aspectRatio: Float = PulverfassMapDefaults.aspectRatio,
    @DrawableRes regionIdMapResId: Int = R.drawable.map_region_id,
) {
    var viewportState by remember { mutableStateOf(MapViewportState()) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val resources = context.resources
    val regionIdBitmap =
        remember(resources, regionIdMapResId) {
            BitmapFactory.decodeResource(resources, regionIdMapResId)
        }
    val regionTintColors =
        regions.associate { region ->
            region.id to (regionStates[region.id]?.accentColor ?: Color(0xFF8F8F8F))
        }
    val territoryRenderAssets =
        remember(resources, regions, regionTintColors) {
            buildTerritoryRenderAssets(
                resources = resources,
                regions = regions,
                regionTintColors = regionTintColors,
            )
        }

    val layoutMetrics =
        remember(viewportSize, aspectRatio) {
            createMapLayoutMetrics(viewportSize = viewportSize, aspectRatio = aspectRatio)
        }

    Box(
        modifier =
            modifier
                .background(Color.White)
                .clipToBounds(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(regions, layoutMetrics, viewportState, regionIdBitmap) {
                        detectTapGestures { tapOffset ->
                            val tappedRegion =
                                findRegionAtScreenPoint(
                                    regions = regions,
                                    tapPoint = tapOffset,
                                    layoutMetrics = layoutMetrics,
                                    viewportState = viewportState,
                                    regionIdBitmap = regionIdBitmap,
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
                    territoryOverlay = territoryRenderAssets.overlay,
                    layoutMetrics = layoutMetrics,
                    viewportState = viewportState,
                    backgroundPainter = backgroundPainter,
                )
            }

            regions.forEach { region ->
                val anchor = territoryRenderAssets.anchors[region.id] ?: region.fallbackAnchor
                val labelPosition =
                    mapPointToScreenOffset(
                        point = anchor,
                        layoutMetrics = layoutMetrics,
                        viewportState = viewportState,
                    )

                if (
                    labelPosition.isFinite() &&
                    labelPosition.isWithin(layoutMetrics.viewportSize)
                ) {
                    RegionTroopCounter(
                        state = regionStates[region.id],
                        isSelected = region.id == selectedRegionId,
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
 * Berechnet die sichtbare Kartenfläche innerhalb des verfügbaren Viewports.
 *
 * Die Karte wird mit festem Seitenverhältnis so skaliert, dass sie den
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
 * möglichst stabil. Das reduziert das typische Wegspringen der Karte.
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
 * Ermittelt die Region unter einem Bildschirmpunkt über die technische ID-Map.
 */
internal fun findRegionAtScreenPoint(
    regions: List<GameMapRegion>,
    tapPoint: Offset,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
    regionIdBitmap: Bitmap,
): GameMapRegion? {
    val imagePixel =
        screenOffsetToImagePixel(
            screenPoint = tapPoint,
            layoutMetrics = layoutMetrics,
            viewportState = viewportState,
            imageSize = IntSize(regionIdBitmap.width, regionIdBitmap.height),
        ) ?: return null

    val pixelRgb = regionIdBitmap.getPixel(imagePixel.x, imagePixel.y) and RGB_MASK
    return findRegionByIdColor(regions = regions, pixelRgb = pixelRgb)
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

internal fun screenOffsetToImagePixel(
    screenPoint: Offset,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
    imageSize: IntSize,
): IntOffset? {
    val normalizedPoint =
        screenOffsetToNormalizedMapPoint(
            screenPoint = screenPoint,
            layoutMetrics = layoutMetrics,
            viewportState = viewportState,
        ) ?: return null

    val pixelX = (normalizedPoint.x * imageSize.width).toInt().coerceIn(0, imageSize.width - 1)
    val pixelY = (normalizedPoint.y * imageSize.height).toInt().coerceIn(0, imageSize.height - 1)
    return IntOffset(pixelX, pixelY)
}

internal fun findRegionByIdColor(
    regions: List<GameMapRegion>,
    pixelRgb: Int,
): GameMapRegion? =
    regions.firstOrNull { region ->
        region.idMapColorRgb == (pixelRgb and RGB_MASK)
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

internal fun calculateMaskCenter(
    width: Int,
    height: Int,
    alphaAt: (x: Int, y: Int) -> Int,
): MapPoint? {
    require(width > 0)
    require(height > 0)

    var visibleCount = 0L
    var sumX = 0.0
    var sumY = 0.0

    for (y in 0 until height) {
        for (x in 0 until width) {
            if (alphaAt(x, y) > 1) {
                visibleCount++
                sumX += x.toDouble()
                sumY += y.toDouble()
            }
        }
    }

    if (visibleCount == 0L) {
        return null
    }

    return MapPoint(
        x = (sumX / visibleCount / (width - 1).coerceAtLeast(1)).toFloat(),
        y = (sumY / visibleCount / (height - 1).coerceAtLeast(1)).toFloat(),
    )
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
private fun RegionTroopCounter(
    state: GameMapRegionState?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) MapOverlayContentColor else MapOverlaySurfaceColor
    val contentColor = if (isSelected) MapOverlayInverseColor else MapOverlayContentColor

    Surface(
        modifier =
            modifier
                .size(36.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.5.dp, MapOverlayBorderColor),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = state?.troopCount?.toString() ?: "0",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun DrawScope.drawGameMap(
    territoryOverlay: ImageBitmap,
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
    backgroundPainter: Painter?,
) {
    if (backgroundPainter == null) {
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

    withMapTransform(layoutMetrics = layoutMetrics, viewportState = viewportState) {
        if (backgroundPainter != null) {
            with(backgroundPainter) {
                draw(size = layoutMetrics.mapSize, alpha = 0.96f)
            }
        }

        drawImage(
            image = territoryOverlay,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(territoryOverlay.width, territoryOverlay.height),
            dstOffset = IntOffset.Zero,
            dstSize =
                IntSize(
                    width = layoutMetrics.mapSize.width.roundToInt(),
                    height = layoutMetrics.mapSize.height.roundToInt(),
                ),
        )
    }
}

private fun DrawScope.withMapTransform(
    layoutMetrics: MapLayoutMetrics,
    viewportState: MapViewportState,
    block: DrawScope.() -> Unit,
) {
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
        block()
    }
}

private fun buildTerritoryRenderAssets(
    resources: Resources,
    regions: List<GameMapRegion>,
    regionTintColors: Map<String, Color>,
): TerritoryRenderAssets {
    var targetWidth = MAP_IMAGE_WIDTH_PX
    var targetHeight = MAP_IMAGE_HEIGHT_PX
    var overlayPixels = IntArray(targetWidth * targetHeight)
    val anchors = mutableMapOf<String, MapPoint>()

    regions.forEach { region ->
        val bitmap = BitmapFactory.decodeResource(resources, region.maskResId) ?: return@forEach
        try {
            if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                targetWidth = bitmap.width
                targetHeight = bitmap.height
                overlayPixels = IntArray(targetWidth * targetHeight)
            }

            val maskPixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(maskPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val tintColor = regionTintColors[region.id] ?: Color(0xFF8F8F8F)
            val tintRgb = tintColor.toArgb() and RGB_MASK
            var visibleCount = 0L
            var sumX = 0.0
            var sumY = 0.0
            var index = 0

            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val alpha = (maskPixels[index] ushr 24) and 0xFF
                    if (alpha > 1) {
                        visibleCount++
                        sumX += x.toDouble()
                        sumY += y.toDouble()
                        overlayPixels[index] =
                            ((alpha * TERRITORY_OVERLAY_ALPHA).roundToInt() shl 24) or tintRgb
                    }
                    index++
                }
            }

            anchors[region.id] =
                if (visibleCount > 0L) {
                    MapPoint(
                        x = (sumX / visibleCount / (bitmap.width - 1).coerceAtLeast(1)).toFloat(),
                        y = (sumY / visibleCount / (bitmap.height - 1).coerceAtLeast(1)).toFloat(),
                    )
                } else {
                    region.fallbackAnchor
                }
        } finally {
            bitmap.recycle()
        }
    }

    val overlayBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    overlayBitmap.setPixels(overlayPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    return TerritoryRenderAssets(
        overlay = overlayBitmap.asImageBitmap(),
        anchors = anchors,
    )
}

private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

private fun Offset.isWithin(size: Size): Boolean =
    x in -150f..(size.width + 150f) && y in -60f..(size.height + 60f)

private const val RGB_MASK = 0x00FFFFFF

private fun rgbKey(
    red: Int,
    green: Int,
    blue: Int,
): Int = ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)
