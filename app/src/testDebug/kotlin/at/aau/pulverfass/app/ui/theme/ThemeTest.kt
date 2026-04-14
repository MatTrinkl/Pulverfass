package at.aau.pulverfass.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// testet design & erscheinungsbild der app
@RunWith(AndroidJUnit4::class)
class ThemeTest {
    // erstellt eine testregel fÃ¼r compose komponenten
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun android_app_theme_rendert_content_im_light_mode() {
        // setzt den inhalt fÃ¼r hellen modus
        composeTestRule.setContent {
            AndroidAppTheme {
                Text("Theme Light")
            }
        }

        // prÃ¼ft ob der text im hellen modus korrekt angezeigt wird
        composeTestRule.onNodeWithText("Theme Light").assertExists()
    }

    @Test
    fun android_app_theme_rendert_content_im_dark_mode() {
        // setzt den inhalt fÃ¼r dunklen modus
        composeTestRule.setContent {
            AndroidAppTheme(darkTheme = true) {
                Text("Theme Dark")
            }
        }

        // prÃ¼ft ob der text im dunklen modus korrekt angezeigt wird
        composeTestRule.onNodeWithText("Theme Dark").assertExists()
    }
}
