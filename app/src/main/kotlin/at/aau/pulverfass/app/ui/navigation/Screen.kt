package at.aau.pulverfass.app.ui.navigation

sealed class Screen(val route: String) {
    object Load : Screen("load")

    object Lobby : Screen("lobby")

    object WaitingRoom : Screen("waiting_room")

    object Game : Screen("game")
}
