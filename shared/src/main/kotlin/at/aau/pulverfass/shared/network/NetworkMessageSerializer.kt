package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.networkmessage.LoginRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Kapselt die Serialisierung und Deserialisierung von Headern und Payloads
 * unseres Netzwerkprotokolls.
 */
object NetworkMessageSerializer {
    /**
     * Serialisiert den [MessageHeader] in ein UTF-8 kodiertes ByteArray.
     *
     * @param header der zu serialisierende Header
     * @return serialisierter Header als ByteArray
     */
    fun serializeHeader(header: MessageHeader): ByteArray {
        return Json.encodeToString(MessageHeader.serializer(), header).encodeToByteArray()
    }

    /**
     * Deserialisiert ein ByteArray in einen [MessageHeader].
     *
     * @param bytes serialisierte Header-Daten
     * @return der deserialisierte Header
     */
    fun deserializeHeader(bytes: ByteArray): MessageHeader {
        return Json.decodeFromString(MessageHeader.serializer(), bytes.decodeToString())
    }

    /**
     * Serialisiert einen Payload mit dem dazu passenden [KSerializer].
     *
     * @param serializer Serializer fuer den konkreten Payload-Typ
     * @param payload der zu serialisierende Payload
     * @return serialisierter Payload als ByteArray
     */
    fun <T : NetworkMessagePayload> serializePayload(
        serializer: KSerializer<T>,
        payload: T,
    ): ByteArray {
        return Json.encodeToString(serializer, payload).encodeToByteArray()
    }

    /**
     * Deserialisiert den Payload anhand des uebergebenen [MessageType]s in die
     * passende Implementierung von [NetworkMessagePayload].
     *
     * Aktuell ist nur [MessageType.LOGIN_REQUEST] mit [LoginRequest]
     * implementiert.
     *
     * @param type Typ der empfangenen Nachricht
     * @param bytes serialisierte Payload-Daten
     * @return die deserialisierte Payload-Instanz
     * @throws IllegalArgumentException wenn fuer den [MessageType] noch keine
     * Deserialisierung implementiert ist
     */
    fun deserializePayload(
        type: MessageType,
        bytes: ByteArray,
    ): NetworkMessagePayload {
        val jsonString = bytes.decodeToString()

        return when (type) {
            MessageType.LOGIN_REQUEST -> Json.decodeFromString(
                LoginRequest.serializer(),
                jsonString,
            )

            else -> throw IllegalArgumentException("Unsupported payload type: $type")
        }
    }
}
