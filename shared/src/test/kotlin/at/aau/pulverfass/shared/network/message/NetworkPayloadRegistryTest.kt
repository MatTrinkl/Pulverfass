package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.codec.NetworkPayloadRegistry
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import at.aau.pulverfass.shared.message.connection.request.ReconnectRequest
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.lobby.event.AttackResolvedBroadcastEvent
import at.aau.pulverfass.shared.message.lobby.event.CharacterSelectedBroadcast
import at.aau.pulverfass.shared.message.lobby.event.ConnectionStatusUpdateEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerConnectionLostReason
import at.aau.pulverfass.shared.message.lobby.event.PlayerJoinedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerLeftLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.CharacterSelectRequest
import at.aau.pulverfass.shared.message.lobby.request.ClaimCheatReinforcementBonusRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacement
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.CharacterSelectResponse
import at.aau.pulverfass.shared.message.lobby.response.ClaimCheatReinforcementBonusResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.CreateLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
import at.aau.pulverfass.shared.message.lobby.response.JoinLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LeaveLobbyResponse
import at.aau.pulverfass.shared.message.lobby.response.LobbyPlayerCountResponse
import at.aau.pulverfass.shared.message.lobby.response.MapDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapGetResponse
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryDefinitionSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryEdgeSnapshot
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.PublicDeterminismMetadataSnapshot
import at.aau.pulverfass.shared.message.lobby.response.PublicTurnStateSnapshot
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CharacterSelectErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ClaimCheatReinforcementBonusErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.CreateLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.JoinLobbyErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
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
    fun `should resolve message type and serialization for connection response`() {
        val payload = ConnectionResponse(SessionToken("123e4567-e89b-12d3-a456-426614174101"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.CONNECTION_RESPONSE, messageType)
        assertEquals(
            """{"sessionToken":"123e4567-e89b-12d3-a456-426614174101"}""",
            serialized,
        )
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for reconnect request`() {
        val payload = ReconnectRequest(SessionToken("123e4567-e89b-12d3-a456-426614174102"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.CONNECTION_RECONNECT_REQUEST, messageType)
        assertEquals(
            """{"sessionToken":"123e4567-e89b-12d3-a456-426614174102"}""",
            serialized,
        )
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for reconnect response`() {
        val payload =
            ReconnectResponse(
                success = false,
                errorCode = ReconnectErrorCode.TOKEN_REVOKED,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.CONNECTION_RECONNECT_RESPONSE, messageType)
        assertEquals("""{"success":false,"errorCode":"TOKEN_REVOKED"}""", serialized)
        assertEquals(payload, deserialized)
    }

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
    fun `should resolve message type and serialization for player connection lost event`() {
        val payload =
            PlayerConnectionLostEvent(
                lobbyCode = LobbyCode("EF57"),
                playerId = PlayerId(18),
                reason = PlayerConnectionLostReason.HEARTBEAT_TIMEOUT,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_CONNECTION_LOST_BROADCAST, messageType)
        assertEquals(
            """{"lobbyCode":"EF57","playerId":18,"reason":"HEARTBEAT_TIMEOUT"}""",
            serialized,
        )
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for connection status update event`() {
        val payload =
            ConnectionStatusUpdateEvent(
                lobbyCode = LobbyCode("EF58"),
                playerId = PlayerId(19),
                status = ConnectionStatus.DISCONNECTED,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CONNECTION_STATUS_UPDATE_BROADCAST, messageType)
        assertEquals(
            """{"lobbyCode":"EF58","playerId":19,"status":"DISCONNECTED"}""",
            serialized,
        )
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for reinforcements granted event`() {
        val payload =
            ReinforcementsGrantedEvent(
                lobbyCode = LobbyCode("RG12"),
                playerId = PlayerId(8),
                amount = 6,
                territoryBonus = 4,
                continentBonus = 2,
                cardBonus = 0,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_REINFORCEMENTS_GRANTED_BROADCAST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for pending reinforcements changed event`() {
        val payload =
            PendingReinforcementsChangedEvent(
                lobbyCode = LobbyCode("PR12"),
                playerId = PlayerId(9),
                delta = -4,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PENDING_REINFORCEMENTS_CHANGED_BROADCAST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for attack request`() {
        val payload =
            AttackRequest(
                lobbyCode = LobbyCode("AT13"),
                playerId = PlayerId(3),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                attackTroops = 3,
                moveAfterCapture = 3,
                requestId = "req-1",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_ATTACK_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for attack response`() {
        val payload = AttackResponse(lobbyCode = LobbyCode("AT14"), requestId = "req-2")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_ATTACK_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for attack error response`() {
        val payload =
            AttackErrorResponse(
                code = AttackErrorCode.INSUFFICIENT_TROOPS,
                reason = "Nicht genug Truppen.",
                requestId = "req-3",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_ATTACK_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for attack resolved event`() {
        val payload =
            AttackResolvedBroadcastEvent(
                lobbyCode = LobbyCode("AT15"),
                attackerPlayerId = PlayerId(3),
                defenderPlayerId = PlayerId(4),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                attackTroops = 3,
                sourceTroopsBefore = 5,
                targetTroopsBefore = 2,
                requestedAttackDice = 3,
                attackDice = 3,
                defendDice = 2,
                attackerRolls = listOf(5, 4, 3),
                defenderRolls = listOf(2, 1),
                attackerLosses = 0,
                defenderLosses = 2,
                attackerRemaining = 5,
                defenderRemaining = 0,
                occupyingTroopCount = 3,
                minOccupyingTroops = 3,
                stateVersion = 7L,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_ATTACK_RESOLVED_BROADCAST, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("attackTroops"))
        assertTrue(serialized.contains("attackerRolls"))
    }

    @Test
    fun `should resolve message type and serialization for player eliminated event`() {
        val payload =
            PlayerEliminatedEvent(
                lobbyCode = LobbyCode("AT16"),
                playerId = PlayerId(4),
                eliminatedByPlayerId = PlayerId(3),
                stateVersion = 8L,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_ELIMINATED_BROADCAST, messageType)
        assertEquals(payload, deserialized)
        assertTrue(serialized.contains("eliminatedByPlayerId"))
    }

    @Test
    fun `should resolve message type and serialization for confirm reinforcements done request`() {
        val payload =
            ConfirmReinforcementsDoneRequest(
                lobbyCode = LobbyCode("CR12"),
                playerId = PlayerId(5),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for confirm reinforcements done response`() {
        val payload = ConfirmReinforcementsDoneResponse(lobbyCode = LobbyCode("CR13"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type for confirm reinforcements done error response`() {
        val payload =
            ConfirmReinforcementsDoneErrorResponse(
                code = ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED,
                reason = "Pflichtabgabe ausstehend.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CONFIRM_REINFORCEMENTS_DONE_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for place reinforcements request`() {
        val payload =
            PlaceReinforcementsRequest(
                lobbyCode = LobbyCode("PR13"),
                playerId = PlayerId(3),
                placements = listOf(TerritoryPlacement(TerritoryId("alpha"), 2)),
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLACE_REINFORCEMENTS_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for place reinforcements response`() {
        val payload = PlaceReinforcementsResponse(lobbyCode = LobbyCode("PR14"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLACE_REINFORCEMENTS_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for place reinforcements error response`() {
        val payload =
            PlaceReinforcementsErrorResponse(
                code = PlaceReinforcementsErrorCode.INVALID_PLACEMENT,
                reason = "Mindestens eine Platzierung ist ungültig.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLACE_REINFORCEMENTS_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for lobby player count request`() {
        val payload = LobbyPlayerCountRequest(LobbyCode("PC12"))

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_COUNT_REQUEST, messageType)
        assertEquals("""{"lobbyCode":"PC12"}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for lobby player count response`() {
        val payload =
            LobbyPlayerCountResponse(
                lobbyCode = LobbyCode("PC34"),
                playerCount = 5,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_COUNT_RESPONSE, messageType)
        assertEquals("""{"lobbyCode":"PC34","playerCount":5}""", serialized)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for lobby player count error response`() {
        val payload =
            LobbyPlayerCountErrorResponse(
                lobbyCode = LobbyCode("PC99"),
                code = LobbyPlayerCountErrorCode.LOBBY_NOT_FOUND,
                reason = "Lobby 'PC99' wurde nicht gefunden.",
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_PLAYER_COUNT_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for fortify move request`() {
        val payload =
            FortifyMoveRequest(
                lobbyCode = LobbyCode("FM12"),
                playerId = PlayerId(3),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                troopCount = 2,
            )

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_FORTIFY_MOVE_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for fortify move response and error`() {
        val response = FortifyMoveResponse(LobbyCode("FM34"))
        val error =
            FortifyMoveErrorResponse(
                code = FortifyMoveErrorCode.NO_PATH,
                reason = "Fortify benötigt einen zusammenhängenden Pfad über eigene Gebiete.",
            )

        val responseType = NetworkPayloadRegistry.messageTypeFor(response)
        val responseSerialized = NetworkPayloadRegistry.serializePayload(response)
        val responseDeserialized =
            NetworkPayloadRegistry.deserializePayload(responseType, responseSerialized)

        val errorType = NetworkPayloadRegistry.messageTypeFor(error)
        val errorSerialized = NetworkPayloadRegistry.serializePayload(error)
        val errorDeserialized =
            NetworkPayloadRegistry.deserializePayload(
                errorType,
                errorSerialized,
            )

        assertEquals(MessageType.LOBBY_FORTIFY_MOVE_RESPONSE, responseType)
        assertEquals(response, responseDeserialized)
        assertEquals(MessageType.LOBBY_FORTIFY_MOVE_ERROR_RESPONSE, errorType)
        assertEquals(error, errorDeserialized)
    }

    @Test
    fun `should resolve message type and serialization for cheat reinforcement bonus messages`() {
        val request =
            ClaimCheatReinforcementBonusRequest(
                lobbyCode = LobbyCode("CH12"),
                playerId = PlayerId(3),
            )
        val response = ClaimCheatReinforcementBonusResponse(lobbyCode = LobbyCode("CH12"))
        val error =
            ClaimCheatReinforcementBonusErrorResponse(
                code = ClaimCheatReinforcementBonusErrorCode.ALREADY_USED,
                reason = "Der Schummel-Verstärkungsbonus wurde bereits verwendet.",
            )

        val requestType = NetworkPayloadRegistry.messageTypeFor(request)
        val requestSerialized = NetworkPayloadRegistry.serializePayload(request)
        val requestDeserialized =
            NetworkPayloadRegistry.deserializePayload(requestType, requestSerialized)

        val responseType = NetworkPayloadRegistry.messageTypeFor(response)
        val responseSerialized = NetworkPayloadRegistry.serializePayload(response)
        val responseDeserialized =
            NetworkPayloadRegistry.deserializePayload(responseType, responseSerialized)

        val errorType = NetworkPayloadRegistry.messageTypeFor(error)
        val errorSerialized = NetworkPayloadRegistry.serializePayload(error)
        val errorDeserialized =
            NetworkPayloadRegistry.deserializePayload(errorType, errorSerialized)

        assertEquals(MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_REQUEST, requestType)
        assertEquals(request, requestDeserialized)
        assertEquals(MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_RESPONSE, responseType)
        assertEquals(response, responseDeserialized)
        assertEquals(MessageType.LOBBY_CHEAT_REINFORCEMENT_BONUS_ERROR_RESPONSE, errorType)
        assertEquals(error, errorDeserialized)
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
    fun `should resolve message type and serialization for character select request`() {
        val payload = CharacterSelectRequest(LobbyCode("CS12"), PlayerId(3), "warrior")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECT_REQUEST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for character select response`() {
        val payload = CharacterSelectResponse(LobbyCode("CS34"), "warrior")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECT_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for character select error response`() {
        val payload = CharacterSelectErrorResponse("Achtung, dieser Charakter ist schon vergeben")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECT_ERROR_RESPONSE, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should resolve message type and serialization for character selected broadcast`() {
        val payload = CharacterSelectedBroadcast(LobbyCode("CS56"), PlayerId(5), "character_04")

        val messageType = NetworkPayloadRegistry.messageTypeFor(payload)
        val serialized = NetworkPayloadRegistry.serializePayload(payload)
        val deserialized = NetworkPayloadRegistry.deserializePayload(messageType, serialized)

        assertEquals(MessageType.LOBBY_CHARACTER_SELECTED_BROADCAST, messageType)
        assertEquals(payload, deserialized)
    }

    @Test
    fun `should reject unsupported payload class`() {
        val exception =
            assertThrows(UnsupportedPayloadClassException::class.java) {
                NetworkPayloadRegistry.messageTypeFor(UnsupportedPayload)
            }

        assertEquals(
            "Unsupported payload class: ${UnsupportedPayload::class.qualifiedName}",
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
            "Unsupported payload class: ${UnsupportedPayload::class.qualifiedName}",
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
