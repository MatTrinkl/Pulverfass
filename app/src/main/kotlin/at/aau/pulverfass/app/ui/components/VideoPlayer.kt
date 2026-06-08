package at.aau.pulverfass.app.ui.components

import android.content.Context
import android.media.MediaPlayer
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
 * Wiederverwendbarer Videoplayer für lokale `res/raw`-Videos.
 *
 * @param videoResId Resource-ID aus `res/raw`.
 * @param onCompleted Callback nach Videoende, nur wenn [loop] deaktiviert ist.
 * @param loop `true`, wenn das Video endlos laufen soll.
 * @param cover `true`, wenn das Video den Container füllen und Überstand clippen soll.
 * @param muted `true`, wenn die Videotonspur stummgeschaltet wird.
 * @param modifier Compose-Modifier für das Parent-Layout.
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
            view.setOnPreparedListener { mediaPlayer ->
                runCatching { configureMediaPlayer(mediaPlayer, view, loop, muted, centerCrop) }
                view.visibility = View.VISIBLE
                view.start()
            }
            view.setOnCompletionListener { if (!loop) onCompleted() }
            view.visibility = View.VISIBLE
            view.setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$videoResId"))
            view
        },
        update = { view -> resetVideoUri(view, videoResId) },
    )
    DisposableEffect(videoResId, loop, muted) {
        onDispose { releaseVideoView(videoRef) }
    }
}

private fun configureMediaPlayer(
    mediaPlayer: MediaPlayer,
    view: VideoView,
    loop: Boolean,
    muted: Boolean,
    centerCrop: Boolean,
) {
    mediaPlayer.isLooping = loop
    if (muted) mediaPlayer.setVolume(0f, 0f)
    (view as? CenterCropVideoView)?.setVideoSize(
        mediaPlayer.videoWidth,
        mediaPlayer.videoHeight,
    )
}

private fun resetVideoUri(
    view: VideoView,
    @RawRes videoResId: Int,
) {
    val expected = "android.resource://${view.context.packageName}/$videoResId"
    if (view.tag == expected) return
    view.tag = expected
    runCatching { view.stopPlayback() }
    view.setVideoURI(Uri.parse(expected))
}

private fun releaseVideoView(videoRef: AtomicReference<VideoView?>) {
    videoRef.get()?.let { v ->
        runCatching {
            v.setOnPreparedListener(null)
            v.setOnCompletionListener(null)
            v.stopPlayback()
        }
    }
    videoRef.set(null)
}

private class CenterCropVideoView(context: Context) : VideoView(context) {
    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(
        width: Int,
        height: Int,
    ) {
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (videoWidth == 0 || videoHeight == 0) {
            setMeasuredDimension(width, height)
            return
        }
        val videoAspect = videoWidth.toFloat() / videoHeight
        val viewAspect = width.toFloat() / height
        if (videoAspect > viewAspect) {
            setMeasuredDimension((height * videoAspect).toInt(), height)
        } else {
            setMeasuredDimension(width, (width / videoAspect).toInt())
        }
    }
}
