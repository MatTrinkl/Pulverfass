package at.aau.pulverfass.app.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts

/**
 * Spiel-Aktionsbutton mit PNG-Hintergrund (Icon links im Asset) und im Code
 * gerendertem Label.
 *
 * Die neuen Button-Grafiken enthalten links ein Icon, aber keinen eingebrannten
 * Text mehr. Der Text wird deshalb hier gerendert und nach rechts versetzt: Er
 * beginnt erst ab [textStartFraction] der Buttonbreite, damit das Icon links
 * vollständig sichtbar bleibt, und wird im freien rechten Bereich horizontal wie
 * vertikal zentriert. Aktiver/inaktiver Zustand nutzt unterschiedliche
 * Hintergrund-PNGs ([backgroundActive] / [backgroundInactive]).
 *
 * @param onClick `null` für reine Anzeige-Buttons (z. B. Phasenindikatoren) ohne
 *   Klickverhalten.
 */
@Composable
fun GameActionButton(
    label: String,
    @DrawableRes backgroundActive: Int,
    @DrawableRes backgroundInactive: Int,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    textStartFraction: Float = 0.36f,
    fontSize: TextUnit = 13.sp,
    backgroundScale: ContentScale = ContentScale.FillBounds,
) {
    val background = if (active) backgroundActive else backgroundInactive
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
            painter = painterResource(id = background),
            contentDescription = null,
            contentScale = backgroundScale,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(start = maxWidth * textStartFraction, end = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (active) PulverfassColors.Parchment else PulverfassColors.TextMuted,
                fontSize = fontSize,
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
