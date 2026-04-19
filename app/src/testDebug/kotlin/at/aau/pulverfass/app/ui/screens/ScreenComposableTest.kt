package at.aau.pulverfass.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenComposableTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun load_screen_shows_version_and_navigates_to_lobby() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Load.route,
                ) {
                    composable(Screen.Load.route) {
                        LoadScreen(navController)
                    }
                    composable(Screen.Lobby.route) {
                        Text("Lobby destination")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Pulverfass").assertIsDisplayed()
        composeTestRule.onNodeWithText("v1.0.0").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(2_100)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Lobby destination").assertIsDisplayed()
    }

    @Test
    fun lobby_screen_shows_primary_lobby_actions() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val controller = LobbyController()
                LobbyScreen(
                    navController = navController,
                    controller = controller,
                )
            }
        }

        composeTestRule.onNodeWithText("Spiel-Lobby").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spielername eingeben").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby erstellen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lobby beitreten").assertIsDisplayed()
        composeTestRule.onNodeWithText("Karte direkt testen").assertIsDisplayed()
    }

    @Test
    fun waiting_room_shows_host_state_and_player_name() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val controller = LobbyController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.WaitingRoom.route + "/AB12/true/Carol",
                ) {
                    composable(
                        route = Screen.WaitingRoom.route + "/{lobbyCode}/{isHost}/{playerName}",
                        arguments =
                            listOf(
                                navArgument("lobbyCode") { type = NavType.StringType },
                                navArgument("isHost") { type = NavType.BoolType },
                                navArgument("playerName") { type = NavType.StringType },
                            ),
                    ) {
                        val lobbyCode = it.arguments?.getString("lobbyCode").orEmpty()
                        val isHost = it.arguments?.getBoolean("isHost") ?: false
                        val playerName = it.arguments?.getString("playerName").orEmpty()
                        WaitingRoomScreen(
                            navController = navController,
                            controller = controller,
                            lobbyCode = lobbyCode,
                            isHost = isHost,
                            playerName = playerName,
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Lobby: AB12").assertIsDisplayed()
        composeTestRule.onNodeWithText("Du bist der Host").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carol").assertIsDisplayed()
    }

    @Test
    fun game_screen_shows_interactive_map_and_region_controls() {
        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreen()
            }
        }

        composeTestRule.onNodeWithTag("game_map_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Mit einem Finger verschieben, mit zwei Fingern zoomen und Regionen direkt antippen.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("region_button_northwest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ausgewaehlt: Nordwest").assertIsDisplayed()
    }
}
