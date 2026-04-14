package at.aau.pulverfass.app

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun main_activity_navigates_from_load_to_waiting_room() {
        composeTestRule.onNodeWithText("Pulverfass").assertExists()

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule
                .onAllNodes(hasSetTextAction())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("Alice")
        composeTestRule.onNodeWithText("Lobby beitreten").performClick()
        composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("1234")
        composeTestRule.onNodeWithText("Warteraum betreten").performClick()

        composeTestRule.onNodeWithText("Lobby: 1234").assertExists()
        composeTestRule.onNodeWithText("Warte auf Host", substring = true).assertExists()
    }
}
