package at.aau.pulverfass.client

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Wird vom SwiftUI-Wrapper in iosApp aufgerufen.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
