package at.aau.pulverfass.shared.network.codec

import at.aau.pulverfass.shared.network.exception.NetworkException

/**
 * Interne Basisklasse für Fehler beim binären Transport-Framing eines Pakets.
 *
 * Diese Fehler entstehen unterhalb der Nachrichten- und Payload-Ebene beim
 * Validieren und Zerlegen des Wire-Formats.
 */
internal sealed class PacketCodecException(
    message: String,
) : NetworkException(message)

/**
 * Wird geworfen, wenn ein Paket mit leerem Header erzeugt oder transportiert
 * werden soll.
 */
internal class EmptyHeaderException :
    IllegalArgumentException("Header must not be empty.")

/**
 * Wird geworfen, wenn Paketdaten nicht einmal die Mindestlänge des Wire-Formats besitzen.
 */
internal class PacketTooShortException :
    PacketCodecException("Packet too short to contain required data.")

/**
 * Wird geworfen, wenn die angegebene Header-Länge kein gültiger Wert ist.
 *
 * @param length ungültige Header-Länge
 */
internal class InvalidHeaderLengthException(
    length: Int,
) : PacketCodecException("Invalid header length: $length")

/**
 * Wird geworfen, wenn deklarierte Strukturinformationen nicht zu den tatsächlich
 * vorhandenen Bytes passen.
 *
 * @param message technische Fehlerbeschreibung
 */
internal class CorruptPacketException(
    message: String,
) : PacketCodecException(message)
