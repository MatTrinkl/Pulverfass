package at.aau.pulverfass.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.client.lobby.CharacterDefinition
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import org.jetbrains.compose.resources.painterResource

private val CoinGoldLight = Color(0xFFFFF5C2)
private val CoinGoldDark = Color(0xFF8B6F2A)

/**
 * Kreisförmiges Charakter-Bild mit goldenem Sweep-Gradient-Rand.
 * Wird im Charakter-Picker, der Münz-Animation und dem Countdown-Overlay genutzt.
 */
@Composable
fun CharacterCoin(
    character: CharacterDefinition,
    size: Dp = 96.dp,
    borderWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush =
                    Brush.sweepGradient(
                        listOf(
                            PulverfassColors.GoldCoin,
                            CoinGoldLight,
                            PulverfassColors.GoldCoin,
                            CoinGoldDark,
                            PulverfassColors.GoldCoin,
                        ),
                    ),
                radius = this.size.minDimension / 2f,
                style = Stroke(width = borderWidth.toPx()),
            )
        }
        Image(
            painter = painterResource(character.drawableRes),
            contentDescription = character.displayName,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize(0.84f)
                    .clip(CircleShape),
        )
    }
}
