package at.aau.pulverfass.server.ids

import at.aau.pulverfass.shared.ids.EntityId
import at.aau.pulverfass.shared.ids.PlayerId


/**
 * Wird geworfen, wenn eine EntityId doppelt registriert wird.
 */
class DuplicateEntityIdException(
    entityId: EntityId,
) : IllegalStateException("Eine Entity mit der ID $entityId ist bereits registriert.")

/**
 * Wird geworfen, wenn eine Entity bereits einem anderen Player zugeordnet ist.
 */
class EntityAlreadyAssignedException(
    entityId: EntityId,
    currentOwner: PlayerId,
    newOwner: PlayerId,
) : IllegalStateException("Entity $entityId gehoert bereits zu $currentOwner, nicht zu $newOwner.")

/**
 * Wird geworfen, wenn eine Entity zu einer ID nicht gefunden wurde.
 */
class EntityNotFoundException(
    entityId: EntityId,
) : NoSuchElementException("Keine Entity mit der ID $entityId gefunden.")
