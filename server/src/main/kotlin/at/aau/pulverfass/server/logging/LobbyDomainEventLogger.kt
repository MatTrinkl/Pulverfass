package at.aau.pulverfass.server.logging

import at.aau.pulverfass.server.lobby.runtime.LobbyRuntimeHooks
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.AttackResolvedEvent
import at.aau.pulverfass.shared.lobby.event.CardSetTradedInEvent
import at.aau.pulverfass.shared.lobby.event.FortifyMoveAppliedEvent
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsChangedEvent
import at.aau.pulverfass.shared.lobby.event.PendingReinforcementsSetEvent
import at.aau.pulverfass.shared.lobby.event.PlayerCardsRemovedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerEliminatedEvent
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.event.PlayerKicked
import at.aau.pulverfass.shared.lobby.event.PlayerLeft
import at.aau.pulverfass.shared.lobby.event.StartPlayerConfigured
import at.aau.pulverfass.shared.lobby.event.TerritoryOwnerChangedEvent
import at.aau.pulverfass.shared.lobby.event.TurnEnded
import at.aau.pulverfass.shared.lobby.event.TurnStateUpdatedEvent
import at.aau.pulverfass.shared.lobby.state.GameState
import java.util.UUID

/**
 * Fachlicher Event-Logger für Lobby-Events rund um die Reducer-Anwendung.
 *
 * Die Klasse hängt über [LobbyRuntimeHooks] an der Runtime und hält den Reducer
 * frei von Logging-Seiteneffekten. Geloggt werden Eingang, erfolgreiche
 * Anwendung und Ablehnung eines Events.
 */
object LobbyDomainEventLogger {
    private val logger = ServerLoggers.domainEvent("LobbyDomainEventLogger")

    /**
     * Markiert den Beginn einer Server-Session im kumulativen Event-Log.
     */
    fun logServerSessionStarted(sessionId: UUID = UUID.randomUUID()) {
        logger.info(
            "session-start | id={}",
            sessionId,
        )
    }

    /**
     * Erstellt Runtime-Hooks, die Lobby-Events in die fachliche Log-Spur schreiben.
     */
    fun hooks(): LobbyRuntimeHooks =
        LobbyRuntimeHooks(
            onEventEnqueued = ::logEventReceived,
            onEventAccepted = ::logEventApplied,
            onEventRejected = ::logEventRejected,
        )

    /**
     * Loggt den Eingang eines Events vor der Verarbeitung durch die Runtime.
     */
    internal fun logEventReceived(
        lobbyCode: LobbyCode,
        event: LobbyEvent,
        context: EventContext?,
    ) {
        logger.info(
            "received | lobby={} player={} event={}",
            lobbyCode.value,
            playerIdValue(event, context),
            eventType(event),
        )
    }

    private fun logEventApplied(
        lobbyCode: LobbyCode,
        event: LobbyEvent,
        beforeState: GameState,
        afterState: GameState,
    ) {
        logger.info(
            "applied  | lobby={} player={} event={} version={}->{}",
            lobbyCode.value,
            playerIdValue(event, context = null),
            eventType(event),
            beforeState.stateVersion,
            afterState.stateVersion,
        )
    }

    private fun logEventRejected(
        lobbyCode: LobbyCode,
        event: LobbyEvent,
        context: EventContext?,
        beforeState: GameState?,
        cause: Throwable,
    ) {
        logger.warn(
            "rejected | lobby={} player={} event={} version={}->{} reason={}",
            lobbyCode.value,
            playerIdValue(event, context),
            eventType(event),
            beforeState?.stateVersion,
            beforeState?.stateVersion,
            rejectReason(cause),
        )
    }

    private fun eventType(event: LobbyEvent): String =
        event::class.simpleName ?: event::class.java.name

    private fun rejectReason(cause: Throwable): String =
        cause.message?.takeIf(String::isNotBlank) ?: cause::class.java.simpleName

    private fun playerIdValue(
        event: LobbyEvent,
        context: EventContext?,
    ): Long? = (context?.playerId ?: playerIdFromEvent(event))?.value

    private fun playerIdFromEvent(event: LobbyEvent): PlayerId? =
        when (event) {
            is AttackResolvedEvent -> event.attackerPlayerId
            is CardSetTradedInEvent -> event.playerId
            is FortifyMoveAppliedEvent -> event.playerId
            is InvalidActionDetected -> event.playerId
            is PendingReinforcementsChangedEvent -> event.playerId
            is PendingReinforcementsSetEvent -> event.playerId
            is PlayerCardsRemovedEvent -> event.playerId
            is PlayerEliminatedEvent -> event.playerId
            is PlayerJoined -> event.playerId
            is PlayerKicked -> event.requesterPlayerId
            is PlayerLeft -> event.playerId
            is StartPlayerConfigured -> event.requesterPlayerId
            is TerritoryOwnerChangedEvent -> event.ownerId
            is TurnEnded -> event.playerId
            is TurnStateUpdatedEvent -> event.activePlayerId
            else -> null
        }
}
