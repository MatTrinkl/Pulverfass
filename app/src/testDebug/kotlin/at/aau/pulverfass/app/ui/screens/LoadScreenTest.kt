package at.aau.pulverfass.app.ui.screens

import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.ui.navigation.Screen
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MAIN_MENU_MARKER = "MainMenuMarker"

@RunWith(AndroidJUnit4::class)
class LoadScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var capturedNav: NavHostController? = null

    private fun setupScreen(
        preloadAssets: suspend (Resources, (Int, Int) -> Unit) -> Unit =
            { _, cb -> cb(1, 1) },
        minDisplayTimeMs: Long = 60_000L,
        loadingTextIntervalMs: Long = 2_200L,
        loadingTexts: List<String> = LoadingTexts,
    ) {
        composeTestRule.setContent {
            val nav = rememberNavController()
            capturedNav = nav
            NavHost(navController = nav, startDestination = Screen.Load.route) {
                composable(Screen.Load.route) {
                    LoadScreen(
                        navController = nav,
                        preloadAssets = preloadAssets,
                        minDisplayTimeMs = minDisplayTimeMs,
                        loadingTextIntervalMs = loadingTextIntervalMs,
                        loadingTexts = loadingTexts,
                        background = {},
                    )
                }
                composable(Screen.MainMenu.route) { Text(MAIN_MENU_MARKER) }
            }
        }
    }

    @Test
    fun loadscreen_rendert_mit_test_tag() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(preloadAssets = { _, _ -> delay(Long.MAX_VALUE) })
        composeTestRule.onNodeWithTag("LoadScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("LoadProgress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("LoadingText").assertIsDisplayed()
    }

    @Test
    fun loadscreen_zeigt_initial_ersten_loading_text() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(preloadAssets = { _, _ -> delay(Long.MAX_VALUE) })
        composeTestRule.onNodeWithText(LoadingTexts.first()).assertIsDisplayed()
    }

    @Test
    fun loadscreen_rotiert_loading_texts_nach_intervall() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(
            preloadAssets = { _, _ -> delay(Long.MAX_VALUE) },
            loadingTextIntervalMs = 1_000L,
        )
        composeTestRule.onNodeWithText(LoadingTexts[0]).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(1_100L)
        composeTestRule.onNodeWithText(LoadingTexts[1]).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(1_100L)
        composeTestRule.onNodeWithText(LoadingTexts[2]).assertIsDisplayed()
    }

    @Test
    fun loadscreen_wrapped_loading_texts_am_ende() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(
            preloadAssets = { _, _ -> delay(Long.MAX_VALUE) },
            loadingTextIntervalMs = 500L,
        )
        // (size + 1) ticks → wraps once → index = (size+1) % size = 1
        val ticks = LoadingTexts.size + 1
        composeTestRule.mainClock.advanceTimeBy(ticks * 500L + 50L)
        composeTestRule.onNodeWithText(LoadingTexts[1]).assertIsDisplayed()
    }

    @Test
    fun loadscreen_navigiert_nach_preload_und_min_time() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(
            preloadAssets = { _, cb -> cb(1, 1) },
            minDisplayTimeMs = 1_500L,
        )
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        assertEquals(Screen.Load.route, capturedNav?.currentDestination?.route)

        composeTestRule.mainClock.advanceTimeBy(1_500L)
        composeTestRule.waitForIdle()
        assertEquals(Screen.MainMenu.route, capturedNav?.currentDestination?.route)
    }

    @Test
    fun loadscreen_navigiert_nicht_wenn_preload_unfertig() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(
            preloadAssets = { _, _ -> delay(Long.MAX_VALUE) },
            minDisplayTimeMs = 500L,
        )
        composeTestRule.mainClock.advanceTimeBy(5_000L)
        composeTestRule.waitForIdle()
        assertEquals(Screen.Load.route, capturedNav?.currentDestination?.route)
    }

    @Test
    fun loadscreen_navigiert_nicht_wenn_min_time_nicht_elapsed() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(
            preloadAssets = { _, cb -> cb(1, 1) },
            minDisplayTimeMs = 10_000L,
        )
        composeTestRule.mainClock.advanceTimeBy(2_000L)
        composeTestRule.waitForIdle()
        assertEquals(Screen.Load.route, capturedNav?.currentDestination?.route)
    }

    @Test
    fun loadscreen_zeigt_error_bei_preload_fehler() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(
            preloadAssets = { _, _ -> throw IllegalStateException("Karte kaputt") },
            minDisplayTimeMs = 500L,
        )
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Karte kaputt").assertIsDisplayed()
    }

    @Test
    fun loadscreen_navigiert_nicht_bei_preload_fehler() {
        composeTestRule.mainClock.autoAdvance = false
        setupScreen(
            preloadAssets = { _, _ -> throw IllegalStateException("Karte kaputt") },
            minDisplayTimeMs = 500L,
        )
        composeTestRule.mainClock.advanceTimeBy(5_000L)
        composeTestRule.waitForIdle()
        assertEquals(Screen.Load.route, capturedNav?.currentDestination?.route)
    }
}
