package at.aau.pulverfass.shared.network

import kotlinx.serialization.Serializable

@Serializable
data class MessageHeader(
    val type: MessageType,
)
