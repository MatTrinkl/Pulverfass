package at.aau.pulverfass.server.routing

import at.aau.pulverfass.server.lobby.mapping.DecodedNetworkRequest
import at.aau.pulverfass.server.lobby.mapping.MappedLobbyEvents
import at.aau.pulverfass.server.lobby.mapping.NetworkToLobbyEventMapper
import at.aau.pulverfass.server.lobby.runtime.LobbyManager
import at.aau.pulverfass.server.lobby.runtime.LobbyRuntimeHooks
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.SystemTick
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.GameJoinRequest
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MainServerRouterTest {
    @Test
    fun `nachricht fuer bekannte lobby wird korrekt weitergeleitet`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val lobbyCode = LobbyCode("AB12")
            val mappedEvent = SystemTick(lobbyCode, tick = 1)
            val mapper =
                fixedMapper(
                    MappedLobbyEvents(
                        lobbyCode = lobbyCode,
                        events = listOf(mappedEvent),
                        context =
                            EventContext(
                                connectionId = ConnectionId(1),
                                occurredAtEpochMillis = 1000,
                            ),
                    ),
                )
            val router = MainServerRouter(manager, mapper)

            try {
                manager.createLobby(lobbyCode)
                router.handle(decodedRequest(connectionId = ConnectionId(1)))
                waitUntilProcessed(manager, lobbyCode, expectedCount = 1)

                val state = manager.getLobby(lobbyCode)?.currentState()
                assertEquals(1, state?.processedEventCount)
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `unbekannte lobby wird erkannt`(): Unit =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val lobbyCode = LobbyCode("CD34")
            val mapper =
                fixedMapper(
                    MappedLobbyEvents(
                        lobbyCode = lobbyCode,
                        events = listOf(SystemTick(lobbyCode, tick = 1)),
                        context =
                            EventContext(
                                connectionId = ConnectionId(2),
                                occurredAtEpochMillis = 2000,
                            ),
                    ),
                )
            val router = MainServerRouter(manager, mapper)

            try {
                assertFailsWith<UnknownLobbyRoutingException> {
                    router.handle(decodedRequest(connectionId = ConnectionId(2)))
                }
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `ungueltige routingdaten erzeugen definierten fehlerpfad`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = LobbyManager(scope)
            val mappedLobby = LobbyCode("EF56")
            val otherLobby = LobbyCode("GH78")
            val mapper =
                fixedMapper(
                    MappedLobbyEvents(
                        lobbyCode = mappedLobby,
                        events = listOf(SystemTick(otherLobby, tick = 1)),
                        context =
                            EventContext(
                                connectionId = ConnectionId(3),
                                occurredAtEpochMillis = 3000,
                            ),
                    ),
                )
            val router = MainServerRouter(manager, mapper)

            try {
                manager.createLobby(mappedLobby)
                val exception =
                    assertFailsWith<InvalidRoutingDataRoutingException> {
                        router.handle(decodedRequest(connectionId = ConnectionId(3)))
                    }
                assertTrue(exception.message?.contains("does not match routed lobby") == true)
            } finally {
                manager.shutdownAll()
                scope.cancel()
            }
        }

    @Test
    fun `router ruft lobbymanager und runtime korrekt auf`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val acceptedCounter = AtomicInteger(0)
            val manager =
                LobbyManager(
                    scope = scope,
                    hooksFactory = {
                        LobbyRuntimeHooks(
                            onEventAccepted = { _, _ -> acceptedCounter.incrementAndGet() },
                        )
                    },
                )
            val lobbyCode = LobbyCode("JK90")
            val mapper =
                fixedMapper(
                    MappedLobbyEvents(
                        lobbyCode = lobbyCode,
                        events = listOf(SystemTick(lobbyCode, tick = 2)),
                        context =
                            EventContext(
                                connectionId = ConnectionId(4),
                                occurredAtEpochMillis = 4000,
                            ),
                    ),
                )
            val routedCounter = AtomicInteger(0)
            val router =
                MainServerRouter(
                    lobbyManager = manager,
                    mapper = mapper,
                    hooks =
                        MainServerRouterHooks(
                            onRouted = { _, _ -> routedCounter.incrementAndGet() },
                        ),
                )

            try {
                manager.createLobby(lobbyCode)
                router.handle(decodedRequest(connectionId = ConnectionId(4)))
                waitUntilProcessed(manager, lobbyCode, expectedCount = 1)

                assertEquals(1, acceptedCounter.get())
                assertEquals(1, routedCounter.get())
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

    private suspend fun waitUntilProcessed(
        manager: LobbyManager,
        lobbyCode: LobbyCode,
        expectedCount: Long,
    ) {
        withTimeout(5_000) {
            while (
                (manager.getLobby(lobbyCode)?.currentState()?.processedEventCount ?: 0L) <
                expectedCount
            ) {
                delay(5)
            }
        }
    }
}
