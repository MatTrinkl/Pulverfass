package at.aau.pulverfass.shared.message.connection.request

import at.aau.pulverfass.shared.ids.SessionToken
import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anfrage eines Clients, eine bestehende Session wieder an eine neue Verbindung
 * zu binden.
 *
 * @property sessionToken stabiler Session-Token des reconnectenden Clients
 */
@Serializable
@SerialName("at.aau.pulverfass.shared.network.message.ReconnectRequest")
data class ReconnectRequest(
    val sessionToken: SessionToken,
) : NetworkMessagePayload

/**
 * Technischer Serializer für [ReconnectRequest].
 */
@OptIn(ExperimentalSerializationApi::class)
object ReconnectRequestSerializer :
    KSerializer<ReconnectRequest> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(ReconnectRequest.serializer())
