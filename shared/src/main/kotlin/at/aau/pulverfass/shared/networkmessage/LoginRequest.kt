package at.aau.pulverfass.shared.networkmessage

import at.aau.pulverfass.shared.network.NetworkMessagePayload
import kotlinx.serialization.Serializable

/**
 * Diese Klasse wird bei einem Login Request vom Client an den Server gesendet.
 *
 * @property username Der Username des Clients.
 * @property password Das Passwort des Clients.
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
) : NetworkMessagePayload
