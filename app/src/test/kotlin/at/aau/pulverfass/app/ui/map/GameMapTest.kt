package at.aau.pulverfass.app.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameMapTest {
    private val mapAspectRatio = PulverfassMapDefaults.aspectRatio

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
    fun screen_offset_to_image_pixel_projects_to_bitmap_coordinates() {
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(1000f, 600f),
                mapSize = Size(1000f, 500f),
                mapOrigin = Offset.Zero,
            )

        val pixel =
            screenOffsetToImagePixel(
                screenPoint = Offset(500f, 250f),
                layoutMetrics = metrics,
                viewportState = MapViewportState(),
                imageSize = IntSize(2540, 1346),
            )

        assertNotNull(pixel)
        assertEquals(1270, pixel.x)
        assertEquals(673, pixel.y)
    }

    @Test
    fun find_region_by_exact_id_color_returns_region() {
        val region =
            findRegionByIdColor(
                regions = PulverfassMapDefaults.regions,
                pixelRgb = 0x00F032E6,
            )

        assertNotNull(region)
        assertEquals("central_europe", region.id)
    }

    @Test
    fun find_region_by_east_siberia_id_color_returns_east_siberia() {
        val region =
            findRegionByIdColor(
                regions = PulverfassMapDefaults.regions,
                pixelRgb = 0x00008080,
            )

        assertNotNull(region)
        assertEquals("east_siberia", region.id)
    }

    @Test
    fun find_region_by_antialiased_or_unknown_color_returns_null() {
        assertNull(
            findRegionByIdColor(
                regions = PulverfassMapDefaults.regions,
                pixelRgb = 0x00F032E5,
            ),
        )
        assertNull(
            findRegionByIdColor(
                regions = PulverfassMapDefaults.regions,
                pixelRgb = 0x00FFFFFF,
            ),
        )
    }

    @Test
    fun calculate_mask_center_uses_visible_pixels_only() {
        val center =
            calculateMaskCenter(
                width = 5,
                height = 5,
                alphaAt = { x, y ->
                    if ((x == 1 && y == 1) || (x == 3 && y == 3)) {
                        128
                    } else {
                        0
                    }
                },
            )

        assertNotNull(center)
        assertFloatEquals(0.5f, center.x)
        assertFloatEquals(0.5f, center.y)
    }

    @Test
    fun create_map_layout_metrics_returns_zero_metrics_for_zero_viewport() {
        val metrics =
            createMapLayoutMetrics(
                viewportSize = IntSize.Zero,
                aspectRatio = mapAspectRatio,
            )

        assertEquals(Size.Zero, metrics.viewportSize)
        assertEquals(Size.Zero, metrics.mapSize)
        assertEquals(Offset.Zero, metrics.mapOrigin)
    }

    @Test
    fun update_viewport_for_gesture_returns_current_when_map_size_is_zero() {
        val current = MapViewportState(scale = 1.25f, offset = Offset(10f, -8f))
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(400f, 300f),
                mapSize = Size.Zero,
                mapOrigin = Offset.Zero,
            )

        val updated =
            updateViewportForGesture(
                current = current,
                centroid = Offset(100f, 100f),
                pan = Offset(50f, 50f),
                zoomChange = 3f,
                layoutMetrics = metrics,
            )

        assertEquals(current, updated)
    }

    @Test
    fun update_viewport_for_gesture_clamps_zoom_to_limits() {
        val metrics =
            MapLayoutMetrics(
                viewportSize = Size(1000f, 600f),
                mapSize = Size(1132.244f, 600f),
                mapOrigin = Offset(-66.122f, 0f),
            )

        val zoomedOut =
            updateViewportForGesture(
                current = MapViewportState(scale = 1f, offset = Offset.Zero),
                centroid = Offset(500f, 300f),
                pan = Offset.Zero,
                zoomChange = 0.05f,
                layoutMetrics = metrics,
            )
        val zoomedIn =
            updateViewportForGesture(
                current = MapViewportState(scale = 5f, offset = Offset.Zero),
                centroid = Offset(500f, 300f),
                pan = Offset.Zero,
                zoomChange = 2f,
                layoutMetrics = metrics,
            )

        assertFloatEquals(1f, zoomedOut.scale)
        assertFloatEquals(5f, zoomedIn.scale)
    }

    @Test
    fun screen_offset_to_normalized_map_point_returns_null_for_zero_map_size() {
        val normalized =
            screenOffsetToNormalizedMapPoint(
                screenPoint = Offset(20f, 30f),
                layoutMetrics =
                    MapLayoutMetrics(
                        viewportSize = Size(100f, 100f),
                        mapSize = Size.Zero,
                        mapOrigin = Offset.Zero,
                    ),
                viewportState = MapViewportState(),
            )

        assertNull(normalized)
    }

    @Test
    fun screen_offset_to_image_pixel_clamps_to_image_edges() {
        val pixel =
            screenOffsetToImagePixel(
                screenPoint = Offset(1000f, 600f),
                layoutMetrics =
                    MapLayoutMetrics(
                        viewportSize = Size(1000f, 600f),
                        mapSize = Size(1000f, 600f),
                        mapOrigin = Offset.Zero,
                    ),
                viewportState = MapViewportState(),
                imageSize = IntSize(4, 3),
            )

        assertNotNull(pixel)
        assertEquals(3, pixel.x)
        assertEquals(2, pixel.y)
    }

    @Test
    fun map_point_to_screen_offset_returns_unspecified_for_zero_map_size() {
        val result =
            mapPointToScreenOffset(
                point = MapPoint(0.5f, 0.5f),
                layoutMetrics =
                    MapLayoutMetrics(
                        viewportSize = Size(200f, 100f),
                        mapSize = Size.Zero,
                        mapOrigin = Offset.Zero,
                    ),
                viewportState = MapViewportState(),
            )

        assertTrue(result.x.isNaN() && result.y.isNaN())
    }

    @Test
    fun clamp_offset_returns_zero_when_scaled_map_is_smaller_than_viewport() {
        val clamped =
            clampOffset(
                offset = Offset(500f, -500f),
                layoutMetrics =
                    MapLayoutMetrics(
                        viewportSize = Size(1000f, 600f),
                        mapSize = Size(500f, 300f),
                        mapOrigin = Offset(250f, 150f),
                    ),
                scale = 1f,
            )

        assertEquals(Offset.Zero, clamped)
    }

    @Test
    fun calculate_mask_center_returns_null_without_visible_pixels() {
        val center = calculateMaskCenter(width = 4, height = 4) { _, _ -> 0 }
        assertNull(center)
    }

    @Test
    fun calculate_mask_center_requires_positive_dimensions() {
        assertFailsWith<IllegalArgumentException> {
            calculateMaskCenter(width = 0, height = 1) { _, _ -> 255 }
        }
        assertFailsWith<IllegalArgumentException> {
            calculateMaskCenter(width = 1, height = 0) { _, _ -> 255 }
        }
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
