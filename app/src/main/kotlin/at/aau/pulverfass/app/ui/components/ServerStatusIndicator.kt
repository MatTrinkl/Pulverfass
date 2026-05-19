package at.aau.pulverfass.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private const val HEALTH_POLL_INTERVAL_MS = 5_000L

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

    Surface(
        modifier =
            modifier
                .size(24.dp)
                .semantics {
                    contentDescription = statusText
                }
                .testTag("server_status_indicator"),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = statusColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .background(statusColor, CircleShape),
        )
    }
}
