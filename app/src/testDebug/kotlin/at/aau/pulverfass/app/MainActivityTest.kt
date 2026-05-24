package at.aau.pulverfass.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun main_activity_navigates_from_load_through_main_menu_to_lobby() {
        // 2. Warte bis LoadScreen-Preload und min-display-time durch sind
        //    und MainMenu mit "START" Button erscheint (15s).
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithText("START")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("START").assertExists()
        composeTestRule.onNodeWithText("OPTIONS").assertExists()
        composeTestRule.onNodeWithText("EXIT").assertExists()

        // 3. Click START → sollte zur Lobby navigieren.
        composeTestRule.onNodeWithText("START").performClick()

        // 4. Warte bis Lobby mit "Spiel-Lobby" erscheint (10s).
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule
                .onAllNodesWithText("Spiel-Lobby")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Spiel-Lobby").assertExists()
        composeTestRule.onNodeWithText("Lobby erstellen").assertExists()
        composeTestRule.onNodeWithText("Lobby beitreten").assertExists()
    }
}
