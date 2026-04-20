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
    private val mapAspectRatio = 2540f / 1346f

    @Test
    fun create_map_layout_metrics_covers_available_viewport() {
        val metrics =
            createMapLayoutMetrics(viewportSize = IntSize(1000, 600), aspectRatio = mapAspectRatio)

        assertFloatEquals(1132.244f, metrics.mapSize.width)
        assertFloatEquals(600f, metrics.mapSize.height)
        assertFloatEquals(-66.122f, metrics.mapOrigin.x)
        assertFloatEquals(0f, metrics.mapOrigin.y)
    }

    @Test
    fun update_viewport_for_gesture_clamps_pan_to_map_bounds() {
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(1000f, 600f),
                mapSize = Size(1132.244f, 600f),
                mapOrigin = Offset(-66.122f, 0f),
            )

        val updated =
            updateViewportForGesture(
                current = MapViewportState(scale = 2f, offset = Offset.Zero),
                centroid = Offset(500f, 300f),
                pan = Offset(800f, 800f),
                zoomChange = 1f,
                layoutMetrics = metrics,
            )

        assertFloatEquals(66.122f, updated.offset.x)
        assertFloatEquals(0f, updated.offset.y)
    }

    @Test
    fun calculate_offset_bounds_allow_full_pan_to_eastern_edge() {
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(1000f, 600f),
                mapSize = Size(1132.244f, 600f),
                mapOrigin = Offset(-66.122f, 0f),
            )

        val bounds = calculateOffsetBounds(layoutMetrics = metrics, scale = 2f)

        assertFloatEquals(-1198.366f, bounds.first.x)
        assertFloatEquals(-600f, bounds.first.y)
        assertFloatEquals(66.122f, bounds.second.x)
        assertFloatEquals(0f, bounds.second.y)
    }

    @Test
    fun screen_offset_to_normalized_map_point_returns_null_outside_visible_map() {
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(1000f, 600f),
                mapSize = Size(1132.244f, 600f),
                mapOrigin = Offset(-66.122f, 0f),
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
            createMapLayoutMetrics(viewportSize = IntSize(1000, 600), aspectRatio = mapAspectRatio)
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
        assertEquals("north_america", region.id)
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

private fun assertFloatEquals(
    expected: Float,
    actual: Float,
    tolerance: Float = 0.001f,
) {
    assertTrue(
        actual in (expected - tolerance)..(expected + tolerance),
        "Expected $expected but was $actual",
    )
}
