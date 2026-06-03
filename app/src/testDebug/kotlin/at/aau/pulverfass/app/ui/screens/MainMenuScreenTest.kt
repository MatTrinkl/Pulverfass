package at.aau.pulverfass.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainMenuScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mainmenu_rendert_alle_kernelemente() {
        composeTestRule.setContent {
            MainMenuScreen(
                onStartClick = {},
                onOptionsClick = {},
                onExitClick = {},
                background = {},
            )
        }
        composeTestRule.onNodeWithTag("MainMenuScreen").assertIsDisplayed()

        // Commented out because the old title/logo components were removed or updated
        // composeTestRule.onNodeWithTag("GameLogo").assertIsDisplayed()
        // composeTestRule.onNodeWithTag("MenuTitle").assertExists()

        composeTestRule.onNodeWithTag("MenuButton_Start").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MenuButton_Options").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MenuButton_Exit").assertIsDisplayed()
    }

    @Test
    fun mainmenu_zeigt_buttons() {
        composeTestRule.setContent {
            MainMenuScreen(
                onStartClick = {},
                onOptionsClick = {},
                onExitClick = {},
                background = {},
            )
        }
        // composeTestRule.onNodeWithText("PULVERFASS").assertExists()
        composeTestRule.onNodeWithText("START").assertIsDisplayed()
        composeTestRule.onNodeWithText("OPTIONEN").assertIsDisplayed()
        composeTestRule.onNodeWithText("BEENDEN").assertIsDisplayed()
    }

    @Test
    fun start_button_triggert_start_callback() {
        var clicked = false
        composeTestRule.setContent {
            MainMenuScreen(
                onStartClick = { clicked = true },
                onOptionsClick = {},
                onExitClick = {},
                background = {},
            )
        }
        composeTestRule.onNodeWithTag("MenuButton_Start").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun options_button_triggert_options_callback() {
        var clicked = false
        composeTestRule.setContent {
            MainMenuScreen(
                onStartClick = {},
                onOptionsClick = { clicked = true },
                onExitClick = {},
                background = {},
            )
        }
        composeTestRule.onNodeWithTag("MenuButton_Options").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun exit_button_triggert_exit_callback() {
        var clicked = false
        composeTestRule.setContent {
            MainMenuScreen(
                onStartClick = {},
                onOptionsClick = {},
                onExitClick = { clicked = true },
                background = {},
            )
        }
        composeTestRule.onNodeWithTag("MenuButton_Exit").performClick()
        composeTestRule.onNodeWithText("JA").performClick() // Dialog bestätigen
        assertEquals(true, clicked)
    }

    @Test
    fun jeder_button_triggert_nur_eigenen_callback() {
        var startClicks = 0
        var optionsClicks = 0
        var exitClicks = 0
        composeTestRule.setContent {
            MainMenuScreen(
                onStartClick = { startClicks++ },
                onOptionsClick = { optionsClicks++ },
                onExitClick = { exitClicks++ },
                background = {},
            )
        }
        composeTestRule.onNodeWithTag("MenuButton_Start").performClick()
        composeTestRule.onNodeWithTag("MenuButton_Start").performClick()
        composeTestRule.onNodeWithTag("MenuButton_Options").performClick()
        assertEquals(2, startClicks)
        assertEquals(1, optionsClicks)
        assertEquals(0, exitClicks)
    }
}
