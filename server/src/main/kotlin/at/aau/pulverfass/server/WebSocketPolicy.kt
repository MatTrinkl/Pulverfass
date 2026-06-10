package at.aau.pulverfass.server

import io.ktor.websocket.CloseReason

/**
 * Zentrale technische Regeln für den WebSocket-Endpunkt des Servers.
 */
object WebSocketPolicy {
    /**
     * Abstand zwischen zwei serverseitigen Ping-Frames.
     *
     * Das kurze Intervall macht still abgebrochene Mobilverbindungen schneller
     * für alle Lobby-Mitglieder sichtbar, ohne ein eigenes Heartbeat-System
     * neben Ktors bestehendem Ping-/Pong-Mechanismus einzuführen.
     */
    const val PING_PERIOD_MILLIS: Long = 5_000

    /**
     * Wartezeit auf das Pong eines Clients nach einem Server-Ping.
     */
    const val TIMEOUT_MILLIS: Long = 5_000

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
