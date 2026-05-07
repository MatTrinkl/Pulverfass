package at.aau.pulverfass.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.R

/**
 * Pulverfass Font Families
 * Cinzel Decorative für Titles, Cormorant Garamond für Body,
 * Philosopher für UI-Labels, Fleur De Leah für seltene Decorative-Akzente.
 */
val CinzelDecorative = FontFamily(
    Font(R.font.cinzel_decorative_regular, FontWeight.Normal),
    Font(R.font.cinzel_decorative_bold, FontWeight.Bold),
)

val CormorantGaramond = FontFamily(
    Font(R.font.cormorant_garamond_regular, FontWeight.Normal),
)

val Philosopher = FontFamily(
    Font(R.font.philosopher_bold, FontWeight.Bold),
)

val FleurDeLeah = FontFamily(
    Font(R.font.fleur_de_leah_regular, FontWeight.Normal),
)

/**
 * Pulverfass Type Scale (siehe Art Bible §3).
 *
 * Verwendung: `MaterialTheme.typography.displayLarge` etc.
 * Die Mapping-Logik (Display = Cinzel, Body = Cormorant, ...) ist hier zentralisiert.
 */
val PulverfassTypography = Typography(
    // Display XL — z.B. "GEWONNEN !!!!"
    displayLarge = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        letterSpacing = 1.12.sp,
    ),
    // Display Large — z.B. "SCHLACHT", "LOBBY"
    displayMedium = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = 0.96.sp,
    ),
    // Display Medium — z.B. "Nächster ZUG"
    displaySmall = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 0.36.sp,
    ),
    // Headline — z.B. "PHASE: ANGRIFF", "RUNDE 3"
    headlineLarge = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.4.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 1.2.sp,
    ),
    // Title — Section-Headers
    titleLarge = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 1.0.sp,
    ),
    // Body Large — Primary Body
    bodyLarge = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    // Button-Label — "WÜRFELN!", "ANGRIFF" etc.
    labelLarge = TextStyle(
        fontFamily = Philosopher,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Philosopher,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.96.sp,
    ),
    // Caption — Stats, Mikro-Text
    labelSmall = TextStyle(
        fontFamily = Philosopher,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.55.sp,
    ),
)
