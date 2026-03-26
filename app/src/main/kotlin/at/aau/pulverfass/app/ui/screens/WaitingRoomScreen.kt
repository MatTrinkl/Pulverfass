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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.navigation.Screen

@Composable
fun WaitingRoomScreen(
    navController: NavController,
    lobbyCode: String,
    isHost: Boolean,
    playerName: String,
) {
    // Simulierte Player List
    val players = remember { mutableStateListOf(playerName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WaitingRoomHeader(lobbyCode, isHost)

        Spacer(modifier = Modifier.height(24.dp))

        PlayerListCard(
            players = players,
            playerName = playerName,
            isHost = isHost,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .weight(1f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        HostActions(
            isHost = isHost,
            playersCount = players.size,
            onStartGame = { navController.navigate(Screen.Game.route) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            Text(stringResource(id = R.string.leave_lobby))
        }
    }
}

@Composable
private fun WaitingRoomHeader(lobbyCode: String, isHost: Boolean) {
    Text(
        text = "${stringResource(id = R.string.lobby_id)}: $lobbyCode",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(8.dp))

    val hostStatusText = if (isHost) {
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
    players: List<String>,
    playerName: String,
    isHost: Boolean,
    modifier: Modifier = Modifier,
) {
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
                        player = player,
                        isHostPlayer = isHost && player == playerName,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(player: String, isHostPlayer: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
private fun HostActions(isHost: Boolean, playersCount: Int, onStartGame: () -> Unit) {
    if (isHost) {
        val canStart = playersCount >= 2
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
