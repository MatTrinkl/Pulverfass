package at.aau.pulverfass.app.network

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Feste Client-seitige Verbindung-ID für die technische Transportpipeline.
 *
 * Der Android-Client verwaltet lokal genau eine aktive WebSocket-Verbindung.
 * Deshalb reicht für den technischen Send/Receive-Pfad eine konstante
 * ConnectionId als Zuordnungsanker.
 */
val CLIENT_CONNECTION_ID: ConnectionId = ConnectionId(1)
