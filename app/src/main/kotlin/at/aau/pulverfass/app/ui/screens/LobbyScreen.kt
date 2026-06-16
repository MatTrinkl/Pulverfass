package at.aau.pulverfass.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
    var showDevPanel by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(PulverfassColors.SurfaceVoid),
    ) {
        LobbyVideoBackground()

        // Oben links: Verbindungsstatus und globale Online-Zahl.
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerStatusPill(isConnected = state.isConnected)
            OnlinePlayersPill(count = state.globalPlayerCount)
        }

        // Oben rechts: kompakte Debug-Infos für Server-URL, letzte Nachricht und Map-Test.
        DevInfoPanel(
            serverUrl = state.serverUrl,
            lastMessageType = state.lastMessageType,
            onMapTestClick = { navController.navigate(Screen.LoadGame.route) },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 16.dp),
        )

        // Unten rechts: einblendbare Dev-Verbindungssteuerung.
        DevControlsPanel(
            controlsState =
                DevControlsState(
                    isConnected = state.isConnected,
                    isConnecting = state.isConnecting,
                    serverUrl = state.serverUrl,
                ),
            showDevPanel = showDevPanel,
            onToggleDevMod = { showDevPanel = !showDevPanel },
            onConnectClick = controller::connect,
            onDisconnectClick = controller::disconnect,
            onUpdateServerUrl = controller::updateServerUrl,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp),
        )

        // Unten links: zurück zum Hauptmenü.
        MainButton(
            text = "ZURÜCK",
            onClick = { navController.popBackStack() },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 24.dp),
        )

        // Mitte: Create-Form oder Join-Form, abhängig vom aktuellen UI-Modus.
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
                val uppercase = input.uppercase()
                if (uppercase.length <= 4 && uppercase.all { it.isLetterOrDigit() }) {
                    onLobbyCodeChange(uppercase)
                }
            },
            placeholder = "4-STELLIGER LOBBY-CODE",
            modifier = Modifier.fillMaxWidth(0.8f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
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

@Composable
private fun DevInfoPanel(
    serverUrl: String,
    lastMessageType: String?,
    onMapTestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(
                    color = PulverfassColors.SurfaceDark.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = "S-URL: $serverUrl",
            color = PulverfassColors.TextOnDark,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "S-MESSAGE: ${lastMessageType ?: "—"}",
            color = PulverfassColors.TextOnDark,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        DevPillButton(text = "MAP-TEST", onClick = onMapTestClick)
    }
}

/**
 * Gruppiert die Verbindungswerte des DevPanels.
 *
 * @param isConnected `true`, wenn die WebSocket-Verbindung aktiv ist.
 * @param isConnecting `true`, solange ein Connect-Versuch läuft.
 * @param serverUrl aktuell editierbare Server-URL.
 */
private data class DevControlsState(
    val isConnected: Boolean,
    val isConnecting: Boolean,
    val serverUrl: String,
)

@Composable
private fun DevControlsPanel(
    controlsState: DevControlsState,
    showDevPanel: Boolean,
    onToggleDevMod: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DevPillButton(text = "DEV-MOD", onClick = onToggleDevMod)
            DevPillButton(
                text =
                    when {
                        controlsState.isConnecting -> "CONNECTING..."
                        controlsState.isConnected -> "DISCONNECT"
                        else -> "CONNECTION TEST"
                    },
                onClick = if (controlsState.isConnected) onDisconnectClick else onConnectClick,
                enabled = !controlsState.isConnecting,
            )
        }
        if (showDevPanel) {
            OutlinedTextField(
                value = controlsState.serverUrl,
                onValueChange = onUpdateServerUrl,
                modifier = Modifier.width(220.dp),
                label = { Text(text = "SERVER URL", fontSize = 8.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PulverfassColors.GoldDark,
                        unfocusedBorderColor = PulverfassColors.GoldDark.copy(alpha = 0.5f),
                        focusedTextColor = PulverfassColors.TextOnDark,
                        unfocusedTextColor = PulverfassColors.TextOnDark,
                        focusedLabelColor = PulverfassColors.Gold,
                        unfocusedLabelColor = PulverfassColors.GoldMuted,
                        cursorColor = PulverfassColors.Gold,
                        focusedContainerColor = PulverfassColors.SurfaceDark.copy(alpha = 0.85f),
                        unfocusedContainerColor = PulverfassColors.SurfaceDark.copy(alpha = 0.85f),
                    ),
            )
        }
    }
}

@Composable
private fun DevPillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .border(
                    width = 1.dp,
                    color = PulverfassColors.GoldDark,
                    shape = RoundedCornerShape(2.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PulverfassColors.SurfaceDark.copy(alpha = 0.85f),
                contentColor = PulverfassColors.Gold,
            ),
        shape = RoundedCornerShape(2.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
