package at.aau.pulverfass.app.ui.screens

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.components.ExitConfirmationDialog
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.theme.PulverfassColors

/**
 * Main-Menu Screen mit gamelogo.png groß über den Buttons.
 * Video-Background (menuvid.mp4).
 * EXIT zeigt Bestätigungsdialog via ExitConfirmationDialog (lobbylist.png Parchment).
 */
@Composable
fun MainMenuScreen(
    onStartClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onExitClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit = {
        VideoPlayer(
            videoResId = R.raw.menuvid,
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
        // Video Background
        background()

        // Horizontal gradient overlay: transparent left → dark right
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

        // Logo + Buttons auf der rechten Seite
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
                    painter = painterResource(id = R.drawable.gamelogo),
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
                    MainButton(
                        text = "BEENDEN",
                        onClick = { showExitDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("MenuButton_Exit"),
                    )
                }
            }
        }

        // Exit Confirmation Overlay
        if (showExitDialog) {
            ExitConfirmationDialog(
                onConfirm = onExitClick,
                onDismiss = { showExitDialog = false },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 500)
@Composable
internal fun MainMenuScreenPreview() {
    MainMenuScreen(
        onStartClick = {},
        onOptionsClick = {},
        onExitClick = {},
        background = {
            Box(Modifier.fillMaxSize().background(PulverfassColors.SurfaceVoid))
        },
    )
}
