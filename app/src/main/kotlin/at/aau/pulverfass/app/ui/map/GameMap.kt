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

private const val MAP_IMAGE_WIDTH_PX = 3840
private const val MAP_IMAGE_HEIGHT_PX = 2160
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val TERRITORY_OVERLAY_ALPHA = 0.78f
private val MapOverlaySurfaceColor = Color.White
private val MapOverlayBorderColor = Color.Black
private val MapOverlayContentColor = Color.Black
private val MapOverlayInverseColor = Color.White

/**
 * Normalisierter Punkt im Kartenkoordinatensystem.
 *
 * Die Koordinaten liegen unabhängig von der Bildschirmgröße im Bereich `0..1`
 * und referenzieren Positionen relativ zur kompletten Kartenbildfläche.
 *
 * @param x horizontale Position relativ zur Kartenbreite
 * @param y vertikale Position relativ zur Kartenhöhe
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
 *
 * @param id Android-interne Region-ID der Bitmap-Assets
 * @param name lesbarer Gebietsname für UI und Debugging
 * @param maskResId transparente Maske des Gebiets
 * @param idMapColorRgb eindeutige RGB-Farbe in der technischen ID-Map
 * @param fallbackAnchor Ersatzposition für Labels, falls eine Maske leer ist
 */
data class GameMapRegion(
    val id: String,
    val name: String,
    @param:DrawableRes val maskResId: Int,
    val idMapColorRgb: Int,
    val fallbackAnchor: MapPoint = MapPoint(0.5f, 0.5f),
)

/**
 * Hält den reinen Frontend-Zustand einer Region für die Kartenansicht.
 *
 * @param ownerPlayerId Spieler-ID des Besitzers oder `neutral`
 * @param ownerName Anzeigename des Besitzers
 * @param troopCount sichtbare Truppenanzahl
 * @param accentColor Farbe des Besitzers für die Territory-Maske
 */
data class GameMapRegionState(
    val ownerPlayerId: String,
    val ownerName: String,
    val troopCount: Int,
    val accentColor: Color,
)

/**
 * Aktueller Viewport der Karte aus Zoomstufe und Verschiebung.
 *
 * @param scale aktuelle Zoomstufe
 * @param offset aktuelle Verschiebung der Karte im Bildschirmraum
 */
data class MapViewportState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

/**
 * Abgeleitete Layoutdaten für die Kartenprojektion im aktuellen Canvas.
 *
 * @param viewportSize Größe der sichtbaren Compose-Fläche
 * @param mapSize Größe der eingepassten Karte vor Zoom
 * @param mapOrigin linke obere Position der eingepassten Karte im Viewport
 */
data class MapLayoutMetrics(
    val viewportSize: Size,
    val mapSize: Size,
    val mapOrigin: Offset,
)

/**
 * Konfiguration für die konkrete Karteninstanz.
 *
 * [backgroundPainter] ist das sichtbare Weltkartenbild. [regionIdMapResId] ist
 * dagegen eine technische, unsichtbare Farbhitmap: Jeder klickbare Bereich hat
 * dort eine eindeutige RGB-Farbe. Dadurch muss die App beim Tippen keine
 * komplexen Polygone testen, sondern liest genau einen Pixel aus.
 *
 * @param backgroundPainter sichtbares Kartenbild
 * @param aspectRatio Seitenverhältnis der Kartenassets
 * @param regionIdMapResId technische Farbhitmap für Hitdetection
 */
data class InteractiveGameMapOptions(
    val backgroundPainter: Painter? = null,
    val aspectRatio: Float = PulverfassMapDefaults.aspectRatio,
    @param:DrawableRes val regionIdMapResId: Int = R.drawable.map_region_id,
)

/**
 * Vorberechnete Bitmap-Daten für eine Compose-Renderphase.
 *
 * [overlay] enthält alle Territory-Masken bereits eingefärbt und zusammengelegt.
 * [anchors] sind automatisch berechnete Schwerpunkte der Masken und dienen als
 * Position für Truppenzähler. Das verhindert, dass Labels von Hand gepflegt
 * werden müssen, solange die Masken sauber sind.
 */
private data class TerritoryRenderAssets(
    val overlay: ImageBitmap,
    val anchors: Map<String, MapPoint>,
)

