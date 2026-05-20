package at.aau.pulverfass.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fester HTTP-Health-Endpunkt des aktuell genutzten Spielservers. */
const val SERVER_HEALTH_URL = "http://5.189.160.80:8080/health"

private const val HEALTH_CONNECT_TIMEOUT_MS = 2_500L
private const val HEALTH_REQUEST_TIMEOUT_MS = 3_000L
private const val HEALTH_SOCKET_TIMEOUT_MS = 3_000L

/**
 * UI-nahe Projektion des technischen Health-Checks.
 *
 * Die Werte sind bewusst auf die drei Ampelfarben der Statusanzeige begrenzt:
 * OK, erreichbarer Serverfehler und nicht erreichbarer Server.
 */
enum class ServerHealthStatus {
    OK,
    ERROR,
    UNREACHABLE,
}

/**
 * Fragt den HTTP-Health-Endpunkt des Servers ab.
 *
 * Der Monitor bleibt unabhängig von der WebSocket-Verbindung, damit die App
 * auch ohne aktive Lobby-Session anzeigen kann, ob der Server erreichbar ist.
 *
 * @param healthUrl vollständige HTTP-URL des Health-Endpunkts
 * @param client injizierbarer Ktor-Client für Tests
 */
class ServerHealthMonitor(
    private val healthUrl: String = SERVER_HEALTH_URL,
    private val client: HttpClient = createServerHealthHttpClient(),
) {
    /**
     * Übersetzt die aktuelle Health-Antwort in einen Status für die UI.
     *
     * Nur eine erfolgreiche HTTP-Antwort mit Body `ok` gilt als [ServerHealthStatus.OK].
     * Alle anderen erreichbaren Antworten werden als [ServerHealthStatus.ERROR]
     * angezeigt. Netzwerkfehler und Timeouts zählen als [ServerHealthStatus.UNREACHABLE].
     */
    suspend fun checkStatus(): ServerHealthStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.get(healthUrl)
                serverHealthStatusFromResponse(
                    isSuccessStatus = response.status.value in 200..299,
                    body = response.bodyAsText(),
                )
            }.getOrElse { cause ->
                if (cause is CancellationException) {
                    throw cause
                }
                ServerHealthStatus.UNREACHABLE
            }
        }

    /** Gibt die Ressourcen des Health-Clients frei. */
    fun close() {
        client.close()
    }
}

/**
 * Übersetzt eine bereits gelesene HTTP-Antwort in den Ampelstatus.
 *
 * Die Funktion enthält keine Netzwerklogik und bleibt deshalb direkt testbar.
 */
internal fun serverHealthStatusFromResponse(
    isSuccessStatus: Boolean,
    body: String,
): ServerHealthStatus =
    if (isSuccessStatus && body.trim().equals("ok", ignoreCase = true)) {
        ServerHealthStatus.OK
    } else {
        ServerHealthStatus.ERROR
    }

private fun createServerHealthHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = HEALTH_CONNECT_TIMEOUT_MS
            requestTimeoutMillis = HEALTH_REQUEST_TIMEOUT_MS
            socketTimeoutMillis = HEALTH_SOCKET_TIMEOUT_MS
        }
    }
