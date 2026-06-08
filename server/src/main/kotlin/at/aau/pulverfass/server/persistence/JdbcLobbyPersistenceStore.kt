package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.shared.ids.LobbyCode
import kotlinx.serialization.json.JsonElement
import org.postgresql.util.PGobject
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

/**
 * Aufbewahrungsregeln für event-sourced Lobby-Daten in PostgreSQL.
 *
 * @property eventRoundsToKeep Anzahl der letzten Runden, deren Einzelereignisse aufbewahrt werden
 * @property snapshotsToKeep Anzahl der neuesten Snapshots pro Lobby
 */
data class LobbyPersistenceRetentionPolicy(
    val eventRoundsToKeep: Int = 2,
    val snapshotsToKeep: Int = 4,
) {
    init {
        require(eventRoundsToKeep >= 1) {
            "LobbyPersistenceRetentionPolicy.eventRoundsToKeep muss mindestens 1 sein."
        }
        require(snapshotsToKeep >= 1) {
            "LobbyPersistenceRetentionPolicy.snapshotsToKeep muss mindestens 1 sein."
        }
    }

    /**
     * Berechnet die kleinste Runde, deren Events noch erhalten bleiben sollen.
     *
     * @param currentRound aktuell persistierte Runde
     * @return untere inklusive Rundengrenze für Event-Aufbewahrung
     * @throws IllegalArgumentException wenn [currentRound] negativ ist
     */
    fun minimumRoundToKeep(currentRound: Int): Int {
        require(currentRound >= 0) {
            "LobbyPersistenceRetentionPolicy erwartet currentRound >= 0, war aber $currentRound."
        }
        return (currentRound - (eventRoundsToKeep - 1)).coerceAtLeast(0)
    }
}

/**
 * Gelesener Datensatz aus `lobby_events`.
 *
 * @property id technische Datenbank-ID
 * @property lobbyCode referenzierte Lobby
 * @property stateVersion autoritative State-Version nach Anwendung des Events
 * @property turnCount Rundenzähler zum Zeitpunkt der Persistierung
 * @property eventType stabiler Persistenztyp des Domain-Events
 * @property eventJson serialisierte Event-Nutzlast
 * @property createdAt Datenbankzeitpunkt der Speicherung
 */
data class PersistedLobbyEventRecord(
    val id: Long,
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val turnCount: Int,
    val eventType: String,
    val eventJson: String,
    val createdAt: Instant,
)

/**
 * Gelesener Datensatz aus `lobby_snapshots`.
 *
 * @property id technische Datenbank-ID
 * @property lobbyCode referenzierte Lobby
 * @property stateVersion Zustandsversion des Snapshots
 * @property turnCount Rundenzähler des Snapshots
 * @property snapshotJson serialisierte Snapshot-Nutzlast
 * @property createdAt Datenbankzeitpunkt der Speicherung
 */
data class PersistedLobbySnapshotRecord(
    val id: Long,
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val turnCount: Int,
    val snapshotJson: String,
    val createdAt: Instant,
)

/**
 * Minimale Leseschnittstelle für Lobby-Recovery.
 *
 * Das Interface trennt den Start-Replay von der konkreten JDBC-Implementierung. Dadurch kann der
 * Recovery-Pfad gezielt gegen beschädigte Persistenzdaten getestet werden, ohne eine echte
 * Datenbankverbindung aufzubauen.
 */
interface LobbyPersistenceReader {
    /**
     * Lädt den neuesten Snapshot einer Lobby.
     *
     * @param lobbyCode wiederherzustellende Lobby
     * @return letzter Snapshot oder `null`, wenn nur Events vorhanden sind
     */
    fun loadLatestSnapshot(lobbyCode: LobbyCode): PersistedLobbySnapshotRecord?