/**
 * Statische Frontend-Daten für die aktuelle Spielkarte.
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
                rgbKey(0xD3, 0x3C, 0x51),
            ),
            GameMapRegion(
                "canada",
                "Kanada",
                R.drawable.territory_canada,
                rgbKey(0x66, 0xAD, 0x56),
            ),
            GameMapRegion(
                "mexico",
                "Mexiko",
                R.drawable.territory_mexico,
                rgbKey(0xF8, 0xE2, 0x58),
            ),
            GameMapRegion(
                "greenland",
                "Grönland",
                R.drawable.territory_greenland,
                rgbKey(0x4E, 0x64, 0xA5),
            ),
            GameMapRegion(
                "british_islands",
                "Britische Inseln",
                R.drawable.territory_british_islands,
                rgbKey(0xE0, 0x8D, 0x4C),
            ),
            GameMapRegion(
                "scandinavia",
                "Skandinavien",
                R.drawable.territory_scandinavia,
                rgbKey(0x78, 0x3D, 0x87),
            ),
            GameMapRegion(
                "west_europe",
                "Westeuropa",
                R.drawable.territory_west_europe,
                rgbKey(0xA6, 0xCF, 0xD4),
            ),
            GameMapRegion(
                "central_europe",
                "Mitteleuropa",
                R.drawable.territory_central_europe,
                rgbKey(0xA5, 0x61, 0x9A),
            ),
            GameMapRegion(
                "russia",
                "Russland",
                R.drawable.territory_russia,
                rgbKey(0xAC, 0xC5, 0x49),
            ),
            GameMapRegion(
                "siberia",
                "Sibirien",
                R.drawable.territory_siberia,
                rgbKey(0xEB, 0xC3, 0xC2),
            ),
            GameMapRegion(
                "east_siberia",
                "Ostsibirien",
                R.drawable.territory_east_siberia,
                rgbKey(0x35, 0x7E, 0x84),
            ),
            GameMapRegion(
                "china",
                "China",
                R.drawable.territory_china,
                rgbKey(0xD1, 0xC1, 0xD8),
            ),
            GameMapRegion(
                "japan",
                "Japan",
                R.drawable.territory_japan,
                rgbKey(0x8A, 0x5F, 0x33),
            ),
            GameMapRegion(
                "orient",
                "Orient",
                R.drawable.territory_orient,
                rgbKey(0xFA, 0xF4, 0xD6),
            ),
            GameMapRegion(
                "middle_east",
                "Mittlerer Osten",
                R.drawable.territory_middle_east,
                rgbKey(0x76, 0x29, 0x20),
            ),
            GameMapRegion(
                "egypt",
                "Ägypten",
                R.drawable.territory_egypt,
                rgbKey(0xCD, 0xE0, 0xCC),
            ),
            GameMapRegion(
                "west_africa",
                "Westafrika",
                R.drawable.territory_west_africa,
                rgbKey(0x82, 0x82, 0x3C),
            ),
            GameMapRegion(
                "central_africa",
                "Zentralafrika",
                R.drawable.territory_central_africa,
                rgbKey(0xF3, 0xD8, 0xBC),
            ),
            GameMapRegion(
                "south_africa",
                "Südafrika",
                R.drawable.territory_south_africa,
                rgbKey(0x19, 0x1D, 0x4F),
            ),
            GameMapRegion(
                "brazil",
                "Brasilien",
                R.drawable.territory_brazil,
                rgbKey(0xAB, 0xAB, 0xAB),
            ),
            GameMapRegion(
                "andean_community",
                "Andengemeinschaft",
                R.drawable.territory_andean_community,
                rgbKey(0xE1, 0x8E, 0x37),
            ),
            GameMapRegion(
                "argentina",
                "Argentinien",
                R.drawable.territory_argentina,
                rgbKey(0x75, 0x33, 0x7D),
            ),
            GameMapRegion(
                "australia",
                "Australien",
                R.drawable.territory_australia,
                rgbKey(0x71, 0xB8, 0xC1),
            ),
            GameMapRegion(
                "oceania",
                "Ozeanien",
                R.drawable.territory_oceania,
                rgbKey(0xCC, 0x39, 0x41),
            ),
        )
}

/**
 * Rendert die interaktive Spielkarte mit Hintergrundbild, einfärbbaren
 * Territory-Masken, Region-ID-Hitmap und Truppenzählern.
 *
 * @param regions bekannte Kartenregionen mit Masken und ID-Farben
 * @param selectedRegionId aktuell ausgewählte Android-Region-ID
 * @param onRegionSelected Callback nach erfolgreicher Farbhitmap-Selektion
 * @param modifier Compose-Modifier der Kartenfläche
 * @param regionStates serverbasierter Zustand pro Android-Region-ID
 * @param options Render- und Hitmap-Konfiguration
 */
