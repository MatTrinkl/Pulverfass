package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts

/**
 * Vollbild-Overlay-Dialog mit Parchment-BG (lobbylist.png).
 *
 * Zeigt eine Bestätigungsfrage mit JA/NEIN MainButtons.
 * Klick auf den dunklen Overlay-Hintergrund = dismiss.
 */
@Composable
fun ExitConfirmationDialog(
    title: String = "SPIEL BEENDEN?",
    message: String = "MÖCHTEST DU WIRKLICH\nDAS SPIEL BEENDEN?",
    confirmText: String = "JA",
    dismissText: String = "NEIN",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.75f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Parchment Card — clickable consumed to prevent dismiss-through
        Box(
            modifier =
                Modifier
                    .width(420.dp)
                    .height(260.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // Parchment background image
            Image(
                painter = painterResource(R.drawable.lobbylist),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            // Dialog content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = title,
                    color = PulverfassColors.TextOnParchment,
                    fontFamily = PulverfassFonts.CinzelDecorative,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    color = PulverfassColors.TextOnParchment,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    MainButton(
                        text = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.width(120.dp),
                    )
                    MainButton(
                        text = dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}
