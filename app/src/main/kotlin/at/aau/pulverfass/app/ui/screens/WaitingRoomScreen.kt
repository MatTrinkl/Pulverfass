package at.aau.pulverfass.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.PulverfassPanel
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.PulverfassColors

/**
 * Warteraum-Screen im Pulverfass-Theme.
 * Logic 1:1 wie vorher, nur visuell neu mit Parchment-Player-List.
 */
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
            listOf(
                WaitingRoomPlayerUi(
                    displayName = effectivePlayerName,
                    isHost = effectiveIsHost,
                ),
            )
        } else {
            state.players.map {
                WaitingRoomPlayerUi(
                    displayName = it.displayName,
                    isHost = it.isHost,
                    isDisconnected = it.isDisconnected,
                )
            }
        }

    LaunchedEffect(state.gameStarted) {
        if (state.gameStarted) {
            navController.navigate(Screen.LoadGame.route)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                PulverfassColors.SurfaceWood,
                                PulverfassColors.SurfaceVoid,
                            ),
                        radius = 1400f,
                    ),
                ),
    ) {
        // Left side: Host/Lobby Info
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp)
                    .fillMaxWidth(0.25f),
            horizontalAlignment = Alignment.Start,
        ) {
            if (effectiveIsHost) {
                Text(
                    text = "DU BIST DER HOST",
                    color = PulverfassColors.SurfaceCard,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "LOBBY: $lobbyCode",
                color = PulverfassColors.GoldBright,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
        }

        // Center: Player List in Parchment
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.5f),
            contentAlignment = Alignment.Center,
        ) {
            PulverfassPanel(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "SPIELER (${players.size}/6)",
                    color = PulverfassColors.TextOnParchment,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(players) { player ->
                        PlayerRow(player = player)
                    }
                }
            }
        }

        // Bottom: Action Buttons
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth(0.6f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (effectiveIsHost) {
                val canStart = players.size >= 3
                MainButton(
                    text = "SPIEL STARTEN",
                    onClick = controller::startGame,
                    enabled = canStart,
                    modifier = Modifier.weight(1f),
                )
            }
            MainButton(
                text = "LOBBY VERLASSEN",
                onClick = {
                    controller.leaveLobby()
                    navController.popBackStack()
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Bottom-Right: Error Text
        state.errorText?.let { error ->
            Text(
                text = "ERROR: $error".uppercase(),
                color = PulverfassColors.DangerBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 96.dp),
            )
        }

        // Min-players warning (host only)
        if (effectiveIsHost && players.size < 3) {
            Text(
                text = "MIND. 3 SPIELER",
                color = PulverfassColors.DangerBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp),
            )
        }
    }
}

private data class WaitingRoomPlayerUi(
    val displayName: String,
    val isHost: Boolean,
    val isDisconnected: Boolean = false,
)

@Composable
private fun PlayerRow(player: WaitingRoomPlayerUi) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = player.displayName.uppercase(),
            color = PulverfassColors.TextOnParchment,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        if (player.isHost) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(HOST)",
                color = PulverfassColors.GoldDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
        if (player.isDisconnected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(DISCONNECTED)",
                color = PulverfassColors.Danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