    /**
     * Lädt alle Events nach einer bereits bekannten State-Version.
     *
     * @param lobbyCode wiederherzustellende Lobby
     * @param stateVersionExclusive letzte bereits angewandte State-Version
     * @return nachfolgende Events in Replay-Reihenfolge
     */
    fun loadEventsAfter(
        lobbyCode: LobbyCode,
        stateVersionExclusive: Long,
    ): List<PersistedLobbyEventRecord>

    /**
     * Ermittelt alle Lobbies mit gespeicherten Events oder Snapshots.
     */
    fun findLobbyCodesWithPersistedState(): Set<LobbyCode>
}

/**
 * JDBC-basierter Event- und Snapshot-Store für Lobby-Recovery.
 *
 * Der Store persistiert Domain-Events und periodische Vollsnapshots getrennt, damit beim
 * Server-Neustart nur der jüngste Snapshot plus nachfolgende Events geladen werden müssen.
 *
 * @param dataSource Datenquelle für PostgreSQL-Zugriffe
 * @param retentionPolicy Regeln zur Bereinigung alter Persistenzdaten
 */
class JdbcLobbyPersistenceStore(
    private val dataSource: DataSource,
    private val retentionPolicy: LobbyPersistenceRetentionPolicy =
        LobbyPersistenceRetentionPolicy(),
) : LobbyPersistenceReader {
    /**
     * Hängt ein einzelnes Domain-Event an den Persistenzstrom einer Lobby an.
     *
     * Nach erfolgreicher Speicherung werden veraltete Event-Runden derselben Lobby entsprechend
     * [retentionPolicy] bereinigt.
     *
     * @throws IllegalArgumentException bei negativen Versions- oder Rundenzählern sowie leerem
     * `eventType`
     */
    fun appendEvent(
        lobbyCode: LobbyCode,
        stateVersion: Long,
        turnCount: Int,
        eventType: String,
        eventJson: JsonElement,
    ) {
        require(stateVersion >= 0) {
            "JdbcLobbyPersistenceStore.appendEvent erwartet stateVersion >= 0."
        }
        require(turnCount >= 0) {
            "JdbcLobbyPersistenceStore.appendEvent erwartet turnCount >= 0."
        }
        require(eventType.isNotBlank()) {
            "JdbcLobbyPersistenceStore.appendEvent erwartet einen nicht-leeren eventType."
        }

        dataSource.connection.use { connection ->
            connection.inTransaction {
                connection.prepareStatement(
                    """
                    INSERT INTO lobby_events (
                        lobby_code,
                        state_version,
                        turn_count,
                        event_type,
                        event_json
                    ) VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, lobbyCode.value)
                    statement.setLong(2, stateVersion)
                    statement.setInt(3, turnCount)
                    statement.setString(4, eventType)
                    statement.setObject(5, eventJson.toJsonb())
                    statement.executeUpdate()
                }

                cleanupEventRounds(connection, lobbyCode, currentRound = turnCount)
            }
        }
    }

    /**
     * Persistiert einen vollständigen Snapshot des aktuellen Lobby-Zustands.
     *
     * Nach erfolgreicher Speicherung werden ältere Snapshots derselben Lobby auf die in
     * [retentionPolicy] konfigurierte Anzahl reduziert.
     *
     * @throws IllegalArgumentException bei negativen Versions- oder Rundenzählern
     */
    fun appendSnapshot(
        lobbyCode: LobbyCode,
        stateVersion: Long,
        turnCount: Int,
        snapshotJson: JsonElement,
    ) {
        require(stateVersion >= 0) {
            "JdbcLobbyPersistenceStore.appendSnapshot erwartet stateVersion >= 0."
        }
        require(turnCount >= 0) {
            "JdbcLobbyPersistenceStore.appendSnapshot erwartet turnCount >= 0."
        }

        dataSource.connection.use { connection ->
            connection.inTransaction {
                connection.prepareStatement(
                    """
                    INSERT INTO lobby_snapshots (
                        lobby_code,
                        state_version,
                        turn_count,
                        snapshot_json
                    ) VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, lobbyCode.value)
                    statement.setLong(2, stateVersion)
                    statement.setInt(3, turnCount)
                    statement.setObject(4, snapshotJson.toJsonb())
                    statement.executeUpdate()
                }

                cleanupSnapshots(connection, lobbyCode)
            }
        }
    }

    /**
     * Führt die Event-Bereinigung für genau eine Lobby explizit aus.
     *
     * @return Anzahl gelöschter Event-Zeilen
     */
    fun cleanupEventsForLobby(
        lobbyCode: LobbyCode,
        currentRound: Int,
    ): Int =
        dataSource.connection.use { connection ->
            connection.inTransaction {
                cleanupEventRounds(connection, lobbyCode, currentRound)
            }
        }

    /**
     * Führt die Snapshot-Bereinigung für genau eine Lobby explizit aus.
     *
     * @return Anzahl gelöschter Snapshot-Zeilen
     */
    fun cleanupSnapshotsForLobby(lobbyCode: LobbyCode): Int =
        dataSource.connection.use { connection ->
            connection.inTransaction {
                cleanupSnapshots(connection, lobbyCode)
            }
        }

    /**
     * Listet alle persistierten Events einer Lobby in deterministischer Reihenfolge.
     */
    fun listEvents(lobbyCode: LobbyCode): List<PersistedLobbyEventRecord> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, lobby_code, state_version, turn_count, event_type, event_json, created_at
                FROM lobby_events
                WHERE lobby_code = ?
                ORDER BY state_version ASC, id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, lobbyCode.value)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                PersistedLobbyEventRecord(
                                    id = resultSet.getLong("id"),
                                    lobbyCode = LobbyCode(resultSet.getString("lobby_code")),
                                    stateVersion = resultSet.getLong("state_version"),
                                    turnCount = resultSet.getInt("turn_count"),
                                    eventType = resultSet.getString("event_type"),
                                    eventJson = resultSet.getString("event_json"),
                                    createdAt = resultSet.getTimestamp("created_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }

    /**
     * Listet alle persistierten Snapshots einer Lobby in aufsteigender Versionsreihenfolge.
     */
    fun listSnapshots(lobbyCode: LobbyCode): List<PersistedLobbySnapshotRecord> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, lobby_code, state_version, turn_count, snapshot_json, created_at
                FROM lobby_snapshots
                WHERE lobby_code = ?
                ORDER BY state_version ASC, id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, lobbyCode.value)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                PersistedLobbySnapshotRecord(
                                    id = resultSet.getLong("id"),
                                    lobbyCode = LobbyCode(resultSet.getString("lobby_code")),
                                    stateVersion = resultSet.getLong("state_version"),
                                    turnCount = resultSet.getInt("turn_count"),
                                    snapshotJson = resultSet.getString("snapshot_json"),
                                    createdAt = resultSet.getTimestamp("created_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }

    /**
     * Lädt den neuesten verfügbaren Snapshot einer Lobby.
     *
     * @return aktuellster Snapshot oder `null`, wenn noch keiner persistiert wurde
     */
    override fun loadLatestSnapshot(lobbyCode: LobbyCode): PersistedLobbySnapshotRecord? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, lobby_code, state_version, turn_count, snapshot_json, created_at
                FROM lobby_snapshots
                WHERE lobby_code = ?
                ORDER BY state_version DESC, id DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, lobbyCode.value)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return@use null
                    }
                    PersistedLobbySnapshotRecord(
                        id = resultSet.getLong("id"),
                        lobbyCode = LobbyCode(resultSet.getString("lobby_code")),
                        stateVersion = resultSet.getLong("state_version"),
                        turnCount = resultSet.getInt("turn_count"),
                        snapshotJson = resultSet.getString("snapshot_json"),
                        createdAt = resultSet.getTimestamp("created_at").toInstant(),
                    )
                }
            }
        }

    /**
     * Lädt alle Events strikt nach einer bekannten State-Version.
     *
     * @param stateVersionExclusive letzte bereits angewandte State-Version
     * @return nachfolgende Events in Anwendungsreihenfolge
     */
    override fun loadEventsAfter(
        lobbyCode: LobbyCode,
        stateVersionExclusive: Long,
    ): List<PersistedLobbyEventRecord> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, lobby_code, state_version, turn_count, event_type, event_json, created_at
                FROM lobby_events
                WHERE lobby_code = ?
                  AND state_version > ?
                ORDER BY state_version ASC, id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, lobbyCode.value)
                statement.setLong(2, stateVersionExclusive)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                PersistedLobbyEventRecord(
                                    id = resultSet.getLong("id"),
                                    lobbyCode = LobbyCode(resultSet.getString("lobby_code")),
                                    stateVersion = resultSet.getLong("state_version"),
                                    turnCount = resultSet.getInt("turn_count"),
                                    eventType = resultSet.getString("event_type"),
                                    eventJson = resultSet.getString("event_json"),
                                    createdAt = resultSet.getTimestamp("created_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }

    /**
     * Ermittelt alle Lobbies, für die noch Events oder Snapshots vorhanden sind.
     */
    override fun findLobbyCodesWithPersistedState(): Set<LobbyCode> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT lobby_code FROM lobby_events
                UNION
                SELECT lobby_code FROM lobby_snapshots
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildSet {
                        while (resultSet.next()) {
                            add(LobbyCode(resultSet.getString("lobby_code")))
                        }
                    }
                }
            }
        }

    /**
     * Entfernt alle persistierten Events und Snapshots einer Lobby.
     *
     * Die beiden Tabellen werden in einer gemeinsamen Transaktion gelöscht, damit kein halb
     * entfernter Recovery-Zustand zurückbleibt.
     */
    fun deleteLobbyState(lobbyCode: LobbyCode) {
        dataSource.connection.use { connection ->
            connection.inTransaction {
                connection.prepareStatement(
                    """
                    DELETE FROM lobby_events
                    WHERE lobby_code = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, lobbyCode.value)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    DELETE FROM lobby_snapshots
                    WHERE lobby_code = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, lobbyCode.value)
                    statement.executeUpdate()
                }
                1
            }
        }
    }

    private fun cleanupEventRounds(
        connection: Connection,
        lobbyCode: LobbyCode,
        currentRound: Int,
    ): Int {
        val minimumRoundToKeep = retentionPolicy.minimumRoundToKeep(currentRound)
        return connection.prepareStatement(
            """
            DELETE FROM lobby_events
            WHERE lobby_code = ?
              AND turn_count < ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, lobbyCode.value)
            statement.setInt(2, minimumRoundToKeep)
            statement.executeUpdate()
        }
    }

    private fun cleanupSnapshots(
        connection: Connection,
        lobbyCode: LobbyCode,
    ): Int =
        connection.prepareStatement(
            """
            DELETE FROM lobby_snapshots
            WHERE lobby_code = ?
              AND id IN (
                  SELECT id
                  FROM lobby_snapshots
                  WHERE lobby_code = ?
                  ORDER BY state_version DESC, id DESC
                  OFFSET ?
              )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, lobbyCode.value)
            statement.setString(2, lobbyCode.value)
            statement.setInt(3, retentionPolicy.snapshotsToKeep)
            statement.executeUpdate()
        }
}

/**
 * Führt mehrere JDBC-Schreibschritte als gemeinsame Transaktion aus.
 */
private fun Connection.inTransaction(block: () -> Int): Int {
    val previousAutoCommit = autoCommit
    autoCommit = false
    return try {
        val updatedRows = block()
        commit()
        updatedRows
    } catch (exception: Exception) {
        rollback()
        throw exception
    } finally {
        autoCommit = previousAutoCommit
    }
}

/**
 * Wandelt ein JSON-Element in ein PostgreSQL-`jsonb`-Objekt um.
 */
private fun JsonElement.toJsonb(): PGobject =
    PGobject().apply {
        type = "jsonb"
        value = this@toJsonb.toString()
    }
