package at.aau.pulverfass.client.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.ui.theme.cinzelDecorative

@Composable
fun PulverfassTitleText(
    text: String,
    fontSize: TextUnit,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text,
        fontFamily = cinzelDecorative(),
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = PulverfassColors.GoldBright,
        letterSpacing = letterSpacing,
    )
}
