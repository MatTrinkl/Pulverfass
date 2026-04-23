package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkPayloadRegistryTest {
    @Test
    fun `should resolve message type and serialization for create lobby request`() {
        val payload = CreateLobbyRequest

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CREATE_REQUEST, messageType)
        assertEquals("{}", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for create lobby error response`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CREATE_ERROR_RESPONSE, messageType)
        assertEquals("""{"reason":"Lobby konnte nicht erstellt werden."}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for create lobby response`() {
        val payload = CreateLobbyResponse(LobbyCode("AB12"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CREATE_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"AB12"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for join lobby request`() {
        val payload = JoinLobbyRequest(LobbyCode("AB12"), "Alice")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_JOIN_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"AB12","playerDisplayName":"Alice"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for join lobby response`() {
        val payload = JoinLobbyResponse(LobbyCode("CD34"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_JOIN_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"CD34"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for join lobby error response`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_JOIN_ERROR_RESPONSE, messageType)
        assertEquals("""{"reason":"Lobby wurde nicht gefunden."}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for player joined lobby event`() {
        val payload =
            PlayerJoinedLobbyEvent(
                LobbyCode("EF56"),
                PlayerId(8),
                "Bob",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_JOINED_BROADCAST, messageType)
        assertEquals(
            """{"lobbyCode":"EF56","playerId":8,"playerDisplayName":"Bob"}""",
            serialized,
        )
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for leave lobby request`() {
        val payload = LeaveLobbyRequest(LobbyCode("GH78"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_LEAVE_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"GH78"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for leave lobby response`() {
        val payload = LeaveLobbyResponse(LobbyCode("IJ90"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_LEAVE_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"IJ90"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for player left lobby event`() {
        val payload = PlayerLeftLobbyEvent(LobbyCode("KL12"), PlayerId(9))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_LEFT_BROADCAST, messageType)
        assertEquals("""{"lobbyCode":"KL12","playerId":9}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for map get request`() {
        val payload = MapGetRequest(LobbyCode("MN34"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_MAP_GET_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"MN34"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for map get response`() {
        val payload =
            MapGetResponse(
                lobbyCode = LobbyCode("MN34"),
                schemaVersion = 1,
                mapHash = "hash",
                stateVersion = 4,
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
                            ownerId = PlayerId(1),
                            troopCount = 4,
                        ),
                    ),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_MAP_GET_RESPONSE, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("mapHash"))
    }

    @Test
    fun `should resolve message type and serialization for map get error response`() {
        val payload =
            MapGetErrorResponse(
                code = MapGetErrorCode.NOT_IN_GAME,
                reason = "Connection ist keinem Spieler in dieser Lobby zugeordnet.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_MAP_GET_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for territory owner changed event`() {
        val payload =
            TerritoryOwnerChangedEvent(
                lobbyCode = LobbyCode("MN34"),
                territoryId = TerritoryId("alpha"),
                ownerId = PlayerId(2),
                stateVersion = 17,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST, messageType)
        assertTrue(serialized.contains("stateVersion"))
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for territory troops changed event`() {
        val payload =
            TerritoryTroopsChangedEvent(
                lobbyCode = LobbyCode("MN34"),
                territoryId = TerritoryId("alpha"),
                troopCount = 9,
                stateVersion = 18,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST, messageType)
        assertTrue(serialized.contains("stateVersion"))
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for turn advance request`() {
        val payload =
            TurnAdvanceRequest(
                lobbyCode = LobbyCode("TA12"),
                playerId = PlayerId(5),
                expectedPhase = TurnPhase.ATTACK,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TURN_ADVANCE_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for turn advance response`() {
        val payload = TurnAdvanceResponse(LobbyCode("TA34"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TURN_ADVANCE_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"TA34"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for turn advance error response`() {
        val payload =
            TurnAdvanceErrorResponse(
                code = TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER,
                reason = "Nur der aktive Spieler '1' darf den Turn-State fortschalten.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for turn state updated broadcast`() {
        val payload =
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("TA56"),
                activePlayerId = PlayerId(2),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 4,
                startPlayerId = PlayerId(1),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("turnCount"))
    }

    @Test
    fun `should resolve message type and serialization for phase boundary broadcast`() {
        val payload =
            PhaseBoundaryEvent(
                lobbyCode = LobbyCode("PB12"),
                stateVersion = 9,
                previousPhase = TurnPhase.ATTACK,
                nextPhase = TurnPhase.FORTIFY,
                activePlayerId = PlayerId(2),
                turnCount = 3,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PHASE_BOUNDARY_BROADCAST, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("previousPhase"))
        assertTrue(serialized.contains("stateVersion"))
    }

    @Test
    fun `should resolve message type and serialization for game state delta broadcast`() {
        val payload =
            GameStateDeltaEvent(
                lobbyCode = LobbyCode("GD12"),
                fromVersion = 7,
                toVersion = 7,
                events =
                    listOf(
                        TerritoryOwnerChangedEvent(
                            lobbyCode = LobbyCode("GD12"),
                            territoryId = TerritoryId("alpha"),
                            ownerId = PlayerId(2),
                            stateVersion = 7,
                        ),
                        TurnStateUpdatedEvent(
                            lobbyCode = LobbyCode("GD12"),
                            activePlayerId = PlayerId(2),
                            turnPhase = TurnPhase.ATTACK,
                            turnCount = 2,
                            startPlayerId = PlayerId(1),
                        ),
                    ),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_DELTA_BROADCAST, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("fromVersion"))
        assertTrue(serialized.contains("messageType"))
    }

    @Test
    fun `should resolve message type and serialization for game state snapshot broadcast`() {
        val payload =
            GameStateSnapshotBroadcast(
                lobbyCode = LobbyCode("GS12"),
                stateVersion = 11,
                determinism =
                    PublicDeterminismMetadataSnapshot(
                        mapHash = "hash",
                        schemaVersion = 1,
                    ),
                turnState =
                    PublicTurnStateSnapshot(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 3,
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
                            troopCount = 9,
                        ),
                    ),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_GAME_STATE_SNAPSHOT_BROADCAST, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("determinism"))
        assertTrue(serialized.contains("turnState"))
    }

    @Test
    fun `should resolve message type and serialization for turn state get request`() {
        val payload = TurnStateGetRequest(LobbyCode("TS12"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TURN_STATE_GET_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"TS12"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for turn state get response`() {
        val payload =
            TurnStateGetResponse(
                lobbyCode = LobbyCode("TS34"),
                activePlayerId = PlayerId(3),
                turnPhase = TurnPhase.DRAW_CARD,
                turnCount = 8,
                startPlayerId = PlayerId(1),
                isPaused = true,
                pauseReason = "manual-pause",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TURN_STATE_GET_RESPONSE, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("pauseReason"))
    }

    @Test
    fun `should resolve message type and serialization for turn state get error response`() {
        val payload =
            TurnStateGetErrorResponse(
                code = TurnStateGetErrorCode.TURN_STATE_NOT_READY,
                reason = "Turn-State ist noch nicht verfuegbar.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for start player set request`() {
        val payload =
            StartPlayerSetRequest(
                lobbyCode = LobbyCode("SP12"),
                startPlayerId = PlayerId(2),
                requesterPlayerId = PlayerId(1),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_START_PLAYER_SET_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for start player set response`() {
        val payload = StartPlayerSetResponse(LobbyCode("SP34"), PlayerId(9))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_START_PLAYER_SET_RESPONSE, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("startPlayerId"))
    }

    @Test
    fun `should resolve message type and serialization for start player set error response`() {
        val payload =
            StartPlayerSetErrorResponse(
                code = StartPlayerSetErrorCode.GAME_ALREADY_STARTED,
                reason = "Der Startspieler kann nach Spielstart nicht mehr geaendert werden.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should reject unsupported payload class`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkPayloadRegistry.messageTypeFor(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    @Test
    fun `should reject unsupported payload class during serialization`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkPayloadRegistry.serializePayload(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    @Test
    fun `should reject unsupported payload type`() {
        val exception =
            assertThrows(UnsupportedPayloadTypeException::class.java) {
                NetworkPayloadRegistry.deserializePayload(
                    MessageType.HEARTBEAT,
                    "{}",
                )
            }

        assertEquals("Unsupported payload type: HEARTBEAT", exception.message)
    }

    private data object UnsupportedPayload : NetworkMessagePayload
}
