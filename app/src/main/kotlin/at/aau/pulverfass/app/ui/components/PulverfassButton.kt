package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassSizes
import at.aau.pulverfass.app.ui.theme.PulverfassSpacing

/**
 * Pulverfass Primary Button — gefüllter Gold-CTA.
 * Verwendung: WÜRFELN!, NEUES SPIEL, primärer Action pro Screen.
 */
@Composable
fun PulverfassButtonPrimary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = PulverfassSizes.buttonHeightPrimary)
                .widthIn(min = PulverfassSizes.buttonMinWidth),
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PulverfassColors.Gold,
                contentColor = PulverfassColors.SurfaceVoid,
                disabledContainerColor = PulverfassColors.GoldDark,
                disabledContentColor = PulverfassColors.TextMuted,
            ),
        border =
            BorderStroke(
                width = PulverfassSizes.borderDefault,
                color = if (enabled) PulverfassColors.GoldDark else PulverfassColors.SurfaceWood,
            ),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Pulverfass Secondary Button — outlined mit Gold-Border.
 * Verwendung: VERSTÄRKEN, ANGRIFF, VERSCHIEBEN, HAUPTMENÜ.
 */
@Composable
fun PulverfassButtonSecondary(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = PulverfassSizes.buttonHeightSecondary)
                .widthIn(min = PulverfassSizes.buttonMinWidth),
        enabled = enabled,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = PulverfassColors.SurfaceCard,
                contentColor = if (selected) PulverfassColors.GoldBright else PulverfassColors.Gold,
                disabledContainerColor = PulverfassColors.SurfaceCard,
                disabledContentColor = PulverfassColors.TextMuted,
            ),
        border =
            BorderStroke(
                width = PulverfassSizes.borderDefault,
                color =
                    when {
                        !enabled -> PulverfassColors.GoldDark
                        selected -> PulverfassColors.GoldBright
                        else -> PulverfassColors.GoldMuted
                    },
            ),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Pulverfass Danger Button — destruktive Aktionen in danger-rot.
 * Verwendung: RÜCKZUG, RUNDE ENDE, Spiel beenden.
 */
@Composable
fun PulverfassButtonDanger(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = PulverfassSizes.buttonHeightPrimary)
                .widthIn(min = PulverfassSizes.buttonMinWidth),
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PulverfassColors.Danger,
                contentColor = PulverfassColors.TextPrimary,
                disabledContainerColor = PulverfassColors.SurfaceWood,
                disabledContentColor = PulverfassColors.TextMuted,
            ),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1F2A, name = "Buttons – Default States")
@Composable
private fun PulverfassButtonsPreview() {
    AndroidAppTheme {
        Column(
            modifier = Modifier.padding(PulverfassSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PulverfassSpacing.base),
        ) {
            PulverfassButtonPrimary(text = "Würfeln!", onClick = {})
            PulverfassButtonSecondary(text = "Verstärken", onClick = {})
            PulverfassButtonSecondary(text = "Angriff", onClick = {}, selected = true)
            PulverfassButtonDanger(text = "Rückzug", onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1F2A, name = "Buttons – Disabled States")
@Composable
private fun PulverfassButtonsDisabledPreview() {
    AndroidAppTheme {
        Column(
            modifier = Modifier.padding(PulverfassSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PulverfassSpacing.base),
        ) {
            PulverfassButtonPrimary(text = "Würfeln!", onClick = {}, enabled = false)
            PulverfassButtonSecondary(text = "Verstärken", onClick = {}, enabled = false)
            PulverfassButtonDanger(text = "Rückzug", onClick = {}, enabled = false)
        }
    }
}
