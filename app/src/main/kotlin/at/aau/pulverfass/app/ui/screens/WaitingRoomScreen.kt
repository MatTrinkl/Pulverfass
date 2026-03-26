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
        Text(
            text = "${stringResource(id = R.string.lobby_id)}: $lobbyCode",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isHost) {
                stringResource(
                    id = R.string.you_are_host,
                )
            } else {
                stringResource(id = R.string.waiting_for_host)
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .weight(1f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${stringResource(id = R.string.players)} (${players.size}/6)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(players) { player ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = player, style = MaterialTheme.typography.bodyLarge)
                            if (player == playerName && isHost) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.host_tag),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isHost) {
            Button(
                onClick = {
                    if (players.size >= 2) {
                        navController.navigate(Screen.Game.route)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.4f),
                enabled = players.size >= 2,
            ) {
                Text(stringResource(id = R.string.start_game))
            }
            if (players.size < 2) {
                Text(
                    text = stringResource(id = R.string.need_players),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            Text(stringResource(id = R.string.leave_lobby))
        }
    }
}
