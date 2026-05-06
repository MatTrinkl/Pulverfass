package at.aau.pulverfass.server.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Server-side logger categories.
 */
object ServerLoggerNames {
    const val TECHNICAL = "at.aau.pulverfass.server.technical"
    const val DOMAIN_EVENT = "at.aau.pulverfass.server.event"
}

object ServerLoggers {
    fun technical(component: String): Logger =
        LoggerFactory.getLogger("${ServerLoggerNames.TECHNICAL}.$component")

    fun domainEvent(component: String): Logger =
        LoggerFactory.getLogger("${ServerLoggerNames.DOMAIN_EVENT}.$component")
}
