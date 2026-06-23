package at.aau.pulverfass.client.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.ui.theme.cinzelDecorative
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class GameActionButtonStyle(
    val backgroundActive: DrawableResource,
    val backgroundInactive: DrawableResource,
    val backgroundScale: ContentScale = ContentScale.FillBounds,
    val textStartFraction: Float = 0.36f,
    val fontSize: TextUnit = 13.sp,
)

@Composable
fun GameActionButton(
    label: String,
    style: GameActionButtonStyle,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val background = if (active) style.backgroundActive else style.backgroundInactive
    val interactionModifier =
        if (onClick != null) {
            Modifier.clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    BoxWithConstraints(
        modifier =
            modifier
                .alpha(if (enabled) 1f else 0.45f)
                .then(interactionModifier),
    ) {
        Image(
            painter = painterResource(background),
            contentDescription = null,
            contentScale = style.backgroundScale,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(start = maxWidth * style.textStartFraction, end = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (active) PulverfassColors.Parchment else PulverfassColors.TextMuted,
                fontSize = style.fontSize,
                fontFamily = cinzelDecorative(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
