package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.connection.response.ReconnectErrorCode
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponse
import at.aau.pulverfass.shared.message.connection.response.ReconnectResponseSerializer
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReconnectResponseTest {
    private val json = Json

    @Test
    fun `should create successful reconnect response correctly`() {
        val response =
            ReconnectResponse(
                success = true,
                playerId = PlayerId(7),
                lobbyCode = LobbyCode("AB12"),
                playerDisplayName = "Alice",
            )

        assertEquals(true, response.success)
        assertEquals(PlayerId(7), response.playerId)
        assertEquals(LobbyCode("AB12"), response.lobbyCode)
        assertEquals("Alice", response.playerDisplayName)
    }

    @Test
    fun `should implement network message payload`() {
        val response =
            ReconnectResponse(
                success = false,
                errorCode = ReconnectErrorCode.TOKEN_INVALID,
            )
        val payload: NetworkMessagePayload = response

        assertEquals(response, payload)
    }

    @Test
    fun `should serialize and deserialize successful reconnect response`() {
        val response =
            ReconnectResponse(
                success = true,
                playerId = PlayerId(8),
                lobbyCode = LobbyCode("CD34"),
                playerDisplayName = "Bob",
            )

        val serialized = json.encodeToString(ReconnectResponse.serializer(), response)
        val deserialized = json.decodeFromString<ReconnectResponse>(serialized)

        assertEquals(
            """{"success":true,"playerId":8,"lobbyCode":"CD34","playerDisplayName":"Bob"}""",
            serialized,
        )
        assertEquals(response, deserialized)
    }

    @Test
    fun `should serialize and deserialize failed reconnect response`() {
        val response =
            ReconnectResponse(
                success = false,
                errorCode = ReconnectErrorCode.TOKEN_EXPIRED,
            )

        val serialized = json.encodeToString(ReconnectResponse.serializer(), response)
        val deserialized = json.decodeFromString<ReconnectResponse>(serialized)

        assertEquals("""{"success":false,"errorCode":"TOKEN_EXPIRED"}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `should reject missing success during deserialization`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<ReconnectResponse>("{}")
        }
    }

    @Test
    fun `should reject unexpected field during deserialization`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun `should reject missing field in serializer directly`() {
        assertThrows(MissingFieldException::class.java) {
            ReconnectResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `should reject success with error code`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectResponse(
                success = true,
                errorCode = ReconnectErrorCode.TOKEN_INVALID,
            )
        }
    }

    @Test
    fun `should reject failed response without error code`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectResponse(success = false)
        }
    }

    @Test
    fun `descriptor should use stable shared network message serial name`() {
        assertEquals(
            "at.aau.pulverfass.shared.network.message.ReconnectResponse",
            ReconnectResponseSerializer.descriptor.serialName,
        )
    }
}
