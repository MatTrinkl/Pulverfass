package at.aau.pulverfass.app.game

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.shared.ids.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GameStateMapperTest {
    @Test
    fun `lobbyPlayersToGamePlayers uses palette color when no ownPlayerId given`() {
        val players =
            listOf(
                LobbyPlayerUi(playerId = PlayerId(1), displayName = "Alice"),
                LobbyPlayerUi(playerId = PlayerId(2), displayName = "Bob"),
            )

        val result = lobbyPlayersToGamePlayers(players)

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals(PulverfassColors.playerColors[1], result[1].color)
    }

    @Test
    fun `lobbyPlayersToGamePlayers applies ownPlayerColor for matching playerId`() {
        val ownId = PlayerId(2)
        val ownColor = Color(0xFFFF0000)
        val players =
            listOf(
                LobbyPlayerUi(playerId = PlayerId(1), displayName = "Alice"),
                LobbyPlayerUi(playerId = ownId, displayName = "Bob"),
            )

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = ownId,
                ownPlayerColor = ownColor,
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals(ownColor, result[1].color)
        assertNotEquals(PulverfassColors.playerColors[1], result[1].color)
    }

    @Test
    fun `lobbyPlayersToGamePlayers ignores ownPlayerColor when ownPlayerId is null`() {
        val ownColor = Color(0xFF00FF00)
        val players = listOf(LobbyPlayerUi(playerId = PlayerId(1), displayName = "Alice"))

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = null,
                ownPlayerColor = ownColor,
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
    }

    @Test
    fun `lobbyPlayersToGamePlayers uses palette when ownPlayerId matches but color is null`() {
        val ownId = PlayerId(1)
        val players = listOf(LobbyPlayerUi(playerId = ownId, displayName = "Alice"))

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = ownId,
                ownPlayerColor = null,
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
    }
}
