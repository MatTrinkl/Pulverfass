package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.server.lobby.mapping.MappedLobbyEvents
import at.aau.pulverfass.server.lobby.mapping.NetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.reducer.LobbyCodeMismatchException
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.GameJoinRequest
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LobbyRoutingResultModelTest {
    @Test
    fun `typische routing fehler werden korrekt modelliert`(): Unit =
        runBlocking {
            val unknownLobbyResult = routeUnknownLobby()
            assertIs<LobbyRoutingResult.Failure>(unknownLobbyResult)
            assertIs<LobbyRoutingError.LobbyNotFound>(unknownLobbyResult.error)

            val invalidRoutingResult = routeInvalidRoutingData()
            assertIs<LobbyRoutingResult.Failure>(invalidRoutingResult)
            assertIs<LobbyRoutingError.InvalidRoutingData>(invalidRoutingResult.error)

            val invalidEventResult = routeInvalidEvent()
            assertIs<LobbyRoutingResult.Failure>(invalidEventResult)
            assertIs<LobbyRoutingError.InvalidEvent>(invalidEventResult.error)

            val invalidStateResult = routeInvalidStateTransition()
            assertIs<LobbyRoutingResult.Failure>(invalidStateResult)
            assertIs<LobbyRoutingError.InvalidStateTransition>(invalidStateResult.error)
        }

    @Test
    fun `fehlerobjekte tragen relevante kontextinformationen`() =
        runBlocking {
            val lobbyCode = LobbyCode("AB12")
            val connectionId = ConnectionId(1)
            val request = decodedRequest(connectionId = connectionId)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val mapper =
                fixedMapper(
                    MappedLobbyEvents(
                        lobbyCode = lobbyCode,
                        events = listOf(SystemTick(lobbyCode, tick = 1)),
                        context =
                            EventContext(
                                connectionId = connectionId,
                                occurredAtEpochMillis = 1,
                            ),
                    ),
                )
            val router = MainServerRouter(manager, mapper)

            try {
                val result = router.route(request)
                val failure = assertIs<LobbyRoutingResult.Failure>(result)
                val error = assertIs<LobbyRoutingError.LobbyNotFound>(failure.error)

                assertEquals(lobbyCode, error.lobbyCode)
                assertEquals(connectionId, error.context.connectionId)
                assertEquals(MessageType.GAME_JOIN_REQUEST, error.context.messageType)
                assertEquals(lobbyCode, error.context.lobbyCode)
                assertTrue(error.reason.contains(lobbyCode.value))
                assertNotNull(error.context)
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    private suspend fun routeUnknownLobby(): LobbyRoutingResult {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = LobbyManager(scope)
        val lobbyCode = LobbyCode("CD34")
        val router =
            MainServerRouter(
                lobbyManager = manager,
                mapper =
                    fixedMapper(
                        MappedLobbyEvents(
                            lobbyCode = lobbyCode,
                            events = listOf(SystemTick(lobbyCode, tick = 1)),
                            context =
                                EventContext(
                                    connectionId = ConnectionId(2),
                                    occurredAtEpochMillis = 2,
                                ),
                        ),
                    ),
            )

        return try {
            router.route(decodedRequest(connectionId = ConnectionId(2)))
        } finally {
            manager.shutdownAll()
            scope.cancel()
        }
    }

    private suspend fun routeInvalidRoutingData(): LobbyRoutingResult {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = LobbyManager(scope)
        val mappedLobby = LobbyCode("EF56")
        val otherLobby = LobbyCode("GH78")
        val router =
            MainServerRouter(
                lobbyManager = manager,
                mapper =
                    fixedMapper(
                        MappedLobbyEvents(
                            lobbyCode = mappedLobby,
                            events = listOf(SystemTick(otherLobby, tick = 1)),
                            context =
                                EventContext(
                                    connectionId = ConnectionId(3),
                                    occurredAtEpochMillis = 3,
                                ),
                        ),
                    ),
            )

        return try {
            manager.createLobby(mappedLobby)
            router.route(decodedRequest(connectionId = ConnectionId(3)))
        } finally {
            manager.shutdownAll()
            scope.cancel()
        }
    }

    private suspend fun routeInvalidEvent(): LobbyRoutingResult {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lobbyCode = LobbyCode("JK90")
        val manager =
            LobbyManager(
                scope = scope,
                reducerFactory = {
                    object : LobbyEventReducer {
                        override fun apply(
                            state: GameState,
                            event: at.aau.pulverfass.shared.lobby.event.LobbyEvent,
                            context: EventContext?,
                        ): GameState {
                            throw LobbyCodeMismatchException(
                                expectedLobbyCode = state.lobbyCode,
                                actualLobbyCode = event.lobbyCode,
                            )
                        }
                    }
                },
            )
        val router =
            MainServerRouter(
                lobbyManager = manager,
                mapper =
                    fixedMapper(
                        MappedLobbyEvents(
                            lobbyCode = lobbyCode,
                            events = listOf(SystemTick(lobbyCode, tick = 1)),
                            context =
                                EventContext(
                                    connectionId = ConnectionId(4),
                                    occurredAtEpochMillis = 4,
                                ),
                        ),
                    ),
            )

        return try {
            manager.createLobby(lobbyCode)
            router.route(decodedRequest(connectionId = ConnectionId(4)))
        } finally {
            manager.shutdownAll()
            scope.cancel()
        }
    }

    private suspend fun routeInvalidStateTransition(): LobbyRoutingResult {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lobbyCode = LobbyCode("LM12")
        val manager = LobbyManager(scope)
        val router =
            MainServerRouter(
                lobbyManager = manager,
                mapper =
                    fixedMapper(
                        MappedLobbyEvents(
                            lobbyCode = lobbyCode,
                            events = listOf(TurnEnded(lobbyCode, PlayerId(9))),
                            context =
                                EventContext(
                                    connectionId = ConnectionId(5),
                                    occurredAtEpochMillis = 5,
                                ),
                        ),
                    ),
            )

        return try {
            manager.createLobby(lobbyCode)
            router.route(decodedRequest(connectionId = ConnectionId(5)))
        } finally {
            manager.shutdownAll()
            scope.cancel()
        }
    }

    private fun fixedMapper(result: MappedLobbyEvents): NetworkToLobbyEventMapper =
        object : NetworkToLobbyEventMapper {
            override fun map(request: DecodedNetworkRequest): MappedLobbyEvents = result
        }

    private fun decodedRequest(connectionId: ConnectionId): DecodedNetworkRequest =
        DecodedNetworkRequest(
            receivedPacket =
                ReceivedPacket(
                    connectionId = connectionId,
                    header = MessageHeader(MessageType.GAME_JOIN_REQUEST),
                    packet =
                        SerializedPacket(
                            headerBytes = byteArrayOf(1),
                            payloadBytes = byteArrayOf(),
                        ),
                ),
            payload = GameJoinRequest(lobbyCode = LobbyCode("AB12")),
            context = EventContext(connectionId = connectionId, occurredAtEpochMillis = 1),
        )
}
