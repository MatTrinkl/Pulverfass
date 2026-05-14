package at.aau.pulverfass.app.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Wiederverwendbarer Video-Player für lokale res/raw Videos.
 *
 * Wrappt Android's eingebauten VideoView via AndroidView-interop.
 * Verwendung: Studio-Intro-Video (mit onCompleted), Loading-BG-Video (mit loop=true).
 *
 * @param videoResId Resource-ID aus res/raw (z.B. R.raw.gabumon_intro)
 * @param onCompleted Wird gerufen wenn Video zu Ende (nur relevant ohne loop)
 * @param loop Wenn true → Endlos-Schleife
 * @param modifier Compose-Modifier für Parent-Layout
 */
@Composable
fun VideoPlayer(
    @RawRes videoResId: Int,
    onCompleted: () -> Unit = {},
    loop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                val uri = Uri.parse("android.resource://${ctx.packageName}/$videoResId")
                setVideoURI(uri)
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = loop
                }
                setOnCompletionListener {
                    if (!loop) onCompleted()
                }
                start()
            }
        },
    )
}
