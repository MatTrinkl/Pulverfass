package at.aau.pulverfass.client.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pulverfass Color Tokens
 * ========================
 * Single source of truth für alle Farben. Niemals hardcoded Color() in Composables —
 * immer einen Token aus dieser Datei verwenden.
 *
 * Reverse-engineered aus dem Figma-Master, dokumentiert in /docs/design/ART_BIBLE.md §2.
 * Im Laufe der Zeit werden und können hier die Farbcodes angepasst gelöscht oder hinzugefügt werdn.
 *
 * Good luck have fun euer UI Götter Bote
 *
 */
object PulverfassColors {
    // ============================================
    // SURFACES (dunkle Untergründe)
    // ============================================
    val SurfaceVoid = Color(0xFF0F1419)
    val SurfaceDark = Color(0xFF1A1F2A)
    val SurfaceWood = Color(0xFF2A1F18)
    val SurfaceCard = Color(0xFF1F1A14)
    val SurfaceOverlay = Color(0xCC000000) // 80% schwarz für Modal-Backdrops

    // ============================================
    // PARCHMENT (warme Mitten)
    // ============================================
    val ParchmentLight = Color(0xFFD4B896)
    val Parchment = Color(0xFFC8A876)
    val ParchmentDark = Color(0xFFA8855A)
    val ParchmentEdge = Color(0xFF6B4D2A)

    // ============================================
    // GOLD (Akzent — sparsam einsetzen!)
    // ============================================
    val GoldBright = Color(0xFFE8C268)
    val Gold = Color(0xFFD4B45A)
    val GoldMuted = Color(0xFFC4A14B)
    val GoldCoin = Color(0xFFD4A843)
    val GoldDark = Color(0xFF8B6F2A)

    // ============================================
    // PLAYER COLORS (semantisch — 8 Spieler)
    // ============================================
    val PlayerBlue = Color(0xFF0057E7)
    val PlayerOrange = Color(0xFFFF6D00)
    val PlayerGreen = Color(0xFF00B050)
    val PlayerPurple = Color(0xFFAA00FF)
    val PlayerYellow = Color(0xFFFFD600)
    val PlayerCyan = Color(0xFF00B8D4)
    val PlayerRed = Color(0xFFD50000)
    val PlayerMagenta = Color(0xFFFF1493)

    /**
     * Liste aller Player-Farben in fester Reihenfolge.
     * Use case: Spieler-Index → Color via [playerColors][index].
     */
    val playerColors: List<Color> =
        listOf(
            PlayerBlue,
            PlayerOrange,
            PlayerGreen,
            PlayerPurple,
            PlayerYellow,
            PlayerCyan,
            PlayerRed,
            PlayerMagenta,
        )

    // ============================================
    // SEMANTIC COLORS
    // ============================================
    val Danger = Color(0xFF8B2828)
    val DangerBright = Color(0xFFC44030)
    val Success = Color(0xFF4A7A4A)
    val Warning = Color(0xFFD17030)
    val Info = Color(0xFF3A5BA0)

    // ============================================
    // TEXT
    // ============================================
    val TextPrimary = Color(0xFFF0E6D2)
    val TextOnDark = Color(0xFFE8DCC8)
    val TextOnParchment = Color(0xFF2A1F18)
    val TextMuted = Color(0xFF8B7F66)
    val TextGold = Color(0xFFD4B45A)
}
