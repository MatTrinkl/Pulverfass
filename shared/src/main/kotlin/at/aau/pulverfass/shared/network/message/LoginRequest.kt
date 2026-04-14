package at.aau.pulverfass.shared.network.message

import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Login-Anfrage eines Clients an den Server.
 *
 * @property username gewünschter Benutzername des Clients
 * @property password Passwort des Clients
 */
@Serializable(with = LoginRequestSerializer::class)
data class LoginRequest(
    val username: String,
    val password: String,
) : NetworkMessagePayload

object LoginRequestSerializer : KSerializer<LoginRequest> {
    override val descriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.LoginRequest") {
            element<String>("username")
            element<String>("password")
        }

    override fun serialize(
        encoder: Encoder,
        value: LoginRequest,
    ) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.username)
        composite.encodeStringElement(descriptor, 1, value.password)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): LoginRequest {
        val composite = decoder.beginStructure(descriptor)
        var username: String? = null
        var password: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> username = composite.decodeStringElement(descriptor, 0)
                1 -> password = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return LoginRequest(
            username = username ?: throw MissingFieldException("username", descriptor.serialName),
            password = password ?: throw MissingFieldException("password", descriptor.serialName),
        )
    }
}
