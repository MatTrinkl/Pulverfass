package at.aau.pulverfass.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Pulverfass Shapes (siehe Art Bible §4.2).
 * Material3 Shape-Slots gemappt auf unsere Radius-Skala.
 */
val PulverfassShapes =
    Shapes(
        // Tags, Chips
        extraSmall = RoundedCornerShape(4.dp),
        // Buttons, Inputs
        small = RoundedCornerShape(8.dp),
        // Cards
        medium = RoundedCornerShape(8.dp),
        // Modals
        large = RoundedCornerShape(16.dp),
        // Hero-Cards
        extraLarge = RoundedCornerShape(24.dp),
    )
