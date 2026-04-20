package at.aau.pulverfass.shared.map.config

import at.aau.pulverfass.shared.ids.ContinentId
import at.aau.pulverfass.shared.ids.TerritoryId

/**
 * Validiert eine geladene [MapConfig] deterministisch und normalisiert Legacy-
 * Felder in die neue Edge-Struktur.
 */
object MapConfigValidator {
    fun validate(config: MapConfig): MapDefinition {
        validateSchemaVersion(config.schemaVersion)

        val territoryIds = LinkedHashSet<TerritoryId>()
        config.territories.forEachIndexed { index, territory ->
            requireAdded(
                added = territoryIds.add(territory.territoryId),
                message =
                    "Territory '${territory.territoryId.value}' ist mehrfach definiert " +
                        "(Index $index).",
            )
        }

        val territoryDefinitions =
            config.territories.map { territory ->
                val edgeTargetIds = LinkedHashSet<TerritoryId>()

                normalizeEdges(territory).forEach { edge ->
                    requireKnownTerritory(
                        sourceId = territory.territoryId,
                        targetId = edge.targetId,
                        knownTerritoryIds = territoryIds,
                    )

                    if (!edgeTargetIds.add(edge.targetId)) {
                        throw MapConfigValidationException(
                            "Territory '${territory.territoryId.value}' definiert die Kante " +
                                "zu '${edge.targetId.value}' mehrfach.",
                        )
                    }
                }

                TerritoryDefinition(
                    territoryId = territory.territoryId,
                    edges =
                        edgeTargetIds.map { targetId ->
                            TerritoryEdgeDefinition(targetId = targetId)
                        },
                )
            }

        val territoriesById = territoryDefinitions.associateBy(TerritoryDefinition::territoryId)
        validateSymmetry(territoryDefinitions, territoriesById)

        val continentIds = LinkedHashSet<ContinentId>()
        val assignedTerritories = LinkedHashMap<TerritoryId, ContinentId>()
        val continentDefinitions =
            config.continents.mapIndexed { index, continent ->
                requireAdded(
                    added = continentIds.add(continent.continentId),
                    message =
                        "Continent '${continent.continentId.value}' ist mehrfach definiert " +
                            "(Index $index).",
                )

                if (continent.territoryIds.isEmpty()) {
                    throw MapConfigValidationException(
                        "Continent '${continent.continentId.value}' muss mindestens ein Territory enthalten.",
                    )
                }

                val localTerritories = LinkedHashSet<TerritoryId>()
                continent.territoryIds.forEach { territoryId ->
                    if (!territoryIds.contains(territoryId)) {
                        throw MapConfigValidationException(
                            "Continent '${continent.continentId.value}' referenziert unbekanntes " +
                                "Territory '${territoryId.value}'.",
                        )
                    }
                    requireAdded(
                        added = localTerritories.add(territoryId),
                        message =
                            "Continent '${continent.continentId.value}' enthält Territory " +
                                "'${territoryId.value}' mehrfach.",
                    )

                    val existingContinent = assignedTerritories.putIfAbsent(territoryId, continent.continentId)
                    if (existingContinent != null) {
                        throw MapConfigValidationException(
                            "Territory '${territoryId.value}' ist mehreren Continents zugeordnet " +
                                "('${existingContinent.value}' und '${continent.continentId.value}').",
                        )
                    }
                }

                ContinentDefinition(
                    continentId = continent.continentId,
                    territoryIds = continent.territoryIds,
                    bonusValue = continent.bonusValue,
                )
            }

        return MapDefinition(
            schemaVersion = config.schemaVersion,
            territories = territoryDefinitions,
            continents = continentDefinitions,
        )
    }

    private fun validateSchemaVersion(schemaVersion: Int) {
        if (schemaVersion != MapConfig.CURRENT_SCHEMA_VERSION) {
            throw MapConfigValidationException(
                "Map-Config schemaVersion $schemaVersion wird nicht unterstützt. " +
                    "Erwartet wird ${MapConfig.CURRENT_SCHEMA_VERSION}.",
            )
        }
    }

    private fun normalizeEdges(territory: TerritoryConfig): List<TerritoryEdgeDefinition> =
        territory.edges.map { TerritoryEdgeDefinition(targetId = it.targetId) } +
            territory.adjacentTerritoryIds.map { TerritoryEdgeDefinition(targetId = it) }

    private fun requireKnownTerritory(
        sourceId: TerritoryId,
        targetId: TerritoryId,
        knownTerritoryIds: Set<TerritoryId>,
    ) {
        if (!knownTerritoryIds.contains(targetId)) {
            throw MapConfigValidationException(
                "Territory '${sourceId.value}' referenziert unbekanntes Ziel-Territory " +
                    "'${targetId.value}'.",
            )
        }
    }

    private fun validateSymmetry(
        territories: List<TerritoryDefinition>,
        territoriesById: Map<TerritoryId, TerritoryDefinition>,
    ) {
        territories.forEach { territory ->
            territory.edges.forEach { edge ->
                val reverseEdge =
                    territoriesById[edge.targetId]
                        ?.edges
                        ?.firstOrNull { candidate -> candidate.targetId == territory.territoryId }

                if (reverseEdge == null) {
                    throw MapConfigValidationException(
                        "Edge '${territory.territoryId.value}' -> '${edge.targetId.value}' " +
                            "hat keine Reverse-Edge.",
                    )
                }
            }
        }
    }

    private fun requireAdded(
        added: Boolean,
        message: String,
    ) {
        if (!added) {
            throw MapConfigValidationException(message)
        }
    }
}
