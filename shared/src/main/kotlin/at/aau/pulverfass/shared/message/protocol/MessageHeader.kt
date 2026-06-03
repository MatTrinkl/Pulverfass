package at.aau.pulverfass.shared.message.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Technischer Nachrichtenkopf des Protokolls.
 *
 * Der Header enthält aktuell nur den [MessageType] und legt damit fest, wie die
 * Payload zu interpretieren ist.
 *
 * @property type Protokolltyp der Nachricht
 */
@Serializable
data class MessageHeader(
    val type: MessageType,
)

/**
 * Serialisiert und deserialisiert [MessageHeader] gemäß dem Netzwerkprotokoll.
 */
object MessageHeaderSerializer :
    KSerializer<MessageHeader> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(MessageHeader.serializer())
