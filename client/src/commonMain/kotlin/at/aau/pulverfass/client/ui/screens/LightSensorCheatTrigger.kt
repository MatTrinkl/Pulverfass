package at.aau.pulverfass.client.ui.screens

import androidx.compose.runtime.Composable

/**
 * Hört auf den Umgebungslichtsensor und löst den Debug-/Cheatbonus einmalig
 * aus (heller Ausgangswert, danach deutliches Abdunkeln).
 *
 * iOS hat keine öffentliche Lichtsensor-API; die iOS-Implementierung ist ein
 * No-Op — identisch zum Android-Verhalten auf Geräten ohne Lichtsensor.
 *
 * @param enabled `true`, wenn die Verstärkungsphase den Cheat aktuell zulässt.
 * @param onTriggered Callback für den Bonusrequest.
 */
@Composable
expect fun LightSensorCheatTrigger(
    enabled: Boolean,
    onTriggered: () -> Unit,
)
