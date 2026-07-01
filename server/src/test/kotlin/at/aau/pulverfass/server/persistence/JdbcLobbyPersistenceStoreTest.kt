package at.aau.pulverfass.server.persistence

import at.aau.pulverfass.shared.ids.LobbyCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.sql.Timestamp
import java.time.Instant

class JdbcLobbyPersistenceStoreTest {
    @Test
    fun `retention policy validates bounds and clamps minimum round`() {
        assertThrows(IllegalArgumentException::class.java) {
            LobbyPersistenceRetentionPolicy(eventRoundsToKeep = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LobbyPersistenceRetentionPolicy(snapshotsToKeep = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LobbyPersistenceRetentionPolicy().minimumRoundToKeep(-1)
        }

        val policy = LobbyPersistenceRetentionPolicy(eventRoundsToKeep = 3)

        assertEquals(0, policy.minimumRoundToKeep(1))
        assertEquals(3, policy.minimumRoundToKeep(5))
    }

    @Test
    fun `appendEvent validates arguments before opening a connection`() {
        val dataSource = FakeJdbcDataSource(emptyList())
        val store = JdbcLobbyPersistenceStore(dataSource)

        assertThrows(IllegalArgumentException::class.java) {
            store.appendEvent(LobbyCode("1003"), -1, 0, "event", jsonPayload("x"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.appendEvent(LobbyCode("1003"), 1, -1, "event", jsonPayload("x"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.appendEvent(LobbyCode("1003"), 1, 0, " ", jsonPayload("x"))
        }

        assertEquals(0, dataSource.openedConnections)
    }

    @Test
    fun `appendEvent inserts jsonb payload cleans old rounds and commits`() {
        val insert =
            FakePreparedStatementScript(
                expectedSqlFragment = "INSERT INTO lobby_events",
                updateCount = 1,
            )
        val cleanup =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_events",
                updateCount = 2,
            )
        val connection = FakeConnectionScript(listOf(insert, cleanup))
        val store =
            JdbcLobbyPersistenceStore(
                dataSource = FakeJdbcDataSource(listOf(connection)),
                retentionPolicy = LobbyPersistenceRetentionPolicy(eventRoundsToKeep = 2),
            )

        store.appendEvent(
            lobbyCode = LobbyCode("1003"),
            stateVersion = 4,
            turnCount = 3,
            eventType = "turn_state_updated",
            eventJson = jsonPayload("payload"),
        )

        assertTrue(connection.committed)
        assertEquals("1003", insert.lastParameters[1])
        assertEquals(4L, insert.lastParameters[2])
        assertEquals(3, insert.lastParameters[3])
        assertEquals("turn_state_updated", insert.lastParameters[4])
        val jsonb = assertInstanceOf(PGobject::class.java, insert.lastParameters[5])
        assertEquals("jsonb", jsonb.type)
        assertEquals("""{"value":"payload"}""", jsonb.value)
        assertEquals("1003", cleanup.lastParameters[1])
        assertEquals(2, cleanup.lastParameters[2])
    }

    @Test
    fun `appendSnapshot inserts jsonb payload cleans stale snapshots and commits`() {
        val insert =
            FakePreparedStatementScript(
                expectedSqlFragment = "INSERT INTO lobby_snapshots",
                updateCount = 1,
            )
        val cleanup =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_snapshots",
                updateCount = 1,
            )
        val connection = FakeConnectionScript(listOf(insert, cleanup))
        val store =
            JdbcLobbyPersistenceStore(
                dataSource = FakeJdbcDataSource(listOf(connection)),
                retentionPolicy = LobbyPersistenceRetentionPolicy(snapshotsToKeep = 4),
            )

        store.appendSnapshot(
            lobbyCode = LobbyCode("1071"),
            stateVersion = 8,
            turnCount = 5,
            snapshotJson = jsonPayload("snapshot"),
        )

        assertTrue(connection.committed)
        assertEquals("1071", insert.lastParameters[1])
        assertEquals(8L, insert.lastParameters[2])
        assertEquals(5, insert.lastParameters[3])
        val jsonb = assertInstanceOf(PGobject::class.java, insert.lastParameters[4])
        assertEquals("""{"value":"snapshot"}""", jsonb.value)
        assertEquals("1071", cleanup.lastParameters[1])
        assertEquals("1071", cleanup.lastParameters[2])
        assertEquals(4, cleanup.lastParameters[3])
    }

    @Test
    fun `cleanup methods delegate delete counts through a transaction`() {
        val cleanupEvents =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_events",
                updateCount = 3,
            )
        val cleanupSnapshots =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_snapshots",
                updateCount = 2,
            )
        val store =
            JdbcLobbyPersistenceStore(
                dataSource =
                    FakeJdbcDataSource(
                        listOf(
                            FakeConnectionScript(listOf(cleanupEvents)),
                            FakeConnectionScript(listOf(cleanupSnapshots)),
                        ),
                    ),
            )

        assertEquals(3, store.cleanupEventsForLobby(LobbyCode("1132"), currentRound = 0))
        assertEquals("1132", cleanupEvents.lastParameters[1])
        assertEquals(0, cleanupEvents.lastParameters[2])
        assertEquals(2, store.cleanupSnapshotsForLobby(LobbyCode("1132")))
        assertEquals("1132", cleanupSnapshots.lastParameters[1])
        assertEquals("1132", cleanupSnapshots.lastParameters[2])
    }

    @Test
    fun `list methods map persisted rows into records`() {
        val createdAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        val eventsQuery =
            FakePreparedStatementScript(
                expectedSqlFragment = "FROM lobby_events WHERE lobby_code = ?",
                rows =
                    listOf(
                        mapOf(
                            "id" to 1L,
                            "lobby_code" to "1003",
                            "state_version" to 3L,
                            "turn_count" to 2,
                            "event_type" to "turn_state_updated",
                            "event_json" to """{"value":"event"}""",
                            "created_at" to createdAt,
                        ),
                    ),
            )
        val snapshotsQuery =
            FakePreparedStatementScript(
                expectedSqlFragment = "FROM lobby_snapshots WHERE lobby_code = ?",
                rows =
                    listOf(
                        mapOf(
                            "id" to 4L,
                            "lobby_code" to "1003",
                            "state_version" to 5L,
                            "turn_count" to 3,
                            "snapshot_json" to """{"value":"snapshot"}""",
                            "created_at" to createdAt,
                        ),
                    ),
            )
        val store =
            JdbcLobbyPersistenceStore(
                FakeJdbcDataSource(
                    listOf(
                        FakeConnectionScript(listOf(eventsQuery)),
                        FakeConnectionScript(listOf(snapshotsQuery)),
                    ),
                ),
            )

        assertEquals(
            listOf(
                PersistedLobbyEventRecord(
                    id = 1L,
                    lobbyCode = LobbyCode("1003"),
                    stateVersion = 3L,
                    turnCount = 2,
                    eventType = "turn_state_updated",
                    eventJson = """{"value":"event"}""",
                    createdAt = createdAt.toInstant(),
                ),
            ),
            store.listEvents(LobbyCode("1003")),
        )
        assertEquals(
            listOf(
                PersistedLobbySnapshotRecord(
                    id = 4L,
                    lobbyCode = LobbyCode("1003"),
                    stateVersion = 5L,
                    turnCount = 3,
                    snapshotJson = """{"value":"snapshot"}""",
                    createdAt = createdAt.toInstant(),
                ),
            ),
            store.listSnapshots(LobbyCode("1003")),
        )
    }

    @Test
    fun `load helpers and delete lobby state map records and union lobby codes`() {
        val createdAt = Timestamp.from(Instant.parse("2026-01-02T00:00:00Z"))
        val latestSnapshot =
            FakePreparedStatementScript(
                expectedSqlFragment = "FROM lobby_snapshots WHERE lobby_code = ?",
                rows =
                    listOf(
                        mapOf(
                            "id" to 6L,
                            "lobby_code" to "1178",
                            "state_version" to 9L,
                            "turn_count" to 4,
                            "snapshot_json" to """{"value":"latest"}""",
                            "created_at" to createdAt,
                        ),
                    ),
            )
        val eventsAfter =
            FakePreparedStatementScript(
                expectedSqlFragment =
                    "FROM lobby_events WHERE lobby_code = ? AND state_version > ?",
                rows =
                    listOf(
                        mapOf(
                            "id" to 7L,
                            "lobby_code" to "1178",
                            "state_version" to 10L,
                            "turn_count" to 5,
                            "event_type" to "player_joined",
                            "event_json" to """{"value":"delta"}""",
                            "created_at" to createdAt,
                        ),
                    ),
            )
        val lobbyCodes =
            FakePreparedStatementScript(
                expectedSqlFragment =
                    "SELECT lobby_code FROM lobby_events UNION SELECT lobby_code " +
                        "FROM lobby_snapshots",
                rows =
                    listOf(
                        mapOf("lobby_code" to "1178"),
                        mapOf("lobby_code" to "1199"),
                    ),
            )
        val deleteEvents =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_events",
                updateCount = 1,
            )
        val deleteSnapshots =
            FakePreparedStatementScript(
                expectedSqlFragment = "DELETE FROM lobby_snapshots",
                updateCount = 1,
            )
        val store =
            JdbcLobbyPersistenceStore(
                FakeJdbcDataSource(
                    listOf(
                        FakeConnectionScript(listOf(latestSnapshot)),
                        FakeConnectionScript(listOf(eventsAfter)),
                        FakeConnectionScript(listOf(lobbyCodes)),
                        FakeConnectionScript(listOf(deleteEvents, deleteSnapshots)),
                    ),
                ),
            )

        assertEquals(9L, store.loadLatestSnapshot(LobbyCode("1178"))?.stateVersion)
        assertEquals(
            listOf(10L),
            store.loadEventsAfter(LobbyCode("1178"), 9L).map { it.stateVersion },
        )
        assertEquals(
            setOf(LobbyCode("1178"), LobbyCode("1199")),
            store.findLobbyCodesWithPersistedState(),
        )

        store.deleteLobbyState(LobbyCode("1178"))

        assertEquals("1178", deleteEvents.lastParameters[1])
        assertEquals("1178", deleteSnapshots.lastParameters[1])
    }

    @Test
    fun `loadLatestSnapshot returns null when no snapshot exists`() {
        val latestSnapshot =
            FakePreparedStatementScript(
                expectedSqlFragment = "FROM lobby_snapshots WHERE lobby_code = ?",
                rows = emptyList(),
            )
        val store =
            JdbcLobbyPersistenceStore(
                FakeJdbcDataSource(listOf(FakeConnectionScript(listOf(latestSnapshot)))),
            )

        assertEquals(null, store.loadLatestSnapshot(LobbyCode("1442")))
    }

    private fun jsonPayload(value: String) =
        buildJsonObject {
            put("value", value)
        }
}
