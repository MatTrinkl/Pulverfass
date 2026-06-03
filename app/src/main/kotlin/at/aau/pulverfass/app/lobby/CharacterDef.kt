package at.aau.pulverfass.app.lobby

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.R

data class CharacterDef(
    val id: String,
    @DrawableRes val drawableRes: Int,
    val color: Color,
    val displayName: String,
    val wallpaperResId: Int,
    val isVideoWallpaper: Boolean = false,
    @DrawableRes val fallbackImageResId: Int? = null,
)

object Characters {
    val all: List<CharacterDef> =
        listOf(
            CharacterDef(
                "blackpurp",
                R.drawable.blackpurp,
                Color(0xFF6B2D8B),
                "Black Purp",
                wallpaperResId = R.raw.vio_mysiker,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.vio_mysiker,
            ),
            CharacterDef(
                "bookmen",
                R.drawable.bookmen,
                Color(0xFF1A3D7C),
                "Book Men",
                wallpaperResId = R.drawable.ghost,
            ),
            CharacterDef(
                "doctor",
                R.drawable.doctor,
                Color(0xFF2A9D8F),
                "Doctor",
                wallpaperResId = R.drawable.alchemist,
            ),
            CharacterDef(
                "ice",
                R.drawable.ice,
                Color(0xFF7EC8E3),
                "Ice",
                wallpaperResId = R.raw.ice_vid,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.blue_atorm,
            ),
            CharacterDef(
                "indiawatta",
                R.drawable.indiawatta,
                Color(0xFFD4763B),
                "India Watta",
                wallpaperResId = R.raw.watermen,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.watermen,
            ),
            CharacterDef(
                "mommy",
                R.drawable.mommy,
                Color(0xFFE07A9B),
                "Mommy",
                wallpaperResId = R.drawable.geleehr,
            ),
            CharacterDef(
                "redmen",
                R.drawable.redmen,
                Color(0xFFA6342B),
                "Red Men",
                wallpaperResId = R.drawable.darkend,
            ),
            CharacterDef(
                "redwomen",
                R.drawable.redwomen,
                Color(0xFFB54A5A),
                "Red Women",
                wallpaperResId = R.raw.redwoman,
                isVideoWallpaper = true,
            ),
            CharacterDef(
                "redwomen2",
                R.drawable.redwomen2,
                Color(0xFF7B1A2A),
                "Red Women II",
                wallpaperResId = R.drawable.le_elegance_w,
            ),
            CharacterDef(
                "whitewomen",
                R.drawable.whitewomen,
                Color(0xFFB8A9C9),
                "White Women",
                wallpaperResId = R.raw.gold,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.gold,
            ),
            CharacterDef(
                "mr",
                R.drawable.mr,
                Color(0xFFAAAAAA),
                "Mr",
                wallpaperResId = R.drawable.codesmell,
            ),
        )

    fun byId(id: String): CharacterDef? = all.firstOrNull { it.id == id }

    fun byIndex(index: Int): CharacterDef = all[index % all.size]
}
