package at.aau.pulverfass.server.lobby.runtime

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import at.aau.pulverfass.shared.lobby.state.LobbyStateReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel

/**
 * Zentrale Lifecycle- und Kapselungseinheit für genau eine Lobby.
 *
 * Diese Runtime bündelt Lobby-Identität, State-Verarbeitung und Event-Loop.
 * Externe Schichten reichen Events ausschließlich über [submit] ein.
 */
class LobbyRuntime(
    val lobbyCode: LobbyCode,
    initialState: GameState = GameState.initial(lobbyCode),
    reducer: LobbyEventReducer = DefaultLobbyEventReducer(),
    scope: CoroutineScope,
    queueCapacity: Int = Channel.BUFFERED,
    private val hooks: LobbyRuntimeHooks = LobbyRuntimeHooks(),
) : LobbyStateReader {
    private val eventLoop =
        LobbyEventLoop(
            lobbyCode = lobbyCode,
            initialState = initialState,
            reducer = reducer,
            scope = scope,
            queueCapacity = queueCapacity,
        )

    private val lifecycleLock = Any()
    private var started = false

    /**
     * Aktiviert die Runtime und startet den internen Event-Loop.
     */
    fun start() {
        synchronized(lifecycleLock) {
            check(!started) {
                "Lobby runtime '${lobbyCode.value}' is already started."
            }
            eventLoop.start()
            started = true
        }
        hooks.onStarted(lobbyCode)
    }

    override fun currentState(): GameState = eventLoop.currentState()

    /**
     * Reicht ein Event in die Runtime ein.
     *
     * Die Verarbeitung erfolgt strikt sequentiell im internen Loop. Hook-Aufrufe
     * für Enqueue/Accept/Reject bilden den zentralen Observability-Einstieg.
     */
    suspend fun submit(
        event: LobbyEvent,
        context: EventContext? = null,
    ) {
        hooks.onEventEnqueued(lobbyCode, event, context)
        try {
            eventLoop.submit(event, context)
            hooks.onEventAccepted(lobbyCode, event)
        } catch (cause: Throwable) {
            hooks.onEventRejected(lobbyCode, event, cause)
            throw cause
        }
    }

    /**
     * Beendet die Runtime kontrolliert.
     *
     * Mehrfache Aufrufe sind erlaubt und wirken nur beim ersten Aufruf.
     */
    suspend fun shutdown() {
        val shouldStop =
            synchronized(lifecycleLock) {
                if (!started) {
                    false
                } else {
                    started = false
                    true
                }
            }
        if (!shouldStop) {
            return
        }

        eventLoop.shutdown()
        hooks.onStopped(lobbyCode)
    }
}

/**
 * Erweiterungspunkt für Logging/Metrics ohne Kopplung an konkrete Frameworks.
 */
data class LobbyRuntimeHooks(
    /** Wird direkt nach erfolgreichem Start ausgelöst. */
    val onStarted: (LobbyCode) -> Unit = {},
    /** Wird nach abgeschlossenem Shutdown ausgelöst. */
    val onStopped: (LobbyCode) -> Unit = {},
    /** Wird unmittelbar vor dem technischen Enqueue ausgelöst. */
    val onEventEnqueued: (LobbyCode, LobbyEvent, EventContext?) -> Unit = { _, _, _ -> },
    /** Wird nach erfolgreicher Eventverarbeitung ausgelöst. */
    val onEventAccepted: (LobbyCode, LobbyEvent) -> Unit = { _, _ -> },
    /** Wird bei Verarbeitungsfehlern ausgelöst. */
    val onEventRejected: (LobbyCode, LobbyEvent, Throwable) -> Unit = { _, _, _ -> },
)
