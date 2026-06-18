package at.aau.pulverfass.app.ui.screens

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.game.GamePlayerUi
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prüft die reine Leave-Erkennung [playersThatLeft] ohne Compose-Rendering.
 *
 * Schwerpunkt ist der zuvor fehlende Fall: ein Spieler, der per
 * PlayerLeftLobbyEvent direkt aus der Liste entfernt wird, ohne vorher als
 * DISCONNECTED aufzutauchen.
 */
class PlayerLeftDetectorTest {
    @Test
    fun `reports player on connected to disconnected transition`() {
        val previous =
            mapOf(remotePlayer.id() to presence(remotePlayer, ConnectionStatus.CONNECTED))

        val left =
            playersThatLeft(
                previous = previous,
                current =
                    listOf(
                        remotePlayer.copy(connectionStatus = ConnectionStatus.DISCONNECTED),
                    ),
                localPlayerId = localId,
            )

        assertEquals(listOf(remotePlayer.name), left)
    }

    @Test
    fun `reports player removed from list while still connected`() {
        val previous =
            mapOf(remotePlayer.id() to presence(remotePlayer, ConnectionStatus.CONNECTED))

        // Spieler ist komplett aus der aktuellen Liste verschwunden (Leave-Event).
        val left =
            playersThatLeft(
                previous = previous,
                current = emptyList(),
                localPlayerId = localId,
            )

        assertEquals(listOf(remotePlayer.name), left)
    }

    @Test
    fun `does not report the local player`() {
        val localPlayer = remotePlayer.copy(playerId = localId, name = "Me")
        val previous = mapOf(localId to presence(localPlayer, ConnectionStatus.CONNECTED))

        val left =
            playersThatLeft(
                previous = previous,
                current = emptyList(),
                localPlayerId = localId,
            )

        assertTrue(left.isEmpty())
    }

    @Test
    fun `does not report a player removed after already being disconnected`() {
        // Wechsel auf DISCONNECTED wurde bereits gemeldet; das spätere Entfernen
        // darf keinen zweiten Toast auslösen.
        val previous =
            mapOf(remotePlayer.id() to presence(remotePlayer, ConnectionStatus.DISCONNECTED))

        val left =
            playersThatLeft(
                previous = previous,
                current = emptyList(),
                localPlayerId = localId,
            )

        assertTrue(left.isEmpty())
    }

    @Test
    fun `does not report a newly appearing player`() {
        val left =
            playersThatLeft(
                previous = emptyMap(),
                current = listOf(remotePlayer),
                localPlayerId = localId,
            )

        assertTrue(left.isEmpty())
    }

    @Test
    fun `does not report a reconnecting player`() {
        val previous =
            mapOf(remotePlayer.id() to presence(remotePlayer, ConnectionStatus.DISCONNECTED))

        val left =
            playersThatLeft(
                previous = previous,
                current = listOf(remotePlayer.copy(connectionStatus = ConnectionStatus.CONNECTED)),
                localPlayerId = localId,
            )

        assertTrue(left.isEmpty())
    }

    private val localId = PlayerId(1)

    private val remotePlayer =
        GamePlayerUi(
            playerId = PlayerId(2),
            name = "Bob",
            avatarText = "B",
            color = Color(0xFF2B6CA6),
        )

    private fun GamePlayerUi.id(): PlayerId = playerId

    private fun presence(
        player: GamePlayerUi,
        status: ConnectionStatus,
    ): PlayerPresence = PlayerPresence(player.name, status)
}
