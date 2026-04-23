package at.aau.pulverfass.shared.lobby.command

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TerritoryTroopsChangedEvent
import at.aau.pulverfass.shared.lobby.state.GameState

/**
 * Standard-Regelservice für Map-bezogene Domain-Commands.
 */
class DefaultMapCommandRuleService : MapCommandRuleService {
    override fun createEvents(
        state: GameState,
        command: MapCommand,
    ): List<LobbyEvent> {
        requireSameLobby(state, command)
        requireMapLoaded(state)
        requireKnownPlayer(state, command.playerId)

        return when (command) {
            is PlaceTroopsCommand -> createPlaceTroopsEvents(state, command)
            is MoveTroopsCommand -> createMoveTroopsEvents(state, command)
            is AttackCommand -> createAttackEvents(state, command)
        }
    }

    private fun createPlaceTroopsEvents(
        state: GameState,
        command: PlaceTroopsCommand,
    ): List<LobbyEvent> {
        requireOwnedTerritory(state, command.playerId, command.territoryId)

        return listOf(
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.territoryId,
                troopCount =
                    state.troopCountOf(command.territoryId) + command.troopCount,
            ),
        )
    }

    private fun createMoveTroopsEvents(
        state: GameState,
        command: MoveTroopsCommand,
    ): List<LobbyEvent> {
        requireOwnedTerritory(state, command.playerId, command.fromTerritoryId)
        requireOwnedTerritory(state, command.playerId, command.toTerritoryId)
        requireAdjacent(state, command.fromTerritoryId, command.toTerritoryId, "Move")

        val sourceTroops = state.troopCountOf(command.fromTerritoryId)
        if (sourceTroops <= command.troopCount) {
            throw InvalidMapCommandException(
                "Move von '${command.fromTerritoryId.value}' nach " +
                    "'${command.toTerritoryId.value}' muss mindestens " +
                    "eine Truppe zurücklassen: vorhanden=$sourceTroops, " +
                    "bewegt=${command.troopCount}.",
            )
        }

        return listOf(
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.fromTerritoryId,
                troopCount = sourceTroops - command.troopCount,
            ),
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.toTerritoryId,
                troopCount =
                    state.troopCountOf(command.toTerritoryId) + command.troopCount,
            ),
        )
    }

    private fun createAttackEvents(
        state: GameState,
        command: AttackCommand,
    ): List<LobbyEvent> {
        requireOwnedTerritory(state, command.playerId, command.fromTerritoryId)
        requireKnownTerritory(state, command.toTerritoryId)
        requireAdjacent(state, command.fromTerritoryId, command.toTerritoryId, "Attack")

        val defenderId = state.ownerOf(command.toTerritoryId)
        if (defenderId == null) {
            throw InvalidMapCommandException(
                "Attack-Ziel '${command.toTerritoryId.value}' muss einen Besitzer haben.",
            )
        }
        if (defenderId == command.playerId) {
            throw InvalidMapCommandException(
                "Attack von '${command.fromTerritoryId.value}' nach " +
                    "'${command.toTerritoryId.value}' ist ungültig, " +
                    "da beide Territorien Spieler " +
                    "'${command.playerId.value}' gehören.",
            )
        }

        val sourceTroops = state.troopCountOf(command.fromTerritoryId)
        val targetTroops = state.troopCountOf(command.toTerritoryId)
        if (sourceTroops < 2) {
            throw InvalidMapCommandException(
                "Attack von '${command.fromTerritoryId.value}' benötigt mindestens 2 Truppen, " +
                    "vorhanden sind $sourceTroops.",
            )
        }
        if (command.defenderLosses > targetTroops) {
            throw InvalidMapCommandException(
                "Attack kann nicht mehr Verteidiger entfernen als vorhanden: " +
                    "vorhanden=$targetTroops, Verluste=${command.defenderLosses}.",
            )
        }

        val remainingTargetTroops = targetTroops - command.defenderLosses
        return if (remainingTargetTroops > 0) {
            createResolvedAttackEventsWithoutCapture(
                state = state,
                command = command,
                sourceTroops = sourceTroops,
                remainingTargetTroops = remainingTargetTroops,
            )
        } else {
            createResolvedAttackEventsWithCapture(
                state = state,
                command = command,
                sourceTroops = sourceTroops,
            )
        }
    }

    private fun createResolvedAttackEventsWithoutCapture(
        state: GameState,
        command: AttackCommand,
        sourceTroops: Int,
        remainingTargetTroops: Int,
    ): List<LobbyEvent> {
        if (command.occupyingTroopCount != null) {
            throw InvalidMapCommandException(
                "Attack auf '${command.toTerritoryId.value}' darf keine " +
                    "occupyingTroopCount setzen, solange das Territorium nicht " +
                    "erobert wurde.",
            )
        }

        val remainingSourceTroops = sourceTroops - command.attackerLosses
        if (remainingSourceTroops < 1) {
            throw InvalidMapCommandException(
                "Attack von '${command.fromTerritoryId.value}' würde " +
                    "das Ursprungsterritorium leer räumen.",
            )
        }

        return listOf(
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.fromTerritoryId,
                troopCount = remainingSourceTroops,
            ),
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.toTerritoryId,
                troopCount = remainingTargetTroops,
            ),
        )
    }

    private fun createResolvedAttackEventsWithCapture(
        state: GameState,
        command: AttackCommand,
        sourceTroops: Int,
    ): List<LobbyEvent> {
        val occupyingTroopCount =
            command.occupyingTroopCount
                ?: throw InvalidMapCommandException(
                    "Eroberte Attack auf '${command.toTerritoryId.value}' " +
                        "benötigt occupyingTroopCount.",
                )
        val remainingSourceTroops =
            sourceTroops - command.attackerLosses - occupyingTroopCount
        if (remainingSourceTroops < 1) {
            throw InvalidMapCommandException(
                "Attack-Eroberung von '${command.toTerritoryId.value}' muss mindestens eine " +
                    "Truppe auf '${command.fromTerritoryId.value}' zurücklassen.",
            )
        }

        return listOf(
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.fromTerritoryId,
                troopCount = remainingSourceTroops,
            ),
            TerritoryOwnerChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.toTerritoryId,
                ownerId = command.playerId,
            ),
            TerritoryTroopsChangedEvent(
                lobbyCode = command.lobbyCode,
                territoryId = command.toTerritoryId,
                troopCount = occupyingTroopCount,
            ),
        )
    }

    private fun requireSameLobby(
        state: GameState,
        command: MapCommand,
    ) {
        if (state.lobbyCode != command.lobbyCode) {
            throw InvalidMapCommandException(
                "MapCommand für Lobby '${command.lobbyCode.value}' passt nicht zum aktuellen " +
                    "State '${state.lobbyCode.value}'.",
            )
        }
    }

    private fun requireMapLoaded(state: GameState) {
        if (!state.hasMap()) {
            throw InvalidMapCommandException(
                "Map-State ist für Lobby '${state.lobbyCode.value}' noch nicht initialisiert.",
            )
        }
    }

    private fun requireKnownPlayer(
        state: GameState,
        playerId: PlayerId,
    ) {
        if (!state.hasPlayer(playerId)) {
            throw InvalidMapCommandException(
                "Spieler '${playerId.value}' ist nicht Teil der Lobby '${state.lobbyCode.value}'.",
            )
        }
    }

    private fun requireOwnedTerritory(
        state: GameState,
        playerId: PlayerId,
        territoryId: TerritoryId,
    ) {
        requireKnownTerritory(state, territoryId)

        val ownerId = state.ownerOf(territoryId)
        if (ownerId != playerId) {
            throw InvalidMapCommandException(
                "Territory '${territoryId.value}' gehört nicht Spieler " +
                    "'${playerId.value}', sondern '${ownerId?.value}'.",
            )
        }
    }

    private fun requireKnownTerritory(
        state: GameState,
        territoryId: TerritoryId,
    ) {
        if (state.territoryStateOf(territoryId) == null) {
            throw InvalidMapCommandException(
                "Territory '${territoryId.value}' ist nicht Teil der Map von Lobby " +
                    "'${state.lobbyCode.value}'.",
            )
        }
    }

    private fun requireAdjacent(
        state: GameState,
        fromTerritoryId: TerritoryId,
        toTerritoryId: TerritoryId,
        actionName: String,
    ) {
        if (!state.isAdjacent(fromTerritoryId, toTerritoryId)) {
            throw InvalidMapCommandException(
                "$actionName von '${fromTerritoryId.value}' nach '${toTerritoryId.value}' " +
                    "ist nur für direkt benachbarte Territorien erlaubt.",
            )
        }
    }
}
