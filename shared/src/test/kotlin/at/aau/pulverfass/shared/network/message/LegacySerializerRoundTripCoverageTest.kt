package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEventSerializer
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEventSerializer
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEventSerializer
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEventSerializer
import at.aau.pulverfass.shared.lobby.state.CardState
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.HandState
import at.aau.pulverfass.shared.lobby.state.TurnPauseReasons
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.lobby.state.TurnState
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEventSerializer
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerHandUpdatedEventSerializer
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshot
import at.aau.pulverfass.shared.message.lobby.event.PrivateHandCardSnapshotSerializer
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEvent
import at.aau.pulverfass.shared.message.lobby.event.ReinforcementsGrantedEventSerializer
import at.aau.pulverfass.shared.message.lobby.request.AttackRequest
import at.aau.pulverfass.shared.message.lobby.request.AttackRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmAttackDoneRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequest
import at.aau.pulverfass.shared.message.lobby.request.ConfirmReinforcementsDoneRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.GameStatePrivateGetRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequest
import at.aau.pulverfass.shared.message.lobby.request.LobbyPlayerCountRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequest
import at.aau.pulverfass.shared.message.lobby.request.MapGetRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequest
import at.aau.pulverfass.shared.message.lobby.request.PlaceReinforcementsRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequest
import at.aau.pulverfass.shared.message.lobby.request.StartPlayerSetRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacement
import at.aau.pulverfass.shared.message.lobby.request.TerritoryPlacementSerializer
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequest
import at.aau.pulverfass.shared.message.lobby.request.TradeInCardsRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnAdvanceRequestSerializer
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequest
import at.aau.pulverfass.shared.message.lobby.request.TurnStateGetRequestSerializer
import at.aau.pulverfass.shared.message.lobby.response.AttackResponse
import at.aau.pulverfass.shared.message.lobby.response.AttackResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmAttackDoneResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponse
import at.aau.pulverfass.shared.message.lobby.response.ConfirmReinforcementsDoneResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.LobbyPlayerCountResponse
import at.aau.pulverfass.shared.message.lobby.response.LobbyPlayerCountResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponse
import at.aau.pulverfass.shared.message.lobby.response.PlaceReinforcementsResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponse
import at.aau.pulverfass.shared.message.lobby.response.StartPlayerSetResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponse
import at.aau.pulverfass.shared.message.lobby.response.TradeInCardsResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnAdvanceResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponse
import at.aau.pulverfass.shared.message.lobby.response.TurnStateGetResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.AttackErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmAttackDoneErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.ConfirmReinforcementsDoneErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.LobbyPlayerCountErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.MapGetErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.PlaceReinforcementsErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartPlayerSetErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TradeInCardsErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnAdvanceErrorResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.TurnStateGetErrorResponseSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LegacySerializerRoundTripCoverageTest {
    private val json = Json

    @Test
    fun `legacy request serializers roundtrip through explicit serializer objects`() {
        assertLegacyRoundTrip(
            AttackRequestSerializer,
            AttackRequest(
                lobbyCode = LobbyCode("LR01"),
                playerId = PlayerId(1),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                attackTroops = 3,
                moveAfterCapture = 2,
                requestId = "attack-1",
            ),
        )
        assertLegacyRoundTrip(
            ConfirmAttackDoneRequestSerializer,
            ConfirmAttackDoneRequest(
                lobbyCode = LobbyCode("LR02"),
                playerId = PlayerId(2),
            ),
        )
        assertLegacyRoundTrip(
            ConfirmReinforcementsDoneRequestSerializer,
            ConfirmReinforcementsDoneRequest(
                lobbyCode = LobbyCode("LR03"),
                playerId = PlayerId(3),
            ),
        )
        assertLegacyRoundTrip(
            GameStatePrivateGetRequestSerializer,
            GameStatePrivateGetRequest(
                lobbyCode = LobbyCode("LR04"),
                playerId = PlayerId(4),
            ),
        )
        assertLegacyRoundTrip(
            LobbyPlayerCountRequestSerializer,
            LobbyPlayerCountRequest(LobbyCode("LR05")),
        )
        assertLegacyRoundTrip(
            MapGetRequestSerializer,
            MapGetRequest(LobbyCode("LR06")),
        )
        assertLegacyRoundTrip(
            TerritoryPlacementSerializer,
            TerritoryPlacement(
                territoryId = TerritoryId("gamma"),
                amount = 4,
            ),
        )
        assertLegacyRoundTrip(
            PlaceReinforcementsRequestSerializer,
            PlaceReinforcementsRequest(
                lobbyCode = LobbyCode("LR07"),
                playerId = PlayerId(5),
                placements =
                    listOf(
                        TerritoryPlacement(TerritoryId("gamma"), 2),
                        TerritoryPlacement(TerritoryId("delta"), 1),
                    ),
            ),
        )
        assertLegacyRoundTrip(
            StartPlayerSetRequestSerializer,
            StartPlayerSetRequest(
                lobbyCode = LobbyCode("LR08"),
                startPlayerId = PlayerId(6),
                requesterPlayerId = PlayerId(7),
            ),
        )
        assertLegacyRoundTrip(
            TradeInCardsRequestSerializer,
            TradeInCardsRequest(
                lobbyCode = LobbyCode("LR09"),
                playerId = PlayerId(8),
                cardIds =
                    listOf(
                        CardId("card-a"),
                        CardId("card-b"),
                        CardId("card-c"),
                    ),
            ),
        )
        assertLegacyRoundTrip(
            TurnAdvanceRequestSerializer,
            TurnAdvanceRequest(
                lobbyCode = LobbyCode("LR10"),
                playerId = PlayerId(9),
                expectedPhase = TurnPhase.ATTACK,
            ),
        )
        assertLegacyRoundTrip(
            TurnStateGetRequestSerializer,
            TurnStateGetRequest(LobbyCode("LR11")),
        )
    }

    @Test
    fun `legacy response and error serializers roundtrip through explicit serializer objects`() {
        assertLegacyRoundTrip(
            AttackResponseSerializer,
            AttackResponse(
                lobbyCode = LobbyCode("LS01"),
                requestId = "attack-ok",
            ),
        )
        assertLegacyRoundTrip(
            ConfirmAttackDoneResponseSerializer,
            ConfirmAttackDoneResponse(LobbyCode("LS02")),
        )
        assertLegacyRoundTrip(
            ConfirmReinforcementsDoneResponseSerializer,
            ConfirmReinforcementsDoneResponse(LobbyCode("LS03")),
        )
        assertLegacyRoundTrip(
            LobbyPlayerCountResponseSerializer,
            LobbyPlayerCountResponse(
                lobbyCode = LobbyCode("LS04"),
                playerCount = 4,
            ),
        )
        assertLegacyRoundTrip(
            PlaceReinforcementsResponseSerializer,
            PlaceReinforcementsResponse(LobbyCode("LS05")),
        )
        assertLegacyRoundTrip(
            StartPlayerSetResponseSerializer,
            StartPlayerSetResponse(
                lobbyCode = LobbyCode("LS06"),
                startPlayerId = PlayerId(10),
            ),
        )
        assertLegacyRoundTrip(
            TradeInCardsResponseSerializer,
            TradeInCardsResponse(LobbyCode("LS07")),
        )
        assertLegacyRoundTrip(
            TurnAdvanceResponseSerializer,
            TurnAdvanceResponse(LobbyCode("LS08")),
        )
        assertLegacyRoundTrip(
            TurnStateGetResponseSerializer,
            TurnStateGetResponse(
                lobbyCode = LobbyCode("LS09"),
                activePlayerId = PlayerId(11),
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 6,
                startPlayerId = PlayerId(10),
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = PlayerId(11),
            ),
        )
        assertLegacyRoundTrip(
            AttackErrorResponseSerializer,
            AttackErrorResponse(
                code = AttackErrorCode.NOT_ADJACENT,
                reason = "Territorien sind nicht benachbart.",
                requestId = "attack-error",
            ),
        )
        assertLegacyRoundTrip(
            ConfirmAttackDoneErrorResponseSerializer,
            ConfirmAttackDoneErrorResponse(
                code = ConfirmAttackDoneErrorCode.PHASE_MISMATCH,
                reason = "Angriffsphase ist bereits geschlossen.",
            ),
        )
        assertLegacyRoundTrip(
            ConfirmReinforcementsDoneErrorResponseSerializer,
            ConfirmReinforcementsDoneErrorResponse(
                code = ConfirmReinforcementsDoneErrorCode.FORCED_TRADE_REQUIRED,
                reason = "Kartentausch ist verpflichtend.",
            ),
        )
        assertLegacyRoundTrip(
            LobbyPlayerCountErrorResponseSerializer,
            LobbyPlayerCountErrorResponse(
                lobbyCode = LobbyCode("LS10"),
                code = LobbyPlayerCountErrorCode.LOBBY_NOT_FOUND,
                reason = "Lobby existiert nicht.",
            ),
        )
        assertLegacyRoundTrip(
            MapGetErrorResponseSerializer,
            MapGetErrorResponse(
                code = MapGetErrorCode.PAYLOAD_TOO_LARGE,
                reason = "Kartenzustand ist zu groß.",
            ),
        )
        assertLegacyRoundTrip(
            PlaceReinforcementsErrorResponseSerializer,
            PlaceReinforcementsErrorResponse(
                code = PlaceReinforcementsErrorCode.OVERSPEND,
                reason = "Mehr Truppen angefordert als verfügbar.",
            ),
        )
        assertLegacyRoundTrip(
            StartPlayerSetErrorResponseSerializer,
            StartPlayerSetErrorResponse(
                code = StartPlayerSetErrorCode.PLAYER_NOT_IN_LOBBY,
                reason = "Startspieler ist nicht Teil der Lobby.",
            ),
        )
        assertLegacyRoundTrip(
            TradeInCardsErrorResponseSerializer,
            TradeInCardsErrorResponse(
                code = TradeInCardsErrorCode.INVALID_SET,
                reason = "Kartenset ist ungültig.",
            ),
        )
        assertLegacyRoundTrip(
            TurnAdvanceErrorResponseSerializer,
            TurnAdvanceErrorResponse(
                code = TurnAdvanceErrorCode.GAME_PAUSED,
                reason = "Spiel ist pausiert.",
            ),
        )
        assertLegacyRoundTrip(
            TurnStateGetErrorResponseSerializer,
            TurnStateGetErrorResponse(
                code = TurnStateGetErrorCode.GAME_NOT_FOUND,
                reason = "Lobby wurde nicht gefunden.",
            ),
        )
    }

    @Test
    fun `legacy event serializers roundtrip through explicit serializer objects`() {
        assertLegacyRoundTrip(
            PendingReinforcementsChangedEventSerializer,
            PendingReinforcementsChangedEvent(
                lobbyCode = LobbyCode("LE01"),
                playerId = PlayerId(1),
                delta = 3,
            ),
        )
        assertLegacyRoundTrip(
            TerritoryOwnerChangedEventSerializer,
            TerritoryOwnerChangedEvent(
                lobbyCode = LobbyCode("LE02"),
                territoryId = TerritoryId("omega"),
                ownerId = PlayerId(2),
                stateVersion = 12,
            ),
        )
        assertLegacyRoundTrip(
            TerritoryTroopsChangedEventSerializer,
            TerritoryTroopsChangedEvent(
                lobbyCode = LobbyCode("LE03"),
                territoryId = TerritoryId("omega"),
                troopCount = 7,
                stateVersion = 13,
            ),
        )
        assertLegacyRoundTrip(
            TurnStateUpdatedEventSerializer,
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("LE04"),
                activePlayerId = PlayerId(3),
                turnPhase = TurnPhase.ATTACK,
                turnCount = 4,
                startPlayerId = PlayerId(3),
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = PlayerId(3),
            ),
        )
        assertLegacyRoundTrip(
            PhaseBoundaryEventSerializer,
            PhaseBoundaryEvent(
                lobbyCode = LobbyCode("LE05"),
                stateVersion = 21,
                previousPhase = TurnPhase.REINFORCEMENTS,
                nextPhase = TurnPhase.ATTACK,
                activePlayerId = PlayerId(4),
                turnCount = 5,
            ),
        )
        assertLegacyRoundTrip(
            ReinforcementsGrantedEventSerializer,
            ReinforcementsGrantedEvent(
                lobbyCode = LobbyCode("LE06"),
                playerId = PlayerId(5),
                amount = 6,
                territoryBonus = 3,
                continentBonus = 2,
                cardBonus = 1,
            ),
        )
        assertLegacyRoundTrip(
            PrivateHandCardSnapshotSerializer,
            PrivateHandCardSnapshot(
                cardId = CardId("card-z"),
                type = CardType.JOKER,
            ),
        )
        assertLegacyRoundTrip(
            PlayerHandUpdatedEventSerializer,
            PlayerHandUpdatedEvent(
                lobbyCode = LobbyCode("LE07"),
                recipientPlayerId = PlayerId(6),
                stateVersion = 33,
                handCards =
                    listOf(
                        PrivateHandCardSnapshot(
                            cardId = CardId("card-z"),
                            type = CardType.JOKER,
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `snapshot helpers and invariants remain deterministic`() {
        val player = PlayerId(42)
        val card = CardState(CardId("card-private"), CardType.B)
        val turnState =
            TurnState(
                activePlayerId = player,
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 8,
                startPlayerId = player,
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = player,
            )
        val gameState =
            GameState(
                lobbyCode = LobbyCode("GF01"),
                players = listOf(player),
                activePlayer = player,
                configuredStartPlayerId = player,
                turnOrder = listOf(player),
                turnNumber = 8,
                turnState = turnState,
                stateVersion = 44,
                handState = HandState(mapOf(player to listOf(card))),
            )

        assertEquals(
            PlayerHandUpdatedEvent(
                lobbyCode = LobbyCode("GF01"),
                recipientPlayerId = player,
                stateVersion = 44,
                handCards =
                    listOf(
                        PrivateHandCardSnapshot(
                            cardId = card.cardId,
                            type = card.type,
                        ),
                    ),
            ),
            PlayerHandUpdatedEvent.fromGameState(gameState, player),
        )
        assertEquals(
            TurnStateGetResponse(
                lobbyCode = LobbyCode("GF01"),
                activePlayerId = player,
                turnPhase = TurnPhase.FORTIFY,
                turnCount = 8,
                startPlayerId = player,
                isPaused = true,
                pauseReason = TurnPauseReasons.WAITING_FOR_PLAYER,
                pausedPlayerId = player,
            ),
            TurnStateGetResponse.fromGameState(gameState),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PlayerHandUpdatedEvent.fromGameState(gameState, PlayerId(99))
        }
        assertThrows(IllegalStateException::class.java) {
            TurnStateGetResponse.fromGameState(
                GameState(
                    lobbyCode = LobbyCode("GF02"),
                    players = listOf(player),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PendingReinforcementsChangedEvent(
                lobbyCode = LobbyCode("GF03"),
                playerId = player,
                delta = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerritoryOwnerChangedEvent(
                lobbyCode = LobbyCode("GF04"),
                territoryId = TerritoryId("invalid-owner"),
                ownerId = player,
                stateVersion = -1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerritoryTroopsChangedEvent(
                lobbyCode = LobbyCode("GF05"),
                territoryId = TerritoryId("invalid-troops"),
                troopCount = -1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TurnStateUpdatedEvent(
                lobbyCode = LobbyCode("GF06"),
                activePlayerId = player,
                turnPhase = TurnPhase.ATTACK,
                turnCount = 1,
                startPlayerId = player,
                isPaused = false,
                pauseReason = "should fail",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PhaseBoundaryEvent(
                lobbyCode = LobbyCode("GF07"),
                stateVersion = 1,
                previousPhase = TurnPhase.ATTACK,
                nextPhase = TurnPhase.ATTACK,
                activePlayerId = player,
                turnCount = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReinforcementsGrantedEvent(
                lobbyCode = LobbyCode("GF08"),
                playerId = player,
                amount = 5,
                territoryBonus = 2,
                continentBonus = 2,
                cardBonus = 0,
            )
        }
    }

    private fun <T> assertLegacyRoundTrip(
        serializer: KSerializer<T>,
        value: T,
    ) {
        val serialized = json.encodeToString(serializer, value)
        val deserialized = json.decodeFromString(serializer, serialized)

        assertEquals(value, deserialized)
    }
}
