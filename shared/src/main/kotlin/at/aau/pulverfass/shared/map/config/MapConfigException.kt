package at.aau.pulverfass.shared.map.config

/**
 * Basistyp für Fehler beim Laden oder Validieren einer Map-Konfiguration.
 */
open class MapConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * Fehler beim Parsen oder Auflösen der Konfigurationsquelle.
 */
class MapConfigLoadException(
    message: String,
    cause: Throwable? = null,
) : MapConfigException(message, cause)

/**
 * Fehler bei der fachlichen Validierung der Map-Konfiguration.
 */
class MapConfigValidationException(
    message: String,
) : MapConfigException(message)
