package at.aau.pulverfass.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Pulverfass Shapes (siehe Art Bible §4.2).
 * Material3 Shape-Slots gemappt auf unsere Radius-Skala.
 */
val PulverfassShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // Tags, Chips
    small = RoundedCornerShape(8.dp),        // Buttons, Inputs
    medium = RoundedCornerShape(8.dp),       // Cards
    large = RoundedCornerShape(16.dp),       // Modals
    extraLarge = RoundedCornerShape(24.dp),  // Hero-Cards
)
