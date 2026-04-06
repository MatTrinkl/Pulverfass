package at.aau.pulverfass.server.player

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Repräsentiert einen Spieler im Spiel.
 *
 * @property playerId eindeutige ID des Spielers
 * @property username optionaler Name des Spielers
 * @property connectionId optionale Verbindung des Spielers
 * @property entityId optionale zugeordnete Entity des Spielers
 */
data class Player(
    val playerId: PlayerId,
    val username: String? = null,
    val connectionId: ConnectionId? = null,
    val entityId: EntityId? = null,
)
