package at.aau.pulverfass.shared.message.connection.event

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.serialization.Serializable

@Serializable
data class GlobalPlayerCountEvent(
    val playerCount: Int,
) : NetworkMessagePayload
