package at.aau.pulverfass.shared.event

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.PlayerId
import kotlinx.serialization.Serializable

/**
 * Neutrales Kontextmodell für fachnahe Event-Verarbeitung.
 *
 * Der Kontext enthält rein technische Metadaten, die für Routing, Logging,
 * spätere Zustandsverwaltung und Korrelation von Verarbeitungsflüssen relevant
 * sind, ohne an Netzwerk- oder UI-Typen gebunden zu sein.
 *
 * @property connectionId auslösende technische Verbindung, falls vorhanden
 * @property playerId auslösender Spieler, falls bereits bekannt
 * @property occurredAtEpochMillis technischer Zeitstempel des Ereignisses in
 * Unix-Epoch-Millis
 * @property correlationId optionale Korrelationskennung für Trace- und Logging-Zwecke
 */
@Serializable
data class EventContext(
    val connectionId: ConnectionId? = null,
    val playerId: PlayerId? = null,
    val occurredAtEpochMillis: Long,
    val correlationId: CorrelationId? = null,
) {
    init {
        require(occurredAtEpochMillis >= 0) {
            "EventContext.occurredAtEpochMillis darf nicht negativ sein, " +
                "war aber $occurredAtEpochMillis."
        }
    }
}
