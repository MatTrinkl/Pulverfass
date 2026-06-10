package at.aau.pulverfass.shared.message.lobby.request

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients an den Server, eine neue Lobby anzulegen.
 *
 * Die Anfrage selbst trägt keine zusätzlichen Parameter; alle notwendigen
 * serverseitigen Daten werden intern ermittelt.
 */
@Serializable
data object CreateLobbyRequest : NetworkMessagePayload

/**
 * Technischer Serializer für [CreateLobbyRequest].
 */
object CreateLobbyRequestSerializer :
    KSerializer<CreateLobbyRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        CreateLobbyRequest.serializer(),
    )
