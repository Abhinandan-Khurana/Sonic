package app.runout

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.playback.DeckState
import app.runout.ui.InkedDeck
import app.runout.ui.snapToBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
class InkedDeckGestureTest {
    @get:Rule val compose = createComposeRule()
    private val album = Album("night", "Night Drive", "Vela Park", 2023, "content://night", emptyList(), AudioQuality("FLAC"))

    @Test fun `band snap prefers the nearest start`() {
        assertEquals(0f, snapToBand(0.1f, listOf(0.5f)))
        assertEquals(0.5f, snapToBand(0.4f, listOf(0.5f)))
    }

    @Test fun `cue lowers and lifts the needle`() {
        val actions = RecordingActions()
        compose.setContent { InkedDeck(DeckState(album = album), false, { _, _ -> }, actions) }
        compose.onNodeWithTag("deck:cue").performClick()
        compose.onNodeWithTag("deck:cue").performClick()
        assertTrue(actions.log.contains("needleDrop"))
    }

    @Test fun `dragging the arm lowers the needle on a band`() {
        val actions = RecordingActions()
        compose.setContent { InkedDeck(DeckState(album = album), false, { _, _ -> }, actions) }
        compose.onNodeWithTag("deck:arm").performTouchInput { swipeDown() }
        assertTrue(actions.log.any { it == "needleDrop" })
    }

    @Test fun `swiping the finished record up turns it over and down puts it away`() {
        val actions = RecordingActions()
        compose.setContent { InkedDeck(DeckState(album = album, sideFinished = true), false, { _, _ -> }, actions) }
        compose.onNodeWithTag("deck:record").performTouchInput { swipeUp() }
        compose.onNodeWithTag("deck:record").performTouchInput { swipeDown() }
        assertEquals(listOf("flip", "eject"), actions.log)
    }
}
