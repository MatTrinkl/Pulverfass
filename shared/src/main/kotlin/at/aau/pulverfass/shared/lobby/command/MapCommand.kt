package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId

/**
 * Fachlicher Intent für autoritative Map-Änderungen.
 *
 * Commands modellieren Nutzer- oder Server-Intents. Erst die Rule-Schicht
 * entscheidet, ob und welche Domain-Events daraus entstehen.
 */
sealed interface MapCommand {
    val lobbyCode: LobbyCode
    val playerId: PlayerId
}

/**
 * Fügt auf einem eigenen Territorium zusätzliche Truppen hinzu.
 */
data class PlaceTroopsCommand(
    override val lobbyCode: LobbyCode,
    override val playerId: PlayerId,
    val territoryId: TerritoryId,
    val troopCount: Int,
) : MapCommand {
    init {
        require(troopCount > 0) {
            "PlaceTroopsCommand.troopCount muss positiv sein, war aber $troopCount."
        }
    }
}

/**
 * Verschiebt Truppen zwischen zwei direkt benachbarten eigenen Territorien.
 */
data class MoveTroopsCommand(
    override val lobbyCode: LobbyCode,
    override val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val troopCount: Int,
) : MapCommand {
    init {
        require(troopCount > 0) {
            "MoveTroopsCommand.troopCount muss positiv sein, war aber $troopCount."
        }
        require(fromTerritoryId != toTerritoryId) {
            "MoveTroopsCommand benötigt unterschiedliche Territorien."
        }
    }
}

/**
 * Serverautoritativ aufgeloester Angriffs-Intent.
 *
 * Die verbindliche Kampf-Formel steht in `docs/architecture/battle-resolution.md`.
 * Der Command enthaelt nur den Angriffswunsch; Verluste, Rolls und RNG-Trace
 * werden serverseitig in ein `AttackResolvedEvent` uebersetzt.
 */
data class AttackCommand(
    override val lobbyCode: LobbyCode,
    override val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val requestedAttackDice: Int = 3,
    val committedTroopCount: Int? = null,
    val occupyingTroopCount: Int? = null,
) : MapCommand {
    init {
        require(fromTerritoryId != toTerritoryId) {
            "AttackCommand benötigt unterschiedliche Territorien."
        }
        require(requestedAttackDice in 1..3) {
            "AttackCommand.requestedAttackDice muss zwischen 1 und 3 liegen, " +
                "war aber $requestedAttackDice."
        }
        require(committedTroopCount == null || committedTroopCount > 0) {
            "AttackCommand.committedTroopCount muss positiv sein, war aber " +
                "$committedTroopCount."
        }
        require(occupyingTroopCount == null || occupyingTroopCount > 0) {
            "AttackCommand.occupyingTroopCount muss positiv sein, war aber $occupyingTroopCount."
        }
    }
}
