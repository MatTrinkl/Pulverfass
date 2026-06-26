package at.aau.pulverfass.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.client.ui.theme.PulverfassColors

/**
 * Pergament-Style Container für Listen / Content im Pulverfass-Theme.
 * Gold-Border, Parchment-BG.
 */
@Composable
fun PulverfassPanel(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .border(
                    width = 2.dp,
                    brush =
                        Brush.verticalGradient(
                            listOf(PulverfassColors.GoldBright, PulverfassColors.GoldDark),
                        ),
                    shape = RoundedCornerShape(12.dp),
                )
                .background(
                    color = PulverfassColors.Parchment,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(contentPadding),
        content = content,
    )
}
