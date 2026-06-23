package at.aau.pulverfass.app.game

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.lobby.Characters
import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prüft die Projektion von Lobby-Spielern und Territory-State in den Game-UI-State.
 *
 * Die Tests sichern charaktergebundene Spielerfarben, Charakterzuordnung und die
 * Darstellung verlassener Spieler ab, damit Karte und Spielerleiste dieselbe
 * Quelle verwenden.
 */
class GameStateMapperTest {
    @Test
    fun `lobbyPlayersToGamePlayers binds color to the chosen character`() {
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = PlayerId(1),
                    displayName = "Alice",
                    characterId = "character_01",
                ),
                LobbyPlayerUi(
                    playerId = PlayerId(2),
                    displayName = "Bob",
                    characterId = "character_02",
                ),
            )

        val result = lobbyPlayersToGamePlayers(players)

        assertEquals("ALICE", result[0].name)
        assertEquals("A", result[0].avatarText)
        assertEquals("BOB", result[1].name)
        assertEquals("B", result[1].avatarText)
        assertEquals(Characters.byId("character_01")!!.color, result[0].color)
        assertEquals(Characters.byId("character_02")!!.color, result[1].color)
    }

    @Test
    fun `lobbyPlayersToGamePlayers applies ownCharacterId for matching playerId`() {
        val ownId = PlayerId(2)
        val players =
            listOf(
                LobbyPlayerUi(
                    playerId = PlayerId(1),
                    displayName = "Alice",
                    characterId = "character_01",
                ),
                LobbyPlayerUi(playerId = ownId, displayName = "Bob"),
            )

        val result =
            lobbyPlayersToGamePlayers(
                players,
                ownPlayerId = ownId,
                ownCharacterId = "character_04",
            )

        assertEquals(Characters.byId("character_01")!!.color, result[0].color)
        assertEquals(Characters.byId("character_04")!!.color, result[1].color)
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
    fun `lobbyPlayersToGamePlayers falls back to neutral color without a character`() {
        val players = listOf(LobbyPlayerUi(playerId = PlayerId(1), displayName = "Alice"))

        val result = lobbyPlayersToGamePlayers(players)

        assertEquals(Color(0xFF9E9E9E), result[0].color)
        assertEquals(null, result[0].characterId)
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

        assertEquals(
            Characters.byId("character_01")!!.color,
            result.getValue("brazil").accentColor,
        )
    }

    @Test
    fun `buildRegionStates uses the neutral color for departed owners`() {
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
        assertEquals(Color(0xFFC2C2C2), region.accentColor)
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

        assertEquals(Color(0xFF9E9E9E), result[0].color)
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

        assertEquals(Characters.byId("character_01")!!.color, result[0].color)
        assertEquals("character_01", result[0].characterId)
    }
}
