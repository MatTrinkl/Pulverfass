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
    /** `turnCount` der beschriebenen Runde. */
    val roundIndex: Int,
    /** Kleinste bekannte öffentliche Version innerhalb der Runde. */
    val startStateVersion: Long,
    /** Größte bekannte öffentliche Version innerhalb der Runde. */
    val endStateVersion: Long,
    /** Alle in dieser Runde gesendeten Delta-Metadaten. */
    val deltas: List<RoundDeltaMetadata>,
    /** Alle in dieser Runde gesendeten Phasenmarker. */
    val phaseBoundaries: List<PhaseBoundaryEvent>,
    /** Alle beobachteten kombinierten Turn-State-Updates der Runde. */
    val turnStateChanges: List<RoundTurnStateChange>,
    /** Snapshot-Marker für Self-Heal- oder Catch-up-Snapshots. */
    val snapshots: List<RoundSnapshotMetadata>,
)

/** Metadaten eines einzelnen öffentlichen Deltas innerhalb einer Runde. */
data class RoundDeltaMetadata(
    val fromVersion: Long,
    val toVersion: Long,
    val eventCount: Int,
)

/** Projektion eines kombinierten Turn-State-Updates für den History-Buffer. */
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

/** Metadaten eines gesendeten Vollsnapshots innerhalb einer Runde. */
data class RoundSnapshotMetadata(
    val stateVersion: Long,
    val trigger: RoundSnapshotTrigger,
)

/** Auslöser, der zur Speicherung eines Snapshot-Markers geführt hat. */
enum class RoundSnapshotTrigger {
    TURN_CHANGE_BROADCAST,
    CATCH_UP_RESPONSE,
}

/**
 * Ring-Buffer-artiger Speicher für öffentliche Rundenmetadaten der letzten
 * [maxRounds] Runden.
 *
 * Der Buffer ist absichtlich flüchtig und nur für Diagnose, Logging und
 * zukünftige Erweiterungen wie ein mögliches `events-since`-API gedacht.
 */
class RoundHistoryBuffer(
    private val maxRounds: Int = 2,
) {
    init {
        require(maxRounds >= 1) {
            "RoundHistoryBuffer.maxRounds muss mindestens 1 sein, war aber $maxRounds."
        }
    }

    private val histories = linkedMapOf<Int, MutableRoundHistory>()

    /** Fügt ein öffentliches Delta der angegebenen Runde hinzu. */
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

    /** Fügt einen Phasenwechsel-Marker der zugehörigen Runde hinzu. */
    @Synchronized
    fun recordBoundary(event: PhaseBoundaryEvent) {
        round(event.turnCount).apply {
            includeVersion(event.stateVersion)
            phaseBoundaries += event
        }
    }

    /** Fügt ein kombiniertes Turn-State-Update der zugehörigen Runde hinzu. */
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

    /** Fügt einen Snapshot-Marker der angegebenen Runde hinzu. */
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

    /** Liefert eine immutable Sicht auf die aktuell gespeicherten Runden. */
    @Synchronized
    fun history(): List<RoundHistory> = histories.values.map(MutableRoundHistory::toImmutable)

    /** Formatiert den Buffer kompakt für Logging und Diagnoseausgaben. */
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
