package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stabiler Identifier einer normalisierten Map-Definition.
 *
 * Der Hash wird aus einer kanonischen JSON-Repräsentation der validierten
 * Map-Definition abgeleitet und ist dadurch unabhängig von Whitespace oder
 * der Feldreihenfolge der Roh-JSON-Datei.
 */
data class MapIdentifier(
    val schemaVersion: Int,
    val mapHash: String,
)

/**
 * Erzeugt eine kanonische Repräsentation und einen stabilen SHA-256-Hash für
 * [MapDefinition]en.
 */
object MapDefinitionHashing {
    private val json =
        Json {
            explicitNulls = false
            encodeDefaults = true
        }

    fun identifierOf(definition: MapDefinition): MapIdentifier =
        MapIdentifier(
            schemaVersion = definition.schemaVersion,
            mapHash = hashCanonicalJson(canonicalJson(definition)),
        )

    fun canonicalJson(definition: MapDefinition): String =
        json.encodeToString(
            CanonicalMapDefinition.serializer(),
            CanonicalMapDefinition.from(definition),
        )

    private fun hashCanonicalJson(canonicalJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson.encodeToByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

@Serializable
private data class CanonicalMapDefinition(
    val schemaVersion: Int,
    val territories: List<CanonicalTerritoryDefinition>,
    val continents: List<CanonicalContinentDefinition>,
) {
    companion object {
        fun from(definition: MapDefinition): CanonicalMapDefinition =
            CanonicalMapDefinition(
                schemaVersion = definition.schemaVersion,
                territories =
                    definition.territories
                        .sortedBy { territory -> territory.territoryId.value }
                        .map(CanonicalTerritoryDefinition::from),
                continents =
                    definition.continents
                        .sortedBy { continent -> continent.continentId.value }
                        .map(CanonicalContinentDefinition::from),
            )
    }
}

@Serializable
private data class CanonicalTerritoryDefinition(
    val territoryId: TerritoryId,
    val edges: List<CanonicalTerritoryEdgeDefinition>,
) {
    companion object {
        fun from(definition: TerritoryDefinition): CanonicalTerritoryDefinition =
            CanonicalTerritoryDefinition(
                territoryId = definition.territoryId,
                edges =
                    definition.edges
                        .sortedBy { edge -> edge.targetId.value }
                        .map(CanonicalTerritoryEdgeDefinition::from),
            )
    }
}

@Serializable
private data class CanonicalTerritoryEdgeDefinition(
    val targetId: TerritoryId,
) {
    companion object {
        fun from(definition: TerritoryEdgeDefinition): CanonicalTerritoryEdgeDefinition =
            CanonicalTerritoryEdgeDefinition(
                targetId = definition.targetId,
            )
    }
}

@Serializable
private data class CanonicalContinentDefinition(
    val continentId: ContinentId,
    val territoryIds: List<TerritoryId>,
    val bonusValue: Int,
) {
    companion object {
        fun from(definition: ContinentDefinition): CanonicalContinentDefinition =
            CanonicalContinentDefinition(
                continentId = definition.continentId,
                territoryIds = definition.territoryIds.sortedBy(TerritoryId::value),
                bonusValue = definition.bonusValue,
            )
    }
}
