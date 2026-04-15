package at.aau.pulverfass.shared.message.codec

import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.NetworkSerializationException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Interner Serializer für Header und Payloads des Netzwerkprotokolls.
 *
 * Die Klasse kapselt ausschließlich die JSON-Ebene. Das eigentliche Framing in
 * Wire-Bytes erfolgt separat über den Packet-Codec.
 */
internal object NetworkMessageSerializer {
    private val json = Json

    /**
     * Serialisiert einen [MessageHeader] in UTF-8-kodierte Bytes.
     *
     * @param header zu serialisierender Header
     * @return serialisierter Header als ByteArray
     * @throws NetworkSerializationException wenn die Serialisierung fehlschlägt
     */
    fun serializeHeader(header: MessageHeader): ByteArray =
        json.encodeToString(MessageHeader.serializer(), header).encodeToByteArray()

    /**
     * Deserialisiert UTF-8-kodierte Bytes in einen [MessageHeader].
     *
     * @param bytes serialisierte Header-Daten
     * @return deserialisierter Header
     * @throws NetworkSerializationException wenn die Deserialisierung fehlschlägt
     */
    fun deserializeHeader(bytes: ByteArray): MessageHeader =
        try {
            json.decodeFromString(MessageHeader.serializer(), bytes.decodeToString())
        } catch (exception: SerializationException) {
            throw NetworkSerializationException("Failed to deserialize message header", exception)
        }

    /**
     * Serialisiert eine Payload mit dem explizit übergebenen Serializer.
     *
     * @param serializer Serializer des konkreten Payload-Typs
     * @param payload zu serialisierende Payload
     * @return serialisierte Payload als ByteArray
     * @throws NetworkSerializationException wenn die Serialisierung fehlschlägt
     */
    fun <T : NetworkMessagePayload> serializePayload(
        serializer: KSerializer<T>,
        payload: T,
    ): ByteArray =
        try {
            json.encodeToString(serializer, payload).encodeToByteArray()
        } catch (exception: SerializationException) {
            throw NetworkSerializationException(
                "Failed to serialize payload of type ${payload.javaClass.name}",
                exception,
            )
        }

    /**
     * Serialisiert eine registrierte Payload anhand ihrer Laufzeitklasse.
     *
     * @param payload registrierte Payload-Instanz
     * @return serialisierte Payload als ByteArray
     * @throws UnsupportedPayloadClassException wenn die Payload-Klasse nicht
     * im Protokoll registriert ist
     * @throws NetworkSerializationException wenn die Serialisierung fehlschlägt
     */
    fun serializePayload(payload: NetworkMessagePayload): ByteArray =
        try {
            NetworkPayloadRegistry.serializePayload(payload).encodeToByteArray()
        } catch (exception: UnsupportedPayloadClassException) {
            throw exception
        }

    /**
     * Deserialisiert eine Payload anhand ihres [MessageType]s.
     *
     * @param type Nachrichtentyp der Payload
     * @param bytes serialisierte Payload-Daten
     * @return passende Payload-Instanz
     * @throws UnsupportedPayloadTypeException wenn für den Typ keine Deserialisierung
     * registriert ist
     * @throws NetworkSerializationException wenn die Deserialisierung fehlschlägt
     */
    fun deserializePayload(
        type: MessageType,
        bytes: ByteArray,
    ): NetworkMessagePayload {
        val jsonString = bytes.decodeToString()

        return try {
            NetworkPayloadRegistry.deserializePayload(type, jsonString)
        } catch (exception: UnsupportedPayloadTypeException) {
            throw exception
        } catch (exception: SerializationException) {
            throw NetworkSerializationException(
                "Failed to deserialize payload for message type $type",
                exception,
            )
        }
    }
}
