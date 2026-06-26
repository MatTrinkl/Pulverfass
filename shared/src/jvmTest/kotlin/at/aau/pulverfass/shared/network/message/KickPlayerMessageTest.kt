package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEvent
import at.aau.pulverfass.shared.message.lobby.event.PlayerKickedLobbyEventSerializer
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequest
import at.aau.pulverfass.shared.message.lobby.request.KickPlayerRequestSerializer
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponse
import at.aau.pulverfass.shared.message.lobby.response.KickPlayerResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.KickPlayerErrorResponseSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for KickPlayer network messages.
 *
 * Validates that all Kick payloads (Request/Response/Event) follow the
 * CustomSerializer pattern documented in docs/NETWORK_MESSAGES.md:
 * - FQCN descriptor naming
 * - Stable field ordering
 * - MissingFieldException for required fields
 * - Roundtrip consistency (encode → decode → equality)
 */
class KickPlayerMessageTest {
    @Test
    fun `KickPlayerRequest serializes and deserializes correctly`() {
        val lobbyCode = LobbyCode("AB12")
        val targetPlayerId = PlayerId(2)
        val requesterPlayerId = PlayerId(1)
        val request = KickPlayerRequest(lobbyCode, targetPlayerId, requesterPlayerId)

        val json = Json.encodeToString(KickPlayerRequestSerializer, request)
        val deserialized = Json.decodeFromString(KickPlayerRequestSerializer, json)

        assertEquals(request, deserialized)
        assertEquals(lobbyCode, deserialized.lobbyCode)
        assertEquals(targetPlayerId, deserialized.targetPlayerId)
        assertEquals(requesterPlayerId, deserialized.requesterPlayerId)
    }

    @Test
    fun `KickPlayerRequest throws MissingFieldException when lobbyCode is missing`() {
        val json = """{"targetPlayerId":2,"requesterPlayerId":1}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(KickPlayerRequestSerializer, json)
        }
    }

    @Test
    fun `KickPlayerRequest throws MissingFieldException when targetPlayerId is missing`() {
        val json = """{"lobbyCode":"AB12","requesterPlayerId":1}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(KickPlayerRequestSerializer, json)
        }
    }

    @Test
    fun `KickPlayerRequest throws MissingFieldException when requesterPlayerId is missing`() {
        val json = """{"lobbyCode":"AB12","targetPlayerId":2}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(KickPlayerRequestSerializer, json)
        }
    }

    @Test
    fun `KickPlayerResponse serializes and deserializes correctly`() {
        val response = KickPlayerResponse()

        val json = Json.encodeToString(KickPlayerResponseSerializer, response)
        val deserialized = Json.decodeFromString(KickPlayerResponseSerializer, json)

        assertEquals(response, deserialized)
        assertEquals(true, deserialized.success)
    }

    @Test
    fun `KickPlayerErrorResponse serializes and deserializes correctly`() {
        val errorResponse = KickPlayerErrorResponse("not_owner")

        val json = Json.encodeToString(KickPlayerErrorResponseSerializer, errorResponse)
        val deserialized = Json.decodeFromString(KickPlayerErrorResponseSerializer, json)

        assertEquals(errorResponse, deserialized)
        assertEquals("not_owner", deserialized.reason)
    }

    @Test
    fun `KickPlayerErrorResponse throws MissingFieldException when reason is missing`() {
        val json = """{}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(KickPlayerErrorResponseSerializer, json)
        }
    }

    @Test
    fun `PlayerKickedLobbyEvent serializes and deserializes correctly`() {
        val lobbyCode = LobbyCode("AB12")
        val targetPlayerId = PlayerId(2)
        val requesterPlayerId = PlayerId(1)
        val event = PlayerKickedLobbyEvent(lobbyCode, targetPlayerId, requesterPlayerId)

        val json = Json.encodeToString(PlayerKickedLobbyEventSerializer, event)
        val deserialized = Json.decodeFromString(PlayerKickedLobbyEventSerializer, json)

        assertEquals(event, deserialized)
        assertEquals(lobbyCode, deserialized.lobbyCode)
        assertEquals(targetPlayerId, deserialized.targetPlayerId)
        assertEquals(requesterPlayerId, deserialized.requesterPlayerId)
    }

    @Test
    fun `PlayerKickedLobbyEvent throws MissingFieldException when lobbyCode is missing`() {
        val json = """{"targetPlayerId":2,"requesterPlayerId":1}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(PlayerKickedLobbyEventSerializer, json)
        }
    }

    @Test
    fun `PlayerKickedLobbyEvent throws MissingFieldException when targetPlayerId is missing`() {
        val json = """{"lobbyCode":"AB12","requesterPlayerId":1}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(PlayerKickedLobbyEventSerializer, json)
        }
    }

    @Test
    fun `PlayerKickedLobbyEvent throws MissingFieldException when requesterPlayerId is missing`() {
        val json = """{"lobbyCode":"AB12","targetPlayerId":2}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(PlayerKickedLobbyEventSerializer, json)
        }
    }
}
