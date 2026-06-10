package at.aau.pulverfass.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Gemeinsamer Einstiegspunkt der Client-UI für Android und iOS.
 * Wird in den Folgetasks durch die portierte Navigation ersetzt.
 */
@Composable
fun App() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pulverfass Client")
    }
}
