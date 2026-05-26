package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.event.PublicGameEvent
import kotlinx.serialization.Serializable

/**
 * Serverautoritativ aufgeloeste Kampf-Runde inklusive RNG-Trace fuer Replay.
 *
 * Das Event ist die persistierbare Battle-Source-of-Truth und wird zusaetzlich
 * als oeffentliches Delta-Event an Clients broadcastet. Die abgeleiteten
 * Territory-Deltas bleiben fuer bestehende Consumer erhalten.
 */
@Serializable
data class AttackResolvedEvent(
    override val lobbyCode: LobbyCode,
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
    val rngTrace: List<Int>,
    val rngStateBefore: Long,
    val rngStateAfter: Long,
    val attackerLosses: Int,
    val defenderLosses: Int,
    val attackerRemaining: Int,
    val defenderRemaining: Int,
    val occupyingTroopCount: Int? = null,
    val minOccupyingTroops: Int? = null,
    val stateVersion: Long? = null,
) : InternalLobbyEvent, PublicGameEvent {
    init {
        require(attackTroops >= 2) {
            "AttackResolvedEvent.attackTroops muss mindestens 2 sein."
        }
        require(sourceTroopsBefore >= 2) {
            "AttackResolvedEvent.sourceTroopsBefore muss mindestens 2 sein."
        }
        require(targetTroopsBefore >= 1) {
            "AttackResolvedEvent.targetTroopsBefore muss mindestens 1 sein."
        }
        require(attackTroops <= sourceTroopsBefore - 1) {
            "AttackResolvedEvent.attackTroops muss mindestens eine Truppe " +
                "zuruecklassen."
        }
        require(requestedAttackDice in 1..3) {
            "AttackResolvedEvent.requestedAttackDice muss zwischen 1 und 3 liegen."
        }
        require(requestedAttackDice <= attackTroops) {
            "AttackResolvedEvent.requestedAttackDice darf attackTroops nicht ueberschreiten."
        }
        require(attackDice >= 1) {
            "AttackResolvedEvent.attackDice muss mindestens 1 sein."
        }
        require(defendDice >= 1) {
            "AttackResolvedEvent.defendDice muss mindestens 1 sein."
        }
        require(attackDice <= requestedAttackDice) {
            "AttackResolvedEvent.attackDice darf requestedAttackDice nicht ueberschreiten."
        }
        require(attackerLosses >= 0) {
            "AttackResolvedEvent.attackerLosses darf nicht negativ sein."
        }
        require(defenderLosses >= 0) {
            "AttackResolvedEvent.defenderLosses darf nicht negativ sein."
        }
        require(attackerRemaining >= 1) {
            "AttackResolvedEvent.attackerRemaining muss mindestens 1 sein."
        }
        require(defenderRemaining >= 0) {
            "AttackResolvedEvent.defenderRemaining darf nicht negativ sein."
        }
        require(attackerRolls.size == attackDice) {
            "AttackResolvedEvent.attackerRolls muss genau attackDice Eintraege enthalten."
        }
        require(defenderRolls.size == defendDice) {
            "AttackResolvedEvent.defenderRolls muss genau defendDice Eintraege enthalten."
        }
        require(rngTrace.size == attackDice + defendDice) {
            "AttackResolvedEvent.rngTrace muss genau attackDice + defendDice Eintraege enthalten."
        }
        require(attackerRemaining == sourceTroopsBefore - attackerLosses) {
            "AttackResolvedEvent.attackerRemaining passt nicht zu sourceTroopsBefore " +
                "und attackerLosses."
        }
        require(defenderRemaining == targetTroopsBefore - defenderLosses) {
            "AttackResolvedEvent.defenderRemaining passt nicht zu targetTroopsBefore " +
                "und defenderLosses."
        }
        require(attackerLosses + defenderLosses == minOf(attackDice, defendDice)) {
            "AttackResolvedEvent-Verluste muessen genau der Vergleichszahl entsprechen."
        }
        require(occupyingTroopCount == null || occupyingTroopCount > 0) {
            "AttackResolvedEvent.occupyingTroopCount muss positiv sein."
        }
        require(occupyingTroopCount == null || occupyingTroopCount <= attackTroops) {
            "AttackResolvedEvent.occupyingTroopCount darf attackTroops nicht ueberschreiten."
        }
        require((defenderRemaining == 0) == (minOccupyingTroops != null)) {
            "AttackResolvedEvent.minOccupyingTroops darf nur bei Capture gesetzt sein."
        }
        require(stateVersion == null || stateVersion >= 0) {
            "AttackResolvedEvent.stateVersion darf nicht negativ sein."
        }
    }

    val capture: Boolean
        get() = defenderRemaining == 0
}
