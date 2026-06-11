package at.aau.pulverfass.client.lobby

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.character_portrait_01
import at.aau.pulverfass.client.resources.character_portrait_02
import at.aau.pulverfass.client.resources.character_portrait_03
import at.aau.pulverfass.client.resources.character_portrait_04
import at.aau.pulverfass.client.resources.character_portrait_05
import at.aau.pulverfass.client.resources.character_portrait_06
import at.aau.pulverfass.client.resources.character_portrait_07
import at.aau.pulverfass.client.resources.character_portrait_08
import at.aau.pulverfass.client.resources.character_portrait_09
import at.aau.pulverfass.client.resources.character_portrait_10
import at.aau.pulverfass.client.resources.character_portrait_11
import at.aau.pulverfass.client.resources.character_wallpaper_01_fallback
import at.aau.pulverfass.client.resources.character_wallpaper_02
import at.aau.pulverfass.client.resources.character_wallpaper_03
import at.aau.pulverfass.client.resources.character_wallpaper_04_fallback
import at.aau.pulverfass.client.resources.character_wallpaper_05_fallback
import at.aau.pulverfass.client.resources.character_wallpaper_06
import at.aau.pulverfass.client.resources.character_wallpaper_07
import at.aau.pulverfass.client.resources.character_wallpaper_09
import at.aau.pulverfass.client.resources.character_wallpaper_10_fallback
import at.aau.pulverfass.client.resources.character_wallpaper_11
import at.aau.pulverfass.client.ui.components.VideoAsset
import org.jetbrains.compose.resources.DrawableResource

/**
 * Beschreibt einen auswählbaren Lobby-Charakter inklusive UI-Assets.
 *
 * Die [id] ist bewusst unabhängig vom Dateinamen. Sie wird im Lobby-Protokoll
 * übertragen und lokal persistiert, weshalb sie stabil bleiben muss, auch wenn
 * Portraits oder Wallpaper später ausgetauscht werden.
 *
 * @param id stabile fachliche Charakter-ID für Server, Persistenz und Tests.
 * @param drawableRes rundes Portrait für Lobby, Playerliste und Spielscreen.
 * @param color Akzentfarbe des Charakters, nicht die Spielerfarbe auf der Karte.
 * @param displayName lesbarer Name für ContentDescription und Debugging.
 * @param wallpaperImage statisches Wallpaper für den Character-Picker.
 * @param wallpaperVideo Video-Wallpaper, falls der Charakter eines hat.
 * @param fallbackImageResId optionales Standbild, falls ein Video nicht gerendert werden kann.
 */
data class CharacterDefinition(
    val id: String,
    val drawableRes: DrawableResource,
    val color: Color,
    val displayName: String,
    val wallpaperImage: DrawableResource? = null,
    val wallpaperVideo: VideoAsset? = null,
    val fallbackImageResId: DrawableResource? = null,
) {
    /** `true`, wenn der Character-Picker ein Video-Wallpaper abspielen soll. */
    val isVideoWallpaper: Boolean get() = wallpaperVideo != null
}

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
                Res.drawable.character_portrait_01,
                Color(0xFF7B2CBF),
                "Charakter 01",
                wallpaperVideo = VideoAsset.CHARACTER_WALLPAPER_01,
                fallbackImageResId = Res.drawable.character_wallpaper_01_fallback,
            ),
            CharacterDefinition(
                "character_02",
                Res.drawable.character_portrait_02,
                Color(0xFF1565C0),
                "Charakter 02",
                wallpaperImage = Res.drawable.character_wallpaper_02,
            ),
            CharacterDefinition(
                "character_03",
                Res.drawable.character_portrait_03,
                Color(0xFF00897B),
                "Charakter 03",
                wallpaperImage = Res.drawable.character_wallpaper_03,
            ),
            CharacterDefinition(
                "character_04",
                Res.drawable.character_portrait_04,
                Color(0xFF00A6D6),
                "Charakter 04",
                wallpaperVideo = VideoAsset.CHARACTER_WALLPAPER_04,
                fallbackImageResId = Res.drawable.character_wallpaper_04_fallback,
            ),
            CharacterDefinition(
                "character_05",
                Res.drawable.character_portrait_05,
                Color(0xFFE76F00),
                "Charakter 05",
                wallpaperVideo = VideoAsset.CHARACTER_WALLPAPER_05,
                fallbackImageResId = Res.drawable.character_wallpaper_05_fallback,
            ),
            CharacterDefinition(
                "character_06",
                Res.drawable.character_portrait_06,
                Color(0xFFD81B60),
                "Charakter 06",
                wallpaperImage = Res.drawable.character_wallpaper_06,
            ),
            CharacterDefinition(
                "character_07",
                Res.drawable.character_portrait_07,
                Color(0xFFC62828),
                "Charakter 07",
                wallpaperImage = Res.drawable.character_wallpaper_07,
            ),
            CharacterDefinition(
                "character_08",
                Res.drawable.character_portrait_08,
                Color(0xFFFF4081),
                "Charakter 08",
                wallpaperVideo = VideoAsset.CHARACTER_WALLPAPER_08,
            ),
            CharacterDefinition(
                "character_09",
                Res.drawable.character_portrait_09,
                Color(0xFF8E24AA),
                "Charakter 09",
                wallpaperImage = Res.drawable.character_wallpaper_09,
            ),
            CharacterDefinition(
                "character_10",
                Res.drawable.character_portrait_10,
                Color(0xFFFBC02D),
                "Charakter 10",
                wallpaperVideo = VideoAsset.CHARACTER_WALLPAPER_10,
                fallbackImageResId = Res.drawable.character_wallpaper_10_fallback,
            ),
            CharacterDefinition(
                "character_11",
                Res.drawable.character_portrait_11,
                Color(0xFF43A047),
                "Charakter 11",
                wallpaperImage = Res.drawable.character_wallpaper_11,
            ),
        )

    fun byId(id: String): CharacterDefinition? = all.firstOrNull { it.id == id }

    fun byIndex(index: Int): CharacterDefinition = all[index % all.size]
}
