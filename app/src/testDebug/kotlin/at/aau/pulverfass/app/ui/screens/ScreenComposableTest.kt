package at.aau.pulverfass.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        composeTestRule.onNodeWithText("Pulverfass").assertExists()
        composeTestRule.onNodeWithText("v1.0.0").assertExists()

        composeTestRule.mainClock.advanceTimeBy(2_100)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Lobby destination").assertExists()
    }

    @Test
    fun lobby_join_flow_toggles_between_join_and_default_actions() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Lobby.route,
                ) {
                    composable(Screen.Lobby.route) {
                        LobbyScreen(navController)
                    }
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
                        WaitingRoomScreen(navController, lobbyCode, isHost, playerName)
                    }
                }
            }
        }

        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("Bob")
        composeTestRule.onNodeWithText("Lobby beitreten").assertIsEnabled().performClick()
        composeTestRule.onNodeWithText("Warteraum betreten").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Abbrechen").assertExists().performClick()
        composeTestRule.onNodeWithText("Lobby erstellen").assertExists()
        composeTestRule.onNodeWithText("Lobby beitreten").assertExists()
    }

    @Test
    fun lobby_create_flow_shows_host_waiting_room_state() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Lobby.route,
                ) {
                    composable(Screen.Lobby.route) {
                        LobbyScreen(navController)
                    }
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
                        WaitingRoomScreen(navController, lobbyCode, isHost, playerName)
                    }
                }
            }
        }

        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("Carol")
        composeTestRule.onNodeWithText("Lobby erstellen").assertIsEnabled().performClick()

        composeTestRule.onNodeWithText("Lobby:", substring = true).assertExists()
        composeTestRule.onNodeWithText("Du bist der Host").assertExists()
        composeTestRule.onNodeWithText("Carol").assertExists()
        composeTestRule.onNodeWithText("(Host)").assertExists()
        composeTestRule.onNodeWithText("Spiel starten").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Mindestens 2 Spieler", substring = true).assertExists()
    }

    @Test
    fun game_screen_shows_placeholder() {
        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreen()
            }
        }

        composeTestRule.onNodeWithText("Risiko-Weltkarte", substring = true).assertExists()
    }
}
