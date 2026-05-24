package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStatePrivateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse

/**
 * Übersetzt serverseitige Fehlercodes in kurze deutsche App-Texte.
 */
object GameErrorTextMapper {
    internal const val GAME_NOT_FOUND_TEXT = "Das Spiel wurde nicht gefunden."
    internal const val NOT_IN_GAME_TEXT = "Du bist diesem Spiel noch nicht zugeordnet."
    internal const val GAME_PAUSED_TEXT = "Das Spiel ist aktuell pausiert."
    internal const val REQUESTER_MISMATCH_TEXT =
        "Die Spielerzuordnung stimmt nicht mehr. Lade den Spielstand neu."

    fun map(error: MapGetErrorResponse): String =
        when (error.code) {
            MapGetErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
            MapGetErrorCode.NOT_IN_GAME -> NOT_IN_GAME_TEXT
            MapGetErrorCode.MAP_NOT_READY -> "Die Karte ist noch nicht bereit."
        }

    fun map(error: GameStateCatchUpErrorResponse): String =
        when (error.code) {
            GameStateCatchUpErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
            GameStateCatchUpErrorCode.NOT_IN_GAME -> NOT_IN_GAME_TEXT
            GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY ->
                "Der Spielstand ist noch nicht bereit."
        }

    fun map(error: GameStatePrivateGetErrorResponse): String =
        when (error.code) {
            GameStatePrivateGetErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
            GameStatePrivateGetErrorCode.NOT_IN_GAME -> NOT_IN_GAME_TEXT
            GameStatePrivateGetErrorCode.REQUESTER_MISMATCH ->
                "Private Spielerdaten können nur für dich selbst geladen werden."
        }

    fun map(error: TurnStateGetErrorResponse): String =
        when (error.code) {
            TurnStateGetErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
            TurnStateGetErrorCode.TURN_STATE_NOT_READY ->
                "Der aktuelle Zugstatus ist noch nicht bereit."
        }

    fun map(error: TurnAdvanceErrorResponse): String =
        when (error.code) {
            TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER -> "Du bist gerade nicht am Zug."
            TurnAdvanceErrorCode.GAME_PAUSED -> GAME_PAUSED_TEXT
            TurnAdvanceErrorCode.PHASE_MISMATCH ->
                "Die Phase hat sich geändert. Lade den Spielstand neu."
            TurnAdvanceErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
        }

    fun map(error: PlaceReinforcementsErrorResponse): String =
        when (error.code) {
            PlaceReinforcementsErrorCode.REQUESTER_MISMATCH -> REQUESTER_MISMATCH_TEXT
            PlaceReinforcementsErrorCode.NOT_ACTIVE_PLAYER ->
                "Verstärkungen können nur im eigenen Zug platziert werden."
            PlaceReinforcementsErrorCode.GAME_PAUSED -> GAME_PAUSED_TEXT
            PlaceReinforcementsErrorCode.PHASE_MISMATCH ->
                "Die Verstärkungsphase ist bereits beendet."
            PlaceReinforcementsErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
            PlaceReinforcementsErrorCode.TERRITORY_NOT_OWNED ->
                "Verstärkungen können nur auf eigene Gebiete gesetzt werden."
            PlaceReinforcementsErrorCode.OVERSPEND ->
                "Es sind nicht genügend Verstärkungen verfügbar."
            PlaceReinforcementsErrorCode.INVALID_PLACEMENT ->
                "Die gewählte Platzierung ist ungültig."
            PlaceReinforcementsErrorCode.FORCED_TRADE_REQUIRED ->
                "Vor dem Platzieren muss ein Kartenset eingetauscht werden."
        }

    fun map(error: ConfirmReinforcementsDoneErrorResponse): String =
        when (error.code) {
            ConfirmReinforcementsDoneErrorCode.REQUESTER_MISMATCH -> REQUESTER_MISMATCH_TEXT
            ConfirmReinforcementsDoneErrorCode.NOT_ACTIVE_PLAYER ->
                "Die Verstärkungsphase kann nur im eigenen Zug beendet werden."
            ConfirmReinforcementsDoneErrorCode.GAME_PAUSED -> GAME_PAUSED_TEXT
            ConfirmReinforcementsDoneErrorCode.PHASE_MISMATCH ->
                "Die Verstärkungsphase ist bereits beendet."
            ConfirmReinforcementsDoneErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
            ConfirmReinforcementsDoneErrorCode.PENDING_REINFORCEMENTS_REMAINING ->
                "Verbleibende Verstärkungen müssen zuerst platziert werden."
            ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED ->
                "Vor dem Beenden muss ein Kartenset eingetauscht werden."
        }

    fun map(error: TradeInCardsErrorResponse): String =
        when (error.code) {
            TradeInCardsErrorCode.REQUESTER_MISMATCH -> REQUESTER_MISMATCH_TEXT
            TradeInCardsErrorCode.NOT_ACTIVE_PLAYER ->
                "Karten können nur im eigenen Zug eingetauscht werden."
            TradeInCardsErrorCode.GAME_PAUSED -> GAME_PAUSED_TEXT
            TradeInCardsErrorCode.PHASE_MISMATCH ->
                "Karten können nur während der Verstärkungsphase eingetauscht werden."
            TradeInCardsErrorCode.GAME_NOT_FOUND -> GAME_NOT_FOUND_TEXT
            TradeInCardsErrorCode.CARDS_NOT_OWNED ->
                "Mindestens eine gewählte Karte ist nicht mehr auf der Hand."
            TradeInCardsErrorCode.INVALID_SET ->
                "Die gewählten Karten bilden kein gültiges Set."
            TradeInCardsErrorCode.INVALID_REQUEST ->
                "Für einen Tausch müssen genau drei Karten ausgewählt werden."
        }
}
