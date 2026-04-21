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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lädt aktuell die Demo-Karte vor dem eigentlichen Spielscreen.
 *
 * Später kann hier derselbe Einstieg für echten Game-State aus Lobby, Server
 * und Shared-Modul verwendet werden.
 */
@Composable
fun LoadGameScreen(
    navController: NavController,
    preloadGame: suspend (Resources, (loaded: Int, total: Int) -> Unit) -> Unit =
        MapAssetPreloader::preload,
) {
    val resources = LocalContext.current.resources
    var loadedSteps by remember { mutableIntStateOf(0) }
    var totalSteps by remember { mutableIntStateOf(1) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            preloadGame(resources) { loaded, total ->
                loadedSteps = loaded
                totalSteps = total.coerceAtLeast(1)
            }
        }.onSuccess {
            withContext(Dispatchers.Main.immediate) {
                navController.navigate(Screen.Game.route) {
                    popUpTo(Screen.LoadGame.route) { inclusive = true }
                }
            }
        }.onFailure { error ->
            if (error is CancellationException) {
                throw error
            }
            withContext(Dispatchers.Main.immediate) {
                loadError = error.message ?: "Spiel konnte nicht geladen werden."
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(id = R.string.load_game_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.load_game_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = { loadedSteps.toFloat() / totalSteps.toFloat() },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text =
                    loadError
                        ?: "${stringResource(id = R.string.loading)} $loadedSteps/$totalSteps",
            )
        }
    }
}
