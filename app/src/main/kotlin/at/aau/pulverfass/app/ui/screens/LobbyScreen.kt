package at.aau.pulverfass.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.components.LobbyVideoBackground
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.MainInputField
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts
import kotlinx.coroutines.launch

/**
 * Lobby-Einstiegspunkt im maritimen Pulverfass-Stil.
 *
 * Der Screen hat zwei Modi: Create/Join-Auswahl und Code-Eingabe. Netzwerk-
 * und Lobbylogik bleiben im [LobbyController]; der Screen sammelt nur
 * Nutzerinput und navigiert nach erfolgreichem Create oder Join weiter.
 *
 * @param navController Navigation in den Warteraum oder zurück ins Hauptmenü.
 * @param controller gemeinsame Lobby-Schicht für Verbindung, Create und Join.
 */
@Composable
fun LobbyScreen(
    navController: NavController,
    controller: LobbyController,
) {
    val state by controller.state.collectAsState()
    val uiScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        controller.connectForStatus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid),
    ) {
        LobbyVideoBackground()

        /**
         * Oben links bleibt nur der Verbindungsstatus.
         */
        ServerStatusPill(
            isConnected = state.isConnected,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp),
        )

        /**
         * Oben rechts steht die globale Online-Zahl spiegelgleich zum Serverstatus.
         */
        OnlinePlayersPill(
            count = state.globalPlayerCount,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 16.dp),
        )

        /**
         * Unten links: zurück zum Hauptmenü.
         */
        MainButton(
            text = "ZURÜCK",
            onClick = { navController.popBackStack() },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 24.dp),
        )

        /**
         * Mitte: Create-Form oder Join-Form, abhängig vom aktuellen UI-Modus.
         */
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!state.isJoining) {
                CreateOrJoinForm(
                    playerName = state.playerName,
                    errorText = state.errorText,
                    onPlayerNameChange = controller::updatePlayerName,
                    onCreateClick = {
                        controller.createLobby { generatedCode ->
                            val encoded = Uri.encode(state.playerName)
                            uiScope.launch {
                                navController.navigate(
                                    Screen.WaitingRoom.route + "/$generatedCode/true/$encoded",
                                )
                            }
                        }
                    },
                    onJoinToggle = { controller.setJoining(true) },
                )
            } else {
                JoinLobbyForm(
                    lobbyCode = state.lobbyCode,
                    playerName = state.playerName,
                    errorText = state.errorText,
                    onLobbyCodeChange = controller::updateLobbyCode,
                    onPlayerNameChange = controller::updatePlayerName,
                    onJoinClick = {
                        controller.joinLobby { joinedCode ->
                            val encoded = Uri.encode(state.playerName)
                            uiScope.launch {
                                navController.navigate(
                                    Screen.WaitingRoom.route + "/$joinedCode/false/$encoded",
                                )
                            }
                        }
                    },
                    onBackClick = {
                        controller.setJoining(false)
                        controller.updateLobbyCode("")
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateOrJoinForm(
    playerName: String,
    errorText: String?,
    onPlayerNameChange: (String) -> Unit,
    onCreateClick: () -> Unit,
    onJoinToggle: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.7f),
    ) {
        LobbyTitle("SPIEL-LOBBY")
        Spacer(modifier = Modifier.height(40.dp))
        MainInputField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            placeholder = "SPIELERNAME",
            modifier = Modifier.fillMaxWidth(0.8f),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MainButton(
                text = "LOBBY ERSTELLEN",
                onClick = onCreateClick,
                enabled = playerName.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            MainButton(
                text = "LOBBY BEITRETEN",
                onClick = onJoinToggle,
                enabled = playerName.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
        ErrorTextSlot(errorText)
    }
}

@Composable
private fun JoinLobbyForm(
    lobbyCode: String,
    playerName: String,
    errorText: String?,
    onLobbyCodeChange: (String) -> Unit,
    onPlayerNameChange: (String) -> Unit,
    onJoinClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.7f),
    ) {
        LobbyTitle("JOIN-LOBBY")
        Spacer(modifier = Modifier.height(40.dp))
        MainInputField(
            value = lobbyCode,
            onValueChange = { input ->
                if (input.length <= 4 && input.all { it.isDigit() }) {
                    onLobbyCodeChange(input)
                }
            },
            placeholder = "4-STELLIGER LOBBY-CODE",
            modifier = Modifier.fillMaxWidth(0.8f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(16.dp))
        MainInputField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            placeholder = "SPIELERNAME",
            modifier = Modifier.fillMaxWidth(0.8f),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MainButton(
                text = "JOIN LOBBY",
                onClick = onJoinClick,
                enabled = playerName.isNotBlank() && lobbyCode.length == 4,
                modifier = Modifier.weight(1f),
            )
            MainButton(
                text = "ZURÜCK",
                onClick = onBackClick,
                modifier = Modifier.weight(1f),
            )
        }
        ErrorTextSlot(errorText)
    }
}

@Composable
private fun LobbyTitle(text: String) {
    Text(
        text = text,
        color = PulverfassColors.GoldBright,
        fontFamily = PulverfassFonts.CinzelDecorative,
        fontSize = 56.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp,
    )
}

@Composable
private fun ErrorTextSlot(errorText: String?) {
    Spacer(modifier = Modifier.height(16.dp))
    errorText?.let {
        Text(
            text = "ERROR: $it".uppercase(),
            color = PulverfassColors.DangerBright,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ServerStatusPill(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(
                    color = PulverfassColors.SurfaceDark.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SERVER:",
            color = PulverfassColors.TextOnDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(
                        color =
                            if (isConnected) {
                                PulverfassColors.Success
                            } else {
                                PulverfassColors.Danger
                            },
                        shape = CircleShape,
                    ),
        )
    }
}

@Composable
private fun OnlinePlayersPill(
    count: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(
                    color = PulverfassColors.SurfaceDark.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ONLINE: ${count ?: "—"}",
            color = PulverfassColors.TextOnDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
