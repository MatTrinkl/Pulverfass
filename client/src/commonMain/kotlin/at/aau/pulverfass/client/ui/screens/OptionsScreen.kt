package at.aau.pulverfass.client.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import at.aau.pulverfass.client.audio.BackgroundMusicManager
import at.aau.pulverfass.client.ui.components.MainButton
import at.aau.pulverfass.client.ui.components.MainInputField
import at.aau.pulverfass.client.ui.components.VideoAsset
import at.aau.pulverfass.client.ui.components.VideoPlayer
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.ui.theme.cinzelDecorative
import at.aau.pulverfass.shared.lobby.MAX_PLAYER_DISPLAY_NAME_LENGTH

/**
 * Globaler Options-Screen für Anzeigename und Audio.
 *
 * Der Anzeigename wird über den Controller persistiert. Musik und SFX werden
 * direkt auf dem [BackgroundMusicManager] geändert, damit der Schalter sofort
 * hörbar wirkt und nicht erst nach einem Screenwechsel.
 *
 * @param navController Navigation zurück zum vorherigen Screen.
 * @param playerName aktuell gespeicherter Anzeigename.
 * @param onPlayerNameChange Callback für persistente Namensänderungen.
 * @param musicManager gemeinsamer Audio-Manager der Activity.
 */
@Composable
fun OptionsScreen(
    navController: NavController,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    musicManager: BackgroundMusicManager,
) {
    var isMusicEnabled by remember { mutableStateOf(!musicManager.isMusicMuted) }
    var isSfxEnabled by remember { mutableStateOf(!musicManager.isSfxMuted) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid),
    ) {
        // Video-Hintergrund ohne eigene Tonspur; Musik kommt routenbasiert aus der Activity.
        VideoPlayer(
            asset = VideoAsset.OPTIONS_BACKGROUND,
            loop = true,
            cover = true,
            muted = true,
            modifier = Modifier.fillMaxSize(),
        )
        // Dunkles Overlay hält Formular und Titel vor wechselnden Videoframes lesbar.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.6f)),
        )

        // Scrollbarer Inhalt, damit kleine Displays nicht von Systemleisten verdeckt werden.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "OPTIONEN",
                color = PulverfassColors.GoldBright,
                fontFamily = cinzelDecorative(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(40.dp))

            MainInputField(
                value = playerName,
                onValueChange = {
                    if (it.length <= MAX_PLAYER_DISPLAY_NAME_LENGTH) {
                        onPlayerNameChange(
                            it,
                        )
                    }
                },
                placeholder = "SPIELERNAME",
                modifier = Modifier.fillMaxWidth(0.5f),
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Gemeinsame Audiokarte für Musik und SFX mit sofortiger Persistenz.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.5f)
                        .background(
                            color = PulverfassColors.SurfaceDark.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                AudioToggleRow(
                    label = "MUSIK",
                    isEnabled = isMusicEnabled,
                    onToggle = { enabled ->
                        isMusicEnabled = enabled
                        musicManager.setMusicMuted(!enabled)
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                AudioToggleRow(
                    label = "SOUND-EFFEKTE",
                    isEnabled = isSfxEnabled,
                    onToggle = { enabled ->
                        isSfxEnabled = enabled
                        musicManager.setSfxMuted(!enabled)
                    },
                )
            }
            Spacer(modifier = Modifier.height(32.dp))

            MainButton(
                text = "ZURÜCK",
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth(0.3f),
            )
        }
    }
}

@Composable
private fun AudioToggleRow(
    label: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PulverfassColors.TextOnDark,
            fontFamily = cinzelDecorative(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PulverfassColors.GoldBright,
                    checkedTrackColor = PulverfassColors.GoldDark,
                    uncheckedThumbColor = PulverfassColors.TextMuted,
                    uncheckedTrackColor = PulverfassColors.SurfaceDark,
                ),
        )
    }
}
