package at.aau.pulverfass.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import at.aau.pulverfass.app.R

/**
 * Pulverfass-Typografie. Cinzel Decorative für Game-UI-Texte (Titel, Buttons,
 * Inputs, Player-Listen). Sans-Serif (System-Default) für Dev-/Debug-Texte.
 */
object PulverfassFonts {
    val CinzelDecorative =
        FontFamily(
            Font(R.font.cinzel_decorative_regular, FontWeight.Normal),
            Font(R.font.cinzel_decorative_bold, FontWeight.Bold),
            Font(R.font.cinzel_decorative_bold, FontWeight.Black),
        )
}
