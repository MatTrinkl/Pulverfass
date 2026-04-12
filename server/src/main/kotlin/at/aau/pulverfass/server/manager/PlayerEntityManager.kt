package at.aau.pulverfass.server.manager

import at.aau.pulverfass.server.entities.PlayerEntity
import at.aau.pulverfass.server.ids.IdFactory
import at.aau.pulverfass.server.ids.PlayerEntityBindingNotFoundException
import at.aau.pulverfass.server.ids.PlayerEntityTypeMismatchException
import at.aau.pulverfass.server.player.Player
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Integrationsmanager zwischen PlayerManager, EntityManager und dem ID-System.
 *
 * Der Manager kapselt den Standard-Flow für Spieler im Risiko-Backend:
 * - PlayerId erzeugen
 * - EntityId erzeugen
 * - Player und PlayerEntity erstellen
 * - beide Manager konsistent befüllen
 * - Rollback bei Fehlern sicherstellen
 */
object PlayerEntityManager {
    /**
     * Erzeugt einen neuen Player samt passender PlayerEntity und registriert beide konsistent.
     *
     * Falls während des Ablaufs ein Fehler auftritt, werden bereits angelegte Einträge
     * wieder entfernt, damit kein inkonsistenter Zustand entsteht.
     */
    fun createPlayer(
        username: String? = null,
        connectionId: ConnectionId? = null,
    ): Player =
        createPlayer(
            playerId = IdFactory.nextPlayerId(),
            entityId = IdFactory.nextEntityId(),
            username = username,
            connectionId = connectionId,
        )

    /**
     * Führt denselben Integrations-Flow mit vorgegebenen IDs aus.
     *
     * Diese Variante ist vor allem für Tests hilfreich, um Fehlerfälle gezielt
     * reproduzierbar zu prüfen.
     */
    internal fun createPlayer(
        playerId: PlayerId,
        entityId: EntityId,
        username: String? = null,
        connectionId: ConnectionId? = null,
    ): Player {
        val player =
            Player(
                playerId = playerId,
                username = username,
                connectionId = connectionId,
                entityId = entityId,
            )

        val playerEntity =
            PlayerEntity(
                entityId = entityId,
                playerId = playerId,
            )

        var playerRegistered = false
        var entityRegistered = false

        try {
            PlayerManager.register(player)
            playerRegistered = true

            EntityManager.register(playerEntity)
            entityRegistered = true

            return player
        } catch (exception: Exception) {
            if (entityRegistered) {
                EntityManager.remove(entityId)
            }

            if (playerRegistered) {
                PlayerManager.remove(playerId)
            }

            throw exception
        }
    }

    /**
     * Gibt die PlayerEntity zu einem Player zurück.
     *
     * Falls kein Binding existiert, wird null zurückgegeben.
     */
    fun getEntityByPlayerId(playerId: PlayerId): PlayerEntity? {
        val player = PlayerManager.get(playerId) ?: return null
        val entityId = player.entityId ?: return null
        val entity = EntityManager.get(entityId) ?: return null
        return entity as? PlayerEntity
    }

    /**
     * Gibt den Player zu einer PlayerEntity zurück.
     *
     * Falls kein Binding existiert, wird null zurückgegeben.
     */
    fun getPlayerByEntityId(entityId: EntityId): Player? {
        val entity = EntityManager.get(entityId) ?: return null
        val playerEntity = entity as? PlayerEntity ?: return null
        return PlayerManager.get(playerEntity.playerId)
    }

    /**
     * Gibt die PlayerEntity zu einem Player zurück.
     *
     * Falls kein Binding existiert, wird eine Exception geworfen.
     */
    fun requireEntityByPlayerId(playerId: PlayerId): PlayerEntity {
        return getEntityByPlayerId(playerId)
            ?: throw PlayerEntityBindingNotFoundException(playerId)
    }

    /**
     * Gibt den Player zu einer PlayerEntity zurück.
     *
     * Falls kein Binding existiert, wird eine Exception geworfen.
     */
    fun requirePlayerByEntityId(entityId: EntityId): Player {
        val playerEntity = requirePlayerEntity(entityId)
        return PlayerManager.require(playerEntity.playerId)
    }

    /**
     * Entfernt einen Player samt zugehöriger PlayerEntity über die PlayerId.
     *
     * Falls der Player nicht existiert, wird null zurückgegeben.
     */
    fun removeByPlayerId(playerId: PlayerId): Player? {
        val player = PlayerManager.remove(playerId) ?: return null
        player.entityId?.let { EntityManager.remove(it) }
        return player
    }

    /**
     * Entfernt einen Player samt zugehöriger PlayerEntity über die EntityId.
     *
     * Falls kein passendes Binding existiert, wird null zurückgegeben.
     */
    fun removeByEntityId(entityId: EntityId): Player? {
        val player = PlayerManager.getByEntityId(entityId) ?: return null
        EntityManager.remove(entityId)
        PlayerManager.remove(player.playerId)
        return player
    }

    private fun requirePlayerEntity(entityId: EntityId): PlayerEntity {
        val entity = EntityManager.require(entityId)
        return entity as? PlayerEntity ?: throw PlayerEntityTypeMismatchException(entityId)
    }
}
