package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PlayerId(val value: Long)
