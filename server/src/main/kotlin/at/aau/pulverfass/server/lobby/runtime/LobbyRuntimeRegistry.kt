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
 * Verwaltet mehrere Lobby-Event-Loops innerhalb eines gemeinsamen CoroutineScope.
 *
 * Jede Lobby besitzt genau einen Loop und verarbeitet Events sequentiell. Über
 * mehrere Lobbys hinweg ist Parallelität möglich.
 */
class LobbyRuntimeRegistry(
    private val scope: CoroutineScope,
    private val reducerFactory: (LobbyCode) -> LobbyEventReducer = { DefaultLobbyEventReducer() },
    private val queueCapacity: Int = Channel.BUFFERED,
    private val hooksFactory: (LobbyCode) -> LobbyRuntimeHooks = { LobbyRuntimeHooks() },
) {
    private val runtimes = ConcurrentHashMap<LobbyCode, LobbyRuntime>()
    private val lifecycleLock = Any()

    fun startLobby(
        lobbyCode: LobbyCode,
        initialState: GameState = GameState.initial(lobbyCode),
    ): LobbyRuntime =
        synchronized(lifecycleLock) {
            require(initialState.lobbyCode == lobbyCode) {
                "Initial state lobbyCode '${initialState.lobbyCode.value}' passt nicht zu Lobby '${lobbyCode.value}'."
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

    suspend fun submit(
        event: LobbyEvent,
        context: EventContext? = null,
    ) {
        val runtime = runtimes[event.lobbyCode]
            ?: throw IllegalStateException("Lobby '${event.lobbyCode.value}' is not running.")
        runtime.submit(event, context)
    }

    fun runtime(lobbyCode: LobbyCode): LobbyRuntime? = runtimes[lobbyCode]

    fun reader(lobbyCode: LobbyCode): LobbyStateReader? = runtimes[lobbyCode]

    fun currentState(lobbyCode: LobbyCode): GameState? = runtimes[lobbyCode]?.currentState()

    suspend fun stopLobby(lobbyCode: LobbyCode) {
        val runtime = synchronized(lifecycleLock) { runtimes.remove(lobbyCode) }
        runtime?.shutdown()
    }

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
