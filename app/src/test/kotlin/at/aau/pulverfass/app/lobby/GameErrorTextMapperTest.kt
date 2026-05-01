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
}
