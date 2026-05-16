package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.shared.ids.LobbyCode
import kotlinx.serialization.json.JsonElement
import org.postgresql.util.PGobject
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource

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

    fun minimumRoundToKeep(currentRound: Int): Int {
        require(currentRound >= 0) {
            "LobbyPersistenceRetentionPolicy erwartet currentRound >= 0, war aber $currentRound."
        }
        return (currentRound - (eventRoundsToKeep - 1)).coerceAtLeast(0)
    }
}

data class PersistedLobbyEventRecord(
    val id: Long,
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val turnCount: Int,
    val eventType: String,
    val eventJson: String,
    val createdAt: Instant,
)

data class PersistedLobbySnapshotRecord(
    val id: Long,
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val turnCount: Int,
    val snapshotJson: String,
    val createdAt: Instant,
)

class JdbcLobbyPersistenceStore(
    private val dataSource: DataSource,
    private val retentionPolicy: LobbyPersistenceRetentionPolicy =
        LobbyPersistenceRetentionPolicy(),
) {
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

    fun cleanupEventsForLobby(
        lobbyCode: LobbyCode,
        currentRound: Int,
    ): Int =
        dataSource.connection.use { connection ->
            connection.inTransaction {
                cleanupEventRounds(connection, lobbyCode, currentRound)
            }
        }

    fun cleanupSnapshotsForLobby(lobbyCode: LobbyCode): Int =
        dataSource.connection.use { connection ->
            connection.inTransaction {
                cleanupSnapshots(connection, lobbyCode)
            }
        }

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

    fun loadLatestSnapshot(lobbyCode: LobbyCode): PersistedLobbySnapshotRecord? =
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

    fun loadEventsAfter(
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

    fun findLobbyCodesWithPersistedState(): Set<LobbyCode> =
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

private fun JsonElement.toJsonb(): PGobject =
    PGobject().apply {
        type = "jsonb"
        value = this@toJsonb.toString()
    }
