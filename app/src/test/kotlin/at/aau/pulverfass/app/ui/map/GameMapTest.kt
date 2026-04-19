package at.aau.pulverfass.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameMapTest {
    @Test
    fun create_map_layout_metrics_keeps_map_centered() {
        val metrics =
            createMapLayoutMetrics(viewportSize = IntSize(1000, 600), aspectRatio = 16f / 9f)

        assertEquals(Size(1000f, 562.5f), metrics.mapSize)
        assertEquals(Offset(0f, 18.75f), metrics.mapOrigin)
    }

    @Test
    fun update_viewport_for_gesture_clamps_pan_to_map_bounds() {
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(1000f, 600f),
                mapSize = Size(1000f, 562.5f),
                mapOrigin = Offset(0f, 18.75f),
            )

        val updated =
            updateViewportForGesture(
                current = MapViewportState(scale = 2f, offset = Offset.Zero),
                centroid = Offset(500f, 300f),
                pan = Offset(800f, 800f),
                zoomChange = 1f,
                layoutMetrics = metrics,
            )

        assertEquals(500f, updated.offset.x)
        assertEquals(262.5f, updated.offset.y)
    }

    @Test
    fun screen_offset_to_normalized_map_point_returns_null_outside_visible_map() {
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(1000f, 600f),
                mapSize = Size(1000f, 562.5f),
                mapOrigin = Offset(0f, 18.75f),
            )

        val point =
            screenOffsetToNormalizedMapPoint(
                screenPoint = Offset(1200f, 40f),
                layoutMetrics = metrics,
                viewportState = MapViewportState(),
            )

        assertNull(point)
    }

    @Test
    fun find_region_at_screen_point_detects_polygon_hit() {
        val metrics =
            createMapLayoutMetrics(viewportSize = IntSize(1000, 600), aspectRatio = 16f / 9f)
        val screenPoint =
            mapPointToScreenOffset(
                point = PulverfassMapDefaults.regions.first().labelAnchor,
                layoutMetrics = metrics,
                viewportState = MapViewportState(),
            )

        val region =
            findRegionAtScreenPoint(
                regions = PulverfassMapDefaults.regions,
                tapPoint = screenPoint,
                layoutMetrics = metrics,
                viewportState = MapViewportState(),
            )

        assertNotNull(region)
        assertEquals("northwest", region.id)
    }

    @Test
    fun region_contains_point_returns_false_for_external_point() {
        val contains =
            regionContainsPoint(
                region = PulverfassMapDefaults.regions.first(),
                point = MapPoint(0.9f, 0.9f),
            )

        assertTrue(!contains)
    }
}
