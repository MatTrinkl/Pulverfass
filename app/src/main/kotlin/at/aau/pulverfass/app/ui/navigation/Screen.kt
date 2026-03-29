package at.aau.pulverfass.app.ui.navigation

// klasse für die verschiedenen bildschirme & routen
sealed class Screen(val route: String) {
    // route für ladebildschirm
    object Load : Screen("load")

    // route für lobby
    object Lobby : Screen("lobby")

    // route für warteraum
    object WaitingRoom : Screen("waiting_room")

    // route für hauptspiel
    object Game : Screen("game")
}
