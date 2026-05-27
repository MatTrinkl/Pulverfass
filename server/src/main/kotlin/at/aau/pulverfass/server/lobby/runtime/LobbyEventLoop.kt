package at.aau.pulverfass.server.lobby.runtime

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.DefaultLobbyStateProcessor
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.LobbyStateReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Asynchroner Event-Loop für genau eine Lobby.
 *
 * Die Verarbeitung ist innerhalb dieser Lobby strikt sequentiell (FIFO über
 * [events]). Reads laufen über Snapshot-Zugriff via [currentState].
 *
 * @param queueCapacity begrenzt die Queue. Ist die Queue voll, blockiert
 * [submit], bis wieder Platz frei ist (Backpressure).
 */
internal class LobbyEventLoop(
    val lobbyCode: LobbyCode,
    initialState: GameState = GameState.initial(lobbyCode),
    private val reducer: LobbyEventReducer = DefaultLobbyEventReducer(),
    private val stateProcessor: DefaultLobbyStateProcessor =
        DefaultLobbyStateProcessor(initialState, reducer),
    private val scope: CoroutineScope,
    queueCapacity: Int = Channel.BUFFERED,
) : LobbyStateReader by stateProcessor {
    init {
        require(initialState.lobbyCode == lobbyCode) {
            "Initial state lobbyCode '${initialState.lobbyCode.value}' " +
                "passt nicht zu Loop '${lobbyCode.value}'."
        }
    }

    private val events = Channel<QueuedItem>(capacity = queueCapacity)
    private val lifecycleLock = Any()
    private var loopJob: Job? = null

    /**
     * Startet den asynchronen Event-Loop.
     *
     * Der Loop darf nur einmal gestartet werden. Weitere Aufrufe sind ein
     * Lifecycle-Fehler und werden explizit abgelehnt.
     */
    fun start() {
        synchronized(lifecycleLock) {
            check(loopJob == null) {
                "Lobby event loop for '${lobbyCode.value}' is already started."
            }
            loopJob =
                scope.launch {
                    for (item in events) {
                        when (item) {
                            is QueuedItem.Single -> processSingle(item)
                            is QueuedItem.Batch -> processBatch(item)
                        }
                    }
                }
        }
    }

    /**
     * Reiht ein Event zur Verarbeitung ein und wartet auf dessen Abschluss.
     *
     * Die Methode liefert erst zurück, wenn das Event im internen FIFO-Loop
     * verarbeitet wurde. Fehler aus Reducer/State-Verarbeitung werden direkt an
     * den Aufrufer propagiert.
     *
     * @throws IllegalStateException wenn der Loop nicht läuft
     */
    suspend fun submit(
        event: LobbyEvent,
        context: EventContext? = null,
    ): ProcessedLobbyEvent {
        require(event.lobbyCode == lobbyCode) {
            "Event lobbyCode '${event.lobbyCode.value}' passt nicht zu Loop '${lobbyCode.value}'."
        }
        val activeJob = synchronized(lifecycleLock) { loopJob }
        check(activeJob?.isActive == true) {
            "Lobby event loop for '${lobbyCode.value}' is not running."
        }
        val processed = CompletableDeferred<ProcessedLobbyEvent>()
        events.send(QueuedItem.Single(event, context, processed))
        return processed.await()
    }

    /**
     * Reiht mehrere Events als atomare Einheit ein.
     *
     * Alle Events werden als einzelne Queue-Nachricht verarbeitet, sodass kein
     * fremdes Event zwischen den Batch-Events interleaven kann.
     */
    suspend fun submitBatch(
        batch: List<LobbyEvent>,
        context: EventContext? = null,
    ): List<ProcessedLobbyEvent> {
        require(batch.all { it.lobbyCode == lobbyCode }) {
            "Alle Batch-Events müssen zur Lobby '$lobbyCode' gehören."
        }
        val activeJob = synchronized(lifecycleLock) { loopJob }
        check(activeJob?.isActive == true) {
            "Lobby event loop for '${lobbyCode.value}' is not running."
        }
        val processed = CompletableDeferred<List<ProcessedLobbyEvent>>()
        events.send(QueuedItem.Batch(batch, context, processed))
        return processed.await()
    }

    private fun processSingle(item: QueuedItem.Single) {
        try {
            val beforeState = stateProcessor.currentState()
            val afterState = stateProcessor.apply(item.event, item.context)
            item.processed.complete(
                ProcessedLobbyEvent(
                    event = item.event,
                    beforeState = beforeState,
                    afterState = afterState,
                ),
            )
        } catch (cause: Throwable) {
            item.processed.completeExceptionally(cause)
        }
    }

    private fun processBatch(item: QueuedItem.Batch) {
        val results = mutableListOf<ProcessedLobbyEvent>()
        for (event in item.events) {
            try {
                val beforeState = stateProcessor.currentState()
                val afterState = stateProcessor.apply(event, item.context)
                results.add(
                    ProcessedLobbyEvent(
                        event = event,
                        beforeState = beforeState,
                        afterState = afterState,
                    ),
                )
            } catch (cause: Throwable) {
                item.processed.completeExceptionally(cause)
                return
            }
        }
        item.processed.complete(results)
    }

    /**
     * Beendet den Event-Loop kontrolliert.
     *
     * Bereits eingequeue-te Events werden vor dem vollständigen Stop weiterhin
     * abgearbeitet. Mehrfache Stop-Aufrufe sind erlaubt und idempotent.
     */
    suspend fun shutdown() {
        val activeJob =
            synchronized(lifecycleLock) {
                val current = loopJob
                loopJob = null
                current
            } ?: return

        events.close()
        activeJob.join()
    }
}

private sealed class QueuedItem {
    data class Single(
        val event: LobbyEvent,
        val context: EventContext?,
        val processed: CompletableDeferred<ProcessedLobbyEvent>,
    ) : QueuedItem()

    data class Batch(
        val events: List<LobbyEvent>,
        val context: EventContext?,
        val processed: CompletableDeferred<List<ProcessedLobbyEvent>>,
    ) : QueuedItem()
}

internal data class ProcessedLobbyEvent(
    val event: LobbyEvent,
    val beforeState: GameState,
    val afterState: GameState,
)
