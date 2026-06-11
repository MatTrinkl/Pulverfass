package at.aau.pulverfass.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.brand_pulverfass_logo
import at.aau.pulverfass.client.ui.components.ExitConfirmationDialog
import at.aau.pulverfass.client.ui.components.MainButton
import at.aau.pulverfass.client.ui.components.VideoAsset
import at.aau.pulverfass.client.ui.components.VideoPlayer
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import org.jetbrains.compose.resources.painterResource

/**
 * Hauptmenü mit Video-Hintergrund, Logo und den primären Einstiegsaktionen.
 *
 * Der Exit-Button beendet die App nicht direkt, sondern öffnet zuerst den
 * Bestätigungsdialog. [background] ist injizierbar, damit UI-Tests keinen
 * echten Videoplayer starten müssen.
 *
 * @param onStartClick Navigation in den Lobby-Einstieg.
 * @param onOptionsClick Navigation in die globalen Optionen.
 * @param onExitClick finale Exit-Aktion nach Bestätigung oder
ull, wenn die
 *   Plattform kein programmatisches Beenden unterstützt (iOS) — der Button entfällt dann.
 * @param modifier äußerer Modifier für Einbettung und Tests.
 * @param background austauschbarer Hintergrundslot für Tests und Preview.
 */
@Composable
fun MainMenuScreen(
    onStartClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onExitClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit = {
        VideoPlayer(
            asset = VideoAsset.MAIN_MENU_BACKGROUND,
            loop = true,
            cover = true,
            muted = true,
            modifier = Modifier.fillMaxSize(),
        )
    },
) {
    var showExitDialog by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(PulverfassColors.SurfaceVoid)
                .testTag("MainMenuScreen"),
    ) {
        // Bewegter Hintergrund; in Tests kann stattdessen ein statisches Box-Stub genutzt werden.
        background()

        // Lesbarkeitsverlauf: links bleibt das Video sichtbar, rechts tragen Logo und Buttons.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                PulverfassColors.SurfaceVoid.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
        )

        // Logo und Buttons bleiben rechts, damit der Videohintergrund links als Motiv wirkt.
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 64.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.brand_pulverfass_logo),
                    contentDescription = "Pulverfass Logo",
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(120.dp)
                            .testTag("GameLogo"),
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.width(IntrinsicSize.Max),
                ) {
                    MainButton(
                        text = "START",
                        onClick = onStartClick,
                        modifier = Modifier.fillMaxWidth().testTag("MenuButton_Start"),
                    )
                    MainButton(
                        text = "OPTIONEN",
                        onClick = onOptionsClick,
                        modifier = Modifier.fillMaxWidth().testTag("MenuButton_Options"),
                    )
                    if (onExitClick != null) {
                        MainButton(
                            text = "BEENDEN",
                            onClick = { showExitDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("MenuButton_Exit"),
                        )
                    }
                }
            }
        }

        // Exit benötigt Bestätigung, weil der Activity-Finish nicht rückgängig gemacht werden kann.
        if (showExitDialog && onExitClick != null) {
            ExitConfirmationDialog(
                onConfirm = onExitClick,
                onDismiss = { showExitDialog = false },
            )
        }
    }
}
