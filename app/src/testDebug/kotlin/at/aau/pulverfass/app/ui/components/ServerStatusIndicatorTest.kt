package at.aau.pulverfass.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.network.ServerHealthStatus
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerStatusIndicatorTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_ok_status_description() {
        composeTestRule.setContent {
            AndroidAppTheme {
                ServerStatusIndicator(status = ServerHealthStatus.OK)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Serverstatus: OK")
            .assertIsDisplayed()
    }

    @Test
    fun shows_unreachable_status_description() {
        composeTestRule.setContent {
            AndroidAppTheme {
                ServerStatusIndicator(status = ServerHealthStatus.UNREACHABLE)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Serverstatus: Nicht erreichbar")
            .assertIsDisplayed()
    }
}
