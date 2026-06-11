package at.aau.pulverfass.client.ui.components

/**
 * Plattformneutrale Referenz auf ein Video-Asset.
 *
 * [fileName] entspricht dem Ressourcennamen ohne Endung: Android löst ihn über
 * `R.raw` auf, iOS über `NSBundle.mainBundle` (Medien liegen im App-Bundle).
 */
enum class VideoAsset(val fileName: String) {
    STUDIO_INTRO("video_studio_intro"),
    LOADING_SCREEN("video_loading_screen"),
    MAIN_MENU_BACKGROUND("video_main_menu_background"),
    LOBBY_BACKGROUND("video_lobby_background"),
    OPTIONS_BACKGROUND("video_options_background"),
    CHARACTER_PICKER_BACKGROUND("video_character_picker_background"),
    BATTLE_INTRO("video_battle_intro"),
    CHARACTER_WALLPAPER_01("character_wallpaper_01_video"),
    CHARACTER_WALLPAPER_04("character_wallpaper_04_video"),
    CHARACTER_WALLPAPER_05("character_wallpaper_05_video"),
    CHARACTER_WALLPAPER_08("character_wallpaper_08_video"),
    CHARACTER_WALLPAPER_10("character_wallpaper_10_video"),
}
