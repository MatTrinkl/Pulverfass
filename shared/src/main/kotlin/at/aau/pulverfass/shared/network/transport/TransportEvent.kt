package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload

/**
 * Gemeinsamer Basistyp für alle transportnahen Ereignisse.
 *
 * Das Eventmodell enthält ausschließlich technische Verbindungsdaten und
 * Nachrichtenrepräsentationen, aber keine Spiellogik.
 */
sealed interface TransportEvent {
    /**
     * Die zugehörige Verbindung, falls das Ereignis bereits einer konkreten
     * technischen Verbindung zugeordnet werden kann.
     */
    val connectionId: ConnectionId?
}

/**
 * Basistyp für alle Ereignisse, die immer genau zu einer Verbindung gehören.
 */
sealed interface ConnectionBoundTransportEvent : TransportEvent {
    override val connectionId: ConnectionId
}

/**
 * Basistyp für empfangene Nachrichtenereignisse.
 *
 * Eine Nachricht kann roh als ByteArray oder bereits dekodiert vorliegen.
 */
sealed interface ReceivedTransportEvent : ConnectionBoundTransportEvent

/**
 * Basistyp für bereits dekodierte Nachrichten mit bekanntem Payload-Typ.
 *
 * Diese Unterhierarchie ist die Vorlage für künftige `MessageType`-spezifische
 * Events oberhalb der reinen Transportebene.
 */
sealed interface DecodedTransportEvent<T : NetworkMessagePayload> : ReceivedTransportEvent {
    val messageType: MessageType
    val payload: T
}
