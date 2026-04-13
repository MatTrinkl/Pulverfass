package at.aau.pulverfass.shared.network.message

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
