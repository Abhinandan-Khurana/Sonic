package app.runout

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Track
import app.runout.playback.DeckState
import app.runout.ui.RoomScreen
import app.runout.ui.RoomState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi", application = Application::class)
class Slice0PressingTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val album = Album("local", "Local album", "Artist", null, "", listOf(Track("track", "content://test/track.flac", "Track", 1, 1, 60000)), AudioQuality("FLAC"))
    private fun open(actions: RecordingActions = RecordingActions()) {
        compose.setContent { RoomScreen(RoomState(listOf(album), true, null, DeckState(), false, "12:00"), actions) }
        compose.onNodeWithTag("nav:PRESSING").performScrollTo().performClick()
        compose.onNodeWithTag("field:Title").performTextInput("Keep this draft")
    }
    private fun chooseTrack() {
        compose.onNodeWithTag("pick-album:Local album").performScrollTo().performClick()
        compose.onNodeWithTag("pick-track:Track").performScrollTo().performClick()
    }

    @Test fun `draft survives press request before asynchronous result`() {
        val actions = RecordingActions()
        open(actions)
        chooseTrack()
        compose.onNodeWithText("Press it").performScrollTo().performClick()
        assertEquals(listOf("press:Keep this draft:1"), actions.log.filter { it.startsWith("press:") })
        compose.onNodeWithTag("field:Title").performScrollTo().assertTextContains("Keep this draft")
    }

    @Test fun `failed press retains draft for retry and successful press clears it`() {
        var completion: ((Boolean) -> Unit)? = null
        val actions = object : RecordingActions() {
            override fun press(draft: app.runout.pressing.PressingDraft, onResult: (Boolean) -> Unit) { completion = onResult }
        }
        open(actions)
        chooseTrack()
        compose.onNodeWithText("Press it").performScrollTo().performClick()
        compose.runOnIdle { completion!!(false) }
        compose.onNodeWithTag("field:Title").performScrollTo().assertTextContains("Keep this draft")
        compose.onNodeWithText("Press it").performScrollTo().performClick()
        compose.runOnIdle { completion!!(true) }
        compose.onNodeWithTag("field:Title").performScrollTo().assertTextEquals("", "Title")
        compose.onNodeWithText("TRACKS · 0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("action:Close").performClick()
        compose.onNodeWithText("Discard pressing draft?").assertDoesNotExist()
    }

    @Test fun `late pressing success cannot erase newer draft edits`() {
        var completion: ((Boolean) -> Unit)? = null
        open(object : RecordingActions() {
            override fun press(draft: app.runout.pressing.PressingDraft, onResult: (Boolean) -> Unit) { completion = onResult }
        })
        chooseTrack()
        compose.onNodeWithText("Press it").performScrollTo().performClick()
        compose.onNodeWithTag("field:Title").performScrollTo().performTextInput(" edited")
        compose.runOnIdle { completion!!(true) }
        compose.onNodeWithTag("field:Title").assertTextContains("Keep this draft edited")
    }

    @Test fun `Close asks before discarding changed draft and keep retains it`() {
        open()
        compose.onNodeWithTag("action:Close").performClick()
        compose.onNodeWithText("Discard pressing draft?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("field:Title").assertTextContains("Keep this draft")
        compose.onNodeWithTag("action:Close").performClick()
        compose.onNodeWithText("Discard draft").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("panel:pressing").assertDoesNotExist()
        compose.onNodeWithTag("nav:PRESSING").performScrollTo().performClick()
        compose.onNodeWithTag("field:Title").assertTextContains("")
    }

    @Test fun `Back asks before discarding changed draft`() {
        open()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Discard pressing draft?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("field:Title").assertTextContains("Keep this draft")
    }
}
