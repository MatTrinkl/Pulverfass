package at.aau.pulverfass.client.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.load_game_description
import at.aau.pulverfass.client.resources.load_game_title
import at.aau.pulverfass.client.ui.map.MapAssetPreloader
import at.aau.pulverfass.client.ui.navigation.Screen
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.client.util.monotonicNowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private const val MIN_LOAD_GAME_SCREEN_MILLIS = 1_600L

/**
 * Lädt die Kartenassets vor dem eigentlichen Spielscreen.
 *
 * Der fachliche GameState kommt bereits über den wiederverwendeten
 * [at.aau.pulverfass.app.lobby.LobbyController].
 *
 * @param minDisplayTimeMillis minimale sichtbare Dauer des Ladebildschirms
 * vor der Navigation; Tests setzen den Wert auf `0`, damit keine künstliche
 * Wartezeit nötig ist
 */
@Composable
fun LoadGameScreen(
    navController: NavController,
    preloadGame: suspend ((loaded: Int, total: Int) -> Unit) -> Unit =
        MapAssetPreloader::preload,
    minDisplayTimeMillis: Long = MIN_LOAD_GAME_SCREEN_MILLIS,
) {
    var loadedSteps by remember { mutableIntStateOf(0) }
    var totalSteps by remember { mutableIntStateOf(1) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val startedAtMillis = monotonicNowMillis()
        runCatching {
            preloadGame { loaded, total ->
                loadedSteps = loaded
                totalSteps = total.coerceAtLeast(1)
            }
        }.onSuccess {
            val remainingMillis =
                minDisplayTimeMillis - (monotonicNowMillis() - startedAtMillis)
            if (remainingMillis > 0) {
                delay(remainingMillis)
            }
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
        modifier =
            Modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(
                                PointerEventPass.Initial,
                            ).changes.forEach { it.consume() }
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.load_game_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.load_game_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = { loadedSteps.toFloat() / totalSteps.toFloat() },
            )
            if (loadError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = loadError.orEmpty())
            }
        }
    }
}
