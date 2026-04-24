package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

private val continentIdPattern = Regex("^[a-z][a-z0-9]*(?:[_-][a-z0-9]+)*$")

/**
 * Fachliche ID eines Kontinents innerhalb einer Map-Konfiguration.
 */
@Serializable
@JvmInline
value class ContinentId(val value: String) {
    init {
        require(continentIdPattern.matches(value)) {
            "ContinentId muss aus Kleinbuchstaben, Zahlen, '_' oder '-' bestehen " +
                "und mit einem Buchstaben beginnen, war aber '$value'."
        }
    }
}
