package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Wird geworfen, wenn eine Player-Entity keine PlayerEntity ist.
 *
 * @param entityId ID der gefundenen Entity
 */
class PlayerEntityTypeMismatchException(
    entityId: EntityId,
) : IllegalStateException("Die Entity mit der ID $entityId ist keine PlayerEntity.")

/**
 * Wird geworfen, wenn ein Player keine zugeordnete Entity besitzt.
 *
 * @param playerId ID des betroffenen Players
 */
class PlayerEntityBindingNotFoundException(
    playerId: PlayerId,
) : NoSuchElementException("Kein Entity-Binding fuer den Player mit der ID $playerId gefunden.")
