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
import kotlin.test.assertEquals

/**
 * Prüft die kompakte Serverstatus-Anzeige im UI.
 *
 * Die Tests sichern den Poll-Intervall und die Accessibility-Beschreibungen für
 * erreichbaren, fehlerhaften und nicht erreichbaren Serverzustand ab.
 */
@RunWith(AndroidJUnit4::class)
class ServerStatusIndicatorTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun default_poll_interval_is_ten_seconds() {
        assertEquals(10_000L, HEALTH_POLL_INTERVAL_MS)
    }

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
    fun shows_error_status_description() {
        composeTestRule.setContent {
            AndroidAppTheme {
                ServerStatusIndicator(status = ServerHealthStatus.ERROR)
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Serverstatus: Fehler")
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
