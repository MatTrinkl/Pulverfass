package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.shared.Constants
import kotlinx.coroutines.delay

// ladebildschirm beim start der app
@Composable
fun LoadScreen(navController: NavController) {
    // simuliert eine ladezeit von zwei sekunden bevor zur lobby gewechselt wird
    LaunchedEffect(Unit) {
        delay(2000)
        navController.navigate(Screen.Lobby.route) {
            popUpTo(Screen.Load.route) { inclusive = true }
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val appName = stringResource(id = R.string.app_name)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // zeigt den namen der app & die aktuelle version an
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "v${Constants.APP_VERSION}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "${stringResource(id = R.string.loading)}…")
        }
    }
}
