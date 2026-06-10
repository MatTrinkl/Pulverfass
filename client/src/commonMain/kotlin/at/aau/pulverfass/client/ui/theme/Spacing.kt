package at.aau.pulverfass.client.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Pulverfass Spacing Scale (8pt-basiert, siehe Art Bible §4.1).
 *
 * Verwendung in Composables:
 *   `Modifier.padding(PulverfassSpacing.base)`
 */
object PulverfassSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
}

/**
 * Component Sizing Tokens.
 * Use Cases sind in der Art Bible §4 dokumentiert.
 */
object PulverfassSizes {
    val buttonHeightPrimary = 56.dp
    val buttonHeightSecondary = 40.dp
    val buttonMinWidth = 120.dp
    val hudTopHeight = 56.dp
    val hudBottomHeight = 60.dp
    val inputHeight = 56.dp
    val avatarSm = 40.dp
    val avatarMd = 56.dp
    val avatarLg = 80.dp
    val avatarXl = 150.dp
    val troopIndicator = 28.dp
    val diceSize = 56.dp
    val iconXs = 16.dp
    val iconSm = 20.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val iconXl = 48.dp
    val borderThin = 1.dp
    val borderDefault = 2.dp
    val borderThick = 4.dp
}
