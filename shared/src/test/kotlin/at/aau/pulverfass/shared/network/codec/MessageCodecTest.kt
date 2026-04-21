package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
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
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.exception.InvalidSerializedPacketException
import at.aau.pulverfass.shared.network.exception.UnsupportedPayloadClassException
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageCodecTest {
    @Test
    fun `should encode and decode create lobby request payload directly`() {
        val payload = CreateLobbyRequest

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode create lobby error payload directly`() {
        val payload = CreateLobbyErrorResponse(reason = "Lobby konnte nicht erstellt werden.")

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode create lobby response payload directly`() {
        val payload =
            CreateLobbyResponse(
                lobbyCode = LobbyCode("AB12"),
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby request payload directly`() {
        val payload =
            JoinLobbyRequest(
                LobbyCode("AB12"),
                "Alice",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby response payload directly`() {
        val payload = JoinLobbyResponse(LobbyCode("CD34"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby error payload directly`() {
        val payload = JoinLobbyErrorResponse(reason = "Lobby wurde nicht gefunden.")

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby request payload with display name directly`() {
        val payload =
            JoinLobbyRequest(
                LobbyCode("BC23"),
                "Bob",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode player joined lobby event payload directly`() {
        val payload =
            PlayerJoinedLobbyEvent(
                lobbyCode = LobbyCode("EF56"),
                playerId = PlayerId(7),
                playerDisplayName = "Carol",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode leave lobby request payload directly`() {
        val payload = LeaveLobbyRequest(LobbyCode("FG67"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode leave lobby response payload directly`() {
        val payload = LeaveLobbyResponse(LobbyCode("GH78"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode player left lobby event payload directly`() {
        val payload = PlayerLeftLobbyEvent(lobbyCode = LobbyCode("HI89"), playerId = PlayerId(6))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode map get request payload directly`() {
        val payload = MapGetRequest(LobbyCode("IJ90"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode map get response payload directly`() {
        val payload =
            MapGetResponse(
                lobbyCode = LobbyCode("JK01"),
                schemaVersion = 1,
                mapHash = "hash",
                stateVersion = 3,
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
                            ownerId = PlayerId(4),
                            troopCount = 6,
                        ),
                    ),
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode territory owner changed payload directly`() {
        val payload =
            TerritoryOwnerChangedEvent(
                lobbyCode = LobbyCode("LM12"),
                territoryId = TerritoryId("alpha"),
                ownerId = PlayerId(4),
                stateVersion = 11,
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode territory troops changed payload directly`() {
        val payload =
            TerritoryTroopsChangedEvent(
                lobbyCode = LobbyCode("NO34"),
                territoryId = TerritoryId("beta"),
                troopCount = 8,
                stateVersion = 12,
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode turn advance request payload directly`() {
        val payload =
            TurnAdvanceRequest(
                lobbyCode = LobbyCode("TA12"),
                playerId = PlayerId(7),
                expectedPhase = TurnPhase.ATTACK,
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode turn advance response payload directly`() {
        val payload = TurnAdvanceResponse(lobbyCode = LobbyCode("TA34"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode turn advance error payload directly`() {
        val payload =
            TurnAdvanceErrorResponse(
                code = TurnAdvanceErrorCode.GAME_PAUSED,
                reason = "Lobby 'TA56' ist pausiert; Turn-Wechsel ist aktuell nicht erlaubt.",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode turn state updated broadcast payload directly`() {
        val payload =
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("TA78"),
                activePlayerId = PlayerId(1),
                turnPhase = TurnPhase.DRAW_CARD,
                turnCount = 4,
                startPlayerId = PlayerId(1),
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode phase boundary broadcast payload directly`() {
        val payload =
            PhaseBoundaryEvent(
                lobbyCode = LobbyCode("PB34"),
                stateVersion = 5,
                previousPhase = TurnPhase.FORTIFY,
                nextPhase = TurnPhase.DRAW_CARD,
                activePlayerId = PlayerId(1),
                turnCount = 2,
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode game state snapshot broadcast payload directly`() {
        val payload =
            GameStateSnapshotBroadcast(
                lobbyCode = LobbyCode("GS34"),
                stateVersion = 8,
                determinism =
                    PublicDeterminismMetadataSnapshot(
                        mapHash = "hash",
                        schemaVersion = 1,
                    ),
                turnState =
                    PublicTurnStateSnapshot(
                        activePlayerId = PlayerId(2),
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        turnCount = 2,
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
                            troopCount = 3,
                        ),
                    ),
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode turn state get request payload directly`() {
        val payload = TurnStateGetRequest(LobbyCode("TS12"))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode turn state get response payload directly`() {
        val payload =
            TurnStateGetResponse(
                lobbyCode = LobbyCode("TS34"),
                activePlayerId = PlayerId(9),
                turnPhase = TurnPhase.ATTACK,
                turnCount = 6,
                startPlayerId = PlayerId(2),
                isPaused = false,
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode turn state get error payload directly`() {
        val payload =
            TurnStateGetErrorResponse(
                code = TurnStateGetErrorCode.GAME_NOT_FOUND,
                reason = "Lobby 'TS99' wurde nicht gefunden.",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode start player set request payload directly`() {
        val payload = StartPlayerSetRequest(LobbyCode("SP12"), PlayerId(2), PlayerId(1))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode start player set response payload directly`() {
        val payload = StartPlayerSetResponse(LobbyCode("SP34"), PlayerId(8))

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode start player set error payload directly`() {
        val payload =
            StartPlayerSetErrorResponse(
                code = StartPlayerSetErrorCode.PLAYER_NOT_IN_LOBBY,
                reason = "Spieler ist nicht Teil der Lobby.",
            )

        val bytes = MessageCodec.encode(payload)
        val result = MessageCodec.decodePayload(bytes)

        assertEquals(payload, result)
    }

    @Test
    fun `should encode and decode join lobby request packet`() {
        val packet =
            NetworkPacket(
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                payload = JoinLobbyRequest(LobbyCode("CD34"), "Dora"),
            )

        val bytes = MessageCodec.encode(packet, JoinLobbyRequest.serializer())
        val result = MessageCodec.decode(bytes)

        assertEquals(packet.header, result.header)
        assertEquals(packet.payload, result.payload)
    }

    @Test
    fun `should decode payload directly from received packet without reframing`() {
        val payload = JoinLobbyRequest(LobbyCode("DE45"), "Eve")
        val encoded = MessageCodec.encode(payload)
        val unpacked = PacketCodec.unpack(encoded)
        val receivedPacket =
            ReceivedPacket(
                connectionId = at.aau.pulverfass.shared.ids.ConnectionId(1),
                header = MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                packet = unpacked,
            )

        val result = MessageCodec.decodePayload(receivedPacket)

        assertEquals(payload, result)
    }

    @Test
    fun `should wrap malformed frame as invalid serialized packet exception`() {
        val exception =
            assertThrows(InvalidSerializedPacketException::class.java) {
                MessageCodec.decodePayload(byteArrayOf(1, 2, 3))
            }

        assertTrue(exception.message!!.contains("Packet too short"))
    }

    @Test
    fun `should reject unsupported payload classes`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                MessageCodec.encode(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.java.name}",
            exception.message,
        )
    }

    private data object UnsupportedPayload :
        NetworkMessagePayload
}
