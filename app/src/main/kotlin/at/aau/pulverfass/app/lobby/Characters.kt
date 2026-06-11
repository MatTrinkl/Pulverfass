package at.aau.pulverfass.app.lobby

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.R

/**
 * Beschreibt einen auswählbaren Lobby-Charakter inklusive UI-Assets.
 *
 * Die [id] ist bewusst unabhängig vom Dateinamen. Sie wird im Lobby-Protokoll
 * übertragen und lokal persistiert, weshalb sie stabil bleiben muss, auch wenn
 * Portraits oder Wallpaper später ausgetauscht werden.
 *
 * @param id stabile fachliche Charakter-ID für Server, Persistenz und Tests.
 * @param drawableRes rundes Portrait für Lobby, Playerliste und Spielscreen.
 * @param color Akzentfarbe des Charakters; zugleich die Spielerfarbe auf der
 * Karte. Die elf Farben sind bewusst gut unterscheidbar gewählt.
 * @param displayName lesbarer Name für ContentDescription und Debugging.
 * @param wallpaperResId Bild- oder Video-Asset für den Character-Picker.
 * @param isVideoWallpaper `true`, wenn [wallpaperResId] auf eine Raw-Videoressource zeigt.
 * @param fallbackImageResId optionales Standbild, falls ein Video nicht gerendert werden kann.
 */
data class CharacterDefinition(
    val id: String,
    @param:DrawableRes val drawableRes: Int,
    val color: Color,
    val displayName: String,
    val wallpaperResId: Int,
    val isVideoWallpaper: Boolean = false,
    @param:DrawableRes val fallbackImageResId: Int? = null,
)

/**
 * Zentrale Character-Liste für Lobby und Spielscreen.
 *
 * Die Reihenfolge ist Teil der UI: sie bestimmt die Carousel-Reihenfolge und
 * den Fallback, falls ein gespeicherter Charakter bereits von einem anderen
 * Spieler belegt ist.
 */
object Characters {
    val all: List<CharacterDefinition> =
        listOf(
            CharacterDefinition(
                "character_01",
                R.drawable.character_portrait_01,
                Color(0xFF9B45CC),
                "Charakter 01",
                wallpaperResId = R.raw.character_wallpaper_01_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_01_fallback,
            ),
            CharacterDefinition(
                "character_02",
                R.drawable.character_portrait_02,
                Color(0xFF1EA85F),
                "Charakter 02",
                wallpaperResId = R.raw.character_wallpaper_02_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_02,
            ),
            CharacterDefinition(
                "character_03",
                R.drawable.character_portrait_03,
                Color(0xFF80582B),
                "Charakter 03",
                wallpaperResId = R.raw.character_wallpaper_03_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_03,
            ),
            CharacterDefinition(
                "character_04",
                R.drawable.character_portrait_04,
                Color(0xFF28A9E0),
                "Charakter 04",
                wallpaperResId = R.raw.character_wallpaper_04_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_04_fallback,
            ),
            CharacterDefinition(
                "character_05",
                R.drawable.character_portrait_05,
                Color(0xFF2F4BD6),
                "Charakter 05",
                wallpaperResId = R.raw.character_wallpaper_05_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_05_fallback,
            ),
            CharacterDefinition(
                "character_06",
                R.drawable.character_portrait_06,
                Color(0xFF78CC18),
                "Charakter 06",
                wallpaperResId = R.raw.character_wallpaper_06_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_06,
            ),
            CharacterDefinition(
                "character_07",
                R.drawable.character_portrait_07,
                Color(0xFFED7913),
                "Charakter 07",
                wallpaperResId = R.raw.character_wallpaper_07_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_07,
            ),
            CharacterDefinition(
                "character_08",
                R.drawable.character_portrait_08,
                Color(0xFFD9342B),
                "Charakter 08",
                wallpaperResId = R.raw.character_wallpaper_08_video,
                isVideoWallpaper = true,
            ),
            CharacterDefinition(
                "character_09",
                R.drawable.character_portrait_09,
                Color(0xFFDB428F),
                "Charakter 09",
                wallpaperResId = R.raw.character_wallpaper_09_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_09,
            ),
            CharacterDefinition(
                "character_10",
                R.drawable.character_portrait_10,
                Color(0xFFF5C918),
                "Charakter 10",
                wallpaperResId = R.raw.character_wallpaper_10_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_10_fallback,
            ),
            CharacterDefinition(
                "character_11",
                R.drawable.character_portrait_11,
                Color(0xFF0C998B),
                "Charakter 11",
                wallpaperResId = R.raw.character_wallpaper_11_video,
                isVideoWallpaper = true,
                fallbackImageResId = R.drawable.character_wallpaper_11,
            ),
        )

    fun byId(id: String): CharacterDefinition? = all.firstOrNull { it.id == id }

    fun byIndex(index: Int): CharacterDefinition = all[index % all.size]
}
