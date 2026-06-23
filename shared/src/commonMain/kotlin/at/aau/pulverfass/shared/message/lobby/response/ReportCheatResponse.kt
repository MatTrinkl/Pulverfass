package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.Serializable

/**
 * Erfolgsantwort auf eine Cheat-Meldung.
 *
 * Erfolgreich bedeutet hier nur: Der Server konnte die Meldung fachlich
 * auswerten. Ob sie richtig oder falsch war, steht in [correct]. Das ist
 * Absicht, weil auch eine falsche Meldung ein gültiger Spielzug ist und eine
 * Strafe für die nächste Verstärkungsphase auslöst.
 *
 * @property correct `true`, wenn für den beschuldigten Spieler noch ein gültiges
 * Cheat-Meldefenster offen war.
 * @property modifierDelta Bonus oder Malus, der beim meldenden Spieler für die
 * nächste Reinforcements-Phase vorgemerkt wurde.
 */
@Serializable
data class ReportCheatResponse(
    val lobbyCode: LobbyCode,
    val accusedPlayerId: PlayerId,
    val correct: Boolean,
    val modifierDelta: Int,
) : NetworkMessagePayload
