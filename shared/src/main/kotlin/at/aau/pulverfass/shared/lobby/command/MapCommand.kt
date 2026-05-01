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
 * Deterministisch aufgelöster Angriff.
 *
 * Die eigentliche Würfel-/Phasenlogik ist bewusst out of scope. Deshalb trägt
 * der Command bereits das autoritative Ergebnis der Kampfauflösung, das gegen
 * den aktuellen GameState validiert und dann in Events übersetzt wird.
 */
data class AttackCommand(
    override val lobbyCode: LobbyCode,
    override val playerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val attackerLosses: Int,
    val defenderLosses: Int,
    val occupyingTroopCount: Int? = null,
) : MapCommand {
    init {
        require(fromTerritoryId != toTerritoryId) {
            "AttackCommand benötigt unterschiedliche Territorien."
        }
        require(attackerLosses >= 0) {
            "AttackCommand.attackerLosses darf nicht negativ sein, war aber $attackerLosses."
        }
        require(defenderLosses >= 0) {
            "AttackCommand.defenderLosses darf nicht negativ sein, war aber $defenderLosses."
        }
        require(attackerLosses + defenderLosses > 0) {
            "AttackCommand muss mindestens einen Verlust enthalten."
        }
        require(occupyingTroopCount == null || occupyingTroopCount > 0) {
            "AttackCommand.occupyingTroopCount muss positiv sein, war aber $occupyingTroopCount."
        }
    }
}
