package at.aau.pulverfass.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIView

/**
 * iOS-Implementierung über AVPlayer + AVPlayerLayer.
 *
 * `cover` entspricht dem Android-Center-Crop: das Video füllt den Container
 * (`resizeAspectFill`) und Überstand wird geclippt; sonst wird mit
 * Letterboxing eingepasst (`resizeAspect`).
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
    val player =
        remember(asset) {
            NSBundle.mainBundle
                .URLForResource(asset.fileName, withExtension = "mp4")
                ?.let { url -> AVPlayer(uRL = url) }
        } ?: return
    val currentOnCompleted = rememberUpdatedState(onCompleted)

    DisposableEffect(player, loop, muted) {
        player.muted = muted
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = player.currentItem,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                if (loop) {
                    player.seekToTime(CMTimeMake(value = 0, timescale = 1))
                    player.play()
                } else {
                    currentOnCompleted.value()
                }
            }
        player.play()

        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            player.pause()
        }
    }

    UIKitView(
        factory = {
            val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player)
            playerLayer.videoGravity =
                if (cover) AVLayerVideoGravityResizeAspectFill else AVLayerVideoGravityResizeAspect
            PlayerContainerView(playerLayer).apply {
                clipsToBounds = cover
            }
        },
        modifier = modifier,
    )
}

/** UIView, die den AVPlayerLayer bei Layoutänderungen mitskaliert. */
private class PlayerContainerView(
    private val playerLayer: AVPlayerLayer,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.frame = bounds
    }
}
