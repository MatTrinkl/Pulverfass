package at.aau.pulverfass.shared.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Kapselt die Serialisierung und Deserialisierung von Headern und Payloads
 * unseres Netzwerkprotokolls.
 */
object NetworkMessageSerializer {
    private val json = Json

    /**
     * Serialisiert den [MessageHeader] in ein UTF-8 kodiertes ByteArray.
     *
     * @param header der zu serialisierende Header
     * @return serialisierter Header als ByteArray
     * @throws NetworkSerializationException wenn die Serialisierung fehlschlägt
     */
    fun serializeHeader(header: MessageHeader): ByteArray =
        try {
            json.encodeToString(MessageHeader.serializer(), header).encodeToByteArray()
        } catch (exception: SerializationException) {
            throw NetworkSerializationException("Failed to serialize message header", exception)
        }

    /**
     * Deserialisiert ein ByteArray in einen [MessageHeader].
     *
     * @param bytes serialisierte Header-Daten
     * @return der deserialisierte Header
     * @throws NetworkSerializationException wenn die Deserialisierung fehlschlägt
     */
    fun deserializeHeader(bytes: ByteArray): MessageHeader =
        try {
            json.decodeFromString(MessageHeader.serializer(), bytes.decodeToString())
        } catch (exception: SerializationException) {
            throw NetworkSerializationException("Failed to deserialize message header", exception)
        }

    /**
     * Serialisiert einen Payload mit dem dazu passenden [KSerializer].
     *
     * @param serializer Serializer für den konkreten Payload-Typ
     * @param payload der zu serialisierende Payload
     * @return serialisierter Payload als ByteArray
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
     * Deserialisiert den Payload anhand des uebergebenen [MessageType]s in die
     * passende Implementierung von [NetworkMessagePayload].
     *
     * @param type Typ der empfangenen Nachricht
     * @param bytes serialisierte Payload-Daten
     * @return die deserialisierte Payload-Instanz
     * @throws UnsupportedPayloadTypeException wenn fuer den [MessageType] noch
     * keine Deserialisierung implementiert ist
     * @throws NetworkSerializationException wenn die Payload nicht in das
     * erwartete Format des [MessageType]s deserialisiert werden kann
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
