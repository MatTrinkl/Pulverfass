package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.map.config.MapDefinition
import kotlin.random.Random

/**
 * Kapselt die Vorbereitungslogik für den eigentlichen Spielstart.
 *
 * Beim Start werden die Territorien zufällig verteilt und direkt mit allen
 * Starttruppen vorbelegt. Die Verteilung erfüllt dabei folgende Regeln:
 *
 * - insgesamt genau 60 Starttruppen
 * - gleich viele Truppen pro Spieler
 * - jedes Territorium besitzt mindestens 1 und höchstens 4 Truppen
 * - jeder Kontinent mit mindestens zwei Territorien wird von mindestens zwei
 *   verschiedenen Spielern geteilt, damit im ersten Zug kein Kontinentbonus
 *   entsteht
 */
internal object GameStartPreparation {
    private const val TOTAL_INITIAL_TROOPS = 60
    private const val MIN_SUPPORTED_PLAYER_COUNT = 3
    private const val MAX_SUPPORTED_PLAYER_COUNT = 6
    private const val MIN_TROOPS_PER_TERRITORY = 1
    private const val MAX_TROOPS_PER_TERRITORY = 4
    private const val MIN_DISTINCT_PLAYERS_PER_CONTINENT = 2

    fun isSupportedPlayerCount(playerCount: Int): Boolean =
        initialTroopsPerPlayer(playerCount) != null

    fun initialTroopsPerPlayer(playerCount: Int): Int? =
        if (
            playerCount in MIN_SUPPORTED_PLAYER_COUNT..MAX_SUPPORTED_PLAYER_COUNT &&
            TOTAL_INITIAL_TROOPS % playerCount == 0
        ) {
            TOTAL_INITIAL_TROOPS / playerCount
        } else {
            null
        }

    fun prepare(
        state: GameState,
        randomSeed: Long,
    ): PreparedGameStart {
        val playerCount = state.players.size
        val initialTroops =
            initialTroopsPerPlayer(playerCount)
                ?: throw IllegalArgumentException(
                    "Spielstart ist nur für 3 bis 6 Spieler unterstützt, waren aber $playerCount.",
                )

        val mapDefinition =
            state.mapDefinition
                ?: throw IllegalArgumentException(
                    "Spielstart benötigt eine geladene Map-Definition.",
                )
        val territoryIds = mapDefinition.territories.map { territory -> territory.territoryId }
        require(territoryIds.isNotEmpty()) {
            "Spielstart benötigt mindestens ein Territorium in der Map."
        }
        require(territoryIds.size * MIN_TROOPS_PER_TERRITORY <= TOTAL_INITIAL_TROOPS) {
            "Map kann nicht vorbereitet werden: ${territoryIds.size} Territorien benötigen mindestens " +
                "${territoryIds.size * MIN_TROOPS_PER_TERRITORY} Starttruppen, aber es stehen nur " +
                "$TOTAL_INITIAL_TROOPS zur Verfügung."
        }
        require(territoryIds.size * MAX_TROOPS_PER_TERRITORY >= TOTAL_INITIAL_TROOPS) {
            "Map kann nicht vorbereitet werden: ${territoryIds.size} Territorien reichen " +
                "mit maximal $MAX_TROOPS_PER_TERRITORY Truppen pro Territorium nicht für " +
                "$TOTAL_INITIAL_TROOPS Starttruppen."
        }

        val random = Random(randomSeed)
        val randomizedTurnOrder = state.players.shuffled(random)
        val territoryOwners =
            assignTerritoryOwners(
                mapDefinition = mapDefinition,
                players = randomizedTurnOrder,
                troopsPerPlayer = initialTroops,
                random = random,
            )
        ensureNoInitialContinentOwner(mapDefinition, territoryOwners)

        val territoryCountsByPlayer =
            randomizedTurnOrder
                .associateWith { playerId ->
                    territoryOwners.values.count { ownerId -> ownerId == playerId }
                }

        territoryCountsByPlayer.forEach { (playerId, territoryCount) ->
            require(territoryCount * MAX_TROOPS_PER_TERRITORY >= initialTroops) {
                "Map kann mit $playerCount Spielern nicht vorbereitet werden: " +
                    "Spieler '${playerId.value}' hat nur $territoryCount Territorien und kann " +
                    "damit höchstens ${territoryCount * MAX_TROOPS_PER_TERRITORY} der " +
                    "$initialTroops Starttruppen aufnehmen."
            }
        }
        val troopCountsByTerritory =
            assignTroopCounts(
                territoryIds = territoryIds,
                territoryOwners = territoryOwners,
                players = randomizedTurnOrder,
                troopsPerPlayer = initialTroops,
                random = random,
            )

        val preparedTerritoryStates =
            state.territoryStates.mapValues { (territoryId, territoryState) ->
                territoryState.copy(
                    ownerId = territoryOwners.getValue(territoryId),
                    troopCount = troopCountsByTerritory.getValue(territoryId),
                )
            }
        val setupTroopsToPlaceByPlayer =
            randomizedTurnOrder.associateWith { 0 }

        return PreparedGameStart(
            randomizedTurnOrder = randomizedTurnOrder,
            preparedTerritoryStates = preparedTerritoryStates,
            setupTroopsToPlaceByPlayer = setupTroopsToPlaceByPlayer,
        )
    }

