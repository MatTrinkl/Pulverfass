package at.aau.pulverfass.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import kotlinx.coroutines.delay

private const val MAX_INTRO_DURATION_MS = 10_000L

/**
 * Gabumon Studios Intro-Screen beim App-Start.
 *
 * Spielt das Studio-Intro-Video und navigiert danach zum LoadScreen. Der
 * immersive Vollbildmodus wird bewusst von `MainActivity` für die gesamte App
 * gehalten: Würde dieser Screen beim Verlassen Systembars wieder einblenden,
 * wären sie während des direkt folgenden LoadScreens sichtbar.
 *
 * - Click anywhere → sofort skip
 * - Video-Ende → auto-navigate zu Load
 * - Safety-Fallback: nach 10s erzwingt der Screen die Navigation,
 *   falls das Video hängt oder fehlt
 *
 * @param navController Navigation Controller für die Weiterleitung
 */
@Composable
fun StudioIntroScreen(navController: NavController) {
    var hasNavigated by remember { mutableStateOf(false) }
    val context = LocalContext.current

    /*
     * navigateNext ist idempotent — egal wie oft sie aufgerufen wird
     * (click + onCompleted + safety timeout), navigiert nur EINMAL.
     */
    val navigateNext = {
        if (!hasNavigated) {
            hasNavigated = true
            navController.navigate(Screen.Load.route) {
                popUpTo(Screen.StudioIntro.route) { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val mp = MediaPlayer.create(context, R.raw.music_studio_intro)
            mp?.start()
        } catch (_: Exception) {
        }
    }

    // safety fallback falls video hängt oder nicht geladen wird
    LaunchedEffect(Unit) {
        delay(MAX_INTRO_DURATION_MS)
        navigateNext()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { navigateNext() },
                ),
    ) {
        VideoPlayer(
            videoResId = R.raw.video_studio_intro,
            onCompleted = { navigateNext() },
            loop = false,
            cover = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
