package at.aau.pulverfass.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import at.aau.pulverfass.client.ui.theme.PulverfassColors

@Composable
fun LobbyVideoBackground() {
    VideoPlayer(
        asset = VideoAsset.LOBBY_BACKGROUND,
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
