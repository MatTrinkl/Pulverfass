package at.aau.pulverfass.server.session

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.SessionToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionContextRegistryTest {
    @Test
    fun `assignPlayer should store stable player mapping for session`() {
        val registry = SessionContextRegistry()
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174300")

        registry.assignPlayer(sessionToken, PlayerId(1))

        assertEquals(PlayerId(1), registry.playerIdForSession(sessionToken))
        assertEquals(sessionToken, registry.sessionTokenForPlayer(PlayerId(1)))
    }

    @Test
    fun `updateLobbyContext should enrich reconnect context`() {
        val registry = SessionContextRegistry()
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174301")
        registry.assignPlayer(sessionToken, PlayerId(2))

        registry.updateLobbyContext(sessionToken, LobbyCode("AB12"), "Alice")

        assertEquals(
            SessionReconnectContext(
                playerId = PlayerId(2),
                lobbyCode = LobbyCode("AB12"),
                playerDisplayName = "Alice",
            ),
            registry.contextFor(sessionToken),
        )
    }

    @Test
    fun `clearLobbyContext should keep player mapping but remove lobby state`() {
        val registry = SessionContextRegistry()
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174302")
        registry.assignPlayer(sessionToken, PlayerId(3))
        registry.updateLobbyContext(sessionToken, LobbyCode("CD34"), "Bob")

        registry.clearLobbyContext(sessionToken)

        assertEquals(
            SessionReconnectContext(playerId = PlayerId(3)),
            registry.contextFor(sessionToken),
        )
        assertEquals(sessionToken, registry.sessionTokenForPlayer(PlayerId(3)))
    }

    @Test
    fun `removeSession should delete player and lobby mapping together`() {
        val registry = SessionContextRegistry()
        val sessionToken = SessionToken("123e4567-e89b-12d3-a456-426614174303")
        registry.assignPlayer(sessionToken, PlayerId(4))
        registry.updateLobbyContext(sessionToken, LobbyCode("EF56"), "Carol")

        registry.removeSession(sessionToken)

        assertNull(registry.contextFor(sessionToken))
        assertNull(registry.playerIdForSession(sessionToken))
        assertNull(registry.sessionTokenForPlayer(PlayerId(4)))
    }
}
