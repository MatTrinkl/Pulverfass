package at.aau.pulverfass.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.ui.theme.PulverfassSizes

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
