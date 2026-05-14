package at.aau.pulverfass.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// testet den studio intro screen und seine navigation
@RunWith(AndroidJUnit4::class)
class StudioIntroScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun studio_intro_screen_rendert_ohne_crash() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.StudioIntro.route,
                ) {
                    composable(Screen.StudioIntro.route) {
                        StudioIntroScreen(navController)
                    }
                    composable(Screen.Load.route) {
                        Text("Load destination")
                    }
                }
            }
        }
        // wenn wir hier ankommen ohne crash ist alles ok
    }

    @Test
    fun studio_intro_navigiert_automatisch_nach_safety_timeout() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.StudioIntro.route,
                ) {
                    composable(Screen.StudioIntro.route) {
                        StudioIntroScreen(navController)
                    }
                    composable(Screen.Load.route) {
                        Text("Load destination")
                    }
                }
            }
        }
        // simulate timeout passing (5 sec + buffer)
        composeTestRule.mainClock.advanceTimeBy(11_000L)
        composeTestRule.waitForIdle()
        // nach safety timeout sollten wir auf Load sein
        composeTestRule.onNodeWithText("Load destination").assertExists()
    }
}
