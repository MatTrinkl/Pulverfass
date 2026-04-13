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
    val onStarted: (LobbyCode) -> Unit = {},
    val onStopped: (LobbyCode) -> Unit = {},
    val onEventEnqueued: (LobbyCode, LobbyEvent, EventContext?) -> Unit = { _, _, _ -> },
    val onEventAccepted: (LobbyCode, LobbyEvent) -> Unit = { _, _ -> },
    val onEventRejected: (LobbyCode, LobbyEvent, Throwable) -> Unit = { _, _, _ -> },
)
