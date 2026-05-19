package at.aau.pulverfass.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SERVER_HEALTH_URL = "http://5.189.160.80:8080/health"

private const val HEALTH_CONNECT_TIMEOUT_MS = 2_500L
private const val HEALTH_REQUEST_TIMEOUT_MS = 3_000L
private const val HEALTH_SOCKET_TIMEOUT_MS = 3_000L

enum class ServerHealthStatus {
    OK,
    ERROR,
    UNREACHABLE,
}

class ServerHealthMonitor(
    private val healthUrl: String = SERVER_HEALTH_URL,
    private val client: HttpClient = createServerHealthHttpClient(),
) {
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

    fun close() {
        client.close()
    }
}

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
