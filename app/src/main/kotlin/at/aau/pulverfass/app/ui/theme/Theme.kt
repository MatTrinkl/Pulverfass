package at.aau.pulverfass.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Farbschema für den dunklen Modus. */
private val DarkColorScheme = darkColorScheme()

/** Farbschema für den hellen Modus. */
private val LightColorScheme = lightColorScheme()

/**
 * Zentrales Material-Theme der Android-App.
 *
 * @param darkTheme steuert, ob das dunkle oder helle Farbschema genutzt wird
 * @param content Inhalt, der innerhalb des Material-Themes gerendert wird
 */
@Composable
fun AndroidAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    /*
     * Auswahl bleibt hier bewusst simpel, weil die App aktuell keine dynamischen
     * Systemfarben oder eigene Brand-Tokens kapselt.
     */
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
