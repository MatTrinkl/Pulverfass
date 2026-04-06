package at.aau.pulverfass.server.player

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Fachliches Modell eines Spielers.
 *
 * Diese Klasse beschreibt den Spielerzustand auf Domänenebene
 * und ist bewusst getrennt von technischen Konzepten wie
 * konkreten Connection- oder Entity-Objekten.
 *
 * Optionale Verknüpfungen werden nur über IDs modelliert.
 */
data class Player(
    val playerId: PlayerId,
    val username: String? = null,
    val connectionId: ConnectionId? = null,
    val entityId: EntityId? = null,
)
