package at.aau.pulverfass.client.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.ui_button_wood
import at.aau.pulverfass.client.ui.screenWidthDp
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.ui.theme.cinzelDecorative
import org.jetbrains.compose.resources.painterResource

/**
 * Pulverfass-Standard-Button mit Wood-Skin Background.
 * Verwendet `ui_button_wood.png` als stretchable BG.
 */
@Composable
fun MainButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 16,
    minHeight: Dp = if (text.contains('\n')) 92.dp else 68.dp,
) {
    val screenWidth = screenWidthDp()
    val compactScreen = screenWidth < 430.dp
    val effectiveFontSize =
        when {
            compactScreen -> (fontSize - 3).coerceAtLeast(12)
            screenWidth < 620.dp -> (fontSize - 1).coerceAtLeast(13)
            else -> fontSize
        }
    val horizontalPadding =
        when {
            compactScreen -> 12.dp
            screenWidth < 620.dp -> 18.dp
            else -> 24.dp
        }
    val letterSpacing =
        when {
            compactScreen -> 1.2.sp
            screenWidth < 620.dp -> 2.sp
            else -> 3.sp
        }

    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = minHeight)
                .heightIn(min = minHeight)
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(
                    enabled = enabled,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.ui_button_wood),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
        val labelLines = text.uppercase().split('\n')
        if (labelLines.size > 1) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(PaddingValues(horizontal = horizontalPadding, vertical = 12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                labelLines.forEach { line ->
                    Text(
                        text = line,
                        color = PulverfassColors.Parchment,
                        fontSize = effectiveFontSize.sp,
                        fontFamily = cinzelDecorative(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = letterSpacing,
                        textAlign = TextAlign.Center,
                        lineHeight = (effectiveFontSize + 2).sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        } else {
            Text(
                text = labelLines.first(),
                color = PulverfassColors.Parchment,
                fontSize = effectiveFontSize.sp,
                fontFamily = cinzelDecorative(),
                fontWeight = FontWeight.Bold,
                letterSpacing = letterSpacing,
                textAlign = TextAlign.Center,
                lineHeight = (effectiveFontSize + 4).sp,
                maxLines = 1,
                softWrap = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(PaddingValues(horizontal = horizontalPadding, vertical = 14.dp)),
            )
        }
    }
}
