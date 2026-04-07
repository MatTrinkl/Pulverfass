package at.aau.pulverfass.server

import io.ktor.websocket.CloseReason

/**
 * Zentrale technische Regeln für den WebSocket-Endpunkt des Servers.
 */
object WebSocketPolicy {
    /**
     * Text Frames werden in Serie 1 nicht fachlich verarbeitet und aktiv abgelehnt.
     */
    const val TEXT_FRAMES_NOT_SUPPORTED = "Text frames are not supported on /ws."

    /**
     * Dokumentierter Close-Code für nicht unterstützte Text Frames.
     */
    val TEXT_FRAME_CLOSE_CODE: Short = CloseReason.Codes.CANNOT_ACCEPT.code
}
