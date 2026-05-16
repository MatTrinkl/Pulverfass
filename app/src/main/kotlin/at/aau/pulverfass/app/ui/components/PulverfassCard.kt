package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassSizes
import at.aau.pulverfass.app.ui.theme.PulverfassSpacing

/**
 * Pulverfass Standard Card — dunkler Card-Background mit Pergament-Edge.
 */
@Composable
fun PulverfassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = PulverfassColors.SurfaceCard,
                contentColor = PulverfassColors.TextPrimary,
            ),
        border =
            BorderStroke(
                width = PulverfassSizes.borderThin,
                color = PulverfassColors.ParchmentEdge,
            ),
        shape = MaterialTheme.shapes.small,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        content()
    }
}

/**
 * Pulverfass Parchment Card — warmes Pergament-BG für Inputs, Schilder, Modals.
 */
@Composable
fun PulverfassParchmentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = PulverfassColors.Parchment,
                contentColor = PulverfassColors.TextOnParchment,
            ),
        border =
            BorderStroke(
                width = PulverfassSizes.borderDefault,
                color = PulverfassColors.ParchmentEdge,
            ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1F2A, name = "Cards – All Variants")
@Composable
internal fun PulverfassCardsPreview() {
    AndroidAppTheme {
        Column(
            modifier = Modifier.padding(PulverfassSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PulverfassSpacing.base),
        ) {
            PulverfassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(PulverfassSpacing.base)) {
                    Text(
                        text = "Standard Dark Card",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Container auf dunklem Hintergrund mit Pergament-Edge.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            PulverfassParchmentCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(PulverfassSpacing.base)) {
                    Text(
                        text = "Parchment Card",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Für Inputs, Schilder und Modals.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
