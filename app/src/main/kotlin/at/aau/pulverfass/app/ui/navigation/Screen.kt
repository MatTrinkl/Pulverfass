package at.aau.pulverfass.app.ui.navigation

/**
 * Zentrale Definition der derzeit vorhandenen Compose-Navigationsziele.
 *
 * Die Routen werden in [at.aau.pulverfass.app.MainActivity] verdrahtet und
 * aktuell nur für den Lobby-Flow sowie einen einfachen Game-Platzhalter
 * verwendet.
 */
sealed class Screen(val route: String) {
    /** Einstiegsscreen der App. */
    object Load : Screen("load")

    /** Lobby-Einstieg für Connect/Create/Join. */
    object Lobby : Screen("lobby")

    /** Warte-/Lobbyraum nach erfolgreichem Join oder Create. */
    object WaitingRoom : Screen("waiting_room")

    /** Platzhalterziel für das eigentliche Spiel. */
    object Game : Screen("game")
}
