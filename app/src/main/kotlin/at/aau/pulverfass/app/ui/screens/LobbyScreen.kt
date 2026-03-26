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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.navigation.Screen
import kotlin.random.Random

@Composable
fun LobbyScreen(navController: NavController) {
    var playerName by remember { mutableStateOf("") }
    var lobbyCodeInput by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
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
            value = playerName,
            onValueChange = { playerName = it },
            label = { Text(stringResource(id = R.string.enter_player_name)) },
            modifier = Modifier.fillMaxWidth(0.6f),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isJoining) {
            LobbyCodeInputField(
                value = lobbyCodeInput,
                onValueChange = { lobbyCodeInput = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        LobbyActions(
            isJoining = isJoining,
            playerName = playerName,
            lobbyCodeInput = lobbyCodeInput,
            onJoinToggled = { isJoining = it },
            onLobbyCodeCleared = { lobbyCodeInput = "" },
            onNavigate = { route -> navController.navigate(route) }
        )
    }
}

@Composable
private fun LobbyCodeInputField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                onValueChange(it)
            }
        },
        label = { Text(stringResource(id = R.string.enter_lobby_code)) },
        modifier = Modifier.fillMaxWidth(0.6f),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun LobbyActions(
    isJoining: Boolean,
    playerName: String,
    lobbyCodeInput: String,
    onJoinToggled: (Boolean) -> Unit,
    onLobbyCodeCleared: () -> Unit,
    onNavigate: (String) -> Unit
) {
    if (!isJoining) {
        Button(
            onClick = {
                val generatedCode = Random.nextInt(1000, 10000).toString()
                onNavigate(Screen.WaitingRoom.route + "/$generatedCode/true/$playerName")
            },
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = playerName.isNotBlank(),
        ) {
            Text(stringResource(id = R.string.create_lobby))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onJoinToggled(true) },
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = playerName.isNotBlank(),
        ) {
            Text(stringResource(id = R.string.join_lobby))
        }
    } else {
        Button(
            onClick = {
                onNavigate(Screen.WaitingRoom.route + "/$lobbyCodeInput/false/$playerName")
            },
            modifier = Modifier.fillMaxWidth(0.4f),
            enabled = playerName.isNotBlank() && lobbyCodeInput.length == 4,
        ) {
            Text(stringResource(id = R.string.enter_waiting_room))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                onJoinToggled(false)
                onLobbyCodeCleared()
            },
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            Text(stringResource(id = R.string.cancel))
        }
    }
}
