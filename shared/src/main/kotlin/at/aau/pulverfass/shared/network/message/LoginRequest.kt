package at.aau.pulverfass.shared.network.message

import kotlinx.serialization.Serializable

/**
 * Login-Anfrage eines Clients an den Server.
 *
 * @property username gewünschter Benutzername des Clients
 * @property password Passwort des Clients
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
) : NetworkMessagePayload
