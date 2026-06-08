package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.theme.PulverfassColors

@Composable
fun LobbyVideoBackground() {
    VideoPlayer(
        videoResId = R.raw.video_lobby_background,
        loop = true,
        cover = true,
        muted = true,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.5f)),
    )
}
