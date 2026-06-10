package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEvent
import at.aau.pulverfass.shared.message.lobby.event.GameStartedEventSerializer
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequest
import at.aau.pulverfass.shared.message.lobby.request.StartGameRequestSerializer
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponse
import at.aau.pulverfass.shared.message.lobby.response.StartGameResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.StartGameErrorResponseSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for StartGame network messages.
 *
 * Validates that all StartGame payloads (Request/Response/Event) follow the
 * CustomSerializer pattern documented in docs/NETWORK_MESSAGES.md:
 * - FQCN descriptor naming
 * - Stable field ordering
 * - MissingFieldException for required fields
 * - Roundtrip consistency (encode → decode → equality)
 */
class StartGameMessageTest {
    @Test
    fun `StartGameRequest serializes and deserializes correctly`() {
        val lobbyCode = LobbyCode("SG01")
        val request = StartGameRequest(lobbyCode)

        val json = Json.encodeToString(StartGameRequestSerializer, request)
        val deserialized = Json.decodeFromString(StartGameRequestSerializer, json)

        assertEquals(request, deserialized)
        assertEquals(lobbyCode, deserialized.lobbyCode)
    }

    @Test
    fun `StartGameRequest throws MissingFieldException when lobbyCode is missing`() {
        val json = """{}"""
        assertThrows(MissingFieldException::class.java) {
            Json.decodeFromString(StartGameRequestSerializer, json)
        }
    }

    @Test
    fun `StartGameResponse serializes and deserializes correctly`() {
        val response = StartGameResponse()

        val json = Json.encodeToString(StartGameResponseSerializer, response)
        val deserialized = Json.decodeFromString(StartGameResponseSerializer, json)

        assertEquals(response, deserialized)
        assertTrue(deserialized.success)
    }

    @Test
    fun `StartGameErrorResponse serializes and deserializes correctly`() {
        val errorResponse = StartGameErrorResponse("not_owner")

        val json = Json.encodeToString(StartGameErrorResponseSerializer, errorResponse)
        val deserialized = Json.decodeFromString(StartGameErrorResponseSerializer, json)

        assertEquals(errorResponse, deserialized)
        assertEquals("not_owner", deserialized.reason)
    }

    @Test
    fun `StartGameErrorResponse throws MissingFieldException when reason is missing`() {
        val json = """{}"""
        assertThrows(MissingFieldException::class.java) {
            Json.decodeFromString(StartGameErrorResponseSerializer, json)
        }
    }

    @Test
    fun `GameStartedEvent serializes and deserializes correctly`() {
        val lobbyCode = LobbyCode("SG02")
        val event = GameStartedEvent(lobbyCode)

        val json = Json.encodeToString(GameStartedEventSerializer, event)
        val deserialized = Json.decodeFromString(GameStartedEventSerializer, json)

        assertEquals(event, deserialized)
        assertEquals(lobbyCode, deserialized.lobbyCode)
    }

    @Test
    fun `GameStartedEvent throws MissingFieldException when lobbyCode is missing`() {
        val json = """{}"""
        assertThrows(MissingFieldException::class.java) {
            Json.decodeFromString(GameStartedEventSerializer, json)
        }
    }
}
