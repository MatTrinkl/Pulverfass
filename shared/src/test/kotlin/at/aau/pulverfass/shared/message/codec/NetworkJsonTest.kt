package at.aau.pulverfass.shared.message.codec

import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Sichert das Vorwärtskompatibilitäts-Verhalten von [NetworkJson] ab.
 *
 * `ignoreUnknownKeys` darf nur unbekannte Felder still überspringen -- fehlende
 * Pflichtfelder und ungültige Enum-Werte müssen weiterhin hart fehlschlagen,
 * damit echte Schema-Verstöße nicht stillschweigend durchrutschen.
 */
class NetworkJsonTest {
    @Test
    fun `ignores unknown fields for forward compatibility`() {
        val json =
            """{"required":"x","kind":"A","futureField":42,"nested":{"a":1}}"""

        val decoded = NetworkJson.decodeFromString<NetworkJsonSamplePayload>(json)

        assertEquals(
            NetworkJsonSamplePayload(required = "x", kind = NetworkJsonSampleKind.A),
            decoded,
        )
    }

    @Test
    fun `keeps explicit values for known fields when unknown fields are present`() {
        val json =
            """{"required":"x","kind":"B","optional":7,"unknown":"ignored"}"""

        val decoded = NetworkJson.decodeFromString<NetworkJsonSamplePayload>(json)

        assertEquals(
            NetworkJsonSamplePayload(required = "x", kind = NetworkJsonSampleKind.B, optional = 7),
            decoded,
        )
    }

    @Test
    fun `still fails when a required field is missing`() {
        val json = """{"kind":"A","unknown":1}"""

        assertThrows(MissingFieldException::class.java) {
            NetworkJson.decodeFromString<NetworkJsonSamplePayload>(json)
        }
    }

    @Test
    fun `still fails on an invalid enum value`() {
        val json = """{"required":"x","kind":"NOT_A_KIND"}"""

        assertThrows(SerializationException::class.java) {
            NetworkJson.decodeFromString<NetworkJsonSamplePayload>(json)
        }
    }
}

@Serializable
private enum class NetworkJsonSampleKind { A, B }

@Serializable
private data class NetworkJsonSamplePayload(
    val required: String,
    val kind: NetworkJsonSampleKind,
    val optional: Int = 0,
)
