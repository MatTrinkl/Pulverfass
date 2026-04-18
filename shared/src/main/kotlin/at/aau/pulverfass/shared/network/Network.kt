package at.aau.pulverfass.shared.network

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.coroutines.flow.SharedFlow

/**
 * Öffentliche High-Level-Schnittstelle für den technischen Nachrichtenaustausch.
 *
 * Integrationen außerhalb des Netzwerk-Layers sollen nur mit dieser Schnittstelle
 * und mit konkreten [NetworkMessagePayload]-Implementierungen arbeiten.
 * WebSocket-, Framing- und Serialisierungsdetails bleiben gekapselt.
 */
interface Network<ConnectionT> {
    /**
     * Liefert den Strom technischer Netzwerkereignisse für Verbindungsstatus,
     * empfangene Payloads und Fehler.
     */
    val events: SharedFlow<Event<ConnectionT>>

    /**
     * Sendet eine fachliche Payload an eine bestehende Verbindung.
     *
     * @param connectionId Zielverbindung der Nachricht
     * @param payload fachliche Nutzlast, die im Hintergrund serialisiert und
     * über den Transport verschickt wird
     */
    suspend fun send(
        connectionId: ConnectionT,
        payload: NetworkMessagePayload,
    )

    /**
     * Gemeinsamer Basistyp aller technischen Netzwerkereignisse.
     */
    sealed interface Event<out ConnectionT> {
        /**
         * Signalisiert, dass eine Verbindung erfolgreich aufgebaut wurde.
         *
         * @property connectionId server- oder clientseitige Kennung der Verbindung
         */
        data class Connected<ConnectionT>(
            val connectionId: ConnectionT,
        ) : Event<ConnectionT>

        /**
         * Signalisiert, dass eine Payload erfolgreich empfangen und dekodiert wurde.
         *
         * @property connectionId Herkunft der Nachricht
         * @property payload fachliche Nutzlast der empfangenen Nachricht
         */
        data class MessageReceived<ConnectionT>(
            val connectionId: ConnectionT,
            val payload: NetworkMessagePayload,
        ) : Event<ConnectionT>

        /**
         * Signalisiert, dass eine Verbindung beendet wurde.
         *
         * @property connectionId beendete Verbindung
         * @property reason optionaler technischer Close-Reason des Transports
         */
        data class Disconnected<ConnectionT>(
            val connectionId: ConnectionT,
            val reason: String?,
        ) : Event<ConnectionT>

        /**
         * Signalisiert einen technischen Fehler im Netzwerkpfad.
         *
         * @property connectionId optionale Verbindung, falls der Fehler bereits
         * einer konkreten Verbindung zugeordnet werden konnte
         * @property cause eigentliche Fehlerursache
         */
        data class Error<ConnectionT>(
            val connectionId: ConnectionT?,
            val cause: Throwable,
        ) : Event<ConnectionT>
    }
}
