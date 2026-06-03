package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `lobbyPlayersToGamePlayers applies ownCharacterId for matching playerId`() {
        val ownId = PlayerId(2)
        val players =
            listOf(
                LobbyPlayerUi(playerId = PlayerId(1), displayName = "Alice"),
                LobbyPlayerUi(playerId = ownId, displayName = "Bob"),
            )

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = ownId,
                ownCharacterId = "ice",
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals(PulverfassColors.playerColors[1], result[1].color)
        assertEquals("ice", result[1].characterId)
    }

    @Test
    fun `lobbyPlayersToGamePlayers keeps gameplay color independent from character`() {
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = PlayerId(1),
                    displayName = "Alice",
                    characterId = "doctor",
                ),
                LobbyPlayerUi(
                    playerId = PlayerId(2),
                    displayName = "Bob",
                    characterId = "redmen",
                ),
            )

        val result = lobbyPlayersToGamePlayers(players)

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals(PulverfassColors.playerColors[1], result[1].color)
        assertEquals("doctor", result[0].characterId)
        assertEquals("redmen", result[1].characterId)
    }

    @Test
    fun `lobbyPlayersToGamePlayers assigns colors by stable player id order`() {
        val players =
            listOf(
                LobbyPlayerUi(playerId = PlayerId(20), displayName = "Bob"),
                LobbyPlayerUi(playerId = PlayerId(10), displayName = "Alice"),
            )

        val result = lobbyPlayersToGamePlayers(players)

        assertEquals(PulverfassColors.playerColors[1], result[0].color)
        assertEquals(PulverfassColors.playerColors[0], result[1].color)
    }

    @Test
    fun `buildRegionStates uses same gameplay color as player sidebar projection`() {
        val aliceId = PlayerId(1)
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = aliceId,
                    displayName = "Alice",
                    characterId = "blackpurp",
                ),
            )
        val territoryId = TerritoryId("brasilien")
        val territories =
            mapOf(
                territoryId to
                    GameTerritoryUiState(
                        territoryId = territoryId,
                        ownerId = aliceId,
                        troopCount = 3,
                    ),
            )

        val result = buildRegionStates(territoryStates = territories, players = players)

        assertEquals(PulverfassColors.playerColors[0], result.getValue("brazil").accentColor)
    }

    @Test
    fun `lobbyPlayersToGamePlayers ignores ownCharacterId when ownPlayerId is null`() {
        val players = listOf(LobbyPlayerUi(playerId = PlayerId(1), displayName = "Alice"))

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = null,
                ownCharacterId = "doctor",
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals(null, result[0].characterId)
    }

    @Test
    fun `lobbyPlayersToGamePlayers uses synced character when ownCharacterId is null`() {
        val ownId = PlayerId(1)
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = ownId,
                    displayName = "Alice",
                    characterId = "blackpurp",
                ),
            )

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = ownId,
                ownCharacterId = null,
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals("blackpurp", result[0].characterId)
    }
}
