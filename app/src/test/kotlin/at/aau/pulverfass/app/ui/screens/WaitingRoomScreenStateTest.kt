package at.aau.pulverfass.app.ui.screens

import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.shared.ids.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaitingRoomScreenStateTest {
    @Test
    fun `initialCharacterIdForLobby keeps current character when it is available`() {
        val ownPlayerId = PlayerId(1)
        val players =
            listOf(
                LobbyPlayerUi(playerId = ownPlayerId, displayName = "Anne"),
                LobbyPlayerUi(
                    playerId = PlayerId(2),
                    displayName = "Mary",
                    characterId = "ice",
                ),
            )

        val result =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = "doctor",
                submittedInitialCharacterId = null,
            )

        assertEquals("doctor", result)
    }

    @Test
    fun `initialCharacterIdForLobby skips characters taken by other players`() {
        val ownPlayerId = PlayerId(1)
        val players =
            listOf(
                LobbyPlayerUi(playerId = ownPlayerId, displayName = "Anne"),
                LobbyPlayerUi(
                    playerId = PlayerId(2),
                    displayName = "Mary",
                    characterId = "blackpurp",
                ),
            )

        val result =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = "blackpurp",
                submittedInitialCharacterId = null,
            )

        assertEquals("bookmen", result)
    }

    @Test
    fun `initialCharacterIdForLobby returns null when own character is already synced`() {
        val ownPlayerId = PlayerId(1)
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = ownPlayerId,
                    displayName = "Anne",
                    characterId = "doctor",
                ),
            )

        val result =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = "doctor",
                submittedInitialCharacterId = null,
            )

        assertNull(result)
    }

    @Test
    fun `initialCharacterIdForLobby returns null while an initial selection is pending`() {
        val ownPlayerId = PlayerId(1)
        val players = listOf(LobbyPlayerUi(playerId = ownPlayerId, displayName = "Anne"))

        val result =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = null,
                submittedInitialCharacterId = "doctor",
            )

        assertNull(result)
    }
}
