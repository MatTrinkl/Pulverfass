package at.aau.pulverfass.client.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pulverfass Type Scale (siehe Art Bible §3).
 *
 * Verwendung: `MaterialTheme.typography.displayLarge` etc.
 * Die Mapping-Logik (Display = Cinzel, Body = Cormorant, ...) ist hier zentralisiert.
 * Als Composable, weil Compose-Resources-Fonts nur in der Composition geladen
 * werden können.
 */
@Composable
fun pulverfassTypography(): Typography {
    val cinzelDecorative = cinzelDecorative()
    val cormorantGaramond = cormorantGaramond()
    val philosopher = philosopher()

    return Typography(
        // Display XL — z.B. "GEWONNEN !!!!"
        displayLarge =
            TextStyle(
                fontFamily = cinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                letterSpacing = 1.12.sp,
            ),
        // Display Large — z.B. "SCHLACHT", "LOBBY"
        displayMedium =
            TextStyle(
                fontFamily = cinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                letterSpacing = 0.96.sp,
            ),
        // Display Medium — z.B. "Nächster ZUG"
        displaySmall =
            TextStyle(
                fontFamily = cinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = 0.36.sp,
            ),
        // Headline — z.B. "PHASE: ANGRIFF", "RUNDE 3"
        headlineLarge =
            TextStyle(
                fontFamily = cinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 1.4.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = cinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 1.2.sp,
            ),
        // Title — Section-Headers
        titleLarge =
            TextStyle(
                fontFamily = cinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 1.0.sp,
            ),
        // Body Large — Primary Body
        bodyLarge =
            TextStyle(
                fontFamily = cormorantGaramond,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = cormorantGaramond,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = cormorantGaramond,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
        // Button-Label — "WÜRFELN!", "ANGRIFF" etc.
        labelLarge =
            TextStyle(
                fontFamily = philosopher,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.4.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = philosopher,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.96.sp,
            ),
        // Caption — Stats, Mikro-Text
        labelSmall =
            TextStyle(
                fontFamily = philosopher,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 0.55.sp,
            ),
    )
}
