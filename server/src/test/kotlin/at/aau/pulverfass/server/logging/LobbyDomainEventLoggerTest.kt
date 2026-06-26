package at.aau.pulverfass.server.logging

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.event.PlayerJoined
import at.aau.pulverfass.shared.lobby.state.GameState
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

class LobbyDomainEventLoggerTest {
    @Test
    fun `domain event hooks log received applied and rejected events`() =
        runBlocking {
            val context = LoggerFactory.getILoggerFactory() as LoggerContext
            val logger =
                context.getLogger("${ServerLoggerNames.DOMAIN_EVENT}.LobbyDomainEventLogger")
            val appender = ListAppender<ILoggingEvent>()
            appender.context = context
            appender.start()
            logger.addAppender(appender)

            try {
                val lobbyCode = LobbyCode("1003")
                val event = PlayerJoined(lobbyCode, PlayerId(7), "Alice")
                val beforeState = GameState.initial(lobbyCode)
                val afterState = beforeState.copy(stateVersion = 1, processedEventCount = 1)
                val hooks = LobbyDomainEventLogger.hooks()
                val sessionId = UUID.fromString("00000000-0000-0000-0000-000000000001")

                LobbyDomainEventLogger.logServerSessionStarted(sessionId)
                hooks.onEventEnqueued(
                    lobbyCode,
                    event,
                    EventContext(
                        connectionId = ConnectionId(3),
                        playerId = PlayerId(7),
                        occurredAtEpochMillis = 1,
                    ),
                )
                hooks.onEventAccepted(lobbyCode, event, beforeState, afterState)
                hooks.onEventRejected(
                    lobbyCode,
                    event,
                    null,
                    beforeState,
                    IllegalArgumentException("duplicate player"),
                )

                val messages = appender.list.map { it.formattedMessage }
                assertTrue(
                    messages.any {
                        it.contains("session-start") &&
                            it.contains("id=$sessionId")
                    },
                )
                assertTrue(messages.any { it.contains("received | lobby=1003") })
                assertTrue(messages.any { it.contains("applied  | lobby=1003") })
                assertTrue(messages.any { it.contains("version=0->1") })
                assertTrue(messages.any { it.contains("rejected | lobby=1003") })
                assertTrue(messages.any { it.contains("reason=duplicate player") })
            } finally {
                logger.detachAppender(appender)
            }
        }
}
