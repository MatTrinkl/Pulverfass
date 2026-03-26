package at.aau.pulverfass.app.ui.navigation

import kotlin.test.Test
import kotlin.test.assertTrue

class ScreenRouteTest {
    @Test
    fun `all screen routes are unique`() {
        val screens = listOf(
            Screen.Load,
            Screen.Lobby,
            Screen.WaitingRoom,
            Screen.Game,
        )
        val routes = screens.map { it.route }
        val uniqueRoutes = routes.distinct()

        assertTrue(routes.size == uniqueRoutes.size, "Found duplicate routes in Screen objects")
    }

    @Test
    fun `routes do not contain spaces`() {
        val screens = listOf(
            Screen.Load,
            Screen.Lobby,
            Screen.WaitingRoom,
            Screen.Game,
        )
        screens.forEach { screen ->
            assertTrue(
                !screen.route.contains(" "),
                "Route for ${screen.javaClass.simpleName} contains spaces",
            )
        }
    }
}
