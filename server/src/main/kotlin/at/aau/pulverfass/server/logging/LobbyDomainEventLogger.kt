package at.aau.pulverfass.server.logging

import at.aau.pulverfass.server.lobby.runtime.LobbyRuntimeHooks
import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.InvalidActionDetected
import at.aau.pulverfass.shared.lobby.event.LobbyEvent
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
 * Server-side domain event logging around lobby event application.
 */
object LobbyDomainEventLogger {
    private val logger = ServerLoggers.domainEvent("LobbyDomainEventLogger")

    fun logServerSessionStarted(sessionId: UUID = UUID.randomUUID()) {
        logger.info(
            "session-start | id={}",
            sessionId,
        )
    }

    fun hooks(): LobbyRuntimeHooks =
        LobbyRuntimeHooks(
            onEventEnqueued = ::logEventReceived,
            onEventAccepted = ::logEventApplied,
            onEventRejected = ::logEventRejected,
        )

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
            is InvalidActionDetected -> event.playerId
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
