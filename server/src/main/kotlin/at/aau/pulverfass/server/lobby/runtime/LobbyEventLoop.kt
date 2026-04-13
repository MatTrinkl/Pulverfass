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
    private val scope: CoroutineScope,
    queueCapacity: Int = Channel.BUFFERED,
) : LobbyStateReader {
    init {
        require(initialState.lobbyCode == lobbyCode) {
            "Initial state lobbyCode '${initialState.lobbyCode.value}' " +
                "passt nicht zu Loop '${lobbyCode.value}'."
        }
    }

    private val stateProcessor = DefaultLobbyStateProcessor(initialState, reducer)
    private val events = Channel<QueuedLobbyEvent>(capacity = queueCapacity)
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
                    for (queued in events) {
                        try {
                            stateProcessor.apply(queued.event, queued.context)
                            queued.processed.complete(Unit)
                        } catch (cause: Throwable) {
                            queued.processed.completeExceptionally(cause)
                        }
                    }
                }
        }
    }

    override fun currentState(): GameState = stateProcessor.currentState()

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
    ) {
        require(event.lobbyCode == lobbyCode) {
            "Event lobbyCode '${event.lobbyCode.value}' passt nicht zu Loop '${lobbyCode.value}'."
        }
        val activeJob = synchronized(lifecycleLock) { loopJob }
        check(activeJob?.isActive == true) {
            "Lobby event loop for '${lobbyCode.value}' is not running."
        }
        val processed = CompletableDeferred<Unit>()
        events.send(QueuedLobbyEvent(event, context, processed))
        processed.await()
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

/**
 * Interne Queue-Nachricht inklusive technischer Completion-Signalisierung.
 */
private data class QueuedLobbyEvent(
    val event: LobbyEvent,
    val context: EventContext?,
    val processed: CompletableDeferred<Unit>,
)
