package at.aau.pulverfass.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E-naher Activity-Test für den echten App-Startpfad.
 *
 * Der Test bleibt ignoriert, weil Video-Playback und echte Activity-Lifecycle-
 * Effekte lokal und in CI instabil sein können. Er dokumentiert trotzdem den
 * gewünschten Pfad vom Ladebildschirm über das Hauptmenü bis in die Lobby.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Ignore("Requires real device with video playback - E2E test")
    @Test
    fun main_activity_navigates_from_load_through_main_menu_to_lobby() {
        // Wartet, bis LoadScreen-Preload und Mindestanzeigezeit durch sind.
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            try {
                composeTestRule
                    .onAllNodesWithText("START")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            } catch (e: IllegalArgumentException) {
                false
            }
        }
        composeTestRule.onNodeWithText("START").assertExists()
        composeTestRule.onNodeWithText("OPTIONEN").assertExists()
        composeTestRule.onNodeWithText("BEENDEN").assertExists()

        // Start muss in den Lobby-Einstieg navigieren.
        composeTestRule.onNodeWithTag("MenuButton_Start").performClick()

        // Wartet, bis der Lobby-Screen sichtbar ist.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeTestRule
                    .onAllNodesWithText("SPIEL-LOBBY", ignoreCase = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            } catch (e: IllegalArgumentException) {
                false
            }
        }
        composeTestRule.onNodeWithText("SPIEL-LOBBY", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("LOBBY ERSTELLEN", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("LOBBY BEITRETEN", ignoreCase = true).assertExists()
    }
}