@Composable
fun InteractiveGameMap(
    regions: List<GameMapRegion>,
    selectedRegionId: String?,
    onRegionSelected: (GameMapRegion) -> Unit,
    modifier: Modifier = Modifier,
    regionStates: Map<String, GameMapRegionState> = emptyMap(),
    options: InteractiveGameMapOptions = InteractiveGameMapOptions(),
) {
    var viewportState by remember { mutableStateOf(MapViewportState()) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val resources = context.resources

    /*
     * Die ID-Map wird nie gezeichnet. Sie ist nur ein Lookup-Bild für Eingaben:
     * Tap -> Kartenpixel -> RGB-Farbe -> GameMapRegion.
     */
    val regionIdBitmap =
        remember(resources, options.regionIdMapResId) {
            BitmapFactory.decodeResource(resources, options.regionIdMapResId)
        }

    /*
     * Die Owner-Farben kommen aus dem serverautoritativen GameState. Für noch
     * neutrale oder unbekannte Territories bleibt die Karte bewusst grau.
     */
    val regionTintColors =
        regions.associate { region ->
            region.id to (regionStates[region.id]?.accentColor ?: Color(0xFF8F8F8F))
        }

    /*
     * Masken werden nur neu zusammengesetzt, wenn sich Regionliste oder Farben
     * ändern. Zoom/Pan löst dadurch kein teures Bitmap-Rebuild aus.
     */
    val territoryRenderAssets =
        remember(resources, regions, regionTintColors) {
            buildTerritoryRenderAssets(
                resources = resources,
                regions = regions,
                regionTintColors = regionTintColors,
            )
        }

    val layoutMetrics =
        remember(viewportSize, options.aspectRatio) {
            createMapLayoutMetrics(viewportSize = viewportSize, aspectRatio = options.aspectRatio)
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
                        /*
                         * Die sichtbare Karte und die technische ID-Map teilen
                         * sich dasselbe Koordinatensystem. Darum genügt eine
                         * Rückprojektion vom Bildschirmpunkt auf den Pixel der
                         * ID-Map, um das angetippte Territory eindeutig zu finden.
                         */
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
                        /*
                         * Pan und Pinch-Zoom verändern ausschließlich den
                         * Viewport. Die fachliche Auswahl bleibt im Reducer und
                         * wird nicht mit Gestenlogik vermischt.
                         */
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
                    backgroundPainter = options.backgroundPainter,
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
                    /*
                     * Der Counter liegt als Compose-Element über dem Canvas,
                     * damit Text nicht in die Bitmap gerendert werden muss und
                     * Test-Tags für UI-Tests stabil bleiben.
                     */
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
 *
 * @param viewportSize verfügbare Pixelgröße des Compose-Containers
 * @param aspectRatio Seitenverhältnis der Karte
 * @return berechnete Größe und Position der Karte im Container
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
 *
 * @param current bisheriger Viewport
 * @param centroid Mittelpunkt der aktuellen Multitouch-Geste
 * @param pan Verschiebung seit dem letzten Gesture-Frame
 * @param zoomChange Zoomfaktor seit dem letzten Gesture-Frame
 * @param layoutMetrics aktuelle Kartenprojektion
 * @return neuer geklemmter Viewport
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
 *
 * Ablauf der Farbauswahl:
 * 1. Bildschirmkoordinate in normalisierte Kartenkoordinate zurückrechnen.
 * 2. Normalisierte Kartenkoordinate auf Pixel der ID-Map übertragen.
 * 3. RGB-Wert des Pixels lesen.
 * 4. RGB-Wert exakt mit [GameMapRegion.idMapColorRgb] vergleichen.
 *
 * Antialiasing-Farben oder transparente Ränder werden nicht toleriert. Das ist
 * Absicht: Nur die eigens gepflegte ID-Map entscheidet, nicht die sichtbare
 * Weltkarte mit ihren Verläufen und Linien.
 *
 * @param regions bekannte Kartenregionen mit technischen RGB-Schlüsseln
 * @param tapPoint Tap-Position im Bildschirmraum der Compose-Fläche
 * @param layoutMetrics aktuelle Kartenprojektion
 * @param viewportState aktueller Zoom- und Pan-Zustand
 * @param regionIdBitmap unsichtbare Farbhitmap
 * @return angetippte Region oder `null`, wenn der Tap außerhalb liegt
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
 *
 * @param screenPoint Position im Bildschirmraum der Compose-Fläche
 * @param layoutMetrics aktuelle Kartenprojektion
 * @param viewportState aktueller Zoom- und Pan-Zustand
 * @return normalisierte Kartenposition oder `null` außerhalb der Karte
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
 * Wandelt einen Bildschirmtap in einen konkreten Bitmap-Pixel um.
 *
 * Diese Funktion ist der eigentliche "Raycast" der 2D-Karte. Statt einen Strahl
 * in eine 3D-Szene zu schicken, projizieren wir den Eingabepunkt rückwärts durch
 * Zoom, Pan und Kartenlayout auf das Quellbild. Das Ergebnis ist ein einzelner
 * Pixel in `map_region_id.png`.
 *
 * @param screenPoint Position im Bildschirmraum der Compose-Fläche
 * @param layoutMetrics aktuelle Kartenprojektion
 * @param viewportState aktueller Zoom- und Pan-Zustand
 * @param imageSize Größe des Zielbildes, meistens der ID-Map
 * @return Pixelkoordinate im Zielbild oder `null` außerhalb der Karte
 */
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

/**
 * Sucht die Region, deren technische ID-Farbe exakt zum gelesenen Pixel passt.
 *
 * Es wird nur RGB verglichen, Alpha wird vorher maskiert. Dadurch funktionieren
 * ID-Map-Bilder zuverlässig, egal ob Android beim Laden intern einen Alpha-Kanal
 * ergänzt.
 *
 * @param regions bekannte Kartenregionen
 * @param pixelRgb gelesener RGB-Wert aus der ID-Map
 * @return passende Region oder `null` bei unbekannter Farbe
 */
internal fun findRegionByIdColor(
    regions: List<GameMapRegion>,
    pixelRgb: Int,
): GameMapRegion? =
    regions.firstOrNull { region ->
        region.idMapColorRgb == (pixelRgb and RGB_MASK)
    }

/**
 * Transformiert einen normalisierten Kartenpunkt in Bildschirmkoordinaten.
 *
 * @param point normalisierte Kartenposition
 * @param layoutMetrics aktuelle Kartenprojektion
 * @param viewportState aktueller Zoom- und Pan-Zustand
 * @return Bildschirmposition für Overlays
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
 * @param offset gewünschte Verschiebung
 * @param layoutMetrics aktuelle Kartenprojektion
 * @param scale aktuelle Zoomstufe
 * @return erlaubte Verschiebung innerhalb der Kartenränder
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
 *
 * @param layoutMetrics aktuelle Kartenprojektion
 * @param scale aktuelle Zoomstufe
 * @return Paar aus minimalem und maximalem Offset
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

/**
 * Berechnet den Schwerpunkt einer Alpha-Maske als normalisierten Kartenpunkt.
 *
 * @param width Breite der Maske in Pixeln
 * @param height Höhe der Maske in Pixeln
 * @param alphaAt Zugriff auf den Alpha-Wert eines Maskenpixels
 * @return Schwerpunkt der sichtbaren Pixel oder `null` bei leerer Maske
 */
internal fun calculateMaskCenter(
    width: Int,
    height: Int,
    alphaAt: (x: Int, y: Int) -> Int,
): MapPoint? {
    require(width > 0)
    require(height > 0)

    /*
     * Schwerpunkt der sichtbaren Maskenpixel. Das ist robuster als manuelle
     * Labelkoordinaten, weil neue Masken automatisch sinnvolle Counterpositionen
     * liefern. Einzelne Ausreißer an der Küste fallen durch die Mittelung kaum
     * ins Gewicht.
     */
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

    /*
     * Jede Territory-Maske ist ein transparentes Bild, dessen sichtbare Pixel
     * genau das Gebiet beschreiben. Beim Rebuild färben wir diese Pixel mit der
     * aktuellen Owner-Farbe ein und schreiben sie in ein gemeinsames Overlay.
     */
    regions.forEach { region ->
        val bitmap = BitmapFactory.decodeResource(resources, region.maskResId) ?: return@forEach
        try {
            if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                /*
                 * Fallback für Asset-Sets mit abweichender Zielgröße. In der
                 * aktuellen Karte sollten alle Masken gleich groß sein; falls
                 * nicht, bleibt der Renderer trotzdem konsistent.
                 */
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

            /*
             * Alpha kommt aus der Maske, RGB aus dem GameState. So bleiben
             * Küsten/Antialiasing der Maske erhalten, während Besitzwechsel nur
             * die Farbe austauschen.
             */
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
