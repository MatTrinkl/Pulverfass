package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.NetworkException

/**
 * Basisklasse für Fehler beim binären Transport-Framing eines Pakets.
 *
 * Diese Exceptions entstehen unterhalb der Nachrichten- und Deserialisierungsebene,
 * also beim Validieren und Zerlegen des Wire-Formats empfangener Daten.
 */
sealed class PacketCodecException(
    message: String,
) : NetworkException(message)

/**
 * Wird geworfen, wenn ein Aufrufer ein Paket mit leerem Header erzeugen oder
 * transportieren möchte, obwohl das Wire-Format einen nicht-leeren Header verlangt.
 *
 * Das ist eine Vertragsverletzung des Aufrufers und kein Transportfehler.
 */
class EmptyHeaderException :
    IllegalArgumentException("Header must not be empty.")

/**
 * Wird geworfen, wenn die übergebenen Paketdaten nicht einmal die Mindestlänge
 * für die benötigten Felder des Wire-Formats besitzen.
 */
class PacketTooShortException :
    PacketCodecException("Packet too short to contain required data.")

/**
 * Wird geworfen, wenn die im Paket angegebene Header-Länge nicht als gültiger
 * Wert für das definierte Wire-Format interpretiert werden kann.
 */
class InvalidHeaderLengthException(
    length: Int,
) : PacketCodecException("Invalid header length: $length")

/**
 * Wird geworfen, wenn ein Paket zwar formal lesbar beginnt, die deklarierten
 * Strukturinformationen aber nicht zu den tatsächlich vorhandenen Daten passen.
 */
class CorruptPacketException(
    message: String,
) : PacketCodecException(message)
