package at.aau.pulverfass.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// farbschema für dunklen modus
private val DarkColorScheme = darkColorScheme()
// farbschema für hellen modus
private val LightColorScheme = lightColorScheme()

@Composable
fun AndroidAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    // wählt das farbschema basierend auf der einstellung aus
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    // setzt das übergeordnete material design theme für die app
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
