package at.aau.pulverfass.client.ui.components

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
import at.aau.pulverfass.client.R
import java.util.concurrent.atomic.AtomicReference

/**
 * Android-Implementierung über VideoView, 1:1 aus dem app-Modul übernommen.
 */
@Composable
actual fun VideoPlayer(
    asset: VideoAsset,
    onCompleted: () -> Unit,
    loop: Boolean,
    cover: Boolean,
    muted: Boolean,
    modifier: Modifier,
) {
    val videoResId = asset.toRawRes()
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

@RawRes
private fun VideoAsset.toRawRes(): Int =
    when (this) {
        VideoAsset.STUDIO_INTRO -> R.raw.video_studio_intro
        VideoAsset.LOADING_SCREEN -> R.raw.video_loading_screen
        VideoAsset.MAIN_MENU_BACKGROUND -> R.raw.video_main_menu_background
        VideoAsset.LOBBY_BACKGROUND -> R.raw.video_lobby_background
        VideoAsset.OPTIONS_BACKGROUND -> R.raw.video_options_background
        VideoAsset.CHARACTER_PICKER_BACKGROUND -> R.raw.video_character_picker_background
        VideoAsset.BATTLE_INTRO -> R.raw.video_battle_intro
        VideoAsset.GAME_LOADING_BACKGROUND -> R.raw.video_game_loading_background
        VideoAsset.CARD_HAND_BACKGROUND -> R.raw.video_card_hand_background
        VideoAsset.VICTORY -> R.raw.video_victory
        VideoAsset.LOSS -> R.raw.video_loss
        VideoAsset.CHARACTER_WALLPAPER_01 -> R.raw.character_wallpaper_01_video
        VideoAsset.CHARACTER_WALLPAPER_02 -> R.raw.character_wallpaper_02_video
        VideoAsset.CHARACTER_WALLPAPER_03 -> R.raw.character_wallpaper_03_video
        VideoAsset.CHARACTER_WALLPAPER_04 -> R.raw.character_wallpaper_04_video
        VideoAsset.CHARACTER_WALLPAPER_05 -> R.raw.character_wallpaper_05_video
        VideoAsset.CHARACTER_WALLPAPER_06 -> R.raw.character_wallpaper_06_video
        VideoAsset.CHARACTER_WALLPAPER_07 -> R.raw.character_wallpaper_07_video
        VideoAsset.CHARACTER_WALLPAPER_08 -> R.raw.character_wallpaper_08_video
        VideoAsset.CHARACTER_WALLPAPER_09 -> R.raw.character_wallpaper_09_video
        VideoAsset.CHARACTER_WALLPAPER_10 -> R.raw.character_wallpaper_10_video
        VideoAsset.CHARACTER_WALLPAPER_11 -> R.raw.character_wallpaper_11_video
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
