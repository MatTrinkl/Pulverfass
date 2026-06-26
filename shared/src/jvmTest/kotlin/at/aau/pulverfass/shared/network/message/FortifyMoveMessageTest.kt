package at.aau.pulverfass.shared.network.message

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequest
import at.aau.pulverfass.shared.message.lobby.request.FortifyMoveRequestSerializer
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponse
import at.aau.pulverfass.shared.message.lobby.response.FortifyMoveResponseSerializer
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorCode
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponse
import at.aau.pulverfass.shared.message.lobby.response.error.FortifyMoveErrorResponseSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FortifyMoveMessageTest {
    private val json = Json

    @Test
    fun `serializer roundtrip fortify move request`() {
        val request =
            FortifyMoveRequest(
                lobbyCode = LobbyCode("1154"),
                playerId = PlayerId(7),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                troopCount = 3,
            )

        val serialized = json.encodeToString(FortifyMoveRequest.serializer(), request)
        val deserialized = json.decodeFromString(FortifyMoveRequest.serializer(), serialized)

        assertTrue(serialized.contains("fromTerritoryId"))
        assertEquals(request, deserialized)
    }

    @Test
    fun `fortify move request rejects invalid move shape`() {
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveRequest(
                lobbyCode = LobbyCode("1152"),
                playerId = PlayerId(7),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("beta"),
                troopCount = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveRequest(
                lobbyCode = LobbyCode("1153"),
                playerId = PlayerId(7),
                fromTerritoryId = TerritoryId("alpha"),
                toTerritoryId = TerritoryId("alpha"),
                troopCount = 1,
            )
        }
    }

    @Test
    fun `fortify move request rejects missing required fields`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(FortifyMoveRequest.serializer(), "{}")
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                FortifyMoveRequest.serializer(),
                """
                {
                    "lobbyCode":"1154",
                    "fromTerritoryId":"alpha",
                    "toTerritoryId":"beta",
                    "troopCount":1
                }
                """.trimIndent(),
            )
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                FortifyMoveRequest.serializer(),
                """{"lobbyCode":"1154","playerId":7,"toTerritoryId":"beta","troopCount":1}""",
            )
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                FortifyMoveRequest.serializer(),
                """{"lobbyCode":"1154","playerId":7,"fromTerritoryId":"alpha","troopCount":1}""",
            )
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                FortifyMoveRequest.serializer(),
                """
                {
                    "lobbyCode":"1154",
                    "playerId":7,
                    "fromTerritoryId":"alpha",
                    "toTerritoryId":"beta"
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `fortify move serializers reject unexpected direct decoder index`() {
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveRequestSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FortifyMoveErrorResponseSerializer.deserialize(SerializerTestDecoder(intArrayOf(99)))
        }
    }

    @Test
    fun `serializer roundtrip fortify move response`() {
        val response = FortifyMoveResponse(lobbyCode = LobbyCode("1155"))

        val serialized = json.encodeToString(FortifyMoveResponse.serializer(), response)
        val deserialized = json.decodeFromString(FortifyMoveResponse.serializer(), serialized)

        assertEquals("""{"lobbyCode":"1155"}""", serialized)
        assertEquals(response, deserialized)
    }

    @Test
    fun `fortify move response rejects missing lobby code`() {
        assertThrows(MissingFieldException::class.java) {
            FortifyMoveResponseSerializer.deserialize(
                SerializerTestDecoder(intArrayOf(CompositeDecoder.DECODE_DONE)),
            )
        }
    }

    @Test
    fun `serializer roundtrip fortify move error response`() {
        val response =
            FortifyMoveErrorResponse(
                code = FortifyMoveErrorCode.NO_PATH,
                reason = "Fortify benötigt einen zusammenhängenden Pfad über eigene Gebiete.",
            )

        val serialized = json.encodeToString(FortifyMoveErrorResponse.serializer(), response)
        val deserialized = json.decodeFromString(FortifyMoveErrorResponse.serializer(), serialized)

        assertTrue(serialized.contains("NO_PATH"))
        assertEquals(response, deserialized)
    }

    @Test
    fun `fortify move error response rejects missing required fields`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                FortifyMoveErrorResponse.serializer(),
                """{"reason":"Fortify wurde abgelehnt."}""",
            )
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(
                FortifyMoveErrorResponse.serializer(),
                """{"code":"NO_PATH"}""",
            )
        }
    }
}
