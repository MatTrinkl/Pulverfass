package at.aau.pulverfass.app.lobby

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.R

data class CharacterDefinition(
    val id: String,
    @param:DrawableRes val drawableRes: Int,
    val color: Color,
    val displayName: String,
    val wallpaperResId: Int,
    val isVideoWallpaper: Boolean = false,
    @param:DrawableRes val fallbackImageResId: Int? = null,
)

object Characters {
    val all: List<CharacterDefinition> =
        listOf(
            CharacterDefinition(
                "character_01",
                R.drawable.character_portrait_01,
                Color(0xFF7B2CBF),
                "Charakter 01",
                wallpaperResId = R.raw.character_wallpaper_01_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_01_fallback,
            ),
            CharacterDefinition(
                "character_02",
                R.drawable.character_portrait_02,
                Color(0xFF1565C0),
                "Charakter 02",
                wallpaperResId = R.drawable.character_wallpaper_02,
            ),
            CharacterDefinition(
                "character_03",
                R.drawable.character_portrait_03,
                Color(0xFF00897B),
                "Charakter 03",
                wallpaperResId = R.drawable.character_wallpaper_03,
            ),
            CharacterDefinition(
                "character_04",
                R.drawable.character_portrait_04,
                Color(0xFF00A6D6),
                "Charakter 04",
                wallpaperResId = R.raw.character_wallpaper_04_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_04_fallback,
            ),
            CharacterDefinition(
                "character_05",
                R.drawable.character_portrait_05,
                Color(0xFFE76F00),
                "Charakter 05",
                wallpaperResId = R.raw.character_wallpaper_05_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_05_fallback,
            ),
            CharacterDefinition(
                "character_06",
                R.drawable.character_portrait_06,
                Color(0xFFD81B60),
                "Charakter 06",
                wallpaperResId = R.drawable.character_wallpaper_06,
            ),
            CharacterDefinition(
                "character_07",
                R.drawable.character_portrait_07,
                Color(0xFFC62828),
                "Charakter 07",
                wallpaperResId = R.drawable.character_wallpaper_07,
            ),
            CharacterDefinition(
                "character_08",
                R.drawable.character_portrait_08,
                Color(0xFFFF4081),
                "Charakter 08",
                wallpaperResId = R.raw.character_wallpaper_08_video,
                isVideoWallpaper = true,
            ),
            CharacterDefinition(
                "character_09",
                R.drawable.character_portrait_09,
                Color(0xFF8E24AA),
                "Charakter 09",
                wallpaperResId = R.drawable.character_wallpaper_09,
            ),
            CharacterDefinition(
                "character_10",
                R.drawable.character_portrait_10,
                Color(0xFFFBC02D),
                "Charakter 10",
                wallpaperResId = R.raw.character_wallpaper_10_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_10_fallback,
            ),
            CharacterDefinition(
                "character_11",
                R.drawable.character_portrait_11,
                Color(0xFF43A047),
                "Charakter 11",
                wallpaperResId = R.drawable.character_wallpaper_11,
            ),
        )

    fun byId(id: String): CharacterDefinition? = all.firstOrNull { it.id == id }

    fun byIndex(index: Int): CharacterDefinition = all[index % all.size]
}
