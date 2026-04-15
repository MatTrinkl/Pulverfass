package at.aau.pulverfass.shared.network.exception

import at.aau.pulverfass.shared.message.protocol.MessageType

/**
 * Wird geworfen, wenn für einen [MessageType] keine Payload-Deserialisierung
 * registriert ist.
 *
 * @param type Nachrichtentyp ohne registrierte Payload-Zuordnung
 */
class UnsupportedPayloadTypeException(
    type: MessageType,
) : NetworkException("Unsupported payload type: $type")
