package at.aau.pulverfass.app.ui.screens

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.map.MapAssetPreloader
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.shared.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Ladebildschirm beim Start der App.
@Composable
fun LoadScreen(
    navController: NavController,
    preloadAssets: suspend (Resources, (loaded: Int, total: Int) -> Unit) -> Unit =
        MapAssetPreloader::preload,
) {
    val resources = LocalContext.current.resources
    var loadedAssets by remember { mutableIntStateOf(0) }
    var totalAssets by remember { mutableIntStateOf(1) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Wechselt erst weiter, wenn die App-Assets tatsächlich dekodierbar sind.
    LaunchedEffect(Unit) {
        runCatching {
            preloadAssets(resources) { loaded, total ->
                loadedAssets = loaded
                totalAssets = total.coerceAtLeast(1)
            }
        }.onSuccess {
            withContext(Dispatchers.Main.immediate) {
                navController.navigate(Screen.Lobby.route) {
                    popUpTo(Screen.Load.route) { inclusive = true }
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) {
                throw error
            }
            withContext(Dispatchers.Main.immediate) {
                loadError = error.message ?: "Assets konnten nicht geladen werden."
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val appName = stringResource(id = R.string.app_name)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Zeigt den Namen der App und die aktuelle Version an.
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "v${Constants.APP_VERSION}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = { loadedAssets.toFloat() / totalAssets.toFloat() },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text =
                    loadError
                        ?: "${stringResource(id = R.string.loading)} $loadedAssets/$totalAssets",
            )
        }
    }
}
