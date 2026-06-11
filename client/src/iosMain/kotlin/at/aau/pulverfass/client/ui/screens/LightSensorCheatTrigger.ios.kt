package at.aau.pulverfass.client.ui.screens

import androidx.compose.runtime.Composable

@Composable
actual fun LightSensorCheatTrigger(
    enabled: Boolean,
    onTriggered: () -> Unit,
) {
    /*
     * Kein öffentlicher Lichtsensor-Zugriff auf iOS — der Cheat bleibt
     * deaktiviert, wie auf Android-Geräten ohne Lichtsensor.
     */
}
