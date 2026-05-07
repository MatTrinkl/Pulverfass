package at.aau.pulverfass.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Pulverfass Color Scheme (Dark only — die App hat kein Light-Theme).
 * Die Material3 Color-Slots sind auf unsere Pulverfass-Tokens gemappt.
 */
private val PulverfassColorScheme = darkColorScheme(
    primary = PulverfassColors.Gold,
    onPrimary = PulverfassColors.SurfaceVoid,
    primaryContainer = PulverfassColors.GoldDark,
    onPrimaryContainer = PulverfassColors.TextPrimary,

    secondary = PulverfassColors.Parchment,
    onSecondary = PulverfassColors.TextOnParchment,
    secondaryContainer = PulverfassColors.ParchmentDark,
    onSecondaryContainer = PulverfassColors.TextOnParchment,

    tertiary = PulverfassColors.GoldBright,
    onTertiary = PulverfassColors.SurfaceVoid,

    background = PulverfassColors.SurfaceDark,
    onBackground = PulverfassColors.TextPrimary,
    surface = PulverfassColors.SurfaceCard,
    onSurface = PulverfassColors.TextPrimary,
    surfaceVariant = PulverfassColors.SurfaceWood,
    onSurfaceVariant = PulverfassColors.TextOnDark,

    error = PulverfassColors.Danger,
    onError = PulverfassColors.TextPrimary,
    errorContainer = PulverfassColors.DangerBright,

    outline = PulverfassColors.GoldMuted,
    outlineVariant = PulverfassColors.ParchmentEdge,
)

/**
 * Zentrales Material-Theme der Android-App.
 *
 * Pulverfass nutzt ausschließlich Dark-Theme (siehe Art Bible §1).
 * Der `darkTheme`-Parameter ist aus Kompatibilitätsgründen erhalten,
 * wird aber intern ignoriert.
 *
 * @param content Inhalt, der innerhalb des Themes gerendert wird.
 */
@Composable
fun AndroidAppTheme(
    @Suppress("UNUSED_PARAMETER")
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PulverfassColorScheme,
        typography = PulverfassTypography,
        shapes = PulverfassShapes,
        content = content,
    )
}
