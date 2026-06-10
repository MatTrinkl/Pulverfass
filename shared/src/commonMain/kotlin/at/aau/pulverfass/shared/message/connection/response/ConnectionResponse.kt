package at.aau.pulverfass.shared.message.connection.response

import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Technische Initialnachricht des Servers nach erfolgreichem Verbindungsaufbau.
 *
 * @property sessionToken stabiler Token der Session für spätere Reconnect-Flows
 */
@Serializable
data class ConnectionResponse(
    val sessionToken: SessionToken,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [ConnectionResponse].
 */
@OptIn(ExperimentalSerializationApi::class)
object ConnectionResponseSerializer :
    KSerializer<ConnectionResponse> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ConnectionResponse.serializer(),
    )
