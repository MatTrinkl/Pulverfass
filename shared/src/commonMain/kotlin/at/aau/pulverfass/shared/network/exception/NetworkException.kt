package at.aau.pulverfass.shared.network.exception

/**
 * Basisklasse für Fehler im Netzwerkprotokoll und dessen technischer Verarbeitung.
 *
 * Fachlogische Fehler gehören nicht in diese Hierarchie.
 */
open class NetworkException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
