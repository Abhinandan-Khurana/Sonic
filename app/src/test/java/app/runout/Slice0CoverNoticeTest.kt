package app.runout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.playback.DeckState
import app.runout.ui.RoomState
import app.runout.ui.RunoutScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
class Slice0CoverNoticeTest {
    @get:Rule val compose = createComposeRule()
    private fun state(albums: List<Album> = emptyList(), hasFolder: Boolean = false, notice: Boolean = false) =
        RoomState(albums, hasFolder, null, DeckState(), false, "12:00", coverLookupNoticePending = notice)

    @Test fun `existing user sees one time cover lookup notice and can acknowledge it`() {
        val actions = RecordingActions()
        compose.setContent { RunoutScreen(state(notice = true), actions) }
        compose.onNodeWithTag("cover-lookup-notice").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf("dismissCoverLookupNotice"), actions.log)
    }

    @Test fun `library shows unavailable record access without claiming valid playback`() {
        val album = Album("missing", "Missing Record", "Artist", 2020, "content://missing", emptyList(), AudioQuality("FLAC"), unavailableReason = "Unavailable · repair access")
        compose.setContent { RunoutScreen(state(listOf(album), hasFolder = true), RecordingActions()) }
        compose.onNodeWithText("Crate", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Unavailable · repair access").assertIsDisplayed()
    }
}
