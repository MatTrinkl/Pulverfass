package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload

/**
 * Gemeinsamer Basistyp für autoritative GameState-Payloads mit expliziter Sichtbarkeit.
 *
 * Der Typ dient als technische Guard-Schicht zwischen GameState-Modell und
 * Netzwerk-Delivery. Öffentliche Payloads dürfen lobbyweit verteilt werden,
 * private Payloads ausschließlich an ihren expliziten Empfänger.
 */
sealed interface VisibleGameStatePayload : NetworkMessagePayload

/**
 * Marker für öffentliche GameState-Payloads.
 *
 * Diese Payloads dürfen serverseitig als S2L-Broadcast an alle Mitglieder einer
 * Lobby gesendet werden.
 */
interface PublicGameStatePayload : VisibleGameStatePayload

/**
 * Marker für private GameState-Payloads.
 *
 * Diese Payloads dürfen serverseitig ausschließlich als S2C-Nachricht an den
 * angegebenen Empfänger gesendet werden.
 */
interface PrivateGameStatePayload : VisibleGameStatePayload {
    val recipientPlayerId: PlayerId
}
