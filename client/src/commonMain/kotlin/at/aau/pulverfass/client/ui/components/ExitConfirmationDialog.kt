package at.aau.pulverfass.client.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.ui_lobby_roster_panel
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.ui.theme.cinzelDecorative
import org.jetbrains.compose.resources.painterResource

/**
 * Vollbild-Overlay-Dialog mit Pergamenthintergrund.
 *
 * Der dunkle Hintergrund kann den Dialog schließen. Klicks auf die
 * Pergamentfläche werden abgefangen, damit keine Dismiss-Aktion durch die
 * Dialogkarte hindurch ausgelöst wird.
 *
 * @param title Überschrift der Bestätigung.
 * @param message erklärender Dialogtext.
 * @param confirmText Text des bestätigenden Buttons.
 * @param dismissText Text des abbrechenden Buttons.
 * @param onConfirm Callback für die bestätigte Aktion.
 * @param onDismiss Callback für Abbruch oder Klick auf den Hintergrund.
 * @param modifier Modifier für Tests und Einbettung.
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
        // Die Pergamentkarte konsumiert Klicks, damit sie den Dialog nicht schließt.
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
            // Pergamentbild als flexible Dialogfläche.
            Image(
                painter = painterResource(Res.drawable.ui_lobby_roster_panel),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            // Dialoginhalt mit einheitlichen Hauptbuttons.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = title,
                    color = PulverfassColors.TextOnParchment,
                    fontFamily = cinzelDecorative(),
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
