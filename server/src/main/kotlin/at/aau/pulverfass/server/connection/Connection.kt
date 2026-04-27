package at.aau.pulverfass.server.connection

import at.aau.pulverfass.shared.ids.ConnectionId

/**
 * Transportneutrale Repräsentation einer aktiven Serververbindung.
 *
 * Die Verbindung kapselt nur die technische Zieladresse und den Rohbyte-Versand.
 * Fachliche Zuordnungen wie Player oder Lobby werden bewusst außerhalb gehalten.
 */
interface Connection {
    /**
     * Eindeutige Kennung dieser aktiven Verbindung.
     */
    val connectionId: ConnectionId

    /**
     * Sendet rohe Bytes an diese Verbindung.
     */
    suspend fun send(bytes: ByteArray)
}
