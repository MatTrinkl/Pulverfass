package at.aau.pulverfass.shared.ids

import kotlinx.serialization.Serializable

private val territoryIdPattern = Regex("^[a-z][a-z0-9]*(?:[_-][a-z0-9]+)*$")

/**
 * Fachliche ID eines Territoriums innerhalb einer Map-Konfiguration.
 */
@Serializable
@JvmInline
value class TerritoryId(val value: String) {
    init {
        require(territoryIdPattern.matches(value)) {
            "TerritoryId muss aus Kleinbuchstaben, Zahlen, '_' oder '-' bestehen " +
                "und mit einem Buchstaben beginnen, war aber '$value'."
        }
    }
}