    private fun assignTerritoryOwners(
        mapDefinition: MapDefinition,
        players: List<PlayerId>,
        troopsPerPlayer: Int,
        random: Random,
    ): Map<TerritoryId, PlayerId> {
        val assignments = linkedMapOf<TerritoryId, PlayerId>()
        val assignedCounts = players.associateWith { 0 }.toMutableMap()
        val minimumTerritoriesPerPlayer =
            (troopsPerPlayer + MAX_TROOPS_PER_TERRITORY - 1) / MAX_TROOPS_PER_TERRITORY

        mapDefinition.continents
            .shuffled(random)
            .forEach { continent ->
                val shuffledTerritories =
                    continent.territoryIds
                        .filterNot(assignments::containsKey)
                        .shuffled(random)
                if (shuffledTerritories.size < MIN_DISTINCT_PLAYERS_PER_CONTINENT) {
                    return@forEach
                }

                val firstPlayer =
                    pickLeastAssignedPlayer(
                        players = players,
                        assignedCounts = assignedCounts,
                        preferredMinimum = minimumTerritoriesPerPlayer,
                        excludedPlayers = emptySet(),
                        random = random,
                    )
                val secondPlayer =
                    pickLeastAssignedPlayer(
                        players = players,
                        assignedCounts = assignedCounts,
                        preferredMinimum = minimumTerritoriesPerPlayer,
                        excludedPlayers = setOf(firstPlayer),
                        random = random,
                    )

                listOf(firstPlayer, secondPlayer)
                    .zip(shuffledTerritories.take(MIN_DISTINCT_PLAYERS_PER_CONTINENT))
                    .forEach { (playerId, territoryId) ->
                        assignments[territoryId] = playerId
                        assignedCounts[playerId] = assignedCounts.getValue(playerId) + 1
                    }
            }

        val remainingTerritories =
            mapDefinition.continents
                .shuffled(random)
                .flatMap { continent ->
                    continent.territoryIds
                        .shuffled(random)
                        .filterNot(assignments::containsKey)
                }

        remainingTerritories.forEach { territoryId ->
            val playerId =
                pickLeastAssignedPlayer(
                    players = players,
                    assignedCounts = assignedCounts,
                    preferredMinimum = minimumTerritoriesPerPlayer,
                    excludedPlayers = emptySet(),
                    random = random,
                )
            assignments[territoryId] = playerId
            assignedCounts[playerId] = assignedCounts.getValue(playerId) + 1
        }

        return assignments
    }

