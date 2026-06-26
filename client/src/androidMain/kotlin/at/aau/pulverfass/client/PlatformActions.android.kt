package at.aau.pulverfass.client

/**
 * Brücke zwischen common code und der Activity: MainActivity registriert hier
 * ihren finish()-Callback, bevor die Composition startet.
 */
object ExitHandler {
    var handler: (() -> Unit)? = null
}

actual val isExitSupported: Boolean = true

actual fun exitApplication() {
    ExitHandler.handler?.invoke()
}
