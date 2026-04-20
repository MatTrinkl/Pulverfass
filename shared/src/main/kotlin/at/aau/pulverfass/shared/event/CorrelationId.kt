package at.aau.pulverfass.shared.event

import kotlinx.serialization.Serializable

/**
 * Standardisierte technische Korrelationskennung für Routing, Logging und Tracing.
 *
 * Die Kennung wird bewusst als eigener Typ modelliert, damit sie nicht als roher
 * String durch verschiedene Schichten gereicht werden muss.
 */
@Serializable
@JvmInline
value class CorrelationId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "CorrelationId darf nicht leer sein."
        }
    }
}
