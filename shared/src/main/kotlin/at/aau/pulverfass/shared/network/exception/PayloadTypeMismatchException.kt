package at.aau.pulverfass.shared.network.exception

import at.aau.pulverfass.shared.network.message.MessageType

/**
 * Wird geworfen, wenn Header-Typ und Payload-Typ nicht zusammenpassen.
 *
 * @param expectedType Typ aus dem Header
 * @param actualType Typ, der sich aus der Payload-Klasse ergibt
 */
class PayloadTypeMismatchException(
    expectedType: MessageType,
    actualType: MessageType,
) : NetworkException("Header type $expectedType does not match payload type $actualType")
