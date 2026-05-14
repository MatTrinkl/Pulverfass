package at.aau.pulverfass.app.ui.screens

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
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import kotlinx.coroutines.delay

private const val MAX_INTRO_DURATION_MS = 5_000L

/**
 * Gabumon Studios Intro-Screen beim App-Start.
 *
 * Spielt das Studio-Intro-Video ab, navigiert danach zum LoadScreen.
 * - Click anywhere → sofort skip
 * - Video-Ende → auto-navigate
 * - Safety-Fallback: nach 5s erzwingt der Screen die Navigation,
 *   falls das Video hängt oder fehlt
 *
 * @param navController Navigation Controller für die Weiterleitung
 */
@Composable
fun StudioIntroScreen(navController: NavController) {
    var hasNavigated by remember { mutableStateOf(false) }

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
            videoResId = R.raw.gabumon_intro,
            onCompleted = { navigateNext() },
            loop = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
