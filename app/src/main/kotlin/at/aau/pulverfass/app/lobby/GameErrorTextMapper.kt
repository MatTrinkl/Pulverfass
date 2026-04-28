package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse

/**
 * Übersetzt serverseitige Fehlercodes in kurze deutsche App-Texte.
 */
object GameErrorTextMapper {
    fun map(error: MapGetErrorResponse): String =
        when (error.code) {
            MapGetErrorCode.GAME_NOT_FOUND -> "Das Spiel wurde nicht gefunden."
            MapGetErrorCode.NOT_IN_GAME -> "Du bist diesem Spiel noch nicht zugeordnet."
            MapGetErrorCode.MAP_NOT_READY -> "Die Karte ist noch nicht bereit."
        }

    fun map(error: GameStateCatchUpErrorResponse): String =
        when (error.code) {
            GameStateCatchUpErrorCode.GAME_NOT_FOUND -> "Das Spiel wurde nicht gefunden."
            GameStateCatchUpErrorCode.NOT_IN_GAME -> "Du bist diesem Spiel noch nicht zugeordnet."
            GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY ->
                "Der Spielstand ist noch nicht bereit."
        }

    fun map(error: GameStatePrivateGetErrorResponse): String =
        when (error.code) {
            GameStatePrivateGetErrorCode.GAME_NOT_FOUND -> "Das Spiel wurde nicht gefunden."
            GameStatePrivateGetErrorCode.NOT_IN_GAME ->
                "Du bist diesem Spiel noch nicht zugeordnet."
            GameStatePrivateGetErrorCode.REQUESTER_MISMATCH ->
                "Private Spielerdaten können nur für dich selbst geladen werden."
        }

    fun map(error: TurnStateGetErrorResponse): String =
        when (error.code) {
            TurnStateGetErrorCode.GAME_NOT_FOUND -> "Das Spiel wurde nicht gefunden."
            TurnStateGetErrorCode.TURN_STATE_NOT_READY ->
                "Der aktuelle Zugstatus ist noch nicht bereit."
        }

    fun map(error: TurnAdvanceErrorResponse): String =
        when (error.code) {
            TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER -> "Du bist gerade nicht am Zug."
            TurnAdvanceErrorCode.GAME_PAUSED -> "Das Spiel ist aktuell pausiert."
            TurnAdvanceErrorCode.PHASE_MISMATCH ->
                "Die Phase hat sich geändert. Lade den Spielstand neu."
            TurnAdvanceErrorCode.GAME_NOT_FOUND -> "Das Spiel wurde nicht gefunden."
        }
}
