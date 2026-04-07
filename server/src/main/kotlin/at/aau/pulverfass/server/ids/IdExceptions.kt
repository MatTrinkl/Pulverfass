package at.aau.pulverfass.server.ids

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
