package at.aau.pulverfass.server.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.FileAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Paths

class LoggingConfigurationTest {
    @Test
    fun `logback routes technical and domain event logs to separate appenders`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME)
        val technicalLogger = context.getLogger(ServerLoggerNames.TECHNICAL)
        val eventLogger = context.getLogger(ServerLoggerNames.DOMAIN_EVENT)

        val technicalFileAppender =
            rootLogger.getAppender("TECHNICAL_FILE") as FileAppender<*>
        val eventFileAppender =
            eventLogger.getAppender("EVENT_FILE") as FileAppender<*>

        assertNotNull(technicalFileAppender)
        assertNotNull(eventFileAppender)
        assertFalse(technicalFileAppender.isAppend)
        assertTrue(eventFileAppender.isAppend)
        assertEquals(
            RepositoryLogDirectory.resolve().resolve("technical-log.log"),
            Paths.get(technicalFileAppender.file).toAbsolutePath().normalize(),
        )
        assertEquals(
            RepositoryLogDirectory.resolve().resolve("event-log.log"),
            Paths.get(eventFileAppender.file).toAbsolutePath().normalize(),
        )
        assertFalse(technicalLogger.isAdditive)
        assertFalse(eventLogger.isAdditive)
        assertNull(technicalLogger.getAppender("EVENT_FILE"))
        assertNull(eventLogger.getAppender("TECHNICAL_FILE"))
    }
}
