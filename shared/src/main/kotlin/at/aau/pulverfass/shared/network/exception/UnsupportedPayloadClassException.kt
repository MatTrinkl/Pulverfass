package at.aau.pulverfass.shared.network.exception

/**
 * Wird geworfen, wenn für eine Payload-Klasse keine Protokollzuordnung
 * registriert ist.
 *
 * @param payloadClassName vollqualifizierter Name der unbekannten Payload-Klasse
 */
class UnsupportedPayloadClassException(
    payloadClassName: String,
) : NetworkException("Unsupported payload class: $payloadClassName")
