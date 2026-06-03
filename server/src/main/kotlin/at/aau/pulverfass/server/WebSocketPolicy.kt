package at.aau.pulverfass.server

import io.ktor.websocket.CloseReason

/**
 * Zentrale technische Regeln für den WebSocket-Endpunkt des Servers.
 */
object WebSocketPolicy {
    /**
     * Obergrenze pro WebSocket-Frame.
     *
     * Die Grenze ist bewusst deutlich oberhalb aller aktuell erwarteten Payloads,
     * verhindert aber triviale Speicher- und CPU-DoS-Versuche mit extrem großen
     * Frames.
     */
    const val MAX_FRAME_SIZE_BYTES: Long = 1_048_576

    /**
     * Text Frames werden in Serie 1 nicht fachlich verarbeitet und aktiv abgelehnt.
     */
    const val TEXT_FRAMES_NOT_SUPPORTED = "Text frames are not supported on /ws."

    /**
     * Dokumentierter Close-Code für nicht unterstützte Text Frames.
     */
    val TEXT_FRAME_CLOSE_CODE: Short = CloseReason.Codes.CANNOT_ACCEPT.code
}
