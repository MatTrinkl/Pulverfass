package at.aau.pulverfass.client

/**
 * `true`, wenn die Plattform programmatisches App-Beenden unterstützt.
 * iOS-Apps beenden sich laut Human Interface Guidelines nicht selbst —
 * dort wird der Exit-Knopf ausgeblendet.
 */
expect val isExitSupported: Boolean

/** Beendet die App (nur aufrufen, wenn [isExitSupported] `true` ist). */
expect fun exitApplication()
