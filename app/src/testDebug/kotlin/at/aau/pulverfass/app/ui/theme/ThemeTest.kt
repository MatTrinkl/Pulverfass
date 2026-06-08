package at.aau.pulverfass.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prüft, ob der globale App-Theme-Wrapper Content in Hell- und Dunkelmodus rendert.
 *
 * Die Tests bleiben bewusst klein, weil hier nur die Compose-Einbettung und nicht
 * einzelne Farbwerte oder Material-Defaults abgesichert werden.
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun android_app_theme_rendert_content_im_light_mode() {
        composeTestRule.setContent {
            AndroidAppTheme {
                Text("Theme Light")
            }
        }

        composeTestRule.onNodeWithText("Theme Light").assertExists()
    }

    @Test
    fun android_app_theme_rendert_content_im_dark_mode() {
        composeTestRule.setContent {
            AndroidAppTheme(darkTheme = true) {
                Text("Theme Dark")
            }
        }

        composeTestRule.onNodeWithText("Theme Dark").assertExists()
    }
}
