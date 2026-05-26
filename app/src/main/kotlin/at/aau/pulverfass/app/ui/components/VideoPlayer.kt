package at.aau.pulverfass.app.ui.components

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.VideoView
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicReference

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
    val videoRef = remember { AtomicReference<VideoView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view: VideoView = if (centerCrop) CenterCropVideoView(ctx) else VideoView(ctx)
            videoRef.set(view)

            // Prepared: configure looping/volume and start playback
            view.setOnPreparedListener { mediaPlayer ->
                try {
                    mediaPlayer.isLooping = loop
                    if (muted) mediaPlayer.setVolume(0f, 0f)
                    (view as? CenterCropVideoView)?.setVideoSize(
                        mediaPlayer.videoWidth,
                        mediaPlayer.videoHeight,
                    )
                } catch (t: Throwable) {
                    // swallow; keep prepared flow
                }
                // ensure view visible and start playback
                view.visibility = View.VISIBLE
                view.start()
            }

            view.setOnCompletionListener {
                if (!loop) onCompleted()
            }

            // ensure view visible by default
            view.visibility = View.VISIBLE

            val uri = Uri.parse("android.resource://${ctx.packageName}/$videoResId")
            // register listeners before setting URI to avoid missing callbacks on some devices
            view.setVideoURI(uri)

            view
        },
        update = { view ->
            // If resource changed, reset playback cleanly
            val expected = "android.resource://${view.context.packageName}/$videoResId"
            if (view.tag != expected) {
                view.tag = expected
                try {
                    view.stopPlayback()
                } catch (_: Throwable) {
                }
                view.setVideoURI(Uri.parse(expected))
            }
        },
    )

    DisposableEffect(videoResId, loop, muted) {
        onDispose {
            videoRef.get()?.let { v ->
                try {
                    v.setOnPreparedListener(null)
                    v.setOnCompletionListener(null)
                    v.stopPlayback()
                } catch (_: Throwable) {
                }
            }
            videoRef.set(null)
        }
    }
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