    private fun assignTroopCounts(
        territoryIds: List<TerritoryId>,
        territoryOwners: Map<TerritoryId, PlayerId>,
        players: List<PlayerId>,
        troopsPerPlayer: Int,
        random: Random,
    ): Map<TerritoryId, Int> {
        val troopCounts = territoryIds.associateWith { MIN_TROOPS_PER_TERRITORY }.toMutableMap()

        players.forEach { playerId ->
            val ownedTerritories =
                territoryIds.filter { territoryId ->
                    territoryOwners.getValue(territoryId) == playerId
                }
            val remainingTroops = troopsPerPlayer - ownedTerritories.size
            require(remainingTroops >= 0) {
                "Spieler '${playerId.value}' besitzt mehr Territorien als Starttruppen."
            }

            repeat(remainingTroops) {
                val eligibleTerritories =
                    ownedTerritories.filter { territoryId ->
                        troopCounts.getValue(territoryId) < MAX_TROOPS_PER_TERRITORY
                    }
                require(eligibleTerritories.isNotEmpty()) {
                    "Spieler '${playerId.value}' kann nicht alle Starttruppen innerhalb der " +
                        "Territoriumsgrenze von $MAX_TROOPS_PER_TERRITORY platzieren."
                }
                val territoryId = eligibleTerritories[random.nextInt(eligibleTerritories.size)]
                troopCounts[territoryId] = troopCounts.getValue(territoryId) + 1
            }
        }

        require(troopCounts.values.sum() == TOTAL_INITIAL_TROOPS) {
            "Interner Fehler beim Spielstart: ${troopCounts.values.sum()} statt " +
                "$TOTAL_INITIAL_TROOPS Starttruppen verteilt."
        }
        require(
            troopCounts.values.all { troopCount ->
                troopCount in MIN_TROOPS_PER_TERRITORY..MAX_TROOPS_PER_TERRITORY
            },
        ) {
            "Interner Fehler beim Spielstart: Truppenzahlen liegen außerhalb der erlaubten Grenzen."
        }

        return troopCounts
    }

    private fun ensureNoInitialContinentOwner(
        mapDefinition: MapDefinition,
        territoryOwners: Map<TerritoryId, PlayerId>,
    ) {
        mapDefinition.continents.forEach { continent ->
            if (continent.territoryIds.size < MIN_DISTINCT_PLAYERS_PER_CONTINENT) {
                return@forEach
            }

            val distinctOwners =
                continent.territoryIds
                    .map { territoryId -> territoryOwners.getValue(territoryId) }
                    .distinct()

            require(distinctOwners.size >= MIN_DISTINCT_PLAYERS_PER_CONTINENT) {
                "Kontinent '${continent.continentId.value}' muss zum Spielstart von mindestens " +
                    "$MIN_DISTINCT_PLAYERS_PER_CONTINENT Spielern geteilt werden."
            }
        }
    }

    private fun pickLeastAssignedPlayer(
        players: List<PlayerId>,
        assignedCounts: Map<PlayerId, Int>,
        preferredMinimum: Int,
        excludedPlayers: Set<PlayerId>,
        random: Random,
    ): PlayerId {
        val availablePlayers = players.filterNot(excludedPlayers::contains)
        val playersBelowPreferredMinimum =
            availablePlayers.filter { playerId ->
                assignedCounts.getValue(playerId) < preferredMinimum
            }
        val candidates =
            if (playersBelowPreferredMinimum.isNotEmpty()) {
                playersBelowPreferredMinimum
            } else {
                availablePlayers
            }
        val minimumAssignedCount =
            candidates.minOf { playerId ->
                assignedCounts.getValue(playerId)
            }
        val tiedCandidates =
            candidates.filter { playerId ->
                assignedCounts.getValue(playerId) == minimumAssignedCount
            }

        return tiedCandidates[random.nextInt(tiedCandidates.size)]
    }
}

internal data class PreparedGameStart(
    val randomizedTurnOrder: List<PlayerId>,
    val preparedTerritoryStates: Map<TerritoryId, TerritoryState>,
    val setupTroopsToPlaceByPlayer: Map<PlayerId, Int>,
)
