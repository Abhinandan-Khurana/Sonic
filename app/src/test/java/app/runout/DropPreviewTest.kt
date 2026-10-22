package app.runout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.runout.drops.DropItem
import app.runout.drops.DropState
import app.runout.drops.DropTrack
import app.runout.drops.DropWeek
import app.runout.playback.DeckState
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekSession
import app.runout.ui.Panel
import app.runout.ui.PreviewPhase
import app.runout.ui.PreviewState
import app.runout.ui.RoomScreen
import app.runout.ui.RoomState
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** A drop's 30 s listen: the track rows follow what the player reports, not what was tapped. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w892dp-h412dp-land-xhdpi", application = android.app.Application::class)
class DropPreviewTest {
    @get:Rule val compose = createComposeRule()

    private val item = DropItem("NS050", "Another Day, Another Way", "No-Source Netlabel", 2012, "http://creativecommons.org/licenses/by-nc-sa/4.0/")
    private val tracks = listOf(DropTrack("Opening", "https://a/1.mp3", null), DropTrack("Lowlight", "https://a/2.flac", null))
    private val base = RoomState(emptyList(), true, null, DeckState(), false, "12:00").copy(
        drops = DropState.Ready(DropWeek(2911, LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-23"), listOf(item))),
        soulseekSession = SoulseekSession(SoulseekConnection.CONNECTED, "abhi"),
    )

    private class Listening(val list: CompletableDeferred<List<DropTrack>?>) : RecordingActions() {
        val listens = mutableListOf<String?>()
        override suspend fun dropTracks(item: DropItem) = list.await()
        override fun listen(url: String?) { listens += url }
    }

    @Test fun `the shelf spins the dotted vinyl while this week's drops load`() {
        compose.setContent { RoomScreen(base.copy(drops = DropState.Loading), RecordingActions(), initialPanel = Panel.DROPS) }
        compose.onNodeWithContentDescription("Loading drops").assertExists()
        compose.onNodeWithText("digging through the archive…").assertExists()
    }

    @Test fun `a track row shows loading, then the stop square, and clears when the player stops`() {
        val list = CompletableDeferred<List<DropTrack>?>()
        val actions = Listening(list)
        var state by mutableStateOf(base)
        compose.setContent { RoomScreen(state, actions, initialPanel = Panel.DROPS) }
        compose.onNodeWithTag("drop:NS050").performClick()
        compose.onNodeWithContentDescription("Loading tracks").assertExists()
        list.complete(tracks)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Loading tracks").assertDoesNotExist()

        compose.onNodeWithTag("listen:0").performScrollTo().performClick()
        assertEquals("https://a/1.mp3", actions.listens.last())
        // Nothing is shown as playing until the player says so.
        compose.onNodeWithTag("listen:0").assertContentDescriptionContains("Listen to Opening")

        state = state.copy(preview = PreviewState("https://a/1.mp3", PreviewPhase.BUFFERING))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Loading preview", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("listen:1").assertContentDescriptionContains("Listen to Lowlight")

        state = state.copy(preview = PreviewState("https://a/1.mp3", PreviewPhase.PLAYING))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Loading preview", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("listen:0").assertContentDescriptionContains("Stop listening")
        // Tapping the playing row stops it.
        compose.onNodeWithTag("listen:0").performClick()
        assertEquals(null, actions.listens.last())

        // The player ending by itself (error, timeout, headphones out) clears the row too.
        state = state.copy(preview = null)
        compose.waitForIdle()
        compose.onNodeWithTag("listen:0").assertContentDescriptionContains("Listen to Opening")
    }
}
