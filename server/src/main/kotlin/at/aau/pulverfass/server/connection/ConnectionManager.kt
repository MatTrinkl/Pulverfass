package at.aau.pulverfass.server.connection

import at.aau.pulverfass.server.ids.ConnectionNotFoundException
import at.aau.pulverfass.server.ids.DuplicateConnectionIdException
import at.aau.pulverfass.shared.ids.ConnectionId
import java.util.concurrent.ConcurrentHashMap

/**
 * Zentrale Registry aller aktiven technischen Verbindungen einer Serverinstanz.
 *
 * Der Manager kapselt Registrierung, Deregistrierung, Lookup und die technischen
 * Versand-Grundoperationen `send`, `sendMany` und `broadcast`.
 */
class ConnectionManager {
    private val connections = ConcurrentHashMap<ConnectionId, Connection>()

    /**
     * Registriert eine aktive Verbindung.
     *
     * @throws DuplicateConnectionIdException wenn die [ConnectionId] bereits belegt ist
     */
    fun register(connection: Connection) {
        if (connections.putIfAbsent(connection.connectionId, connection) != null) {
            throw DuplicateConnectionIdException(connection.connectionId)
        }
    }

    /**
     * Liefert eine Verbindung per [ConnectionId] oder `null`.
     */
    fun get(connectionId: ConnectionId): Connection? = connections[connectionId]

    /**
     * Liefert eine Verbindung per [ConnectionId] oder wirft bei unbekannter ID.
     */
    fun require(connectionId: ConnectionId): Connection =
        connections[connectionId] ?: throw ConnectionNotFoundException(connectionId)

    /**
     * Entfernt eine Verbindung aus der Registry und liefert sie optional zurück.
     */
    fun remove(connectionId: ConnectionId): Connection? = connections.remove(connectionId)

    /**
     * Prüft, ob eine aktive Verbindung mit dieser ID registriert ist.
     */
    fun contains(connectionId: ConnectionId): Boolean = connections.containsKey(connectionId)

    /**
     * Liefert alle aktuell registrierten Verbindungs-IDs als Snapshot.
     */
    fun allConnectionIds(): Set<ConnectionId> = connections.keys.toSet()

    /**
     * Liefert alle aktuell registrierten Verbindungen als Snapshot.
     */
    fun all(): List<Connection> = connections.values.toList()

    /**
     * Sendet rohe Bytes an genau eine bekannte Verbindung.
     */
    suspend fun send(
        connectionId: ConnectionId,
        bytes: ByteArray,
    ) {
        require(connectionId).send(bytes)
    }

    /**
     * Schließt eine bekannte Verbindung serverseitig.
     */
    suspend fun close(
        connectionId: ConnectionId,
        reason: String?,
    ) {
        remove(connectionId)?.close(reason) ?: throw ConnectionNotFoundException(connectionId)
    }

    /**
     * Sendet rohe Bytes an mehrere bekannte Verbindungen.
     *
     * Doppelte IDs werden innerhalb eines Aufrufs nur einmal bedient.
     */
    suspend fun sendMany(
        connectionIds: Iterable<ConnectionId>,
        bytes: ByteArray,
    ) {
        connectionIds.toSet().forEach { connectionId ->
            send(connectionId, bytes)
        }
    }

    /**
     * Sendet rohe Bytes an alle aktuell aktiven Verbindungen.
     */
    suspend fun broadcast(bytes: ByteArray) {
        sendMany(allConnectionIds(), bytes)
    }

    /**
     * Leert die Registry. Vor allem für Tests gedacht.
     */
    fun clear() {
        connections.clear()
    }
}
