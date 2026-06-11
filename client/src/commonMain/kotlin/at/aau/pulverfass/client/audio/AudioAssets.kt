package at.aau.pulverfass.client.audio

/**
 * Plattformneutrale Referenz auf einen Musiktitel.
 *
 * [fileName] entspricht dem Ressourcennamen ohne Endung: Android löst ihn über
 * `R.raw` auf, iOS über `NSBundle.mainBundle` (Medien liegen im App-Bundle).
 */
enum class MusicTrack(val fileName: String) {
    MAIN_MENU("music_main_menu"),
    MAIN_MENU_ALT("music_main_menu_alt"),
    MAIN_THEME_ALT("music_main_theme_alt"),
    LOBBY_MENU("music_lobby_menu"),
    LOBBY_WAITING("music_lobby_waiting"),
    OPTIONS_MENU("music_options_menu"),
    GAMEPLAY_LOOP("music_gameplay_loop"),
    GAME_TENSION("music_game_tension"),
    GAME_VICTORY("music_game_victory"),
    GAME_LOSS("music_game_loss"),
    CHARACTER_PICKER("music_character_picker"),
    STUDIO_INTRO("music_studio_intro"),
    BONUS_TRACK_01("music_bonus_track_01"),
    BONUS_TRACK_02("music_bonus_track_02"),
    BONUS_TRACK_03("music_bonus_track_03"),
}

/**
 * Plattformneutrale Referenz auf einen One-Shot-Soundeffekt.
 */
enum class SfxSound(val fileName: String) {
    UI_CLICK("sfx_ui_click"),
    CARD_SELECT("sfx_card_select"),
    CARD_SELECT_ALT("sfx_card_select_alt"),
    ATTACK_CONFIRM("sfx_attack_confirm"),
    ATTACK_ROLL("sfx_attack_roll"),
}
