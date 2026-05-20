package at.aau.pulverfass.app.ui.navigation

/**
 * Zentrale Definition der derzeit vorhandenen Compose-Navigationsziele.
 */
sealed class Screen(val route: String) {
    /** Studio-Intro-Video vor dem LoadScreen. */
    object StudioIntro : Screen("studio_intro")

    /** Einstiegsscreen der App. */
    object Load : Screen("load")

    /** Hauptmenü mit Video-BG, animiertem Logo und Start/Options/Exit. */
    object MainMenu : Screen("main_menu")

    /** Lobby-Einstieg für Connect/Create/Join. */
    object Lobby : Screen("lobby")

    /** Warte-/Lobbyraum nach erfolgreichem Join oder Create. */
    object WaitingRoom : Screen("waiting_room")

    /** route für spielvorbereitung */
    object LoadGame : Screen("load_game")

    /** Platzhalterziel für das eigentliche Spiel. */
    object Game : Screen("game")
}
