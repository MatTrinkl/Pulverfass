package com.example.androidapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `welcome screen shows app name and next action`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appName = context.getString(R.string.app_name)
        val nextAction =
            context.getString(
                R.string.welcome_next_action,
                at.aau.pulverfass.shared.network.MessageType.LOGIN_REQUEST.name,
            )

        composeRule.setContent {
            MaterialTheme {
                WelcomeScreen()
            }
        }

        composeRule.onNodeWithText(appName).assertIsDisplayed()
        composeRule.onNodeWithText(nextAction).assertIsDisplayed()
    }

    @Test
    fun `activity onCreate sets compose content`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNotNull(activity)
    }
}
