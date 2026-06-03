package at.aau.pulverfass.app.lobby

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.R

data class CharacterDef(
    val id: String,
    @param:DrawableRes val drawableRes: Int,
    val color: Color,
    val displayName: String,
    val wallpaperResId: Int,
    val isVideoWallpaper: Boolean = false,
    @param:DrawableRes val fallbackImageResId: Int? = null,
)

object Characters {
    val all: List<CharacterDef> =
        listOf(
            CharacterDef(
                "blackpurp",
                R.drawable.blackpurp,
                Color(0xFF7B2CBF),
                "Black Purp",
                wallpaperResId = R.raw.vio_mysiker,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.vio_mysiker,
            ),
            CharacterDef(
                "bookmen",
                R.drawable.bookmen,
                Color(0xFF1565C0),
                "Book Men",
                wallpaperResId = R.drawable.ghost,
            ),
            CharacterDef(
                "doctor",
                R.drawable.doctor,
                Color(0xFF00897B),
                "Doctor",
                wallpaperResId = R.drawable.alchemist,
            ),
            CharacterDef(
                "ice",
                R.drawable.ice,
                Color(0xFF00A6D6),
                "Ice",
                wallpaperResId = R.raw.ice_vid,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.blue_atorm,
            ),
            CharacterDef(
                "indiawatta",
                R.drawable.indiawatta,
                Color(0xFFE76F00),
                "India Watta",
                wallpaperResId = R.raw.watermen,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.watermen,
            ),
            CharacterDef(
                "mommy",
                R.drawable.mommy,
                Color(0xFFD81B60),
                "Mommy",
                wallpaperResId = R.drawable.geleehr,
            ),
            CharacterDef(
                "redmen",
                R.drawable.redmen,
                Color(0xFFC62828),
                "Red Men",
                wallpaperResId = R.drawable.darkend,
            ),
            CharacterDef(
                "redwomen",
                R.drawable.redwomen,
                Color(0xFFFF4081),
                "Red Women",
                wallpaperResId = R.raw.redwoman,
                isVideoWallpaper = true,
            ),
            CharacterDef(
                "redwomen2",
                R.drawable.redwomen2,
                Color(0xFF8E24AA),
                "Red Women II",
                wallpaperResId = R.drawable.le_elegance_w,
            ),
            CharacterDef(
                "whitewomen",
                R.drawable.whitewomen,
                Color(0xFFFBC02D),
                "White Women",
                wallpaperResId = R.raw.gold,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.gold,
            ),
            CharacterDef(
                "mr",
                R.drawable.mr,
                Color(0xFF43A047),
                "Mr",
                wallpaperResId = R.drawable.codesmell,
            ),
        )

    fun byId(id: String): CharacterDef? = all.firstOrNull { it.id == id }

    fun byIndex(index: Int): CharacterDef = all[index % all.size]
}
