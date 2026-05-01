package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpReason
import at.aau.pulverfass.shared.message.lobby.request.GameStateCatchUpRequest
import at.aau.pulverfass.shared.message.lobby.response.GameStateCatchUpResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.GameStateCatchUpErrorResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameStateCatchUpCodecTest {
    @Test
    fun `should encode and decode catch up request payload directly`() {
        val payload =
            GameStateCatchUpRequest(
                lobbyCode = LobbyCode("CU12"),
                clientStateVersion = 4,
                reason = GameStateCatchUpReason.AFTER_RECONNECT,
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode catch up response payload directly`() {
        val payload =
            GameStateCatchUpResponse(
                lobbyCode = LobbyCode("CU34"),
                stateVersion = 12,
                determinism =
                    PublicDeterminismMetadataSnapshot(
                        mapHash = "hash",
                        schemaVersion = 1,
                    ),
                turnState =
                    PublicTurnStateSnapshot(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.FORTIFY,
                        turnCount = 4,
                        startPlayerId = PlayerId(1),
                    ),
                definition =
                    MapDefinitionSnapshot(
                        territories =
                            listOf(
                                MapTerritoryDefinitionSnapshot(
                                    territoryId = TerritoryId("alpha"),
                                    edges = listOf(MapTerritoryEdgeSnapshot(TerritoryId("beta"))),
                                ),
                            ),
                        continents = emptyList(),
                    ),
                territoryStates =
                    listOf(
                        MapTerritoryStateSnapshot(
                            territoryId = TerritoryId("alpha"),
                            ownerId = PlayerId(2),
                            troopCount = 8,
                        ),
                    ),
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode catch up error payload directly`() {
        val payload =
            GameStateCatchUpErrorResponse(
                code = GameStateCatchUpErrorCode.GAME_NOT_FOUND,
                reason = "Lobby 'CU99' wurde nicht gefunden.",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }
}
