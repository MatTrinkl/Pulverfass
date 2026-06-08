package at.aau.pulverfass.app.ui.navigation

/**
 * Zentrale Definition der derzeit vorhandenen Compose-Navigationsziele.
 */
sealed class Screen(val route: String) {
    /** Studio-Intro-Video vor dem LoadScreen. */
    object StudioIntro : Screen("studio_intro")

    /** Einstiegsscreen der App. */
    object Load : Screen("load")

    /** Hauptmenü mit Video-Hintergrund, Logo und Start-/Options-/Exit-Aktionen. */
    object MainMenu : Screen("main_menu")

    /** Lobby-Einstieg für Connect/Create/Join. */
    object Lobby : Screen("lobby")

    /** Warte-/Lobbyraum nach erfolgreichem Join oder Create. */
    object WaitingRoom : Screen("waiting_room")

    /** Spielvorbereitung mit Countdown und Asset-Übergang vor der Karte. */
    object LoadGame : Screen("load_game")

    /** Eigentliches Spiel mit Karte, HUD und Phasensteuerung. */
    object Game : Screen("game")

    /** Optionsscreen für Anzeigename und Audio-Schalter. */
    object Options : Screen("options")
}
