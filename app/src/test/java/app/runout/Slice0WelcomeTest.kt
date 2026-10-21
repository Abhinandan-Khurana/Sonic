package app.runout

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.runout.playback.DeckState
import app.runout.ui.RoomState
import app.runout.ui.SetupScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi", application = Application::class)
class Slice0WelcomeTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `first run welcome Back exits without completing onboarding`() {
        var finishes = 0
        compose.setContent {
            SetupScreen(RoomState(emptyList(), false, null, DeckState(), false, "12:00").copy(onboardingComplete = false), RecordingActions(), 0) { finishes++ }
        }
        compose.onNodeWithTag("setup-offline").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals("Back must not finish onboarding", 0, finishes)
        org.junit.Assert.assertTrue("Back must exit the activity", compose.activity.isFinishing)
    }

    @Test fun `explicit offline finish still completes onboarding`() {
        var finishes = 0
        compose.setContent {
            SetupScreen(RoomState(emptyList(), false, null, DeckState(), false, "12:00").copy(onboardingComplete = false), RecordingActions(), 0) { finishes++ }
        }
        compose.onNodeWithTag("setup-offline").performClick()
        compose.onNodeWithTag("setup-finish").performClick()
        // The greeting pops in, then hands over to the room.
        compose.onNodeWithTag("setup-welcome").assertExists()
        compose.waitForIdle()
        assertEquals(1, finishes)
    }
}
