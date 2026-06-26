package at.aau.pulverfass.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.cinzel_decorative_bold
import at.aau.pulverfass.client.resources.cinzel_decorative_regular
import at.aau.pulverfass.client.resources.cormorant_garamond_regular
import at.aau.pulverfass.client.resources.fleur_de_leah_regular
import at.aau.pulverfass.client.resources.philosopher_bold
import org.jetbrains.compose.resources.Font

/**
 * Pulverfass Font Families (Compose-Resources-Variante).
 * Cinzel Decorative für Titles, Cormorant Garamond für Body,
 * Philosopher für UI-Labels, Fleur De Leah für seltene Decorative-Akzente.
 *
 * Compose Multiplatform lädt Fonts über Composable-Funktionen statt
 * top-level `val`s — daher sind alle Familien hier Funktionen.
 */
@Composable
fun cinzelDecorative(): FontFamily =
    FontFamily(
        Font(Res.font.cinzel_decorative_regular, FontWeight.Normal),
        Font(Res.font.cinzel_decorative_bold, FontWeight.Bold),
        Font(Res.font.cinzel_decorative_bold, FontWeight.Black),
    )

@Composable
fun cormorantGaramond(): FontFamily =
    FontFamily(
        Font(Res.font.cormorant_garamond_regular, FontWeight.Normal),
    )

@Composable
fun philosopher(): FontFamily =
    FontFamily(
        Font(Res.font.philosopher_bold, FontWeight.Bold),
    )

@Composable
fun fleurDeLeah(): FontFamily =
    FontFamily(
        Font(Res.font.fleur_de_leah_regular, FontWeight.Normal),
    )
