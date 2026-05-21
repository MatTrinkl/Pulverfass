package at.aau.pulverfass.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.theme.PulverfassColors

/**
 * Main-Menu Screen.
 * Design: Branding (Logo & Title) auf der linken Seite, Navigations-Buttons auf der rechten Seite.
 * Hintergrund: Video-Loop.
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

        // Overlay Gradient for visibility
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    PulverfassColors.SurfaceVoid.copy(alpha = 0.5f),
                                    PulverfassColors.SurfaceVoid.copy(alpha = 0.1f),
                                    PulverfassColors.SurfaceVoid.copy(alpha = 0.6f),
                                ),
                        ),
                    ),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Branding Area (Left)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "branding")
                val sway by infiniteTransition.animateFloat(
                    initialValue = -2f,
                    targetValue = 2f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(4000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "sway",
                )

                // Game Logo Image
                Image(
                    painter = painterResource(id = R.drawable.gamelogo),
                    contentDescription = "Pulverfass Game Logo",
                    modifier =
                        Modifier
                            .size(280.dp)
                            .graphicsLayer { rotationZ = sway }
                            .testTag("GameLogo"),
                )
            }

            // Navigation Area (Right)
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MenuButton("START", onStartClick, Modifier.testTag("MenuButton_Start"))
                MenuButton("OPTIONS", onOptionsClick, Modifier.testTag("MenuButton_Options"))
                MenuButton("EXIT", onExitClick, Modifier.testTag("MenuButton_Exit"))
            }
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .width(220.dp)
                .border(
                    width = 2.dp,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(PulverfassColors.GoldBright, PulverfassColors.GoldDark),
                        ),
                    shape = RoundedCornerShape(8.dp),
                ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PulverfassColors.SurfaceDark.copy(alpha = 0.85f),
                contentColor = PulverfassColors.Gold,
            ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
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
