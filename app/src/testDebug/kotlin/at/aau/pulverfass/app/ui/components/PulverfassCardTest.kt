package at.aau.pulverfass.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prüft die beiden wiederverwendbaren Card-Container der App.
 *
 * Die Tests stellen sicher, dass Standard- und Pergament-Card ihre Kinder
 * zuverlässig rendern, weil viele Screens diese Container als Layout-Grundlage
 * verwenden.
 */
@RunWith(AndroidJUnit4::class)
class PulverfassCardTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun standard_card_rendert_einfachen_content() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassCard {
                    Text("Standard Card Inhalt")
                }
            }
        }
        composeTestRule.onNodeWithText("Standard Card Inhalt").assertIsDisplayed()
    }

    @Test
    fun standard_card_rendert_mehrere_kinder() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassCard {
                    Text("Erster Text")
                    Text("Zweiter Text")
                }
            }
        }
        composeTestRule.onNodeWithText("Erster Text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zweiter Text").assertIsDisplayed()
    }

    @Test
    fun parchment_card_rendert_einfachen_content() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassParchmentCard {
                    Text("Pergament Card Inhalt")
                }
            }
        }
        composeTestRule.onNodeWithText("Pergament Card Inhalt").assertIsDisplayed()
    }

    @Test
    fun parchment_card_rendert_mehrere_kinder() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PulverfassParchmentCard {
                    Text("Pergament Zeile 1")
                    Text("Pergament Zeile 2")
                }
            }
        }
        composeTestRule.onNodeWithText("Pergament Zeile 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pergament Zeile 2").assertIsDisplayed()
    }
}
