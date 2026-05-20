package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.network.ServerHealthMonitor
import at.aau.pulverfass.app.network.ServerHealthStatus
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import kotlinx.coroutines.delay

/** Standardintervall für den globalen Health-Check. */
internal const val HEALTH_POLL_INTERVAL_MS = 10_000L

/**
 * Erzeugt den Health-Monitor für die aktuelle Composition.
 *
 * Beim Verlassen der Composition wird der zugrunde liegende HTTP-Client wieder
 * geschlossen, damit keine Netzwerkressourcen offen bleiben.
 */
@Composable
fun rememberServerHealthMonitor(): ServerHealthMonitor {
    val monitor = remember { ServerHealthMonitor() }

    DisposableEffect(monitor) {
        onDispose {
            monitor.close()
        }
    }

    return monitor
}

/**
 * Startet das regelmäßige Polling des Serverstatus.
 *
 * Der erste Check läuft sofort, danach wird mit dem angegebenen Intervall
 * weiter abgefragt. Sehr kleine Intervalle werden auf mindestens eine Sekunde
 * begrenzt.
 *
 * @param monitor Health-Monitor, der den HTTP-Endpunkt abfragt
 * @param pollIntervalMillis Abstand zwischen zwei Health-Checks
 * @param initialStatus Status bis zur ersten Serverantwort
 */
@Composable
fun rememberServerHealthStatus(
    monitor: ServerHealthMonitor = rememberServerHealthMonitor(),
    pollIntervalMillis: Long = HEALTH_POLL_INTERVAL_MS,
    initialStatus: ServerHealthStatus = ServerHealthStatus.UNREACHABLE,
): State<ServerHealthStatus> {
    val status = remember { mutableStateOf(initialStatus) }

    LaunchedEffect(monitor, pollIntervalMillis) {
        while (true) {
            status.value = monitor.checkStatus()
            delay(pollIntervalMillis.coerceAtLeast(1_000L))
        }
    }

    return status
}

/**
 * Zeigt den aktuellen Serverstatus als einfachen farbigen Kreis.
 *
 * Grün steht für einen gesunden Server, Orange für eine erreichbare
 * Fehlerantwort und Rot für einen nicht erreichbaren Server.
 */
@Composable
fun ServerStatusIndicator(
    status: ServerHealthStatus,
    modifier: Modifier = Modifier,
) {
    val statusColor =
        when (status) {
            ServerHealthStatus.OK -> PulverfassColors.Success
            ServerHealthStatus.ERROR -> PulverfassColors.Warning
            ServerHealthStatus.UNREACHABLE -> PulverfassColors.DangerBright
        }
    val statusText =
        when (status) {
            ServerHealthStatus.OK -> stringResource(id = R.string.server_status_ok)
            ServerHealthStatus.ERROR -> stringResource(id = R.string.server_status_error)
            ServerHealthStatus.UNREACHABLE ->
                stringResource(id = R.string.server_status_unreachable)
        }

    Box(
        modifier =
            modifier
                .size(16.dp)
                .background(statusColor, CircleShape)
                .semantics {
                    contentDescription = statusText
                }
                .testTag("server_status_indicator"),
    )
}
