package at.aau.pulverfass.client.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun LightSensorCheatTrigger(
    enabled: Boolean,
    onTriggered: () -> Unit,
    modifier: Modifier,
) {
    // iOS bietet keinen öffentlichen Zugriff auf den Umgebungslichtsensor.
}
