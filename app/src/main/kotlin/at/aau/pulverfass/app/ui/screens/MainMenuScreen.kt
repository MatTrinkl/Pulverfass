package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.theme.PulverfassColors

/**
 * Main-Menu Screen mit gamelogo.png groß über den Buttons.
 * Video-Background (menuvid.mp4), kein freischwebendes Logo mehr.
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
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid)
                .testTag("MainMenuScreen"),
    ) {
        // Video Background
        background()

        // Dark overlay für Kontrast zwischen Logo und Video
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.4f)),
        )

        // Main Content: Logo groß über Buttons
// Main Content: Logo groß über Buttons
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Vertikales Padding reduziert, damit mehr Platz für die Buttons ist
                    .padding(horizontal = 48.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center, // Zentriert jetzt alles richtig, da es reinpasst
        ) {
            // Pulverfass Logo — verkleinert für Landscape!
            Image(
                painter = painterResource(id = R.drawable.gamelogo),
                contentDescription = "Pulverfass Logo",
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(120.dp) // FIX 1: Vorher 180.dp (viel zu groß für Landscape)
                        .testTag("GameLogo"),
            )

            Spacer(modifier = Modifier.height(24.dp)) // FIX 2: Spacer etwas verkleinert

            // Button-Column unter dem Logo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.width(280.dp),
            ) {
                MainButton(
                    text = "START",
                    onClick = onStartClick,
                    modifier = Modifier.fillMaxWidth().testTag("MenuButton_Start"),
                )
                MainButton(
                    text = "OPTIONS",
                    onClick = onOptionsClick,
                    modifier = Modifier.fillMaxWidth().testTag("MenuButton_Options"),
                )
                MainButton(
                    text = "EXIT",
                    onClick = onExitClick,
                    modifier = Modifier.fillMaxWidth().testTag("MenuButton_Exit"),
                )
            }
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
