package at.aau.pulverfass.shared.network.exception

/**
 * Wird geworfen, wenn ein serialisiertes Paket keine gültige Grundstruktur hat.
 *
 * @param message technische Fehlerbeschreibung
 */
class InvalidSerializedPacketException(
    message: String,
) : NetworkException(message)
