package at.aau.pulverfass.app.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractiveGameMapTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun interactive_game_map_renders_canvas_and_region_counter() {
        val region = PulverfassMapDefaults.regions.first()

        composeTestRule.setContent {
            AndroidAppTheme {
                InteractiveGameMap(
                    regions = listOf(region),
                    selectedRegionId = region.id,
                    onRegionSelected = {},
                    regionStates =
                        mapOf(
                            region.id to
                                GameMapRegionState(
                                    ownerPlayerId = "p1",
                                    ownerName = "Alice",
                                    troopCount = 7,
                                    accentColor = Color(0xFF1E88E5),
                                ),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithTag("game_map_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("region_button_${region.id}").assertIsDisplayed()
    }

    @Test
    fun interactive_game_map_handles_gesture_and_tap_with_background_painter() {
        val region = PulverfassMapDefaults.regions.first()
        val selectedRegions = mutableListOf<String>()

        composeTestRule.setContent {
            AndroidAppTheme {
                InteractiveGameMap(
                    regions = listOf(region),
                    selectedRegionId = null,
                    onRegionSelected = { selectedRegions += it.id },
                    backgroundPainter = ColorPainter(Color(0xFF0A3D62)),
                )
            }
        }

        composeTestRule.onNodeWithTag("game_map_canvas").performTouchInput {
            swipeLeft()
            swipeUp()
            down(center)
            up()
        }
        composeTestRule.waitForIdle()
    }
}
