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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameErrorTextMapperTest {
    @Test
    fun `map get errors are translated for UI`() {
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            GameErrorTextMapper.map(MapGetErrorResponse(MapGetErrorCode.GAME_NOT_FOUND, "raw")),
        )
        assertEquals(
            GameErrorTextMapper.NOT_IN_GAME_TEXT,
            GameErrorTextMapper.map(MapGetErrorResponse(MapGetErrorCode.NOT_IN_GAME, "raw")),
        )
        assertEquals(
            "Die Karte ist noch nicht bereit.",
            GameErrorTextMapper.map(MapGetErrorResponse(MapGetErrorCode.MAP_NOT_READY, "raw")),
        )
    }

    @Test
    fun `snapshot errors are translated for UI`() {
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            GameErrorTextMapper.map(
                GameStateCatchUpErrorResponse(GameStateCatchUpErrorCode.GAME_NOT_FOUND, "raw"),
            ),
        )
        assertEquals(
            GameErrorTextMapper.NOT_IN_GAME_TEXT,
            GameErrorTextMapper.map(
                GameStateCatchUpErrorResponse(GameStateCatchUpErrorCode.NOT_IN_GAME, "raw"),
            ),
        )
        assertEquals(
            "Der Spielstand ist noch nicht bereit.",
            GameErrorTextMapper.map(
                GameStateCatchUpErrorResponse(
                    GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY,
                    "raw",
                ),
            ),
        )
    }

    @Test
    fun `private and turn state errors are translated for UI`() {
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            GameErrorTextMapper.map(
                GameStatePrivateGetErrorResponse(
                    GameStatePrivateGetErrorCode.GAME_NOT_FOUND,
                    "raw",
                ),
            ),
        )
        assertEquals(
            GameErrorTextMapper.NOT_IN_GAME_TEXT,
            GameErrorTextMapper.map(
                GameStatePrivateGetErrorResponse(GameStatePrivateGetErrorCode.NOT_IN_GAME, "raw"),
            ),
        )
        assertTrue(
            GameErrorTextMapper.map(
                GameStatePrivateGetErrorResponse(
                    GameStatePrivateGetErrorCode.REQUESTER_MISMATCH,
                    "raw",
                ),
            ).contains("Private Spielerdaten"),
        )
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            GameErrorTextMapper.map(
                TurnStateGetErrorResponse(TurnStateGetErrorCode.GAME_NOT_FOUND, "raw"),
            ),
        )
        assertEquals(
            "Der aktuelle Zugstatus ist noch nicht bereit.",
            GameErrorTextMapper.map(
                TurnStateGetErrorResponse(TurnStateGetErrorCode.TURN_STATE_NOT_READY, "raw"),
            ),
        )
    }

    @Test
    fun `turn advance errors are translated for UI`() {
        assertEquals(
            "Du bist gerade nicht am Zug.",
            GameErrorTextMapper.map(
                TurnAdvanceErrorResponse(TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER, "raw"),
            ),
        )
        assertEquals(
            "Das Spiel ist aktuell pausiert.",
            GameErrorTextMapper.map(
                TurnAdvanceErrorResponse(TurnAdvanceErrorCode.GAME_PAUSED, "raw"),
            ),
        )
        assertTrue(
            GameErrorTextMapper.map(
                TurnAdvanceErrorResponse(TurnAdvanceErrorCode.PHASE_MISMATCH, "raw"),
            ).contains("Lade den Spielstand neu."),
        )
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            GameErrorTextMapper.map(
                TurnAdvanceErrorResponse(TurnAdvanceErrorCode.GAME_NOT_FOUND, "raw"),
            ),
        )
    }

    @Test
    fun `reinforcement placement errors are translated for UI`() {
        val messages =
            PlaceReinforcementsErrorCode.entries.associateWith { code ->
                GameErrorTextMapper.map(PlaceReinforcementsErrorResponse(code, "raw"))
            }

        assertTrue(
            messages.getValue(
                PlaceReinforcementsErrorCode.REQUESTER_MISMATCH,
            ).contains("Spielerzuordnung"),
        )
        assertTrue(
            messages.getValue(
                PlaceReinforcementsErrorCode.NOT_ACTIVE_PLAYER,
            ).contains("eigenen Zug"),
        )
        assertEquals(
            "Das Spiel ist aktuell pausiert.",
            messages.getValue(PlaceReinforcementsErrorCode.GAME_PAUSED),
        )
        assertTrue(
            messages.getValue(PlaceReinforcementsErrorCode.PHASE_MISMATCH).contains("beendet"),
        )
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            messages.getValue(PlaceReinforcementsErrorCode.GAME_NOT_FOUND),
        )
        assertTrue(
            messages.getValue(
                PlaceReinforcementsErrorCode.TERRITORY_NOT_OWNED,
            ).contains("eigene Gebiete"),
        )
        assertTrue(messages.getValue(PlaceReinforcementsErrorCode.OVERSPEND).contains("genügend"))
        assertTrue(
            messages.getValue(PlaceReinforcementsErrorCode.INVALID_PLACEMENT).contains("ungültig"),
        )
        assertTrue(
            messages.getValue(
                PlaceReinforcementsErrorCode.FORCED_TRADE_REQUIRED,
            ).contains("Kartenset"),
        )
    }

    @Test
    fun `reinforcement finish errors are translated for UI`() {
        val messages =
            ConfirmReinforcementsDoneErrorCode.entries.associateWith { code ->
                GameErrorTextMapper.map(ConfirmReinforcementsDoneErrorResponse(code, "raw"))
            }

        assertTrue(
            messages.getValue(
                ConfirmReinforcementsDoneErrorCode.REQUESTER_MISMATCH,
            ).contains("Spielerzuordnung"),
        )
        assertTrue(
            messages.getValue(
                ConfirmReinforcementsDoneErrorCode.NOT_ACTIVE_PLAYER,
            ).contains("eigenen Zug"),
        )
        assertEquals(
            "Das Spiel ist aktuell pausiert.",
            messages.getValue(ConfirmReinforcementsDoneErrorCode.GAME_PAUSED),
        )
        assertTrue(
            messages.getValue(
                ConfirmReinforcementsDoneErrorCode.PHASE_MISMATCH,
            ).contains("beendet"),
        )
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            messages.getValue(ConfirmReinforcementsDoneErrorCode.GAME_NOT_FOUND),
        )
        assertTrue(
            messages.getValue(
                ConfirmReinforcementsDoneErrorCode.PENDING_REINFORCEMENTS_REMAINING,
            ).contains("Verbleibende"),
        )
        assertTrue(
            messages.getValue(
                ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED,
            ).contains("Kartenset"),
        )
    }

    @Test
    fun `card trade errors are translated for UI`() {
        val messages =
            TradeInCardsErrorCode.entries.associateWith { code ->
                GameErrorTextMapper.map(TradeInCardsErrorResponse(code, "raw"))
            }

        assertTrue(
            messages.getValue(
                TradeInCardsErrorCode.REQUESTER_MISMATCH,
            ).contains("Spielerzuordnung"),
        )
        assertTrue(
            messages.getValue(TradeInCardsErrorCode.NOT_ACTIVE_PLAYER).contains("eigenen Zug"),
        )
        assertEquals(
            "Das Spiel ist aktuell pausiert.",
            messages.getValue(TradeInCardsErrorCode.GAME_PAUSED),
        )
        assertTrue(
            messages.getValue(TradeInCardsErrorCode.PHASE_MISMATCH).contains("Verstärkungsphase"),
        )
        assertEquals(
            GameErrorTextMapper.GAME_NOT_FOUND_TEXT,
            messages.getValue(TradeInCardsErrorCode.GAME_NOT_FOUND),
        )
        assertTrue(messages.getValue(TradeInCardsErrorCode.CARDS_NOT_OWNED).contains("Hand"))
        assertTrue(messages.getValue(TradeInCardsErrorCode.INVALID_SET).contains("gültiges Set"))
        assertTrue(messages.getValue(TradeInCardsErrorCode.INVALID_REQUEST).contains("genau drei"))
    }
}
