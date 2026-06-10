package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Gemeinsamer Basistyp für alle transportnahen Ereignisse.
 *
 * Das Eventmodell enthält ausschließlich technische Verbindungsdaten und
 * Nachrichtenrepräsentationen, aber keine Spiellogik.
 */
sealed interface TransportEvent {
    /**
     * Zugehörige Verbindung, sofern das Ereignis bereits eindeutig einer
     * Verbindung zugeordnet werden kann.
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
 * Basistyp für empfangene technische Nachrichtenereignisse.
 *
 * Auf der Transportebene liegen Nachrichten weiterhin roh vor und werden erst in
 * höheren Schichten weiter dekodiert.
 */
sealed interface ReceivedTransportEvent : ConnectionBoundTransportEvent
