package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.ids.DuplicateConnectionIdException
import at.aau.pulverfass.server.ids.DuplicatePlayerEntityIdException
import at.aau.pulverfass.server.ids.DuplicatePlayerIdException
import at.aau.pulverfass.server.ids.PlayerNotFoundException
import at.aau.pulverfass.server.player.Player
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Zentrale Verwaltung für alle registrierten Player.
 *
 * Der PlayerManager ist für folgende Aufgaben zuständig:
 * - neue Player registrieren
 * - Player per ID finden
 * - Player entfernen
 * - Player optional über ConnectionId oder EntityId finden
 *
 */
object PlayerManager {
    private val players: MutableMap<PlayerId, Player> = mutableMapOf()

    /**
     * Registriert einen neuen Player.
     *
     * Doppelte PlayerIds sind nicht erlaubt.
     */
    fun register(player: Player) {
        if (players.containsKey(player.playerId)) {
            throw DuplicatePlayerIdException(player.playerId)
        }

        if (player.connectionId != null && getByConnectionId(player.connectionId) != null) {
            throw DuplicateConnectionIdException(player.connectionId)
        }

        if (player.entityId != null && getByEntityId(player.entityId) != null) {
            throw DuplicatePlayerEntityIdException(player.entityId)
        }

        players[player.playerId] = player
    }

    /**
     * Gibt einen Player anhand seiner ID zurück.
     *
     * Falls kein Player gefunden wird, wird null zurückgegeben.
     */
    fun get(playerId: PlayerId): Player? = players[playerId]

    /**
     * Gibt einen Player anhand seiner ID zurück.
     *
     * Falls kein Player gefunden wird, wird eine Exception geworfen.
     */
    fun require(playerId: PlayerId): Player =
        players[playerId] ?: throw PlayerNotFoundException(playerId)

    /**
     * Entfernt einen Player anhand seiner ID.
     *
     * Falls kein Player existiert, wird null zurückgegeben.
     */
    fun remove(playerId: PlayerId): Player? = players.remove(playerId)

    /**
     * Prüft, ob ein Player mit dieser ID existiert.
     */
    fun contains(playerId: PlayerId): Boolean = players.containsKey(playerId)

    /**
     * Gibt einen Player über seine ConnectionId zurück.
     *
     * Falls kein passender Player gefunden wird, wird null zurückgegeben.
     */
    fun getByConnectionId(connectionId: ConnectionId): Player? =
        players.values.firstOrNull { it.connectionId == connectionId }

    /**
     * Gibt einen Player über seine EntityId zurück.
     *
     * Falls kein passender Player gefunden wird, wird null zurückgegeben.
     */
    fun getByEntityId(entityId: EntityId): Player? =
        players.values.firstOrNull { it.entityId == entityId }

    /**
     * Gibt alle registrierten PlayerIds zurück.
     */
    fun allPlayerIds(): List<PlayerId> = players.keys.toList()

    /**
     * Gibt alle registrierten Player zurück.
     */
    fun all(): List<Player> = players.values.toList()

    /**
     * Leert den kompletten Manager.
     *
     * Vor allem für Tests hilfreich.
     */
    fun clear() {
        players.clear()
    }
}
