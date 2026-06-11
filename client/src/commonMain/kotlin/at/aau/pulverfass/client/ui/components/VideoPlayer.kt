package at.aau.pulverfass.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wiederverwendbarer Videoplayer für gebündelte Videos
 * (Android: `res/raw` via VideoView, iOS: App-Bundle via AVPlayer).
 *
 * @param asset abzuspielendes Video.
 * @param onCompleted Callback nach Videoende, nur wenn [loop] deaktiviert ist.
 * @param loop `true`, wenn das Video endlos laufen soll.
 * @param cover `true`, wenn das Video den Container füllen und Überstand clippen soll.
 * @param muted `true`, wenn die Videotonspur stummgeschaltet wird.
 * @param modifier Compose-Modifier für das Parent-Layout.
 */
@Composable
expect fun VideoPlayer(
    asset: VideoAsset,
    onCompleted: () -> Unit = {},
    loop: Boolean = false,
    cover: Boolean = false,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
)
