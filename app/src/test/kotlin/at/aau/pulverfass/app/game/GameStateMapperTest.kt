package at.aau.pulverfass.app.game

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prüft die Projektion von Lobby-Spielern und Territory-State in den Game-UI-State.
 *
 * Die Tests sichern stabile Spielerfarben, Charakterzuordnung und die Darstellung
 * verlassener Spieler ab, damit Karte und Spielerleiste dieselbe Quelle verwenden.
 */
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
                ownCharacterId = "character_04",
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals(PulverfassColors.playerColors[1], result[1].color)
        assertEquals("character_04", result[1].characterId)
    }

    @Test
    fun `lobbyPlayersToGamePlayers keeps each connection status`() {
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = PlayerId(1),
                    displayName = "Alice",
                    connectionStatus = ConnectionStatus.CONNECTED,
                ),
                LobbyPlayerUi(
                    playerId = PlayerId(2),
                    displayName = "Bob",
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                ),
            )

        val result = lobbyPlayersToGamePlayers(players)

        assertEquals(ConnectionStatus.CONNECTED, result[0].connectionStatus)
        assertEquals(ConnectionStatus.DISCONNECTED, result[1].connectionStatus)
    }

    @Test
    fun `lobbyPlayersToGamePlayers keeps gameplay color independent from character`() {
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = PlayerId(1),
                    displayName = "Alice",
                    characterId = "character_03",
                ),
                LobbyPlayerUi(
                    playerId = PlayerId(2),
                    displayName = "Bob",
                    characterId = "character_07",
                ),
            )

        val result = lobbyPlayersToGamePlayers(players)

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals(PulverfassColors.playerColors[1], result[1].color)
        assertEquals("character_03", result[0].characterId)
        assertEquals("character_07", result[1].characterId)
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
                    characterId = "character_01",
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
    fun `buildRegionStates grays departed owners while keeping them visible`() {
        val departedId = PlayerId(99)
        val territoryId = TerritoryId("brasilien")
        val territories =
            mapOf(
                territoryId to
                    GameTerritoryUiState(
                        territoryId = territoryId,
                        ownerId = departedId,
                        troopCount = 3,
                    ),
            )

        val result = buildRegionStates(territoryStates = territories, players = emptyList())
        val region = result.getValue("brazil")

        assertEquals("99", region.ownerPlayerId)
        assertEquals("Verlassener Spieler", region.ownerName)
        assertEquals(Color(0xFF5E6268), region.accentColor)
    }

    @Test
    fun `lobbyPlayersToGamePlayers ignores ownCharacterId when ownPlayerId is null`() {
        val players = listOf(LobbyPlayerUi(playerId = PlayerId(1), displayName = "Alice"))

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = null,
                ownCharacterId = "character_03",
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
                    characterId = "character_01",
                ),
            )

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = ownId,
                ownCharacterId = null,
            )

        assertEquals(PulverfassColors.playerColors[0], result[0].color)
        assertEquals("character_01", result[0].characterId)
    }
}
