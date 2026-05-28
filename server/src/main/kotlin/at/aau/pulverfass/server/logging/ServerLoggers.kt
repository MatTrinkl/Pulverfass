package at.aau.pulverfass.server.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Zentrale Logger-Namen für die getrennten serverseitigen Log-Spuren.
 *
 * Die Namen werden in der Logback-Konfiguration separat geroutet, damit
 * technische Diagnose und fachliche Domain-Events nicht in dieselben Dateien
 * geschrieben werden.
 */
object ServerLoggerNames {
    const val TECHNICAL = "at.aau.pulverfass.server.technical"
    const val DOMAIN_EVENT = "at.aau.pulverfass.server.event"
}

/**
 * Factory für serverseitige SLF4J-Logger mit stabilen Kategorien.
 *
 * Aufrufer geben nur ihren Komponentenamen an. Die technische oder fachliche
 * Root-Kategorie wird hier zentral ergänzt.
 */
object ServerLoggers {
    /**
     * Erstellt einen Logger für technische Diagnosemeldungen einer Serverkomponente.
     */
    fun technical(component: String): Logger =
        LoggerFactory.getLogger("${ServerLoggerNames.TECHNICAL}.$component")

    /**
     * Erstellt einen Logger für fachliche Lobby- und Game-Domain-Events.
     */
    fun domainEvent(component: String): Logger =
        LoggerFactory.getLogger("${ServerLoggerNames.DOMAIN_EVENT}.$component")
}
