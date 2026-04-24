package at.aau.pulverfass.shared.lobby.state

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.lobby.command.DefaultMapCommandRuleService
import at.aau.pulverfass.shared.lobby.command.MapCommand
import at.aau.pulverfass.shared.lobby.command.MapCommandRuleService
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
 * den [LobbyEventReducer] oder die vorgeschaltete Command-Regelschicht.
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

    /**
     * Validiert einen Map-Command gegen den aktuellen State und wendet die
     * resultierenden Events atomar an.
     */
    fun apply(
        command: MapCommand,
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
    private val mapCommandRuleService: MapCommandRuleService = DefaultMapCommandRuleService(),
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

    override fun apply(
        command: MapCommand,
        context: EventContext?,
    ): GameState {
        lock.writeLock().lock()

        return try {
            val resultingEvents = mapCommandRuleService.createEvents(state, command)
            val updated =
                resultingEvents.fold(state) { currentState, event ->
                    reducer.apply(currentState, event, context)
                }
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
        playerDisplayNames = playerDisplayNames.toMap(),
        turnOrder = turnOrder.toList(),
        territoryStates = territoryStates.toMap(),
    )
