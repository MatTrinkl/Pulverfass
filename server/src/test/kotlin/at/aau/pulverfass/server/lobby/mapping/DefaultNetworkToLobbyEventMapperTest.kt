package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.network.codec.SerializedPacket
import at.aau.pulverfass.shared.network.message.GameJoinRequest
import at.aau.pulverfass.shared.network.message.LoginRequest
import at.aau.pulverfass.shared.network.message.MessageHeader
import at.aau.pulverfass.shared.network.message.MessageType
import at.aau.pulverfass.shared.network.receive.ReceivedPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
                        MessageHeader(MessageType.GAME_JOIN_REQUEST),
                    ),
                payload = GameJoinRequest(lobbyCode),
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
        assertEquals(PlayerJoined(lobbyCode, playerId), mapped.events.single())
    }

    @Test
    fun `ungueltige lobby payload kombination wird erkannt`() {
        val request =
            DecodedNetworkRequest(
                receivedPacket =
                    receivedPacket(
                        ConnectionId(2),
                        MessageHeader(MessageType.GAME_CREATE_REQUEST),
                    ),
                payload = GameJoinRequest(LobbyCode("CD34")),
                context =
                    EventContext(
                        connectionId = ConnectionId(2),
                        playerId = PlayerId(8),
                        occurredAtEpochMillis = 5678,
                    ),
            )

        assertFailsWith<PayloadHeaderMismatchMappingException> {
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
                        MessageHeader(MessageType.LOGIN_REQUEST),
                    ),
                payload = LoginRequest(username = "alice", password = "secret"),
                context =
                    EventContext(
                        connectionId = ConnectionId(3),
                        playerId = PlayerId(9),
                        occurredAtEpochMillis = 9876,
                    ),
            )

        val exception =
            assertFailsWith<UnsupportedLobbyMappingPayloadException> {
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
                        MessageHeader(MessageType.GAME_JOIN_REQUEST),
                    ),
                payload = GameJoinRequest(LobbyCode("EF56")),
                context =
                    EventContext(
                        connectionId = ConnectionId(4),
                        occurredAtEpochMillis = 1000,
                    ),
            )

        val exception =
            assertFailsWith<MissingPlayerContextMappingException> {
                mapper.map(request)
            }

        assertTrue(exception.message?.contains("GAME_JOIN_REQUEST") == true)
        assertTrue(exception.message?.contains("EventContext.playerId") == true)
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
