package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.PrivateGameEvent
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GameStateDeliveryDispatcherTest {
    @Test
    fun `broadcast public delta sends one shared delta to all connected lobby members`() =
        runBlocking {
            val lobbyCode = LobbyCode("DLV1")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val playerThree = PlayerId(3)
            val connectionOne = ConnectionId(101)
            val connectionTwo = ConnectionId(202)
            val sentPayloads = mutableListOf<Pair<ConnectionId, NetworkMessagePayload>>()

            val dispatcher =
                GameStateDeliveryDispatcher(
                    sendPayload = { connectionId, payload -> sentPayloads += connectionId to payload },
                    lobbyMembers = { requestedLobby ->
                        if (requestedLobby == lobbyCode) {
                            listOf(playerOne, playerTwo, playerThree)
                        } else {
                            emptyList()
                        }
                    },
                    connectionIdResolver = { playerId ->
                        when (playerId) {
                            playerOne -> connectionOne
                            playerTwo -> connectionTwo
                            else -> null
                        }
                    },
                )

            val publicEvent = FakePublicEvent("territory-owner-changed")

            dispatcher.broadcastPublicDelta(
                lobbyCode = lobbyCode,
                fromVersion = 7,
                toVersion = 7,
                events = listOf(publicEvent),
            )

            assertEquals(listOf(connectionOne, connectionTwo), sentPayloads.map { it.first })
            assertEquals(
                listOf(
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 7,
                        toVersion = 7,
                        events = listOf(publicEvent),
                    ),
                    GameStateDeltaEvent(
                        lobbyCode = lobbyCode,
                        fromVersion = 7,
                        toVersion = 7,
                        events = listOf(publicEvent),
                    ),
                ),
                sentPayloads.map { it.second },
            )
        }

    @Test
    fun `private state reaches only the addressed player connection`() =
        runBlocking {
            val lobbyCode = LobbyCode("DLV2")
            val playerOne = PlayerId(1)
            val playerTwo = PlayerId(2)
            val connectionOne = ConnectionId(101)
            val connectionTwo = ConnectionId(202)
            val sentPayloads = mutableListOf<Pair<ConnectionId, NetworkMessagePayload>>()

            val dispatcher =
                GameStateDeliveryDispatcher(
                    sendPayload = { connectionId, payload -> sentPayloads += connectionId to payload },
                    lobbyMembers = { requestedLobby ->
                        if (requestedLobby == lobbyCode) {
                            listOf(playerOne, playerTwo)
                        } else {
                            emptyList()
                        }
                    },
                    connectionIdResolver = { playerId ->
                        when (playerId) {
                            playerOne -> connectionOne
                            playerTwo -> connectionTwo
                            else -> null
                        }
                    },
                )

            val privatePayload = FakePrivateEvent(recipientPlayerId = playerTwo, secret = "hand-cards")

            dispatcher.sendPrivateState(lobbyCode, privatePayload)

            assertEquals(listOf(connectionTwo to privatePayload), sentPayloads)
        }

    @Test
    fun `private state for non member is rejected before any send`() =
        runBlocking {
            val lobbyCode = LobbyCode("DLV3")
            val playerOne = PlayerId(1)
            val outsider = PlayerId(99)
            val sentPayloads = mutableListOf<Pair<ConnectionId, NetworkMessagePayload>>()

            val dispatcher =
                GameStateDeliveryDispatcher(
                    sendPayload = { connectionId, payload -> sentPayloads += connectionId to payload },
                    lobbyMembers = { requestedLobby ->
                        if (requestedLobby == lobbyCode) {
                            listOf(playerOne)
                        } else {
                            emptyList()
                        }
                    },
                    connectionIdResolver = { ConnectionId(it.value) },
                )

            val exception =
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking {
                        dispatcher.sendPrivateState(
                            lobbyCode,
                            FakePrivateEvent(recipientPlayerId = outsider, secret = "should-fail"),
                        )
                    }
                }

            assertEquals(
                "Spieler '99' ist nicht Teil der Lobby 'DLV3'.",
                exception.message,
            )
            assertEquals(emptyList<Pair<ConnectionId, NetworkMessagePayload>>(), sentPayloads)
        }

    private data class FakePublicEvent(
        val marker: String,
    ) : PublicGameEvent

    private data class FakePrivateEvent(
        override val recipientPlayerId: PlayerId,
        val secret: String,
    ) : PrivateGameEvent
}
