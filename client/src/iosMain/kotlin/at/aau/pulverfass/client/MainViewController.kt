package at.aau.pulverfass.client

import androidx.compose.ui.window.ComposeUIViewController
import at.aau.pulverfass.client.audio.BackgroundMusicManager
import platform.UIKit.UIViewController

/**
 * iOS-Shell des Multiplatform-Clients; wird vom SwiftUI-Wrapper in iosApp
 * aufgerufen. Der [BackgroundMusicManager] lebt außerhalb der Composition,
 * analog zum Activity-Field auf Android.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    val musicManager = BackgroundMusicManager()
    return ComposeUIViewController {
        App(musicManager = musicManager)
    }
}
