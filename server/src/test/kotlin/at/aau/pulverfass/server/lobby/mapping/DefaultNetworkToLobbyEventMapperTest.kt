package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.GameStarted
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerKicked
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.message.lobby.request.CreateLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.JoinLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.LeaveLobbyRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.protocol.MessageHeader
import at.aau.pulverfass.shared.message.protocol.MessageType
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultNetworkToLobbyEventMapperTest {
    private val mapper = DefaultNetworkToLobbyEventMapper()

    @Test
    fun `join request wird korrekt auf domain event gemappt`() {
        val lobbyCode = LobbyCode("AB12")
        val playerId = PlayerId(7)
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(1),
                        MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                    ),
                payload = JoinLobbyRequest(lobbyCode, "Alice"),
                context =
                    EventContext(
                        connectionId = ConnectionId(1),
                        playerId = playerId,
                        occurredAtEpochMillis = 1234,
                    ),
            )

        val mapped = mapper.map(request)

        assertEquals(lobbyCode, mapped.lobbyCode)
        assertEquals(request.context, mapped.context)
        assertEquals(1, mapped.events.size)
        assertEquals(PlayerJoined(lobbyCode, playerId, "Alice"), mapped.events.single())
    }

    @Test
    fun `leave request wird korrekt auf domain event gemappt`() {
        val lobbyCode = LobbyCode("GH78")
        val playerId = PlayerId(11)
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(5),
                        MessageHeader(MessageType.LOBBY_LEAVE_REQUEST),
                    ),
                payload = LeaveLobbyRequest(lobbyCode),
                context =
                    EventContext(
                        connectionId = ConnectionId(5),
                        playerId = playerId,
                        occurredAtEpochMillis = 2222,
                    ),
            )

        val mapped = mapper.map(request)

        assertEquals(lobbyCode, mapped.lobbyCode)
        assertEquals(request.context, mapped.context)
        assertEquals(1, mapped.events.size)
        assertEquals(PlayerLeft(lobbyCode, playerId), mapped.events.single())
    }

    @Test
    fun `ungültige lobby payload kombination wird erkannt`() {
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(2),
                        MessageHeader(MessageType.LOBBY_CREATE_REQUEST),
                    ),
                payload = JoinLobbyRequest(LobbyCode("CD34"), "Bob"),
                context =
                    EventContext(
                        connectionId = ConnectionId(2),
                        playerId = PlayerId(8),
                        occurredAtEpochMillis = 5678,
                    ),
            )

        assertThrows(PayloadHeaderMismatchMappingException::class.java) {
            mapper.map(request)
        }
    }

    @Test
    fun `mapping komponente ist ohne ktor testbar`() {
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(3),
                        MessageHeader(MessageType.CONNECTION_REQUEST),
                    ),
                payload = CreateLobbyRequest,
                context =
                    EventContext(
                        connectionId = ConnectionId(3),
                        playerId = PlayerId(9),
                        occurredAtEpochMillis = 9876,
                    ),
            )

        val exception =
            assertThrows(UnsupportedLobbyMappingPayloadException::class.java) {
                mapper.map(request)
            }

        assertTrue(exception.message?.contains("nicht unterstützt") == true)
    }

    @Test
    fun `fehlender player context wird klar abgelehnt`() {
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(4),
                        MessageHeader(MessageType.LOBBY_JOIN_REQUEST),
                    ),
                payload = JoinLobbyRequest(LobbyCode("EF56"), "Carol"),
                context =
                    EventContext(
                        connectionId = ConnectionId(4),
                        occurredAtEpochMillis = 1000,
                    ),
            )

        val exception =
            assertThrows(MissingPlayerContextMappingException::class.java) {
                mapper.map(request)
            }

        assertTrue(exception.message?.contains("LOBBY_JOIN_REQUEST") == true)
        assertTrue(exception.message?.contains("EventContext.playerId") == true)
    }

    @Test
    fun `create request wird als unsupported abgelehnt`() {
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(10),
                        MessageHeader(MessageType.LOBBY_CREATE_REQUEST),
                    ),
                payload = CreateLobbyRequest,
                context =
                    EventContext(
                        connectionId = ConnectionId(10),
                        playerId = PlayerId(42),
                        occurredAtEpochMillis = 3000,
                    ),
            )

        val exception =
            assertThrows(UnsupportedLobbyMappingPayloadException::class.java) {
                mapper.map(request)
            }

        assertTrue(exception.message?.contains("LOBBY_CREATE_REQUEST") == true)
    }

    @Test
    fun `kick request wird korrekt auf domain event gemappt`() {
        val lobbyCode = LobbyCode("AB12")
        val requesterPlayerId = PlayerId(1)
        val targetPlayerId = PlayerId(2)
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(11),
                        MessageHeader(MessageType.LOBBY_KICK_REQUEST),
                    ),
                payload = KickPlayerRequest(lobbyCode, targetPlayerId, requesterPlayerId),
                context =
                    EventContext(
                        connectionId = ConnectionId(11),
                        playerId = requesterPlayerId,
                        occurredAtEpochMillis = 4000,
                    ),
            )

        val mapped = mapper.map(request)

        assertEquals(lobbyCode, mapped.lobbyCode)
        assertEquals(request.context, mapped.context)
        assertEquals(1, mapped.events.size)
        assertEquals(
            PlayerKicked(lobbyCode, targetPlayerId, requesterPlayerId),
            mapped.events.single(),
        )
    }

    @Test
    fun `start game request wird korrekt auf domain event gemappt`() {
        val lobbyCode = LobbyCode("ZZ88")
        val playerId = PlayerId(99)
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(12),
                        MessageHeader(MessageType.LOBBY_START_REQUEST),
                    ),
                payload = StartGameRequest(lobbyCode),
                context =
                    EventContext(
                        connectionId = ConnectionId(12),
                        playerId = playerId,
                        occurredAtEpochMillis = 5000,
                    ),
            )

        val mapped = mapper.map(request)

        assertEquals(lobbyCode, mapped.lobbyCode)
        assertEquals(request.context, mapped.context)
        assertEquals(1, mapped.events.size)
        assertEquals(GameStarted(lobbyCode), mapped.events.single())
    }

    private fun receivedPacket(
        connectionId: ConnectionId,
        header: MessageHeader,
    ): ReceivedPacket =
        ReceivedPacket(
            connectionId = connectionId,
            header = header,
            packet = SerializedPacket(headerBytes = byteArrayOf(1), payloadBytes = byteArrayOf()),
        )
}
