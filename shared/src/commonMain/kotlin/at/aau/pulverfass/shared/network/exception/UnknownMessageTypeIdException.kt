package at.aau.pulverfass.shared.network.exception

/**
 * Wird geworfen, wenn eine numerische MessageType-ID nicht bekannt ist.
 *
 * @param id unbekannte numerische Kennung
 */
class UnknownMessageTypeIdException(
    id: Int,
) : NetworkException("Unknown MessageType id: $id")
