package at.aau.pulverfass.client.network.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

actual fun defaultWebSocketHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(WebSockets)
    }
