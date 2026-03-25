package com.example.androidapp

import com.example.shared.Constants
import kotlin.test.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `app version matches shared constant`() {
        assertEquals("1.0.0", Constants.APP_VERSION)
    }

    @Test
    fun `default server port is 8080`() {
        assertEquals(8080, Constants.DEFAULT_PORT)
    }
}
