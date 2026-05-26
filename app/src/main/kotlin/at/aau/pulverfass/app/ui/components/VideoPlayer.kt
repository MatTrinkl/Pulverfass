package at.aau.pulverfass.app.ui.components

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Wiederverwendbarer Video-Player für lokale res/raw Videos.
 *
 * @param videoResId Resource-ID aus res/raw
 * @param onCompleted Wird gerufen wenn Video zu Ende (nur ohne loop)
 * @param loop true → Endlos-Schleife
 * @param cover true → Video füllt Container (CSS object-fit: cover), overflow geclippt
 * @param muted true → Video-Audio stummgeschaltet (für BG-Videos unter Music)
 * @param modifier Compose-Modifier für Parent-Layout
 */
@Composable
fun VideoPlayer(
    @RawRes videoResId: Int,
    onCompleted: () -> Unit = {},
    loop: Boolean = false,
    cover: Boolean = false,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (cover) {
        Box(
            modifier = modifier.clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            VideoViewInterop(
                videoResId = videoResId,
                onCompleted = onCompleted,
                loop = loop,
                centerCrop = true,
                muted = muted,
            )
        }
    } else {
        VideoViewInterop(
            videoResId = videoResId,
            onCompleted = onCompleted,
            loop = loop,
            centerCrop = false,
            muted = muted,
            modifier = modifier,
        )
    }
}

@Composable
private fun VideoViewInterop(
    @RawRes videoResId: Int,
    onCompleted: () -> Unit,
    loop: Boolean,
    centerCrop: Boolean,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view: VideoView =
                if (centerCrop) CenterCropVideoView(ctx) else VideoView(ctx)
            view.apply {
                val uri = Uri.parse("android.resource://${ctx.packageName}/$videoResId")
                setVideoURI(uri)
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = loop
                    if (muted) {
                        mediaPlayer.setVolume(0f, 0f)
                    }
                    (view as? CenterCropVideoView)?.setVideoSize(
                        mediaPlayer.videoWidth,
                        mediaPlayer.videoHeight,
                    )
                    // start playback once prepared to ensure settings (loop/volume) applied
                    view.start()
                }
                setOnCompletionListener {
                    if (!loop) onCompleted()
                }
            }
        },
    )
}

private class CenterCropVideoView(context: Context) : VideoView(context) {
    private var sourceWidth = 0
    private var sourceHeight = 0

    fun setVideoSize(
        width: Int,
        height: Int,
    ) {
        if (sourceWidth == width && sourceHeight == height) return
        sourceWidth = width
        sourceHeight = height
        requestLayout()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (sourceWidth == 0 || sourceHeight == 0) {
            setMeasuredDimension(parentWidth, parentHeight)
            return
        }
        val videoRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        val parentRatio = parentWidth.toFloat() / parentHeight.toFloat()
        if (videoRatio > parentRatio) {
            setMeasuredDimension((parentHeight * videoRatio).toInt(), parentHeight)
        } else {
            setMeasuredDimension(parentWidth, (parentWidth / videoRatio).toInt())
        }
    }
}
