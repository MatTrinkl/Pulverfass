package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.navigation.Screen

/**
 * Lobby-Einstiegspunkt fuer die Android-App.
 *
 * Der Screen bindet die UI an den LobbyController und nutzt damit die neue
 * technische WebSocket-Pipeline aus den Shared/Server-inspirierten Modulen.
 */
@Composable
fun LobbyScreen(navController: NavController) {
    val controller = remember { LobbyController() }
    val state by controller.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            controller.close()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(id = R.string.game_lobby),
            style = MaterialTheme.typography.displaySmall,
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = controller::updateServerUrl,
            label = { Text(stringResource(id = R.string.server_url)) },
            modifier = Modifier.fillMaxWidth(0.8f),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.playerName,
            onValueChange = controller::updatePlayerName,
            label = { Text(stringResource(id = R.string.enter_player_name)) },
            modifier = Modifier.fillMaxWidth(0.6f),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.isJoining) {
            LobbyCodeInputField(
                value = state.lobbyCode,
                onValueChange = controller::updateLobbyCode,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "${stringResource(id = R.string.connection_status)}: ${state.statusText}",
            style = MaterialTheme.typography.labelLarge,
        )

        state.lastMessageType?.let { lastType ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${stringResource(id = R.string.last_message_type)}: $lastType",
                style = MaterialTheme.typography.labelMedium,
            )
        }

        state.errorText?.let { errorText ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        ConnectionActions(
            isConnected = state.isConnected,
            isConnecting = state.isConnecting,
            onConnect = controller::connect,
            onDisconnect = controller::disconnect,
        )

        Spacer(modifier = Modifier.height(12.dp))

        LobbyActions(
            isJoining = state.isJoining,
            isConnected = state.isConnected,
            playerName = state.playerName,
            lobbyCode = state.lobbyCode,
            onJoinToggled = controller::setJoining,
            onLobbyCodeCleared = { controller.updateLobbyCode("") },
            onCreateLobby = {
                controller.createLobby { generatedCode ->
                    navController.navigate(
                        Screen.WaitingRoom.route +
                            "/$generatedCode/true/${state.playerName}",
                    )
                }
            },
            onJoinLobby = {
                controller.joinLobby { lobbyCode ->
                    navController.navigate(
                        Screen.WaitingRoom.route +
                            "/$lobbyCode/false/${state.playerName}",
                    )
                }
            },
        )
    }
}

@Composable
private fun LobbyCodeInputField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            // Nur vierstellige, numerische Lobbycodes akzeptieren.
            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                onValueChange(it)
            }
        },
        label = { Text(stringResource(id = R.string.enter_lobby_code)) },
        modifier = Modifier.fillMaxWidth(0.6f),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
    )
}

@Composable
private fun ConnectionActions(
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    if (!isConnected) {
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = !isConnecting,
        ) {
            Text(
                if (isConnecting) {
                    stringResource(id = R.string.connecting)
                } else {
                    stringResource(id = R.string.connect_server)
                },
            )
        }
    } else {
        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            Text(stringResource(id = R.string.disconnect_server))
        }
    }
}

@Composable
private fun LobbyActions(
    isJoining: Boolean,
    isConnected: Boolean,
    playerName: String,
    lobbyCode: String,
    onJoinToggled: (Boolean) -> Unit,
    onLobbyCodeCleared: () -> Unit,
    onCreateLobby: () -> Unit,
    onJoinLobby: () -> Unit,
) {
    if (!isJoining) {
        Button(
            onClick = onCreateLobby,
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = playerName.isNotBlank() && isConnected,
        ) {
            Text(stringResource(id = R.string.create_lobby))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onJoinToggled(true) },
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = playerName.isNotBlank() && isConnected,
        ) {
            Text(stringResource(id = R.string.join_lobby))
        }
    } else {
        Button(
            onClick = onJoinLobby,
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = playerName.isNotBlank() && lobbyCode.length == 4 && isConnected,
        ) {
            Text(stringResource(id = R.string.enter_waiting_room))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                // Zurueck zur Auswahl zwischen Erstellen und Beitreten.
                onJoinToggled(false)
                onLobbyCodeCleared()
            },
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            Text(stringResource(id = R.string.cancel))
        }
    }
}
