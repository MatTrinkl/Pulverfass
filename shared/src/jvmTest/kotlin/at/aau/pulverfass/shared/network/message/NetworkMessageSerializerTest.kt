package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.codec.NetworkMessageSerializer
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LobbyPlayerCountResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.NetworkSerializationException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadTypeException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkMessageSerializerTest {
    @Test
    fun `should serialize and deserialize header`() {
        val header = MessageHeader(MessageType.CONNECTION_REQUEST)

        val bytes = NetworkMessageSerializer.serializeHeader(header)
        val result = NetworkMessageSerializer.deserializeHeader(bytes)

        assertEquals(header, result)
    }

    @Test
    fun `should reject malformed utf8 header bytes`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializeHeader(byteArrayOf(0xC3.toByte(), 0x28))
            }

        assertTrue(exception.message.orEmpty().contains("Failed to deserialize message header"))
    }

    @Test
    fun `should deserialize create lobby request payload for create request type`() {
        val payload = CreateLobbyRequest
        val bytes =
            NetworkMessageSerializer.serializePayload(CreateLobbyRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize create lobby error payload for error type`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")
        val bytes =
            NetworkMessageSerializer.serializePayload(
                CreateLobbyErrorResponse.serializer(),
                payload,
            )

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize join lobby request payload for join request type`() {
        val payload =
            JoinLobbyRequest(
                lobbyCode = LobbyCode("1003"),
                playerDisplayName = "Alice",
            )
        val bytes =
            NetworkMessageSerializer.serializePayload(JoinLobbyRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize join lobby error payload for error type`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")
        val bytes =
            NetworkMessageSerializer.serializePayload(JoinLobbyErrorResponse.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered payload by runtime type`() {
        val payload =
            JoinLobbyRequest(
                lobbyCode = LobbyCode("1003"),
                playerDisplayName = "Alice",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered connection response by runtime type`() {
        val payload = ConnectionResponse(SessionToken("123e4567-e89b-12d3-a456-426614174102"))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.CONNECTION_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should wrap invalid payload values as network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.CONNECTION_RESPONSE,
                    """{"sessionToken":"not-a-valid-session-token"}""".encodeToByteArray(),
                )
            }

        assertTrue(
            exception.message.orEmpty().contains(
                "Failed to deserialize payload for message type CONNECTION_RESPONSE",
            ),
        )
    }

    @Test
    fun `should serialize registered reconnect request by runtime type`() {
        val payload = ReconnectRequest(SessionToken("123e4567-e89b-12d3-a456-426614174103"))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.CONNECTION_RECONNECT_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered reconnect response by runtime type`() {
        val payload =
            ReconnectResponse(
                success = false,
                errorCode = ReconnectErrorCode.TOKEN_INVALID,
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.CONNECTION_RECONNECT_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered join lobby response by runtime type`() {
        val payload =
            JoinLobbyResponse(
                lobbyCode = LobbyCode("1071"),
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered join lobby error by runtime type`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_JOIN_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered create lobby response by runtime type`() {
        val payload =
            CreateLobbyResponse(
                lobbyCode = LobbyCode("1071"),
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered create lobby error by runtime type`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_CREATE_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered player joined broadcast by runtime type`() {
        val payload =
            PlayerJoinedLobbyEvent(
                lobbyCode = LobbyCode("1132"),
                playerId = PlayerId(7),
                playerDisplayName = "Bob",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_JOINED_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered player connection lost broadcast by runtime type`() {
        val payload =
            PlayerConnectionLostEvent(
                lobbyCode = LobbyCode("1133"),
                playerId = PlayerId(17),
                reason = PlayerConnectionLostReason.SOCKET_CLOSED,
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_CONNECTION_LOST_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize lobby player count request payload for player count request type`() {
        val payload = LobbyPlayerCountRequest(lobbyCode = LobbyCode("1273"))
        val bytes =
            NetworkMessageSerializer.serializePayload(
                LobbyPlayerCountRequest.serializer(),
                payload,
            )

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_COUNT_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered lobby player count response by runtime type`() {
        val payload = LobbyPlayerCountResponse(lobbyCode = LobbyCode("1274"), playerCount = 3)

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_COUNT_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered lobby player count error by runtime type`() {
        val payload =
            LobbyPlayerCountErrorResponse(
                lobbyCode = LobbyCode("1275"),
                code = LobbyPlayerCountErrorCode.LOBBY_NOT_FOUND,
                reason = "Lobby '1275' wurde nicht gefunden.",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_COUNT_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize leave lobby request payload for leave request type`() {
        val payload = LeaveLobbyRequest(lobbyCode = LobbyCode("1178"))
        val bytes =
            NetworkMessageSerializer.serializePayload(LeaveLobbyRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_LEAVE_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered leave lobby response by runtime type`() {
        val payload = LeaveLobbyResponse(lobbyCode = LobbyCode("1199"))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_LEAVE_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered player left broadcast by runtime type`() {
        val payload = PlayerLeftLobbyEvent(lobbyCode = LobbyCode("1205"), playerId = PlayerId(8))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_PLAYER_LEFT_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize map get request payload for map get request type`() {
        val payload = MapGetRequest(lobbyCode = LobbyCode("1247"))
        val bytes = NetworkMessageSerializer.serializePayload(MapGetRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_MAP_GET_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered map get response by runtime type`() {
        val payload =
            MapGetResponse(
                lobbyCode = LobbyCode("1247"),
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
                            troopCount = 5,
                        ),
                    ),
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_MAP_GET_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered map get error by runtime type`() {
        val payload = MapGetErrorResponse(MapGetErrorCode.GAME_NOT_FOUND, "Lobby nicht gefunden.")

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_MAP_GET_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered territory owner changed broadcast by runtime type`() {
        val payload =
            TerritoryOwnerChangedEvent(
                lobbyCode = LobbyCode("1262"),
                territoryId = TerritoryId("alpha"),
                ownerId = PlayerId(3),
                stateVersion = 5,
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TERRITORY_OWNER_CHANGED_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered territory troops changed broadcast by runtime type`() {
        val payload =
            TerritoryTroopsChangedEvent(
                lobbyCode = LobbyCode("1313"),
                territoryId = TerritoryId("beta"),
                troopCount = 7,
                stateVersion = 6,
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TERRITORY_TROOPS_CHANGED_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize turn advance request payload for turn advance request type`() {
        val payload =
            TurnAdvanceRequest(
                lobbyCode = LobbyCode("1374"),
                playerId = PlayerId(5),
                expectedPhase = TurnPhase.ATTACK,
            )
        val bytes =
            NetworkMessageSerializer.serializePayload(TurnAdvanceRequest.serializer(), payload)

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TURN_ADVANCE_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered turn advance response by runtime type`() {
        val payload = TurnAdvanceResponse(lobbyCode = LobbyCode("1378"))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TURN_ADVANCE_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered turn advance error by runtime type`() {
        val payload =
            TurnAdvanceErrorResponse(
                code = TurnAdvanceErrorCode.NOT_ACTIVE_PLAYER,
                reason = "Nur der aktive Spieler '1' darf den Turn-State fortschalten.",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TURN_ADVANCE_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered turn state updated broadcast by runtime type`() {
        val payload =
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("1379"),
                activePlayerId = PlayerId(1),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 2,
                startPlayerId = PlayerId(1),
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TURN_STATE_UPDATED_BROADCAST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize turn state get request payload for turn state get request type`() {
        val payload = TurnStateGetRequest(lobbyCode = LobbyCode("1410"))
        val bytes =
            NetworkMessageSerializer.serializePayload(
                TurnStateGetRequest.serializer(),
                payload,
            )

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TURN_STATE_GET_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered turn state get response by runtime type`() {
        val payload =
            TurnStateGetResponse(
                lobbyCode = LobbyCode("1411"),
                activePlayerId = PlayerId(2),
                turnPhase = TurnPhase.REINFORCEMENTS,
                turnCount = 9,
                startPlayerId = PlayerId(1),
                isPaused = true,
                pauseReason = "sync-pause",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TURN_STATE_GET_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered turn state get error by runtime type`() {
        val payload =
            TurnStateGetErrorResponse(
                code = TurnStateGetErrorCode.TURN_STATE_NOT_READY,
                reason = "Turn-State fuer diese Lobby ist noch nicht verfuegbar.",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_TURN_STATE_GET_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should deserialize start player set request payload for start player set request type`() {
        val payload =
            StartPlayerSetRequest(
                lobbyCode = LobbyCode("1345"),
                startPlayerId = PlayerId(2),
                requesterPlayerId = PlayerId(1),
            )
        val bytes =
            NetworkMessageSerializer.serializePayload(
                StartPlayerSetRequest.serializer(),
                payload,
            )

        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_START_PLAYER_SET_REQUEST,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered start player set response by runtime type`() {
        val payload = StartPlayerSetResponse(LobbyCode("1348"), PlayerId(8))

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_START_PLAYER_SET_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should serialize registered start player set error by runtime type`() {
        val payload =
            StartPlayerSetErrorResponse(
                code = StartPlayerSetErrorCode.NOT_HOST,
                reason = "Nur der Lobby Owner darf den Startspieler setzen.",
            )

        val bytes = NetworkMessageSerializer.serializePayload(payload)
        val result =
            NetworkMessageSerializer.deserializePayload(
                MessageType.LOBBY_START_PLAYER_SET_ERROR_RESPONSE,
                bytes,
            )

        assertEquals(payload, result)
    }

    @Test
    fun `should throw for unsupported payload type`() {
        val payloadBytes =
            """
            {"lobbyCode":"1003","playerDisplayName":"Alice"}
            """.trimIndent().encodeToByteArray()

        val exception =
            assertThrows(UnsupportedPayloadTypeException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.HEARTBEAT,
                    payloadBytes,
                )
            }

        assertEquals("Unsupported payload type: HEARTBEAT", exception.message)
    }

    @Test
    fun `should wrap invalid header deserialization in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializeHeader(
                    """{"type":"NOT_REAL"}""".encodeToByteArray(),
                )
            }

        assertEquals("Failed to deserialize message header", exception.message)
        assertTrue(exception.cause != null)
    }

    @Test
    fun `should throw for unsupported payload class`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkMessageSerializer.serializePayload(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.qualifiedName}",
            exception.message,
        )
    }

    @Test
    fun `should wrap serializer failures in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.serializePayload(
                    FailingPayloadSerializer,
                    FailingPayload,
                )
            }

        assertEquals(
            "Failed to serialize payload of type ${FailingPayload::class.qualifiedName}",
            exception.message,
        )
        assertTrue(exception.cause is SerializationException)
    }

    @Test
    fun `should wrap invalid payload deserialization in network serialization exception`() {
        val exception =
            assertThrows(NetworkSerializationException::class.java) {
                NetworkMessageSerializer.deserializePayload(
                    MessageType.LOBBY_JOIN_REQUEST,
                    """{"lobbyCode":123,"playerDisplayName":true}""".encodeToByteArray(),
                )
            }

        assertEquals(
            "Failed to deserialize payload for message type LOBBY_JOIN_REQUEST",
            exception.message,
        )
        assertTrue(exception.cause != null)
    }

    private data object UnsupportedPayload : NetworkMessagePayload

    private data object FailingPayload : NetworkMessagePayload

    private object FailingPayloadSerializer : KSerializer<FailingPayload> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("FailingPayload", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: FailingPayload,
        ) {
            throw SerializationException("boom")
        }

        override fun deserialize(decoder: Decoder): FailingPayload {
            throw SerializationException("boom")
        }
    }
}
