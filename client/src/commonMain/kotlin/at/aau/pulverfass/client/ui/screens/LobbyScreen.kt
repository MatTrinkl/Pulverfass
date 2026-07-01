package at.aau.pulverfass.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import at.aau.pulverfass.client.lobby.LobbyController
import at.aau.pulverfass.client.ui.components.LobbyVideoBackground
import at.aau.pulverfass.client.ui.components.MainButton
import at.aau.pulverfass.client.ui.components.MainInputField
import at.aau.pulverfass.client.ui.navigation.Screen
import at.aau.pulverfass.client.ui.screenHeightDp
import at.aau.pulverfass.client.ui.screenWidthDp
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.ui.theme.cinzelDecorative
import io.ktor.http.encodeURLParameter
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
    val screenHeight = screenHeightDp()
    val compactHeight = screenHeight < 430.dp

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

        /*
         * Oben links bleibt nur der Verbindungsstatus.
         */
        ServerStatusPill(
            isConnected = state.isConnected,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp),
        )

        /*
         * Oben rechts steht die globale Online-Zahl spiegelgleich zum Serverstatus.
         */
        OnlinePlayersPill(
            count = state.globalPlayerCount,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 16.dp),
        )

        // Mitte: Create-Form oder Join-Form, abhängig vom aktuellen UI-Modus.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = if (compactHeight) 72.dp else 64.dp,
                        bottom = 48.dp,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val formWidth =
                when {
                    maxWidth < 520.dp -> maxWidth
                    maxWidth < 760.dp -> minOf(maxWidth * 0.82f, 520.dp)
                    else -> 500.dp
                }
            val formModifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = formWidth)
                    .verticalScroll(rememberScrollState())
            if (!state.isJoining) {
                CreateOrJoinForm(
                    playerName = state.playerName,
                    errorText = state.errorText,
                    modifier = formModifier,
                    onPlayerNameChange = controller::updatePlayerName,
                    onCreateClick = {
                        controller.createLobby { generatedCode ->
                            val encoded = state.playerName.encodeURLParameter()
                            uiScope.launch {
                                navController.navigate(
                                    Screen.WaitingRoom.route + "/$generatedCode/true/$encoded",
                                )
                            }
                        }
                    },
                    onJoinToggle = { controller.setJoining(true) },
                    onBackClick = { navController.popBackStack() },
                )
            } else {
                JoinLobbyForm(
                    lobbyCode = state.lobbyCode,
                    playerName = state.playerName,
                    errorText = state.errorText,
                    modifier = formModifier,
                    onLobbyCodeChange = controller::updateLobbyCode,
                    onPlayerNameChange = controller::updatePlayerName,
                    onJoinClick = {
                        controller.joinLobby { joinedCode ->
                            val encoded = state.playerName.encodeURLParameter()
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
    modifier: Modifier = Modifier,
    onPlayerNameChange: (String) -> Unit,
    onCreateClick: () -> Unit,
    onJoinToggle: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        LobbyTitle("SPIEL-LOBBY")
        Spacer(modifier = Modifier.height(28.dp))
        MainInputField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            placeholder = "SPIELERNAME",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MainButton(
                text = "LOBBY ERSTELLEN",
                onClick = onCreateClick,
                enabled = playerName.isNotBlank(),
                fontSize = 15,
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
            MainButton(
                text = "LOBBY BEITRETEN",
                onClick = onJoinToggle,
                enabled = playerName.isNotBlank(),
                fontSize = 15,
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
            MainButton(
                text = "ZURÜCK",
                onClick = onBackClick,
                fontSize = 15,
                modifier = Modifier.fillMaxWidth().height(88.dp),
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
    modifier: Modifier = Modifier,
    onLobbyCodeChange: (String) -> Unit,
    onPlayerNameChange: (String) -> Unit,
    onJoinClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        LobbyTitle("JOIN-LOBBY")
        Spacer(modifier = Modifier.height(24.dp))
        MainInputField(
            value = lobbyCode,
            onValueChange = { input ->
                if (input.length <= 4 && input.all { it.isDigit() }) {
                    onLobbyCodeChange(input)
                }
            },
            placeholder = "4-STELLIGER LOBBY-CODE",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(16.dp))
        MainInputField(
            value = playerName,
            onValueChange = onPlayerNameChange,
            placeholder = "SPIELERNAME",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
        )
        Spacer(modifier = Modifier.height(22.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MainButton(
                text = "LOBBY BEITRETEN",
                onClick = onJoinClick,
                enabled = playerName.isNotBlank() && lobbyCode.length == 4,
                fontSize = 15,
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
            MainButton(
                text = "ZURÜCK",
                onClick = onBackClick,
                fontSize = 15,
                modifier = Modifier.fillMaxWidth().height(88.dp),
            )
        }
        ErrorTextSlot(errorText)
    }
}

@Composable
private fun LobbyTitle(text: String) {
    val screenWidth = screenWidthDp()
    val compactWidth = screenWidth < 760.dp
    Text(
        text = text,
        color = PulverfassColors.GoldBright,
        fontFamily = cinzelDecorative(),
        fontSize = if (compactWidth) 38.sp else 56.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = if (compactWidth) 2.sp else 4.sp,
        textAlign = TextAlign.Center,
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
