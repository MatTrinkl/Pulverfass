package at.aau.pulverfass.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

/**
 * Lobby-Einstiegspunkt für die Android-App.
 *
 * Der Screen bindet die UI an den LobbyController und nutzt damit die neue
 * technische WebSocket-Pipeline aus den Shared/Server-inspirierten Modulen.
 */
@Composable
fun LobbyScreen(
    navController: NavController,
    controller: LobbyController,
) {
    val state by controller.state.collectAsState()
    val uiScope = rememberCoroutineScope()
    var showServerSettings by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
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

        LobbyPrimaryActions(
            navController = navController,
            isJoining = state.isJoining,
            playerName = state.playerName,
            lobbyCode = state.lobbyCode,
            handlers =
                LobbyActionHandlers(
                    onJoinToggled = controller::setJoining,
                    onLobbyCodeCleared = { controller.updateLobbyCode("") },
                    onCreateLobby = {
                        controller.createLobby { generatedCode ->
                            val encodedPlayerName = Uri.encode(state.playerName)
                            uiScope.launch {
                                navController.navigate(
                                    Screen.WaitingRoom.route +
                                        "/$generatedCode/true/$encodedPlayerName",
                                )
                            }
                        }
                    },
                    onJoinLobby = {
                        controller.joinLobby { lobbyCode ->
                            val encodedPlayerName = Uri.encode(state.playerName)
                            uiScope.launch {
                                navController.navigate(
                                    Screen.WaitingRoom.route +
                                        "/$lobbyCode/false/$encodedPlayerName",
                                )
                            }
                        }
                    },
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

        OutlinedButton(
            onClick = { showServerSettings = !showServerSettings },
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text(
                if (showServerSettings) {
                    "Server-Optionen ausblenden"
                } else {
                    "Server-Optionen anzeigen"
                },
            )
        }

        if (showServerSettings) {
            Spacer(modifier = Modifier.height(12.dp))
            ConnectionActions(
                isConnected = state.isConnected,
                isConnecting = state.isConnecting,
                onConnect = controller::connect,
                onDisconnect = controller::disconnect,
            )
        }
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
            // Nur vierstellige, alphanumerische Lobbycodes akzeptieren.
            val uppercase = it.uppercase()
            if (uppercase.length <= 4 && uppercase.all { char -> char.isLetterOrDigit() }) {
                onValueChange(uppercase)
            }
        },
        label = { Text(stringResource(id = R.string.enter_lobby_code)) },
        modifier = Modifier.fillMaxWidth(0.6f),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
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
private fun LobbyPrimaryActions(
    navController: NavController,
    isJoining: Boolean,
    playerName: String,
    lobbyCode: String,
    handlers: LobbyActionHandlers,
) {
    if (!isJoining) {
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = handlers.onCreateLobby,
                modifier = Modifier.weight(1f),
                enabled = playerName.isNotBlank(),
            ) {
                Text(stringResource(id = R.string.create_lobby))
            }
            OutlinedButton(
                onClick = { handlers.onJoinToggled(true) },
                modifier = Modifier.weight(1f),
                enabled = playerName.isNotBlank(),
            ) {
                Text(stringResource(id = R.string.join_lobby))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { navController.navigate(Screen.LoadGame.route) },
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text(stringResource(id = R.string.open_map_playground))
        }
    } else {
        Button(
            onClick = handlers.onJoinLobby,
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = playerName.isNotBlank() && lobbyCode.length == 4,
        ) {
            Text(stringResource(id = R.string.enter_waiting_room))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                // Zurück zur Auswahl zwischen Erstellen und Beitreten.
                handlers.onJoinToggled(false)
                handlers.onLobbyCodeCleared()
            },
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            Text(stringResource(id = R.string.cancel))
        }
    }
}

private data class LobbyActionHandlers(
    val onJoinToggled: (Boolean) -> Unit,
    val onLobbyCodeCleared: () -> Unit,
    val onCreateLobby: () -> Unit,
    val onJoinLobby: () -> Unit,
)
