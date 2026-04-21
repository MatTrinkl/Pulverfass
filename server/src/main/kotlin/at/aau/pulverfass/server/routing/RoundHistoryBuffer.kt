package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.lobby.event.PhaseBoundaryEvent

/**
 * Serverseitiger, flüchtiger Verlauf der zuletzt gesendeten öffentlichen
 * Rundenmetadaten. Der Buffer dient ausschließlich Observability-/Debug-Zwecken
 * und speichert bewusst keine privaten Nutzdaten.
 */
data class RoundHistory(
    val roundIndex: Int,
    val startStateVersion: Long,
    val endStateVersion: Long,
    val deltas: List<RoundDeltaMetadata>,
    val phaseBoundaries: List<PhaseBoundaryEvent>,
    val turnStateChanges: List<RoundTurnStateChange>,
    val snapshots: List<RoundSnapshotMetadata>,
)

data class RoundDeltaMetadata(
    val fromVersion: Long,
    val toVersion: Long,
    val eventCount: Int,
)

data class RoundTurnStateChange(
    val stateVersion: Long,
    val activePlayerId: PlayerId,
    val turnPhase: TurnPhase,
    val turnCount: Int,
    val startPlayerId: PlayerId,
    val isPaused: Boolean,
    val pauseReason: String?,
    val pausedPlayerId: PlayerId?,
) {
    companion object {
        fun from(
            stateVersion: Long,
            event: TurnStateUpdatedEvent,
        ): RoundTurnStateChange =
            RoundTurnStateChange(
                stateVersion = stateVersion,
                activePlayerId = event.activePlayerId,
                turnPhase = event.turnPhase,
                turnCount = event.turnCount,
                startPlayerId = event.startPlayerId,
                isPaused = event.isPaused,
                pauseReason = event.pauseReason,
                pausedPlayerId = event.pausedPlayerId,
            )
    }
}

data class RoundSnapshotMetadata(
    val stateVersion: Long,
    val trigger: RoundSnapshotTrigger,
)

enum class RoundSnapshotTrigger {
    TURN_CHANGE_BROADCAST,
    CATCH_UP_RESPONSE,
}

class RoundHistoryBuffer(
    private val maxRounds: Int = 2,
) {
    init {
        require(maxRounds >= 1) {
            "RoundHistoryBuffer.maxRounds muss mindestens 1 sein, war aber $maxRounds."
        }
    }

    private val histories = linkedMapOf<Int, MutableRoundHistory>()

    @Synchronized
    fun recordDelta(
        roundIndex: Int,
        fromVersion: Long,
        toVersion: Long,
        eventCount: Int,
    ) {
        require(roundIndex >= 1) {
            "RoundHistoryBuffer.recordDelta erwartet roundIndex >= 1, war aber $roundIndex."
        }
        require(eventCount >= 1) {
            "RoundHistoryBuffer.recordDelta erwartet eventCount >= 1, war aber $eventCount."
        }

        round(roundIndex).apply {
            includeRange(fromVersion, toVersion)
            deltas += RoundDeltaMetadata(fromVersion = fromVersion, toVersion = toVersion, eventCount = eventCount)
        }
    }

    @Synchronized
    fun recordBoundary(event: PhaseBoundaryEvent) {
        round(event.turnCount).apply {
            includeVersion(event.stateVersion)
            phaseBoundaries += event
        }
    }

    @Synchronized
    fun recordTurnStateChange(
        stateVersion: Long,
        event: TurnStateUpdatedEvent,
    ) {
        round(event.turnCount).apply {
            includeVersion(stateVersion)
            turnStateChanges += RoundTurnStateChange.from(stateVersion = stateVersion, event = event)
        }
    }

    @Synchronized
    fun recordSnapshot(
        roundIndex: Int,
        stateVersion: Long,
        trigger: RoundSnapshotTrigger,
    ) {
        round(roundIndex).apply {
            includeVersion(stateVersion)
            snapshots += RoundSnapshotMetadata(stateVersion = stateVersion, trigger = trigger)
        }
    }

    @Synchronized
    fun history(): List<RoundHistory> = histories.values.map(MutableRoundHistory::toImmutable)

    @Synchronized
    fun describe(): String =
        history().joinToString(separator = " | ") { history ->
            "round=${history.roundIndex} versions=${history.startStateVersion}..${history.endStateVersion} deltas=${history.deltas.size} boundaries=${history.phaseBoundaries.size} turnUpdates=${history.turnStateChanges.size} snapshots=${history.snapshots.size}"
        }.ifBlank {
            "empty"
        }

    private fun round(roundIndex: Int): MutableRoundHistory {
        require(roundIndex >= 1) {
            "RoundHistoryBuffer erwartet roundIndex >= 1, war aber $roundIndex."
        }

        val history = histories.getOrPut(roundIndex) { MutableRoundHistory(roundIndex) }
        evictIfNecessary()
        return history
    }

    private fun evictIfNecessary() {
        while (histories.size > maxRounds) {
            val oldestRound = histories.keys.first()
            histories.remove(oldestRound)
        }
    }

    private class MutableRoundHistory(
        val roundIndex: Int,
    ) {
        var startStateVersion: Long? = null
            private set
        var endStateVersion: Long? = null
            private set

        val deltas = mutableListOf<RoundDeltaMetadata>()
        val phaseBoundaries = mutableListOf<PhaseBoundaryEvent>()
        val turnStateChanges = mutableListOf<RoundTurnStateChange>()
        val snapshots = mutableListOf<RoundSnapshotMetadata>()

        fun includeRange(
            start: Long,
            end: Long,
        ) {
            require(start >= 1) {
                "RoundHistoryBuffer.startStateVersion darf nicht kleiner als 1 sein, war aber $start."
            }
            require(end >= start) {
                "RoundHistoryBuffer.endStateVersion darf nicht kleiner als startStateVersion sein, war aber $end < $start."
            }
            startStateVersion = minOf(startStateVersion ?: start, start)
            endStateVersion = maxOf(endStateVersion ?: end, end)
        }

        fun includeVersion(version: Long) {
            includeRange(version, version)
        }

        fun toImmutable(): RoundHistory =
            RoundHistory(
                roundIndex = roundIndex,
                startStateVersion =
                    startStateVersion
                        ?: error("RoundHistory für Runde $roundIndex wurde ohne stateVersion-Bereich aufgebaut."),
                endStateVersion =
                    endStateVersion
                        ?: error("RoundHistory für Runde $roundIndex wurde ohne stateVersion-Bereich aufgebaut."),
                deltas = deltas.toList(),
                phaseBoundaries = phaseBoundaries.toList(),
                turnStateChanges = turnStateChanges.toList(),
                snapshots = snapshots.toList(),
            )
    }
}
