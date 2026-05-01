package at.aau.pulverfass.server

import at.aau.pulverfass.shared.message.connection.response.ConnectionResponse
import at.aau.pulverfass.shared.message.lobby.event.GameStateDeltaEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStateSnapshotBroadcast
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import at.aau.pulverfass.shared.network.codec.MessageCodec
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
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
        (
            skipGameSync &&
                (
                    this is GameStateDeltaEvent ||
                        this is GameStateSnapshotBroadcast
                )
        )
