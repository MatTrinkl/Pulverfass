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
import java.util.concurrent.ConcurrentHashMap

/**
 * Verwaltet mehrere Lobby-Runtimes innerhalb eines gemeinsamen CoroutineScope.
 *
 * Jede Lobby besitzt genau eine Runtime und verarbeitet Events intern
 * sequentiell. Über mehrere Lobbys hinweg ist Parallelität möglich.
 */
class LobbyRuntimeRegistry(
    private val scope: CoroutineScope,
    private val reducerFactory: (LobbyCode) -> LobbyEventReducer = { DefaultLobbyEventReducer() },
    private val queueCapacity: Int = Channel.BUFFERED,
    private val hooksFactory: (LobbyCode) -> LobbyRuntimeHooks = { LobbyRuntimeHooks() },
) {
    private val runtimes = ConcurrentHashMap<LobbyCode, LobbyRuntime>()
    private val lifecycleLock = Any()

    /**
     * Erstellt oder liefert die Runtime einer Lobby und startet sie bei Bedarf.
     */
    fun startLobby(
        lobbyCode: LobbyCode,
        initialState: GameState = GameState.initial(lobbyCode),
    ): LobbyRuntime =
        synchronized(lifecycleLock) {
            require(initialState.lobbyCode == lobbyCode) {
                "Initial state lobbyCode '${initialState.lobbyCode.value}' " +
                    "passt nicht zu Lobby '${lobbyCode.value}'."
            }
            runtimes[lobbyCode]?.let { existing -> return@synchronized existing }

            val runtime =
                LobbyRuntime(
                    lobbyCode = lobbyCode,
                    initialState = initialState,
                    reducer = reducerFactory(lobbyCode),
                    scope = scope,
                    queueCapacity = queueCapacity,
                    hooks = hooksFactory(lobbyCode),
                )
            runtime.start()
            runtimes[lobbyCode] = runtime
            runtime
        }

    /**
     * Reicht ein Event an die passende laufende Runtime weiter.
     */
    suspend fun submit(
        event: LobbyEvent,
        context: EventContext? = null,
    ) {
        val runtime =
            runtimes[event.lobbyCode]
                ?: throw IllegalStateException("Lobby '${event.lobbyCode.value}' is not running.")
        runtime.submit(event, context)
    }

    /**
     * Liefert die aktive Runtime einer Lobby, falls vorhanden.
     */
    fun runtime(lobbyCode: LobbyCode): LobbyRuntime? = runtimes[lobbyCode]

    /**
     * Liefert eine reine Read-Sicht auf die aktive Runtime.
     */
    fun reader(lobbyCode: LobbyCode): LobbyStateReader? = runtimes[lobbyCode]

    /**
     * Liefert den aktuellen Snapshot einer Lobby oder `null`, falls nicht aktiv.
     */
    fun currentState(lobbyCode: LobbyCode): GameState? = runtimes[lobbyCode]?.currentState()

    /**
     * Stoppt eine einzelne Lobby-Runtime kontrolliert.
     */
    suspend fun stopLobby(lobbyCode: LobbyCode) {
        val runtime = synchronized(lifecycleLock) { runtimes.remove(lobbyCode) }
        runtime?.shutdown()
    }

    /**
     * Stoppt alle aktiven Lobby-Runtimes kontrolliert.
     */
    suspend fun shutdown() {
        val activeRuntimes =
            synchronized(lifecycleLock) {
                val values = runtimes.values.toList()
                runtimes.clear()
                values
            }
        activeRuntimes.forEach { runtime -> runtime.shutdown() }
    }
}
