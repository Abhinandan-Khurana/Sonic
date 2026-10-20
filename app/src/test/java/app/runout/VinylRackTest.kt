package app.runout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.playback.DeckState
import app.runout.ui.RoomState
import app.runout.ui.RoomScreen
import app.runout.ui.RunoutScreen
import app.runout.ui.VinylRack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h892dp-xhdpi")
class VinylRackTest {
    @get:Rule val compose = createComposeRule()
    private fun album(title: String) = Album(title, title, "Artist", 2023, "content://$title", emptyList(), AudioQuality("FLAC"))

    @Test fun `a vertical flick moves which record is facing`() {
        compose.setContent { VinylRack(listOf(album("Night Drive"), album("Lowlight"), album("Opening")), {}) }
        compose.onNodeWithTag("rack-focus").assertTextEquals("Night Drive")
        compose.onNodeWithTag("vinyl-rack").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("rack-focus").assertTextEquals("Opening")
    }

    @Test fun `tapping the facing disc unboxes it onto the room for a drag`() {
        val album = album("Night Drive")
        val actions = RecordingActions()
        compose.setContent {
            RoomScreen(RoomState(listOf(album), true, null, DeckState(), false, "12:00"), actions, idleRack = true)
        }
        compose.onNodeWithTag("vinyl-rack").assertIsDisplayed()
        compose.onNodeWithTag("rack-disc:Night Drive").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("pulled-disc").assertIsDisplayed()
        compose.onNodeWithTag("vinyl-rack").assertDoesNotExist()
        assertEquals(emptyList<String>(), actions.log.filter { it.startsWith("load:") })
    }

    @Test fun `home is the glass room again`() {
        compose.setContent { RunoutScreen(RoomState(emptyList(), false, null, DeckState(), false, "12:00"), RecordingActions()) }
        compose.onNodeWithTag("screen:home").assertIsDisplayed()
        compose.onNodeWithTag("turntable").assertIsDisplayed()
        compose.onNodeWithTag("vinyl-rack").assertDoesNotExist()
        compose.onNodeWithText("Library").assertIsDisplayed()
    }
}
