package at.aau.pulverfass.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun main_activity_navigates_from_load_to_lobby() {
        // warte bis StudioIntroScreen durchgelaufen ist (safety timeout 10s)
        // und LoadScreen mit "Pulverfass" text erscheint
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule
                .onAllNodesWithText("Pulverfass")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("Pulverfass").assertExists()

        // warte bis LoadScreen Asset-Preload durch ist und Lobby erscheint
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
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
