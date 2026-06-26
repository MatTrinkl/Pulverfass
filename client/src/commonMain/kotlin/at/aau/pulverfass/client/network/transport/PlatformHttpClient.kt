package at.aau.pulverfass.client.network.transport

import io.ktor.client.HttpClient

/**
 * Ktor-Client mit plattformspezifischer Engine und installiertem
 * WebSockets-Plugin (Android: CIO, iOS: Darwin).
 */
expect fun defaultWebSocketHttpClient(): HttpClient
