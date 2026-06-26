package at.aau.pulverfass.client.network.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

actual fun defaultWebSocketHttpClient(): HttpClient =
    HttpClient(Darwin) {
        install(WebSockets)
    }
