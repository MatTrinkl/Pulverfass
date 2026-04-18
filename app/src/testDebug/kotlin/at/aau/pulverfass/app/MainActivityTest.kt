package at.aau.pulverfass.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        composeTestRule.onNodeWithText("Pulverfass").assertExists()

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule
                .onAllNodes(hasSetTextAction())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("Spiel-Lobby").assertExists()
        composeTestRule.onNodeWithText("Lobby erstellen").assertExists()
        composeTestRule.onNodeWithText("Lobby beitreten").assertExists()
    }
}
