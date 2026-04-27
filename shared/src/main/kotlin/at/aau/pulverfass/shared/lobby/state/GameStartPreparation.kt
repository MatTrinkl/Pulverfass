package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import kotlin.random.Random

/**
 * Kapselt die Vorbereitungslogik für den eigentlichen Spielstart.
 *
 * Beim Start werden die Territorien zufällig, möglichst gleichmäßig verteilt,
 * jedes Territorium mit genau einer Einheit vorbelegt und die verbleibenden
 * Starttruppen pro Spieler berechnet.
 */
internal object GameStartPreparation {
    private val initialTroopsByPlayerCount =
        mapOf(
            3 to 35,
            4 to 30,
            5 to 25,
            6 to 20,
        )

    fun isSupportedPlayerCount(playerCount: Int): Boolean = initialTroopsByPlayerCount.containsKey(playerCount)

    fun initialTroopsPerPlayer(playerCount: Int): Int? = initialTroopsByPlayerCount[playerCount]

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

        val territoryIds = state.allTerritoryStates().map { territory -> territory.territoryId }
        require(territoryIds.isNotEmpty()) {
            "Spielstart benötigt mindestens ein Territorium in der Map."
        }

        val random = Random(randomSeed)
        val randomizedTurnOrder = state.players.shuffled(random)
        val shuffledTerritories = territoryIds.shuffled(random)
        val territoryOwners =
            shuffledTerritories.mapIndexed { index, territoryId ->
                territoryId to randomizedTurnOrder[index % randomizedTurnOrder.size]
            }.toMap()

        val territoryCountsByPlayer =
            randomizedTurnOrder
                .associateWith { playerId ->
                    territoryOwners.values.count { ownerId -> ownerId == playerId }
                }

        val maxTerritoriesPerPlayer = territoryCountsByPlayer.values.maxOrNull() ?: 0
        require(initialTroops >= maxTerritoriesPerPlayer) {
            "Map kann mit $playerCount Spielern nicht vorbereitet werden: " +
                "$maxTerritoriesPerPlayer Territorien pro Spieler überschreiten die $initialTroops Starttruppen."
        }

        val preparedTerritoryStates =
            state.territoryStates.mapValues { (territoryId, territoryState) ->
                territoryState.copy(
                    ownerId = territoryOwners.getValue(territoryId),
                    troopCount = 1,
                )
            }
        val setupTroopsToPlaceByPlayer =
            randomizedTurnOrder.associateWith { playerId ->
                initialTroops - territoryCountsByPlayer.getValue(playerId)
            }

        return PreparedGameStart(
            randomizedTurnOrder = randomizedTurnOrder,
            preparedTerritoryStates = preparedTerritoryStates,
            setupTroopsToPlaceByPlayer = setupTroopsToPlaceByPlayer,
        )
    }
}

internal data class PreparedGameStart(
    val randomizedTurnOrder: List<PlayerId>,
    val preparedTerritoryStates: Map<TerritoryId, TerritoryState>,
    val setupTroopsToPlaceByPlayer: Map<PlayerId, Int>,
)
