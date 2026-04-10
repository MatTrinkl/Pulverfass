package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.message.MessageType

/**
 * Basisklasse für Fehler im Netzwerkprotokoll und der (De-)Serialisierung von Netzwerk-Nachrichten.
 */
open class NetworkException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Wird geworfen, wenn eine numerische MessageType-ID unbekannt ist.
 */
class UnknownMessageTypeIdException(
    id: Int,
) : NetworkException("Unknown MessageType id: $id")

/**
 * Wird geworfen, wenn für einen MessageType noch keine Payload-Mapping-Implementierung vorhanden ist.
 */
class UnsupportedPayloadTypeException(
    type: MessageType,
) : NetworkException("Unsupported payload type: $type")

/**
 * Wird geworfen, wenn ein Payload-Typ nicht im Netzwerkprotokoll registriert ist.
 */
class UnsupportedPayloadClassException(
    payloadClassName: String,
) : NetworkException("Unsupported payload class: $payloadClassName")

/**
 * Wird geworfen, wenn Header-Typ und Payload-Typ eines Pakets nicht zusammenpassen.
 */
class PayloadTypeMismatchException(
    expectedType: MessageType,
    actualType: MessageType,
) : NetworkException("Header type $expectedType does not match payload type $actualType")

/**
 * Wird geworfen, wenn ein serialisiertes Paket ungültige Basisdaten enthält.
 */
class InvalidSerializedPacketException(
    message: String,
) : NetworkException(message)

/**
 * Wird geworfen, wenn die JSON-Serialisierung oder -Deserialisierung einer Nachricht fehlschlägt.
 */
class NetworkSerializationException(
    message: String,
    cause: Throwable,
) : NetworkException(message, cause)

/**
 * Wird geworfen, wenn empfangene Transport-Bytes nicht bis zum technischen
 * Nachrichtenkopf dekodiert werden können.
 */
class PacketReceiveException(
    val connectionId: ConnectionId,
    message: String,
    cause: Throwable,
) : NetworkException(message, cause)
