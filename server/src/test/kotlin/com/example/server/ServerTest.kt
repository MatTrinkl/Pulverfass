package com.example.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.Test

class ServerTest {
    @Test
    fun `health endpoint returns HTTP 200`() = testApplication {
        application {
            configurePlugins()
            configureRoutes()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `health endpoint response contains UP status`() = testApplication {
        application {
            configurePlugins()
            configureRoutes()
        }

        val response = client.get("/health")
        val body = response.bodyAsText()

        assertContains(body, "UP")
    }
}
