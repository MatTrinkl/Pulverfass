package at.aau.pulverfass.server

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.connection.event.GlobalPlayerCountEvent
import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.lobby.event.PlayerCountUpdateEvent
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertTrue

internal suspend fun receiveRawTestPayload(
    session: DefaultClientWebSocketSession,
    timeoutMillis: Long = 5_000,
): NetworkMessagePayload {
    val frame =
        withTimeout(timeoutMillis) {
            session.incoming.receive()
        }

    assertTrue(frame is Frame.Binary)
    return MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
}

internal suspend fun receiveRelevantTestPayload(
    session: DefaultClientWebSocketSession,
    skipGameSync: Boolean = false,
    maxMessages: Int = 20,
): NetworkMessagePayload {
    repeat(maxMessages) {
        val payload = receiveRawTestPayload(session)
        if (!payload.isIgnoredTestPayload(skipGameSync)) {
            return payload
        }
    }
    throw AssertionError("Expected relevant payload within $maxMessages messages.")
}

internal suspend fun receiveRelevantTestPayloadOrNull(
    session: DefaultClientWebSocketSession,
    skipGameSync: Boolean = false,
    timeoutMillis: Long = 300,
    maxMessages: Int = 10,
): NetworkMessagePayload? {
    repeat(maxMessages) {
        val payload =
            receiveRawTestPayloadOrNull(
                session = session,
                timeoutMillis = timeoutMillis,
            ) ?: return null
        if (!payload.isIgnoredTestPayload(skipGameSync)) {
            return payload
        }
    }
    return null
}

internal suspend fun receiveTestConnectionToken(
    session: DefaultClientWebSocketSession,
): SessionToken {
    val payload = receiveRawTestPayload(session)
    assertTrue(payload is ConnectionResponse)
    return (payload as ConnectionResponse).sessionToken
}

internal suspend fun awaitTestConnectionId(
    network: ServerNetwork,
    sessionToken: SessionToken,
): ConnectionId =
    withTimeout(5_000) {
        var connectionId: ConnectionId? = null
        while (connectionId == null) {
            connectionId = network.sessionManager.getByToken(sessionToken)?.connectionId
            if (connectionId == null) {
                delay(5)
            }
        }
        connectionId
    }

private suspend fun receiveRawTestPayloadOrNull(
    session: DefaultClientWebSocketSession,
    timeoutMillis: Long,
): NetworkMessagePayload? {
    val frame =
        withTimeoutOrNull(timeoutMillis) {
            session.incoming.receive()
        } ?: return null

    assertTrue(frame is Frame.Binary)
    return MessageCodec.decodePayload((frame as Frame.Binary).readBytes())
}

private fun NetworkMessagePayload.isIgnoredTestPayload(skipGameSync: Boolean): Boolean =
    this is ConnectionResponse ||
        this is PlayerCountUpdateEvent ||
        this is GlobalPlayerCountEvent ||
        (
            skipGameSync &&
                (
                    this is GameStateDeltaEvent ||
                        this is GameStateSnapshotBroadcast
                )
        )
