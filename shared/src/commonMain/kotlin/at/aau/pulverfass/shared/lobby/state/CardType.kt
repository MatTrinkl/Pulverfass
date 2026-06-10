package at.aau.pulverfass.shared.lobby.state

import kotlinx.serialization.Serializable

/**
 * Grundtypen der Risiko-Karten für Trade-In und Draw.
 */
@Serializable
enum class CardType {
    A,
    B,
    C,
    JOKER,
}
