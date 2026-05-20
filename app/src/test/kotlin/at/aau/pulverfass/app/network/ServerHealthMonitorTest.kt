package at.aau.pulverfass.app.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerHealthMonitorTest {
    @Test
    fun health_url_points_to_requested_endpoint() {
        assertEquals(
            "http://5.189.160.80:8080/health",
            SERVER_HEALTH_URL,
        )
    }

    @Test
    fun success_status_with_ok_body_maps_to_ok() {
        assertEquals(
            ServerHealthStatus.OK,
            serverHealthStatusFromResponse(isSuccessStatus = true, body = "ok"),
        )
    }

    @Test
    fun success_status_with_trimmed_case_insensitive_ok_body_maps_to_ok() {
        assertEquals(
            ServerHealthStatus.OK,
            serverHealthStatusFromResponse(isSuccessStatus = true, body = " OK "),
        )
    }

    @Test
    fun success_status_with_non_ok_body_maps_to_error() {
        assertEquals(
            ServerHealthStatus.ERROR,
            serverHealthStatusFromResponse(isSuccessStatus = true, body = "error"),
        )
    }

    @Test
    fun failing_status_maps_to_error_even_with_ok_body() {
        assertEquals(
            ServerHealthStatus.ERROR,
            serverHealthStatusFromResponse(isSuccessStatus = false, body = "ok"),
        )
    }
}
