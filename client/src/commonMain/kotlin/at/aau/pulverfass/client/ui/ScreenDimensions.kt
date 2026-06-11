package at.aau.pulverfass.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * Multiplatform-Ersatz für `LocalConfiguration.current.screenWidthDp.dp`:
 * Breite des aktuellen Compose-Containers in Dp.
 */
@Composable
fun screenWidthDp(): Dp =
    with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }

/**
 * Multiplatform-Ersatz für `LocalConfiguration.current.screenHeightDp.dp`:
 * Höhe des aktuellen Compose-Containers in Dp.
 */
@Composable
fun screenHeightDp(): Dp =
    with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
