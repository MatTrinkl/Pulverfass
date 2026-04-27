package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.navigation.Screen

// Bildschirm für den Warteraum vor Spielbeginn.
@Composable
fun WaitingRoomScreen(
    navController: NavController,
    controller: LobbyController,
    lobbyCode: String,
    isHost: Boolean,
    playerName: String,
) {
    val state by controller.state.collectAsState()
    val effectivePlayerName = state.playerName.ifBlank { playerName }
    val effectiveIsHost = state.isHost || isHost
    val players =
        if (state.players.isEmpty()) {
            listOf(WaitingRoomPlayerUi(displayName = effectivePlayerName, isHost = effectiveIsHost))
        } else {
            state.players.map {
                WaitingRoomPlayerUi(displayName = it.displayName, isHost = it.isHost)
            }
        }

    LaunchedEffect(state.gameStarted) {
        if (state.gameStarted) {
            navController.navigate(Screen.LoadGame.route)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WaitingRoomHeader(lobbyCode, effectiveIsHost)

        Spacer(modifier = Modifier.height(24.dp))

        PlayerListCard(
            players = players,
            modifier = Modifier.fillMaxWidth(0.7f).weight(1f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        HostActions(
            isHost = effectiveIsHost,
            playersCount = players.size,
            onStartGame = controller::startGame,
        )

        state.errorText?.let { errorText ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                controller.leaveLobby()
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            // Ermöglicht das Verlassen der aktuellen Lobby.
            Text(stringResource(id = R.string.leave_lobby))
        }
    }
}

private data class WaitingRoomPlayerUi(
    val displayName: String,
    val isHost: Boolean,
)

@Composable
private fun WaitingRoomHeader(
    lobbyCode: String,
    isHost: Boolean,
) {
    // Zeigt die eindeutige Lobby-ID an.
    Text(
        text = "${stringResource(id = R.string.lobby_id)}: $lobbyCode",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(8.dp))

    val hostStatusText =
        if (isHost) {
            stringResource(id = R.string.you_are_host)
        } else {
            stringResource(id = R.string.waiting_for_host)
        }

    Text(
        text = hostStatusText,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun PlayerListCard(
    players: List<WaitingRoomPlayerUi>,
    modifier: Modifier = Modifier,
) {
    // Kartenansicht für die Auflistung aller Teilnehmer.
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${stringResource(id = R.string.players)} (${players.size}/6)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn {
                items(players) { player ->
                    PlayerRow(
                        player = player.displayName,
                        isHostPlayer = player.isHost,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: String,
    isHostPlayer: Boolean,
) {
    // Zeigt einen einzelnen Spieler in einer Zeile an.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = player, style = MaterialTheme.typography.bodyLarge)
        if (isHostPlayer) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.host_tag),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun HostActions(
    isHost: Boolean,
    playersCount: Int,
    onStartGame: () -> Unit,
) {
    // Nur der Host kann das Spiel starten, wenn genug Spieler da sind.
    if (isHost) {
        val canStart = playersCount >= 3
        Button(
            onClick = onStartGame,
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = canStart,
        ) {
            Text(stringResource(id = R.string.start_game))
        }
        if (!canStart) {
            Text(
                text = stringResource(id = R.string.need_players),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
