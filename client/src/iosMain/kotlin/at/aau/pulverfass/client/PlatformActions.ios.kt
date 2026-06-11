package at.aau.pulverfass.client

actual val isExitSupported: Boolean = false

actual fun exitApplication() {
    // iOS-Apps beenden sich nicht selbst (App Store Review / HIG).
}
