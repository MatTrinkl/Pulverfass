package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId

/**
 * Wird geworfen, wenn eine EntityId doppelt registriert wird.
 *
 * @param entityId ID der Entity, die bereits registriert ist
 */
class DuplicateEntityIdException(
    entityId: EntityId,
) : IllegalStateException("Eine Entity mit der ID $entityId ist bereits registriert.")

/**
 * Wird geworfen, wenn eine PlayerId doppelt registriert wird.
 *
 * @param playerId die doppelte ID
 */
class DuplicatePlayerIdException(
    playerId: PlayerId,
) : IllegalStateException("Ein Player mit der ID $playerId ist bereits registriert.")

/**
 * Wird geworfen, wenn eine Entity bereits einem anderen Spieler zugeordnet ist.
 *
 * @param entityId ID der betroffenen Entity
 * @param currentOwner aktueller Besitzer der Entity
 * @param newOwner neuer Spieler, dem die Entity zugeordnet werden sollte
 */
class EntityAlreadyAssignedException(
    entityId: EntityId,
    currentOwner: PlayerId,
    newOwner: PlayerId,
) : IllegalStateException("Entity $entityId gehoert bereits zu $currentOwner, nicht zu $newOwner.")

/**
 * Wird geworfen, wenn zu einer EntityId keine Entity gefunden wird.
 *
 * @param entityId ID der gesuchten Entity
 */
class EntityNotFoundException(
    entityId: EntityId,
) : NoSuchElementException("Keine Entity mit der ID $entityId gefunden.")

/**
 * Wird geworfen, wenn ein Player nicht gefunden wird.
 *
 * @param playerId die gesuchte ID
 */
class PlayerNotFoundException(
    playerId: PlayerId,
) : NoSuchElementException("Kein Player mit der ID $playerId gefunden.")

/**
 * Wird geworfen, wenn eine ConnectionId bereits einem anderen Player zugeordnet ist.
 *
 * @param connectionId die betroffene ConnectionId
 */
class DuplicateConnectionIdException(
    val connectionId: ConnectionId,
) : IllegalStateException("Die Connection ID $connectionId ist bereits einem Player zugeordnet.")

/**
 * Wird geworfen, wenn eine EntityId bereits einem anderen Player zugeordnet ist.
 *
 * @param entityId die betroffene EntityId
 */
class DuplicatePlayerEntityIdException(
    val entityId: EntityId,
) : IllegalStateException("Die Entity ID $entityId ist bereits einem Player zugeordnet.")
