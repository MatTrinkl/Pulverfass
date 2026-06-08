package at.aau.pulverfass.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke-Tests für `@Preview`-Funktionen.
 *
 * Previews werden von normalen Unit-Tests nicht automatisch aufgerufen. Diese
 * Tests rendern jede Preview einmal, damit Preview-Code in Coverage und Compose-
 * Smoke-Tests sichtbar bleibt.
 */
@RunWith(AndroidJUnit4::class)
class ComponentPreviewsSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pulverfass_buttons_preview_rendert_ohne_crash() {
        composeTestRule.setContent { PulverfassButtonsPreview() }
    }

    @Test
    fun pulverfass_buttons_disabled_preview_rendert_ohne_crash() {
        composeTestRule.setContent { PulverfassButtonsDisabledPreview() }
    }

    @Test
    fun pulverfass_cards_preview_rendert_ohne_crash() {
        composeTestRule.setContent { PulverfassCardsPreview() }
    }
}
