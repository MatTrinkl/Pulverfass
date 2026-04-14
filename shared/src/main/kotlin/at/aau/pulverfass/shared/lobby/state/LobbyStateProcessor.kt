package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.reducer.DefaultLobbyEventReducer
import at.aau.pulverfass.shared.lobby.reducer.LobbyEventReducer
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

/**
 * Kombiniert kontrollierte Event-Anwendung mit Snapshot-Reads.
 *
 * Aufrufende, die nur [LobbyStateReader] erhalten, können den State nicht
 * mutieren. Schreibzugriffe laufen ausschließlich über [apply] und damit über
 * den [LobbyEventReducer].
 */
interface LobbyStateProcessor : LobbyStateReader {
    /**
     * Wendet ein Lobby-Event kontrolliert auf den internen Zustand an.
     *
     * @param event fachliches Lobby-Event
     * @param context optionale technische Metadaten zur Eventverarbeitung
     * @return Snapshot des aktualisierten Zustands
     * @throws at.aau.pulverfass.shared.lobby.reducer.LobbyEventReductionException
     * wenn das Event für den aktuellen Zustand ungültig ist
     */
    fun apply(
        event: LobbyEvent,
        context: EventContext? = null,
    ): GameState
}

/**
 * Thread-sichere Referenzimplementierung für sequentielle Event-Verarbeitung.
 *
 * - Writes sind exklusiv (Write-Lock).
 * - Reads laufen parallel zueinander (Read-Lock).
 * - Jeder Read liefert eine Snapshot-Kopie.
 */
class DefaultLobbyStateProcessor(
    initialState: GameState,
    private val reducer: LobbyEventReducer = DefaultLobbyEventReducer(),
) : LobbyStateProcessor {
    private val lock = ReentrantReadWriteLock()
    private var state: GameState = initialState.snapshot()

    override fun currentState(): GameState =
        lock.read {
            state.snapshot()
        }

    override fun apply(
        event: LobbyEvent,
        context: EventContext?,
    ): GameState {
        lock.writeLock().lock()

        return try {
            val updated = reducer.apply(state, event, context)
            state = updated
            updated.snapshot()
        } finally {
            lock.writeLock().unlock()
        }
    }
}

/**
 * Erstellt eine defensive Snapshot-Kopie, damit aufrufende Schichten nie mit
 * internen Listenreferenzen arbeiten.
 */
private fun GameState.snapshot(): GameState =
    copy(
        players = players.toList(),
        turnOrder = turnOrder.toList(),
    )
