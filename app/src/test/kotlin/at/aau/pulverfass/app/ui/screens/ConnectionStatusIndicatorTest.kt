package at.aau.pulverfass.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionStatusIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `connected status uses green color and connected description`() {
        composeRule.setContent {
            MaterialTheme {
                ConnectionStatusIndicator(status = ConnectionStatus.CONNECTED)
            }
        }

        assertEquals(
            PulverfassColors.Success,
            connectionStatusIndicatorColor(ConnectionStatus.CONNECTED),
        )
        composeRule.onNodeWithContentDescription("Verbunden").fetchSemanticsNode()
    }

    @Test
    fun `disconnected status uses red color and disconnected description`() {
        composeRule.setContent {
            MaterialTheme {
                ConnectionStatusIndicator(status = ConnectionStatus.DISCONNECTED)
            }
        }

        assertEquals(
            PulverfassColors.DangerBright,
            connectionStatusIndicatorColor(ConnectionStatus.DISCONNECTED),
        )
        composeRule.onNodeWithContentDescription("Verbindung getrennt").fetchSemanticsNode()
    }

    @Test
    fun `status change updates connection indicator without refresh`() {
        var status by mutableStateOf(ConnectionStatus.CONNECTED)
        composeRule.setContent {
            MaterialTheme {
                ConnectionStatusIndicator(status = status)
            }
        }
        composeRule.onNodeWithContentDescription("Verbunden").fetchSemanticsNode()

        composeRule.runOnIdle {
            status = ConnectionStatus.DISCONNECTED
        }

        assertTrue(
            composeRule
                .onAllNodesWithContentDescription("Verbunden")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        composeRule.onNodeWithContentDescription("Verbindung getrennt").fetchSemanticsNode()
    }
}
