package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

// Eindeutige ID für einen fachlichen Spieler.
@Serializable
@JvmInline
value class PlayerId(val value: Long)

// Eindeutige ID für eine spielinterne Entity (z. B. Einheit, Gebiet).
@Serializable
@JvmInline
value class EntityId(val value: Long)

// Eindeutige ID für eine technische Verbindung (z. B. WebSocket).
@Serializable
@JvmInline
value class ConnectionId(val value: Long)

// Eindeutige ID für ein Spiel.
@Serializable
@JvmInline
value class GameId(val value: Long)
