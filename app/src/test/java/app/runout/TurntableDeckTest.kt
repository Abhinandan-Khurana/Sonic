package app.runout

import android.net.Uri
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Track
import app.runout.playback.Deck
import app.runout.playback.DeckState
import app.runout.playback.VinylSfx
import app.runout.sound.PlayerFactory
import app.runout.sound.SoundEngine
import app.runout.ui.DeckGeometry
import app.runout.ui.screenLines
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * Suite 12 — the turntable's controls driving a real ExoPlayer: start/stop, next and previous within
 * a side, cueing a song from search, the fixed recorded speed; plus the deck's layout and its screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TurntableDeckTest {

    private val context get() = Fixtures.context
    private lateinit var scope: CoroutineScope
    private lateinit var player: ExoPlayer
    private lateinit var engine: SoundEngine
    private lateinit var deck: Deck

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        engine = SoundEngine(null)
        // The clock only moves when told to, so playback can't run ahead of the test.
        player = PlayerFactory.create(context, engine, handleAudioFocus = false, clock = FakeClock(false)) { ctx, _ -> AudioOutputTest.CapturingOutput(ctx) }
        deck = Deck(context, VinylSfx(scope), scope, engine) { ready -> ready(player) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        player.release()
    }

    private fun settle(ms: Long = 800) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Four one-minute tracks: two sides of two. */
    private fun album(): Album {
        val tone = Fixtures.file("Music/Vela Park - Night Drive (2023)/01 Opening.flac")
        val tracks = (1..4).map { i ->
            val f = File(context.cacheDir, "t$i.flac").also { tone.copyTo(it, overwrite = true) }
            Track("t$i", Uri.fromFile(f).toString(), "Track $i", i, 1, 60_000)
        }
        return Album("deck", "Deck Test", "Runout", 2026, "", tracks, AudioQuality("FLAC", 24, 96_000))
    }

    private val s: DeckState get() = deck.state.value

    @Test fun `search cues the selected song on its side and only the deck starts it`() {
        val record = album()
        deck.cueTrack(record, "t4")
        settle()
        assertEquals("deck", s.album?.id)
        assertEquals("B2", s.trackCode)
        assertEquals("t4", player.currentMediaItem?.mediaId)
        assertFalse(s.needleDown)
        assertFalse(player.playWhenReady)
        deck.startStop() // START on the deck lowers the needle on the cued song
        settle()
        assertTrue(s.needleDown)
        assertTrue(player.playWhenReady)
        assertEquals("B2", s.trackCode)
        assertEquals("t4", player.currentMediaItem?.mediaId)
    }

    @Test fun `loading a new record cancels the previous needle settling playback`() {
        deck.load(album())
        deck.startStop()
        deck.load(album().copy(id = "next-record"))
        settle()
        assertEquals("next-record", s.album?.id)
        assertFalse(s.needleDown)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `start and stop like the big square key`() {
        deck.load(album())
        settle()
        assertEquals(2, player.mediaItemCount)
        assertFalse(s.needleDown)
        deck.startStop() // lowers the needle
        settle()
        assertTrue(s.needleDown)
        assertTrue(player.playWhenReady)
        deck.startStop() // stops the platter; the needle stays down
        settle()
        assertTrue(s.held && s.needleDown)
        assertFalse(player.playWhenReady)
        deck.startStop() // spins up again
        settle()
        assertFalse(s.held)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `next and previous stay on the side and flip is the only turnover`() {
        deck.load(album())
        settle()
        deck.nextTrack()
        settle()
        assertEquals("A2", s.trackCode)
        assertEquals(1, player.currentMediaItemIndex)
        assertTrue(player.playWhenReady)
        deck.nextTrack()
        settle()
        assertEquals("A2", s.trackCode)
        assertEquals(0, s.sideIndex)
        deck.flip()
        settle()
        assertEquals("B1", s.trackCode)
        assertEquals(1, s.sideIndex)
        deck.previousTrack()
        settle()
        assertEquals("B1", s.trackCode)
    }

    @Test
    fun `records always play at the speed they were recorded at`() {
        deck.load(album())
        settle()
        deck.startStop()
        settle()
        assertEquals(1f, player.playbackParameters.speed, 0.001f)
        // Stopping and starting the platter winds down and back up to the recorded speed.
        deck.startStop()
        settle()
        deck.startStop()
        settle()
        assertEquals(1f, player.playbackParameters.speed, 0.001f)
        engine.update { it.copy(pure = true) }
        deck.onPureChanged(true)
        assertEquals(1f, player.playbackParameters.speed, 0.001f)
    }

    @Test
    fun `the strobe stands still at 33 and a third and drifts when the platter is off speed`() {
        assertEquals(0f, app.runout.ui.strobeDrift(app.runout.ui.RPM_33_DEG), 0.001f)
        assertEquals(0f, app.runout.ui.strobeDrift(0f), 0.001f)
        // Slightly slow: the pattern creeps backwards; slightly fast: forwards.
        assertTrue(app.runout.ui.strobeDrift(app.runout.ui.RPM_33_DEG - 4f) < 0f)
        assertTrue(app.runout.ui.strobeDrift(app.runout.ui.RPM_33_DEG + 4f) > 0f)
        assertEquals("1 record", app.runout.ui.recordCount(1))
        assertEquals("5 records", app.runout.ui.recordCount(5))
    }

    @Test
    fun `the screen says what's playing`() {
        assertEquals("NO RECORD", screenLines(DeckState()).second)
        deck.load(album())
        settle()
        assertEquals("CUE A1", screenLines(s).second)
        deck.nextTrack()
        settle()
        val (top, bottom) = screenLines(s)
        assertEquals("A2 Track 2 · Runout", top)
        assertTrue(bottom, bottom.startsWith("0:00"))
    }

    @Test
    fun `everything on the plinth sits where it should`() {
        for (h in listOf(300f, 900f)) {
            val g = DeckGeometry(Size(h * DeckGeometry.ASPECT, h))
            val plinth = androidx.compose.ui.geometry.Rect(0f, 0f, h * DeckGeometry.ASPECT, h)
            val controls = listOf(g.startStop, g.prev, g.next, g.screen)
            // Inside the plinth...
            controls.forEach { assertTrue("$it inside", plinth.contains(it.topLeft) && plinth.contains(it.bottomRight)) }
            // ...not overlapping each other...
            for (i in controls.indices) for (j in i + 1 until controls.size) {
                assertFalse("${controls[i]} vs ${controls[j]}", controls[i].overlaps(controls[j]))
            }
            // ...and clear of the spinning platter.
            controls.forEach { r ->
                val nearest = androidx.compose.ui.geometry.Offset(g.center.x.coerceIn(r.left, r.right), g.center.y.coerceIn(r.top, r.bottom))
                assertTrue("$r clear of platter", (nearest - g.center).getDistance() > g.ringR)
            }
            // The arm reaches from the edge of the record to the run-out, and rests off the platter.
            assertEquals(g.recordR * 0.95f, (g.stylus(g.alphaOut) - g.center).getDistance(), h * 0.002f)
            assertEquals(g.recordR * 0.43f, (g.stylus(g.alphaIn) - g.center).getDistance(), h * 0.002f)
            assertTrue((g.stylus(g.alphaRest) - g.center).getDistance() > g.ringR)
        }
    }
}
