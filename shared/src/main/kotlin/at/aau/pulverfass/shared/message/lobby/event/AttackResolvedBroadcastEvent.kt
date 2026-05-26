package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import kotlinx.serialization.Serializable

/**
 * Öffentliches, clientsichtbares Kampfergebnis ohne serverinterne RNG-Cursor.
 *
 * Die Broadcast-Payload enthält alle kampfrelevanten Resultate für UI und Replay
 * auf Client-Seite, hält aber die fortlaufende RNG-Entwicklung ausschließlich
 * serverintern.
 */
@Serializable
data class AttackResolvedBroadcastEvent(
    val lobbyCode: LobbyCode,
    val attackerPlayerId: PlayerId,
    val defenderPlayerId: PlayerId,
    val fromTerritoryId: TerritoryId,
    val toTerritoryId: TerritoryId,
    val attackTroops: Int,
    val sourceTroopsBefore: Int,
    val targetTroopsBefore: Int,
    val requestedAttackDice: Int,
    val attackDice: Int,
    val defendDice: Int,
    val attackerRolls: List<Int>,
    val defenderRolls: List<Int>,
    val attackerLosses: Int,
    val defenderLosses: Int,
    val attackerRemaining: Int,
    val defenderRemaining: Int,
    val occupyingTroopCount: Int? = null,
    val minOccupyingTroops: Int? = null,
    val stateVersion: Long? = null,
) : PublicGameEvent {
    companion object {
        fun from(
            event: AttackResolvedEvent,
            stateVersion: Long,
        ): AttackResolvedBroadcastEvent =
            AttackResolvedBroadcastEvent(
                lobbyCode = event.lobbyCode,
                attackerPlayerId = event.attackerPlayerId,
                defenderPlayerId = event.defenderPlayerId,
                fromTerritoryId = event.fromTerritoryId,
                toTerritoryId = event.toTerritoryId,
                attackTroops = event.attackTroops,
                sourceTroopsBefore = event.sourceTroopsBefore,
                targetTroopsBefore = event.targetTroopsBefore,
                requestedAttackDice = event.requestedAttackDice,
                attackDice = event.attackDice,
                defendDice = event.defendDice,
                attackerRolls = event.attackerRolls,
                defenderRolls = event.defenderRolls,
                attackerLosses = event.attackerLosses,
                defenderLosses = event.defenderLosses,
                attackerRemaining = event.attackerRemaining,
                defenderRemaining = event.defenderRemaining,
                occupyingTroopCount = event.occupyingTroopCount,
                minOccupyingTroops = event.minOccupyingTroops,
                stateVersion = stateVersion,
            )
    }

    val capture: Boolean
        get() = defenderRemaining == 0
}
