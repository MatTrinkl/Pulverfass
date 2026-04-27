package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.map.config.ContinentDefinition
import at.aau.pulverfass.shared.map.config.MapDefinition
import at.aau.pulverfass.shared.map.config.TerritoryDefinition
import at.aau.pulverfass.shared.map.config.TerritoryEdgeDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameStateTest {
    @Test
    fun `should expose consistent initial state`() {
        val state = GameState.initial(LobbyCode("AB12"))

        assertEquals(LobbyCode("AB12"), state.lobbyCode)
        assertTrue(state.players.isEmpty())
        assertTrue(state.turnOrder.isEmpty())
        assertNull(state.activePlayer)
        assertEquals(0, state.turnNumber)
        assertNull(state.turnState)
        assertNull(state.activeTurnPhase)
        assertEquals(GameStatus.WAITING_FOR_PLAYERS, state.status)
        assertEquals(0, state.processedEventCount)
        assertEquals(0, state.stateVersion)
        assertEquals(0, state.playerCount)
    }

    @Test
    fun `should hold players turn and status data consistently when instantiated`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val context = EventContext(occurredAtEpochMillis = 1234)
        val state =
            GameState(
                lobbyCode = LobbyCode("CD34"),
                players = listOf(playerOne, playerTwo),
                activePlayer = playerOne,
                turnOrder = listOf(playerOne, playerTwo),
                turnNumber = 3,
                turnState =
                    TurnState(
                        activePlayerId = playerOne,
                        turnPhase = TurnPhase.ATTACK,
                        turnCount = 3,
                        startPlayerId = playerOne,
                    ),
                status = GameStatus.RUNNING,
                stateVersion = 7,
                processedEventCount = 4,
                lastEventContext = context,
            )

        assertEquals(listOf(playerOne, playerTwo), state.players)
        assertEquals(listOf(playerOne, playerTwo), state.turnOrder)
        assertEquals(playerOne, state.activePlayer)
        assertEquals(3, state.turnNumber)
        assertEquals(TurnPhase.ATTACK, state.activeTurnPhase)
        assertEquals(playerOne, state.resolvedTurnState?.startPlayerId)
        assertEquals(GameStatus.RUNNING, state.status)
        assertEquals(context, state.lastEventContext)
        assertNull(state.closedReason)
        assertNull(state.lastInvalidActionReason)
        assertEquals(7, state.stateVersion)
        assertEquals(4, state.processedEventCount)
        assertEquals(2, state.playerCount)
        assertTrue(state.hasPlayer(playerTwo))
    }

    @Test
    fun `should keep two game state instances isolated from each other`() {
        val firstPlayer = PlayerId(21)
        val secondPlayer = PlayerId(22)

        val firstState =
            GameState(
                lobbyCode = LobbyCode("GH78"),
                players = listOf(firstPlayer),
                turnOrder = listOf(firstPlayer),
            )
        val secondState =
            GameState(
                lobbyCode = LobbyCode("JK90"),
                players = listOf(secondPlayer),
                turnOrder = listOf(secondPlayer),
            )

        assertEquals(listOf(firstPlayer), firstState.players)
        assertEquals(listOf(secondPlayer), secondState.players)
        assertFalse(firstState.hasPlayer(secondPlayer))
        assertFalse(secondState.hasPlayer(firstPlayer))
    }

    @Test
    fun `should initialize all territories as entities when map is provided`() {
        val firstPlayer = PlayerId(1)
        val state =
            GameState.initial(
                lobbyCode = LobbyCode("MP12"),
                mapDefinition = sampleMapDefinition(),
                players = listOf(firstPlayer, PlayerId(2)),
            )

        assertTrue(state.hasMap())
        assertEquals(firstPlayer, state.activePlayer)
        assertEquals(TurnPhase.REINFORCEMENTS, state.activeTurnPhase)
        assertEquals(1, state.turnState?.turnCount)
        assertEquals(firstPlayer, state.turnState?.startPlayerId)
        assertEquals(3, state.allTerritoryStates().size)
        assertNotNull(state.territoryStateOf(TerritoryId("alpha")))
        assertNotNull(state.territoryStateOf(TerritoryId("beta")))
        assertNotNull(state.territoryStateOf(TerritoryId("gamma")))
        assertEquals(0, state.troopCountOf(TerritoryId("alpha")))
        assertNull(state.territoryOwnerOf(TerritoryId("alpha")))
    }

    @Test
    fun `should expose adjacency from readonly map definition`() {
        val state =
            GameState.initial(
                lobbyCode = LobbyCode("MQ34"),
                mapDefinition = sampleMapDefinition(),
            )

        val alphaAdjacency = state.adjacencyOf(TerritoryId("alpha"))

        assertEquals(2, alphaAdjacency.size)
        assertTrue(alphaAdjacency.any { it.targetId == TerritoryId("beta") })
        assertTrue(alphaAdjacency.any { it.targetId == TerritoryId("gamma") })
    }

    @Test
    fun `should expose gameplay adjacency queries`() {
        val state =
            GameState.initial(
                lobbyCode = LobbyCode("QA12"),
                mapDefinition = sampleMapDefinition(),
            )

        assertEquals(
            listOf(TerritoryId("beta"), TerritoryId("gamma")),
            state.neighbors(TerritoryId("alpha")),
        )
        assertEquals(
            listOf(TerritoryId("beta"), TerritoryId("gamma")),
            state.adjacentTerritories(TerritoryId("alpha")).map { it.territoryId },
        )
        assertTrue(state.isAdjacent(TerritoryId("alpha"), TerritoryId("beta")))
        assertFalse(state.isAdjacent(TerritoryId("beta"), TerritoryId("gamma")))
    }

    @Test
    fun `should update ownership and troops through territory state helpers`() {
        val playerOne = PlayerId(10)
        val reducer = DefaultLobbyEventReducer()
        val state =
            reducer.apply(
                reducer.apply(
                    GameState.initial(
                        lobbyCode = LobbyCode("MR56"),
                        mapDefinition = sampleMapDefinition(),
                        players = listOf(playerOne),
                    ),
                    TerritoryOwnerChangedEvent(LobbyCode("MR56"), TerritoryId("alpha"), playerOne),
                ),
                TerritoryTroopsChangedEvent(LobbyCode("MR56"), TerritoryId("alpha"), 5),
            )

        assertEquals(playerOne, state.territoryOwnerOf(TerritoryId("alpha")))
        assertEquals(playerOne, state.ownerOf(TerritoryId("alpha")))
        assertEquals(5, state.troopCountOf(TerritoryId("alpha")))
        assertEquals(5, state.troopsOn(TerritoryId("alpha")))
        assertEquals(
            listOf(TerritoryId("alpha")),
            state.territoriesOwnedBy(playerOne).map {
                it.territoryId
            },
        )
    }

    @Test
    fun `should derive continent owner and bonus from territory ownership`() {
        val playerOne = PlayerId(11)
        val playerTwo = PlayerId(12)
        val reducer = DefaultLobbyEventReducer()
        val lobbyCode = LobbyCode("MS78")
        val state =
            reducer.apply(
                reducer.apply(
                    GameState.initial(
                        lobbyCode = lobbyCode,
                        mapDefinition = sampleMapDefinition(),
                        players = listOf(playerOne, playerTwo),
                    ),
                    TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerOne),
                ),
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("beta"), playerOne),
            )

        assertEquals(playerOne, state.continentOwner(ContinentId("north")))
        assertNull(state.continentOwner(ContinentId("south")))
        assertTrue(state.playerOwnsContinent(playerOne, ContinentId("north")))
        assertFalse(state.playerOwnsContinent(playerTwo, ContinentId("north")))
        assertEquals(listOf(ContinentId("north")), state.continentsOwnedBy(playerOne))
        assertTrue(state.continentsOwnedBy(playerTwo).isEmpty())
        assertEquals(3, state.bonusFor(playerOne))
        assertEquals(0, state.bonusFor(playerTwo))
    }

    @Test
    fun `should determine owned path connectivity deterministically`() {
        val playerOne = PlayerId(21)
        val playerTwo = PlayerId(22)
        val reducer = DefaultLobbyEventReducer()
        val lobbyCode = LobbyCode("QB34")
        val baseState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne, playerTwo),
            )

        val connectedState =
            reducer.apply(
                reducer.apply(
                    reducer.apply(
                        baseState,
                        TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerOne),
                    ),
                    TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("beta"), playerOne),
                ),
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("gamma"), playerOne),
            )
        val disconnectedState =
            reducer.apply(
                connectedState,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerTwo),
            )

        assertTrue(
            connectedState.isConnectedByOwnedPath(
                playerId = playerOne,
                from = TerritoryId("beta"),
                to = TerritoryId("gamma"),
            ),
        )
        assertFalse(
            disconnectedState.isConnectedByOwnedPath(
                playerId = playerOne,
                from = TerritoryId("beta"),
                to = TerritoryId("gamma"),
            ),
        )
        assertFalse(
            disconnectedState.isConnectedByOwnedPath(
                playerId = playerOne,
                from = TerritoryId("alpha"),
                to = TerritoryId("beta"),
            ),
        )
        assertTrue(
            connectedState.isConnectedByOwnedPath(
                playerId = playerOne,
                from = TerritoryId("beta"),
                to = TerritoryId("beta"),
            ),
        )
    }

    @Test
    fun `should expose optional metadata fields`() {
        val state =
            GameState(
                lobbyCode = LobbyCode("LM12"),
                closedReason = "timeout",
                lastInvalidActionReason = "invalid move",
            )

        assertEquals("timeout", state.closedReason)
        assertEquals("invalid move", state.lastInvalidActionReason)
    }

    @Test
    fun `should resolve legacy turn state when only legacy fields are set`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)
        val state =
            GameState(
                lobbyCode = LobbyCode("LG12"),
                players = listOf(playerOne, playerTwo),
                activePlayer = playerTwo,
                turnOrder = listOf(playerOne, playerTwo),
                turnNumber = 4,
            )

        assertEquals(playerTwo, state.resolvedTurnState?.activePlayerId)
        assertEquals(playerOne, state.resolvedTurnState?.startPlayerId)
        assertEquals(4, state.resolvedTurnState?.turnCount)
        assertEquals(TurnPhase.REINFORCEMENTS, state.activeTurnPhase)
    }

    @Test
    fun `should reject invalid constructor arguments`() {
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)

        assertThrows(IllegalArgumentException::class.java) {
            GameState(lobbyCode = LobbyCode("NO34"), turnNumber = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(lobbyCode = LobbyCode("OP12"), stateVersion = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(lobbyCode = LobbyCode("PQ56"), processedEventCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("RS78"),
                players = listOf(playerOne, playerOne),
                turnOrder = listOf(playerOne),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("TU90"),
                players = listOf(playerOne),
                turnOrder = listOf(playerOne, playerOne),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("VW12"),
                players = listOf(playerOne),
                turnOrder = listOf(playerTwo),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("ZA56"),
                players = listOf(playerOne, playerTwo),
                turnOrder = listOf(playerOne),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("XY34"),
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                activePlayer = playerTwo,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("XY56"),
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                setupTroopsToPlaceByPlayer = emptyMap(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("XY78"),
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                setupTroopsToPlaceByPlayer = mapOf(playerOne to -1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("XZ56"),
                mapDefinition = sampleMapDefinition(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("QB78"),
                players = listOf(playerOne),
                turnOrder = listOf(playerOne),
                activePlayer = playerOne,
                turnState =
                    TurnState(
                        activePlayerId = playerTwo,
                        turnPhase = TurnPhase.REINFORCEMENTS,
                        startPlayerId = playerOne,
                    ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameState(
                lobbyCode = LobbyCode("YA78"),
                mapDefinition = sampleMapDefinition(),
                territoryStates =
                    mapOf(
                        TerritoryId("alpha") to TerritoryState(TerritoryId("beta")),
                        TerritoryId("beta") to TerritoryState(TerritoryId("beta")),
                        TerritoryId("gamma") to TerritoryState(TerritoryId("gamma")),
                    ),
            )
        }
    }

    @Test
    fun `should increment state version monotonically for each applied event`() {
        val reducer = DefaultLobbyEventReducer()
        val lobbyCode = LobbyCode("SV12")
        val playerOne = PlayerId(1)
        val playerTwo = PlayerId(2)

        val initialState =
            GameState.initial(
                lobbyCode = lobbyCode,
                mapDefinition = sampleMapDefinition(),
                players = listOf(playerOne, playerTwo),
            )
        val afterOwnerChanged =
            reducer.apply(
                initialState,
                TerritoryOwnerChangedEvent(lobbyCode, TerritoryId("alpha"), playerOne),
            )
        val afterTroopsChanged =
            reducer.apply(
                afterOwnerChanged,
                TerritoryTroopsChangedEvent(lobbyCode, TerritoryId("alpha"), 3),
            )

        assertEquals(0, initialState.stateVersion)
        assertEquals(1, afterOwnerChanged.stateVersion)
        assertEquals(2, afterTroopsChanged.stateVersion)
        assertEquals(0, initialState.processedEventCount)
        assertEquals(1, afterOwnerChanged.processedEventCount)
        assertEquals(2, afterTroopsChanged.processedEventCount)
    }

    private fun sampleMapDefinition(): MapDefinition =
        MapDefinition(
            schemaVersion = 1,
            territories =
                listOf(
                    TerritoryDefinition(
                        territoryId = TerritoryId("alpha"),
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(targetId = TerritoryId("beta")),
                                TerritoryEdgeDefinition(targetId = TerritoryId("gamma")),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("beta"),
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(targetId = TerritoryId("alpha")),
                            ),
                    ),
                    TerritoryDefinition(
                        territoryId = TerritoryId("gamma"),
                        edges =
                            listOf(
                                TerritoryEdgeDefinition(targetId = TerritoryId("alpha")),
                            ),
                    ),
                ),
            continents =
                listOf(
                    ContinentDefinition(
                        continentId = ContinentId("north"),
                        territoryIds = listOf(TerritoryId("alpha"), TerritoryId("beta")),
                        bonusValue = 3,
                    ),
                    ContinentDefinition(
                        continentId = ContinentId("south"),
                        territoryIds = listOf(TerritoryId("gamma")),
                        bonusValue = 1,
                    ),
                ),
        )
}
