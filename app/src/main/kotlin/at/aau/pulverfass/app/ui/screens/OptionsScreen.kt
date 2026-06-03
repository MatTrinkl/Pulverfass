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
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.MainInputField
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts

/**
 * Options-Screen — Username ändern, Music/SFX Toggles.
 *
 * Video-BG: options_vid.mp4, Musik: settings.mp3 (via MainActivity route-based playback).
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
        // Video Background
        VideoPlayer(
            videoResId = R.raw.options_vid,
            loop = true,
            cover = true,
            muted = true,
            modifier = Modifier.fillMaxSize(),
        )
        // Dark overlay für Lesbarkeit
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.6f)),
        )

        // Content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 48.dp),
                    .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Title
            Text(
                text = "OPTIONEN",
                color = PulverfassColors.GoldBright,
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(40.dp))

            // Username
            MainInputField(
                value = playerName,
                onValueChange = { if (it.length <= 20) onPlayerNameChange(it) },
                onValueChange = onPlayerNameChange,
                placeholder = "SPIELERNAME",
                modifier = Modifier.fillMaxWidth(0.5f),
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Audio Controls Card
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

            // Back
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
            fontFamily = PulverfassFonts.CinzelDecorative,
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
