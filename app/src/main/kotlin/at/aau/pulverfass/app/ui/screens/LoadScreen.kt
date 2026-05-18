package at.aau.pulverfass.app.ui.screens

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.map.MapAssetPreloader
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.shared.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val DEFAULT_MIN_DISPLAY_TIME_MS = 3500L
private const val DEFAULT_LOADING_TEXT_INTERVAL_MS = 2200L

internal val LoadingTexts =
    listOf(
        "Hisse die Segel...",
        "Schartnerbomb building...",
        "Pulver ins Fass laden...",
        "Karten entrollen...",
        "Würfel polieren...",
        "Mannschaft an Deck rufen...",
        "Anker lichten...",
        "Auf hohe See stechen...",
        "Kanonen scharfmachen...",
        "Rum an Bord verstauen...",
    )

/**
 * Ladebildschirm beim Start der App mit loopable Pulverfass-Video als BG.
 *
 * Navigation passiert erst wenn Preload erfolgreich UND die min-display-time
 * abgelaufen ist — damit das Video sichtbar bleibt bei schnellem Preload.
 *
 * @param navController Navigation in die Lobby nach erfolgreichem Asset-Preload.
 * @param preloadAssets injizierbare Preload-Funktion für Tests.
 * @param minDisplayTimeMs Minimale Anzeigedauer (für Test-Override).
 * @param loadingTextIntervalMs Intervall für Loading-Text-Rotation in ms.
 * @param loadingTexts Liste der Loading-Texte zum Durchrotieren.
 * @param background Slot für den Background; default = Pulverfass-Video-Loop.
 *   Tests übergeben ein leeres Lambda um die VideoView zu umgehen.
 */
@Composable
fun LoadScreen(
    navController: NavController,
    preloadAssets: suspend (Resources, (loaded: Int, total: Int) -> Unit) -> Unit =
        MapAssetPreloader::preload,
    minDisplayTimeMs: Long = DEFAULT_MIN_DISPLAY_TIME_MS,
    loadingTextIntervalMs: Long = DEFAULT_LOADING_TEXT_INTERVAL_MS,
    loadingTexts: List<String> = LoadingTexts,
    background: @Composable () -> Unit = {
        VideoPlayer(
            videoResId = R.raw.pulverfass_loading,
            loop = true,
            modifier = Modifier.fillMaxSize(),
        )
    },
) {
    val resources = LocalContext.current.resources
    var loadedAssets by remember { mutableIntStateOf(0) }
    var totalAssets by remember { mutableIntStateOf(1) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var assetsReady by remember { mutableStateOf(false) }
    var minTimeElapsed by remember { mutableStateOf(false) }
    var currentTextIndex by remember { mutableIntStateOf(0) }

    /*
     * Loading-Text-Rotation. Default-Intervall ist 2.2s — kurz genug für
     * Lebendigkeit, lang genug zum Lesen.
     */
    LaunchedEffect(loadingTexts, loadingTextIntervalMs) {
        if (loadingTexts.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(loadingTextIntervalMs)
            currentTextIndex = (currentTextIndex + 1) % loadingTexts.size
        }
    }

    /*
     * Min-Display-Time-Gate. Verhindert dass schneller Preload den Splash
     * sofort wieder wegschiebt — das Video soll auch wirklich sichtbar sein.
     */
    LaunchedEffect(minDisplayTimeMs) {
        delay(minDisplayTimeMs)
        minTimeElapsed = true
    }

    /*
     * Preload-Logik bleibt 1:1 wie vorher. Kaputte Assets fallen sofort hier
     * auf statt später als leere Karte im GameScreen.
     */
    LaunchedEffect(Unit) {
        runCatching {
            preloadAssets(resources) { loaded, total ->
                loadedAssets = loaded
                totalAssets = total.coerceAtLeast(1)
            }
        }.onSuccess {
            assetsReady = true
        }.onFailure { error ->
            if (error is CancellationException) {
                throw error
            }
            withContext(Dispatchers.Main.immediate) {
                loadError = error.message ?: "Assets konnten nicht geladen werden."
            }
        }
    }

    /*
     * Navigation erst wenn beide Gates offen sind und kein Error vorliegt.
     */
    LaunchedEffect(assetsReady, minTimeElapsed, loadError) {
        if (assetsReady && minTimeElapsed && loadError == null) {
            withContext(Dispatchers.Main.immediate) {
                navController.navigate(Screen.Lobby.route) {
                    popUpTo(Screen.Load.route) { inclusive = true }
                }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid)
                .testTag("LoadScreen"),
        contentAlignment = Alignment.Center,
    ) {
        background()

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    PulverfassColors.SurfaceVoid.copy(alpha = 0.35f),
                                    PulverfassColors.SurfaceVoid.copy(alpha = 0.75f),
                                ),
                        ),
                    ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = PulverfassColors.Gold,
            )
            Text(
                text = "v${Constants.APP_VERSION}",
                style = MaterialTheme.typography.bodyMedium,
                color = PulverfassColors.TextMuted,
            )
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = { loadedAssets.toFloat() / totalAssets.toFloat() },
                color = PulverfassColors.Gold,
                trackColor = PulverfassColors.SurfaceCard,
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .testTag("LoadProgress"),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text =
                    loadError
                        ?: loadingTexts.getOrElse(currentTextIndex) { "" },
                color =
                    if (loadError != null) {
                        PulverfassColors.DangerBright
                    } else {
                        PulverfassColors.TextPrimary
                    },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(horizontal = 32.dp)
                        .testTag("LoadingText"),
            )
        }
    }
}
