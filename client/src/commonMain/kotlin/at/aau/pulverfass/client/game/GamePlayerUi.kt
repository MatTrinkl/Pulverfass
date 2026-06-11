package at.aau.pulverfass.client.game

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.connection.ConnectionStatus

/**
 * Darstellungsmodell eines Spielers im GameScreen.
 *
 * Die [PlayerId] kommt aus dem `shared`-Modul; Farbe und Kürzel sind reine
 * Android-UI-Daten. [characterId] bleibt getrennt von der Spielerfarbe, damit
 * das Portrait frei gewählt werden kann, ohne die spielrelevante Kartenfarbe zu
 * verändern.
 */
data class GamePlayerUi(
    val playerId: PlayerId,
    val name: String,
    val avatarText: String,
    val characterId: String? = null,
    val color: Color,
    val isHost: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
)
