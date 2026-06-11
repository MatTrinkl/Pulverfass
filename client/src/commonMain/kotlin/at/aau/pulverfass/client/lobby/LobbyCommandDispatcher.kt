package at.aau.pulverfass.client.lobby

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload
import kotlinx.coroutines.delay

/**
 * Eindeutiger Schlüssel für ausgehende Lobby- und Game-Requests.
 *
 * Der Schlüssel wird im UI-State als Pending-Markierung genutzt, damit Buttons
 * nicht mehrfach dieselbe Aktion auslösen können.
 */
enum class LobbyCommandKey {
    CREATE_LOBBY,
    JOIN_LOBBY,
    LEAVE_LOBBY,
    START_GAME,
    MAP_GET,
    TURN_STATE_GET,
    CATCH_UP,
    PRIVATE_STATE,
    TURN_ADVANCE,
    PLACE_REINFORCEMENTS,
    CLAIM_CHEAT_REINFORCEMENT_BONUS,
    CONFIRM_REINFORCEMENTS_DONE,
    TRADE_IN_CARDS,
    ATTACK,
    CONFIRM_ATTACK_DONE,
    FORTIFY_MOVE,
    CHARACTER_SELECT,
}

/**
 * Retry-Strategie für App-seitig sichere Requests.
 */
enum class LobbyRetryPolicy {
    NONE,
    SAFE_ONCE,
}

/**
 * Ausgehender Request inklusive UI-Schlüssel und Retry-Regel.
 */
data class LobbyCommand(
    val key: LobbyCommandKey,
    val payload: NetworkMessagePayload,
    val retryPolicy: LobbyRetryPolicy = LobbyRetryPolicy.NONE,
)

/**
 * Zentraler Sendepfad für Requests, die über die bestehende WebSocket-Verbindung
 * laufen.
 *
 * Der Dispatcher kennt keine UI und keine Fachlogik. Er sorgt nur dafür, dass
 * sichere Requests bei einem kurzen Transportfehler genau einmal erneut
 * versucht werden.
 */
class LobbyCommandDispatcher(
    private val sendPayload: suspend (NetworkMessagePayload) -> Unit,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) {
    suspend fun send(command: LobbyCommand) {
        val maxAttempts =
            when (command.retryPolicy) {
                LobbyRetryPolicy.NONE -> 1
                LobbyRetryPolicy.SAFE_ONCE -> 2
            }
        var lastError: Throwable? = null

        repeat(maxAttempts) { attempt ->
            runCatching {
                sendPayload(command.payload)
            }.onSuccess {
                return
            }.onFailure { error ->
                lastError = error
                if (attempt < maxAttempts - 1) {
                    delay(retryDelayMillis)
                }
            }
        }

        throw lastError ?: IllegalStateException("Request konnte nicht gesendet werden.")
    }

    private companion object {
        const val DEFAULT_RETRY_DELAY_MILLIS = 150L
    }
}
