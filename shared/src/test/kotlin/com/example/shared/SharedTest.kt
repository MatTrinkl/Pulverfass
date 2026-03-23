package com.example.shared

import kotlin.test.assertEquals
import org.junit.Test

class SharedTest {
    @Test
    fun `HealthResponse has correct default message`() {
        val response = HealthResponse(status = "UP")
        assertEquals("UP", response.status)
        assertEquals("OK", response.message)
    }

    @Test
    fun `ApiRoutes health route is correct`() {
        assertEquals("/health", ApiRoutes.HEALTH)
    }

    @Test
    fun `Constants app version is defined`() {
        assertEquals("1.0.0", Constants.APP_VERSION)
    }

    @Test
    fun `Constants default port is 8080`() {
        assertEquals(8080, Constants.DEFAULT_PORT)
    }
}
