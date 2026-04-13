package at.aau.pulverfass.shared.network.exception

/**
 * Wird geworfen, wenn die Serialisierung oder Deserialisierung einer
 * Netzwerknachricht fehlschlägt.
 *
 * @param message technische Fehlerbeschreibung
 * @param cause eigentliche Serialisierungsursache
 */
class NetworkSerializationException(
    message: String,
    cause: Throwable,
) : NetworkException(message, cause)
