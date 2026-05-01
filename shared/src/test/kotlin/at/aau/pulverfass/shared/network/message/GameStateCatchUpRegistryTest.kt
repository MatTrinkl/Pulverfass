package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
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
import at.aau.pulverfass.shared.message.protocol.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameStateCatchUpRegistryTest {
    @Test
    fun `should resolve message type and serialization for catch up request`() {
        val payload =
            GameStateCatchUpRequest(
                lobbyCode = LobbyCode("CU12"),
                clientStateVersion = 4,
                reason = GameStateCatchUpReason.OUT_OF_ORDER,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_CATCH_UP_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for catch up response`() {
        val payload =
            GameStateCatchUpResponse(
                lobbyCode = LobbyCode("CU34"),
                stateVersion = 10,
                determinism =
                    PublicDeterminismMetadataSnapshot(
                        mapHash = "hash",
                        schemaVersion = 1,
                    ),
                turnState =
                    PublicTurnStateSnapshot(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 5,
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
                            troopCount = 6,
                        ),
                    ),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_CATCH_UP_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for catch up error response`() {
        val payload =
            GameStateCatchUpErrorResponse(
                code = GameStateCatchUpErrorCode.SNAPSHOT_NOT_READY,
                reason = "Catch-up-Snapshot ist noch nicht verfügbar.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_CATCH_UP_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }
}
