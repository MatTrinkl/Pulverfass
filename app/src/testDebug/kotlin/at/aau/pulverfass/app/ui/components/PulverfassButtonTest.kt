package at.aau.pulverfass.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sichert die gemeinsamen Erwartungen der Pulverfass-Buttonvarianten ab.
 *
 * Die Tests prüfen Textnormalisierung, Klickbarkeit, ausgewählte Zustände und
 * deaktivierte Zustände für Primary-, Secondary- und Danger-Buttons.
 */
@RunWith(AndroidJUnit4::class)
class PulverfassButtonTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun primary_button_zeigt_text_in_uppercase() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonPrimary(text = "Würfeln", onClick = {})
            }
        }

        composeTestRule.onNodeWithText("WÜRFELN").assertIsDisplayed()
    }

    @Test
    fun primary_button_triggert_callback_bei_klick() {
        var clickCount = 0
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonPrimary(text = "Würfeln", onClick = { clickCount++ })
            }
        }
        composeTestRule.onNodeWithText("WÜRFELN").performClick()
        assert(clickCount == 1)
    }

    @Test
    fun primary_button_ist_enabled_per_default() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonPrimary(text = "Würfeln", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("WÜRFELN").assertIsEnabled()
    }

    @Test
    fun primary_button_disabled_zeigt_disabled_state() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonPrimary(
                    text = "Würfeln",
                    onClick = {},
                    enabled = false,
                )
            }
        }
        composeTestRule.onNodeWithText("WÜRFELN").assertIsNotEnabled()
    }

    @Test
    fun secondary_button_zeigt_text() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonSecondary(text = "Angriff", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("ANGRIFF").assertIsDisplayed()
    }

    @Test
    fun secondary_button_triggert_callback_bei_klick() {
        var clicked = false
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonSecondary(text = "Verstärken", onClick = { clicked = true })
            }
        }
        composeTestRule.onNodeWithText("VERSTÄRKEN").performClick()
        assert(clicked)
    }

    @Test
    fun secondary_button_selected_state_bleibt_klickbar() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonSecondary(
                    text = "Angriff",
                    onClick = {},
                    selected = true,
                )
            }
        }
        composeTestRule.onNodeWithText("ANGRIFF").assertIsDisplayed()
        composeTestRule.onNodeWithText("ANGRIFF").assertHasClickAction()
    }

    @Test
    fun secondary_button_disabled_zeigt_disabled_state() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonSecondary(
                    text = "Verstärken",
                    onClick = {},
                    enabled = false,
                )
            }
        }
        composeTestRule.onNodeWithText("VERSTÄRKEN").assertIsNotEnabled()
    }

    @Test
    fun danger_button_zeigt_text() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonDanger(text = "Rückzug", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("RÜCKZUG").assertIsDisplayed()
    }

    @Test
    fun danger_button_triggert_callback_bei_klick() {
        var clicked = false
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonDanger(text = "Rückzug", onClick = { clicked = true })
            }
        }
        composeTestRule.onNodeWithText("RÜCKZUG").performClick()
        assert(clicked)
    }

    @Test
    fun danger_button_disabled_zeigt_disabled_state() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassButtonDanger(
                    text = "Rückzug",
                    onClick = {},
                    enabled = false,
                )
            }
        }
        composeTestRule.onNodeWithText("RÜCKZUG").assertIsNotEnabled()
    }
}
