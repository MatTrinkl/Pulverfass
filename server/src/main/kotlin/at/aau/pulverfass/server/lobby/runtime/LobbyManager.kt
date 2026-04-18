package at.aau.pulverfass.server.lobby.runtime

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import at.aau.pulverfass.shared.lobby.state.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

/**
 * Zentrale Verwaltung mehrerer parallel laufender Lobby-Runtimes.
 *
 * Der Manager ist reine Orchestrierung: Er erstellt, findet und beendet Lobbys,
 * enthält aber keine Spiellogik.
 */
class LobbyManager(
    private val runtimeFactory: (LobbyCode, GameState) -> LobbyRuntime,
) {
    constructor(
        scope: CoroutineScope,
        reducerFactory: (LobbyCode) -> LobbyEventReducer = { DefaultLobbyEventReducer() },
        queueCapacity: Int = Channel.BUFFERED,
        hooksFactory: (LobbyCode) -> LobbyRuntimeHooks = { LobbyRuntimeHooks() },
    ) : this(
        runtimeFactory = { lobbyCode, initialState ->
            LobbyRuntime(
                lobbyCode = lobbyCode,
                initialState = initialState,
                reducer = reducerFactory(lobbyCode),
                scope = scope,
                queueCapacity = queueCapacity,
                hooks = hooksFactory(lobbyCode),
            )
        },
    )

    private val lobbies = ConcurrentHashMap<LobbyCode, LobbyRuntime>()
    private val lifecycleLock = Any()

    /**
     * Erstellt eine neue Lobby-Runtime und startet deren Lifecycle.
     *
     * @throws IllegalStateException wenn die Lobby bereits existiert
     */
    fun createLobby(
        lobbyCode: LobbyCode,
        initialState: GameState = GameState.initial(lobbyCode),
    ): LobbyRuntime =
        synchronized(lifecycleLock) {
            require(initialState.lobbyCode == lobbyCode) {
                "Initial state lobbyCode '${initialState.lobbyCode.value}' " +
                    "passt nicht zu Lobby '${lobbyCode.value}'."
            }
            check(lobbies[lobbyCode] == null) {
                "Lobby '${lobbyCode.value}' exists already."
            }

            val runtime = runtimeFactory(lobbyCode, initialState)
            runtime.start()
            lobbies[lobbyCode] = runtime
            runtime
        }

    /**
     * Liefert eine aktive Lobby-Runtime per Lobbycode.
     */
    fun getLobby(lobbyCode: LobbyCode): LobbyRuntime? = lobbies[lobbyCode]

    /**
     * Leitet ein Event an die zugehörige laufende Lobby weiter.
     *
     * @throws IllegalStateException wenn keine Runtime für die Lobby aktiv ist
     */
    suspend fun submit(
        event: LobbyEvent,
        context: EventContext? = null,
    ) {
        return requireRuntime(event.lobbyCode).submit(event, context)
    }

    /**
     * Entfernt und stoppt eine einzelne Lobby-Runtime.
     */
    suspend fun removeLobby(lobbyCode: LobbyCode) {
        val runtime = removeRuntime(lobbyCode)
        if (runtime != null) {
            runtime.shutdown()
        }
    }

    /**
     * Stoppt alle aktiven Lobbys kontrolliert und leert die Verwaltung.
     */
    suspend fun shutdownAll() {
        val activeRuntimes =
            synchronized(lifecycleLock) {
                val values = lobbies.values.toList()
                lobbies.clear()
                values
            }
        activeRuntimes.forEach { runtime -> runtime.shutdown() }
    }

    private fun requireRuntime(lobbyCode: LobbyCode): LobbyRuntime =
        lobbies[lobbyCode]
            ?: throw IllegalStateException(
                "Lobby '${lobbyCode.value}' is not running.",
            )

    private fun removeRuntime(lobbyCode: LobbyCode): LobbyRuntime? =
        synchronized(lifecycleLock) { lobbies.remove(lobbyCode) }
}
