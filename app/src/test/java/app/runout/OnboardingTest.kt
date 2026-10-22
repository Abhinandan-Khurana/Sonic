package app.runout

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.runout.playback.DeckState
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekSession
import app.runout.ui.RoomState
import app.runout.ui.SetupScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The onboarding steps after sign-in: one decision per screen, and each answer moves on by itself. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi", application = Application::class)
class OnboardingTest {
    @get:Rule val compose = createComposeRule()

    private val connected = RoomState(emptyList(), false, null, DeckState(), false, "12:00")
        .copy(onboardingComplete = false, soulseekSession = SoulseekSession(SoulseekConnection.CONNECTED, "listener"))

    private class Recording : RecordingActions() {
        var folderAsked = 0
        var shareAsked = 0
        var sharing: Boolean? = null
        var background: Boolean? = null
        override fun pickDownloadFolder() { folderAsked++ }
        override fun pickSharedFolder() { shareAsked++ }
        override fun setSoulseekSharing(enabled: Boolean) { sharing = enabled }
        override fun setSoulseekStayOnline(enabled: Boolean) { background = enabled }
    }

    @Test fun `choosing a download folder, then a shared folder, moves on with no Continue buttons`() {
        var state by mutableStateOf(connected)
        val actions = Recording()
        compose.setContent { SetupScreen(state, actions, 2) {} }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/14_setup_downloads.png")
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithTag("setup-download-folder").performClick()
        assertEquals(1, actions.folderAsked)
        // The picker comes back with a folder: that is the answer, so the next step appears.
        state = state.copy(downloadFolder = "Music")
        compose.waitForIdle()
        compose.onNodeWithTag("setup-share").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/15_setup_share.png")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/readme/setup-share.png")
        compose.onNodeWithText("public", substring = true).assertIsDisplayed()
        // Both sides of the choice, with the phone-friendly defaults spelled out.
        compose.onNodeWithText("you share 0 files", substring = true).assertExists()
        compose.onNodeWithText("uploads only run on wi-fi, at most 2 people at a time", substring = true).assertExists()

        compose.onNodeWithTag("setup-share").performClick()
        assertEquals(1, actions.shareAsked)
        state = state.copy(sharedFolder = "Music/Share")
        compose.waitForIdle()
        assertEquals(true, actions.sharing)
        compose.onNodeWithTag("rhythm:app").assertIsDisplayed()
    }

    @Test fun `skipping sharing turns it off, and tapping a rhythm card picks it and finishes`() {
        val actions = Recording()
        var finished = 0
        compose.setContent { SetupScreen(connected, actions, 3) { finished++ } }
        compose.onNodeWithTag("setup-share-skip").performClick()
        assertEquals(false, actions.sharing)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/16_setup_rhythm.png")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/readme/setup-rhythm.png")
        compose.onNodeWithText("Use background mode").assertDoesNotExist()
        compose.onNodeWithTag("rhythm:background").performClick()
        assertEquals(true, actions.background)
        compose.onNodeWithTag("setup-welcome").assertExists()
        compose.waitForIdle()
        assertEquals(1, finished)
    }

    @Test fun `the sign-in shows the app's name and keeps listening offline as a quiet link`() {
        compose.setContent { SetupScreen(connected.copy(soulseekSession = SoulseekSession()), RecordingActions(), 1) {} }
        compose.onNodeWithContentDescription("Runout").assertIsDisplayed()
        compose.onNodeWithText("Drop the needle.").assertDoesNotExist()
        compose.onNodeWithText("Create an ID").assertDoesNotExist()
        compose.onNodeWithText("Remember my sign-in").assertDoesNotExist()
        compose.onNodeWithTag("setup-offline").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("setup-offline").fetchSemanticsNode().boundsInRoot.top > 892 * 0.7f * 2)
    }
}
