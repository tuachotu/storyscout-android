package app.storyscout.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class StoryScoutSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun showsAccessFlow() {
        compose.onNodeWithText("StoryScout").assertIsDisplayed()
        compose.onNodeWithText("Step 1 of 2").assertIsDisplayed()
        compose.onNodeWithText("Enter your access code").assertIsDisplayed()
    }
}
