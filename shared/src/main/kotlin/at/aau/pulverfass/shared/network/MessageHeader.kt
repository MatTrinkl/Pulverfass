package at.aau.pulverfass.shared.network

import kotlinx.serialization.Serializable

/**
 * Definiert den Header für eine zu übertragende Nachricht in unserer Serverstruktur.
 *
 * @property type Typ des Headers, welcher die Datenstruktur des zu deserialisierenden Objektes vorgibt.
 */
@Serializable
data class MessageHeader(
    val type: MessageType,
)
