package at.aau.pulverfass.app.ui.navigation

import at.aau.pulverfass.app.lobby.LobbyUiState
import at.aau.pulverfass.shared.ids.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoredGameNavigationTest {
    @Test
    fun `restored running game should navigate to game loader`() {
        val state =
            LobbyUiState(
                isConnected = true,
                sessionToken = "session-token",
                activeLobbyCode = "ABCD",
                ownPlayerId = PlayerId(3),
                gameStarted = true,
            )

        assertEquals(Screen.LoadGame.route, restoredGameNavigationTarget(state))
    }

    @Test
    fun `navigation should wait until reconnect is finished`() {
        val state =
            LobbyUiState(
                isConnected = true,
                isReconnecting = true,
                sessionToken = "session-token",
                activeLobbyCode = "ABCD",
                ownPlayerId = PlayerId(3),
                gameStarted = true,
            )

        assertNull(restoredGameNavigationTarget(state))
    }

    @Test
    fun `navigation should require player and lobby context`() {
        val stateWithoutPlayer =
            LobbyUiState(
                isConnected = true,
                sessionToken = "session-token",
                activeLobbyCode = "ABCD",
                gameStarted = true,
            )
        val stateWithoutLobby =
            LobbyUiState(
                isConnected = true,
                sessionToken = "session-token",
                ownPlayerId = PlayerId(3),
                gameStarted = true,
            )

        assertNull(restoredGameNavigationTarget(stateWithoutPlayer))
        assertNull(restoredGameNavigationTarget(stateWithoutLobby))
    }

    @Test
    fun `auto navigation should only start from lobby`() {
        assertTrue(canAutoNavigateToRestoredGame(Screen.Lobby.route))
        assertFalse(canAutoNavigateToRestoredGame(Screen.Load.route))
        assertFalse(canAutoNavigateToRestoredGame(Screen.LoadGame.route))
        assertFalse(canAutoNavigateToRestoredGame(Screen.Game.route))
        assertFalse(canAutoNavigateToRestoredGame(null))
    }
}
