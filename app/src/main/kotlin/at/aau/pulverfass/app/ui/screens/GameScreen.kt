package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import at.aau.pulverfass.app.R

// hauptbildschirm für das eigentliche spielgeschehen
@Composable
fun GameScreen() {
    // zentriert den inhalt auf dem bildschirm
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // platzhalter für die spätere spielkarte
            Text(
                text = stringResource(id = R.string.game_map_placeholder),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
