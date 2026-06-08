package at.aau.pulverfass.app.ui.screens

import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.shared.ids.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Prüft die reine Charakterauswahl-Logik des Warteraums.
 *
 * Die Tests sichern ab, dass neue Spieler automatisch ein freies Icon erhalten
 * und belegte Charaktere beim Zentrieren oder Initialisieren übersprungen werden.
 */
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
                    characterId = "character_04",
                ),
            )

        val result =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = "character_03",
                submittedInitialCharacterId = null,
            )

        assertEquals("character_03", result)
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
                    characterId = "character_01",
                ),
            )

        val result =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = "character_01",
                submittedInitialCharacterId = null,
            )

        assertEquals("character_02", result)
    }

    @Test
    fun `initialCharacterIdForLobby returns null when own character is already synced`() {
        val ownPlayerId = PlayerId(1)
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = ownPlayerId,
                    displayName = "Anne",
                    characterId = "character_03",
                ),
            )

        val result =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = "character_03",
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
                submittedInitialCharacterId = "character_03",
            )

        assertNull(result)
    }

    @Test
    fun `availableCharacterIndexNear skips taken centered character forward first`() {
        val result =
            availableCharacterIndexNear(
                centerIndex = 0,
                takenCharacterIds = setOf("character_01"),
            )

        assertEquals(1, result)
    }

    @Test
    fun `availableCharacterIndexNear falls back backward at the end of the list`() {
        val result =
            availableCharacterIndexNear(
                centerIndex = 10,
                takenCharacterIds = setOf("character_11"),
            )

        assertEquals(9, result)
    }
}
