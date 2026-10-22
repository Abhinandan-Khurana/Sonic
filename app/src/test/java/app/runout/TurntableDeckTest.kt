package app.runout

import android.net.Uri
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Track
import app.runout.playback.Deck
import app.runout.playback.DeckState
import app.runout.playback.VinylSfx
import app.runout.sound.PlayerFactory
import app.runout.sound.SoundEngine
import app.runout.ui.deck.DeckCamera
import app.runout.ui.deck.DeckModel
import app.runout.ui.deck.V3
import app.runout.ui.DeckLeds
import app.runout.ui.ledLines
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
    fun `a held platter rests at the recorded speed, so a play from outside the deck never starts slowed`() {
        deck.load(album())
        settle()
        deck.startStop() // needle down
        settle()
        deck.startStop() // hold: the music winds down and stops
        settle()
        assertTrue(s.held)
        assertFalse(player.playWhenReady)
        assertEquals(1f, player.playbackParameters.speed, 0.001f)
        // The notification, a headset or a car kit presses play.
        player.play()
        settle()
        assertEquals(1f, player.playbackParameters.speed, 0.001f)
        // Lifting the needle mid-wind-down leaves it at the recorded speed too.
        deck.startStop()
        settle(60)
        deck.liftNeedle()
        settle()
        assertEquals(1f, player.playbackParameters.speed, 0.001f)
    }

    /** A deck on a player whose clock runs by itself, so it really reaches playing (and failing). */
    private fun runningDeck(): Pair<Deck, ExoPlayer> {
        val p = PlayerFactory.create(context, engine, handleAudioFocus = false, clock = FakeClock(true)) { ctx, _ -> AudioOutputTest.CapturingOutput(ctx) }
        return Deck(context, VinylSfx(scope), scope, engine) { ready -> ready(p) } to p
    }

    @Test
    fun `a pause from outside holds the platter, so one START spins it up again`() {
        val (d, p) = runningDeck()
        try {
            d.load(album())
            d.startStop()
            settle()
            TestPlayerRunHelper.run(p).untilState(Player.STATE_READY)
            settle(50)
            assertTrue(d.state.value.isPlaying)
            p.playWhenReady = false // the headset button, or Bluetooth headphones switching off
            settle(50)
            assertTrue(d.state.value.needleDown)
            assertTrue("the platter is held", d.state.value.held)
            d.startStop()
            settle()
            assertTrue("one tap plays again", p.playWhenReady)
            assertFalse(d.state.value.held)
            // And a play from outside with the needle up lowers it: the deck follows the player.
            d.liftNeedle()
            settle(50)
            p.play()
            settle(50)
            assertTrue(d.state.value.needleDown)
        } finally { p.release() }
    }

    @Test
    fun `the needle is only followed while the record plays`() {
        val (d, p) = runningDeck()
        try {
            assertFalse("nothing ticks with nothing on", d.ticking)
            d.load(album())
            d.startStop()
            settle()
            TestPlayerRunHelper.run(p).untilState(Player.STATE_READY)
            settle(50)
            assertTrue(d.state.value.isPlaying)
            assertTrue(d.ticking)
            p.playWhenReady = false
            settle(50)
            assertFalse("paused: no ticker waking the app", d.ticking)
            assertEquals("the place it stopped at is kept", p.currentPosition, d.state.value.positionMs)
            p.play()
            settle(50)
            assertTrue(d.ticking)
        } finally { p.release() }
    }

    @Test fun `with auto-play on, a record put on the platter starts, cued songs too`() {
        deck.setAutoPlay(true)
        deck.load(album())
        settle()
        assertTrue(s.needleDown)
        assertTrue(player.playWhenReady)
        deck.cueTrack(album().copy(id = "again"), "t4")
        settle()
        assertTrue(s.needleDown)
        assertEquals("B2", s.trackCode)
        assertEquals("t4", player.currentMediaItem?.mediaId)
        deck.setAutoPlay(false)
        deck.load(album().copy(id = "quiet"))
        settle()
        assertFalse("off again: the needle waits", s.needleDown)
    }

    @Test fun `with auto-flip on, side A running out turns the record and plays side B`() {
        val (d, p) = runningDeck()
        try {
            d.setAutoFlip(true)
            d.load(album())
            d.startStop()
            settle()
            TestPlayerRunHelper.run(p).untilState(Player.STATE_ENDED)
            settle(50)
            assertTrue(d.state.value.sideFinished)
            assertEquals(0, d.state.value.sideIndex)
            settle(3_000) // the runout groove, then the turn
            assertEquals(1, d.state.value.sideIndex)
            assertTrue("side B plays", d.state.value.needleDown)
            assertEquals("t3", p.currentMediaItem?.mediaId)
            // The last side rests when it runs out.
            TestPlayerRunHelper.run(p).untilState(Player.STATE_ENDED)
            settle(3_000)
            assertEquals(1, d.state.value.sideIndex)
            assertFalse(d.state.value.needleDown)
        } finally { p.release() }
    }

    @Test fun `the sleep timer fades the music out and lifts the needle, and can be called off`() {
        deck.load(album())
        deck.startStop()
        settle()
        deck.setSleepTimer(15)
        assertTrue(deck.sleepAt.value != null)
        settle(14 * 60_000L)
        assertTrue("still playing at 14 minutes", s.needleDown)
        settle(2 * 60_000L)
        assertFalse("asleep after 15 minutes and the fade", s.needleDown)
        assertEquals(null, deck.sleepAt.value)
        assertEquals("the next record plays at full volume", 1f, player.volume)
        deck.startStop()
        settle()
        deck.setSleepTimer(20)
        deck.setSleepTimer(null)
        settle(25 * 60_000L)
        assertTrue("off: the music plays on", s.needleDown)
        deck.setSleepMode(true)
        deck.setSleepTimer(30)
        deck.setSleepMode(false)
        assertEquals("sleep mode off calls the timer off too", null, deck.sleepAt.value)
    }

    @Test
    fun `a track that can't be played lifts the needle and says why`() {
        val (d, p) = runningDeck()
        try {
            val record = album()
            d.load(record)
            // The file disappears before the player reaches it.
            record.tracks.forEach { File(Uri.parse(it.uri).path!!).delete() }
            d.startStop()
            settle()
            TestPlayerRunHelper.run(p).untilPlayerError()
            settle(50)
            assertFalse(d.state.value.needleDown)
            assertFalse(d.state.value.isPlaying)
            assertTrue(d.problem.value.orEmpty().isNotBlank())
            d.clearProblem()
            assertEquals(null, d.problem.value)
        } finally { p.release() }
    }

    @Test
    fun `playback errors read as plain words`() {
        fun error(code: Int) = androidx.media3.common.PlaybackException("x", null, code)
        assertTrue(Deck.describe(error(androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED)).contains("couldn't be decoded"))
        assertTrue(Deck.describe(error(androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)).contains("audio output"))
        assertTrue(Deck.describe(error(androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)).contains("Couldn't read"))
    }

    @Test
    fun `records count in words`() {
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
    fun `a record in hand lands exactly as the record on the platter`() {
        val cam = DeckCamera(1080f, 628f)
        val centre = cam.project(V3(DeckModel.platterCentre.x, DeckModel.RECORD_Y, DeckModel.platterCentre.z))
        for (degrees in listOf(0f, 42f, 217.5f)) {
            val held = DeckModel.heldDiscTransform(cam, centre, radiusPx = 180f, landing = 1f, spinDegrees = degrees)
            val onPlatter = DeckModel.recordTransform(degrees)
            for (i in held.indices) assertEquals("element $i at $degrees°", onPlatter[i], held[i], 2e-3f)
        }
        // Held anywhere on screen, its centre stays right under the finger.
        val finger = centre + androidx.compose.ui.geometry.Offset(60f, 900f)
        val inHand = DeckModel.heldDiscTransform(cam, finger, 180f, 0f, 0f)
        val seen = cam.project(V3(inHand[12], inHand[13], inHand[14]))
        assertEquals(finger.x, seen.x, 0.5f)
        assertEquals(finger.y, seen.y, 0.5f)
    }

    @Test
    fun `the flat view looks straight down, keeps the deck in its box, and touch still lands`() {
        for (tilt in listOf(DeckCamera.FLAT_DEG, 12f, DeckCamera.TILT_DEG)) {
            val cam = DeckCamera(1080f, 628f, tilt)
            listOf(cam.eye.x, cam.eye.y, cam.eye.z, cam.viewUp.x, cam.viewUp.y, cam.viewUp.z).forEach { assertTrue("finite at $tilt°", it.isFinite()) }
            // The platter's centre maps to a pixel inside the view and back to the same point.
            val p = V3(DeckModel.platterCentre.x, DeckModel.RECORD_Y, DeckModel.platterCentre.z)
            val px = cam.project(p)
            assertTrue("inside at $tilt°", px.x in 0f..1080f && px.y in 0f..628f)
            val back = cam.onPlane(px, DeckModel.RECORD_Y)!!
            assertEquals(p.x, back.x, 1e-3f)
            assertEquals(p.z, back.z, 1e-3f)
        }
        // Straight down, the back of the deck is up the screen.
        val flat = DeckCamera(1080f, 628f, DeckCamera.FLAT_DEG)
        assertTrue(flat.project(V3(0f, 0.5f, -1f)).y < flat.project(V3(0f, 0.5f, 1f)).y)
    }

    @Test
    fun `turning the record over lifts it edge-on at halfway and lands it back on the mat`() {
        for (degrees in listOf(0f, 73f)) {
            val flat = DeckModel.recordTransform(degrees)
            assertTrue(DeckModel.flipTransform(degrees, 0f).contentEquals(flat))
            assertTrue(DeckModel.flipTransform(degrees, 1f).contentEquals(flat))
            // Halfway it's highest and stands on edge: its up axis (column 1) lies flat.
            val mid = DeckModel.flipTransform(degrees, 0.4999f)
            assertEquals(DeckModel.RECORD_Y + DeckModel.FLIP_LIFT, mid[13], 1e-3f)
            assertEquals(0f, mid[5], 2e-3f)
            // The roll restarts from the other edge, so either side of halfway is the same edge-on pose.
            val after = DeckModel.flipTransform(degrees, 0.5001f)
            assertEquals(kotlin.math.abs(mid[6]), kotlin.math.abs(after[6]), 2e-3f)
            assertEquals(mid[13], after[13], 1e-3f)
            // At every step the lowest point of the rim stays above the record's own plane.
            for (k in 1..19) {
                val m = DeckModel.flipTransform(degrees, k / 20f)
                val lowest = m[13] - DeckModel.RECORD_R * kotlin.math.abs(m[6])
                assertTrue("rim dips below the mat at ${k / 20f}", lowest >= DeckModel.RECORD_Y - 1e-3f)
            }
        }
    }

    @Test
    fun `the LED windows show the track, its code and the time`() {
        assertEquals(DeckLeds("DROP A RECORD", null, null), ledLines(DeckState()))
        deck.load(album())
        settle()
        assertEquals(DeckLeds("CUE  Track 1", "A1", "0:00"), ledLines(s))
        deck.nextTrack()
        settle()
        val leds = ledLines(s)
        assertEquals("A2", leds.code)
        assertTrue(leds.title, leds.title.endsWith("Track 2"))
    }

    @Test
    fun `the 3D deck's arm reaches across the record and rests off it`() {
        val c = DeckModel.platterCentre
        fun radius(a: Float) = DeckModel.stylus(a).let { kotlin.math.hypot(it.x - c.x, it.z - c.z) }
        assertEquals(DeckModel.RECORD_R * 0.95f, radius(DeckModel.angleOut), 0.002f)
        assertEquals(DeckModel.RECORD_R * 0.43f, radius(DeckModel.angleIn), 0.002f)
        assertTrue("rest is off the platter", radius(DeckModel.angleRest) > DeckModel.PLATTER_R)
        assertFalse(DeckModel.stylusOverRecord(DeckModel.angleRest))
        // As modelled, the arm needs no turn at rest; the needle lands on the record's face.
        assertEquals(0f, DeckModel.armYaw(DeckModel.angleRest), 1e-5f)
        assertEquals(0.588f, 0.643f - DeckModel.NEEDLE_DROP, 0.001f)
        assertEquals(0f, DeckModel.fractionForAngle(DeckModel.angleOut), 1e-4f)
        assertEquals(1f, DeckModel.fractionForAngle(DeckModel.angleIn), 1e-4f)
        // Dragging can't take the arm past the run-out or behind its rest.
        assertEquals(DeckModel.angleIn, DeckModel.angleTowards(c), 1e-4f)
    }

    @Test
    fun `the deck's keys have their own zones, clear of the platter, and the camera maps touch back`() {
        val keys = DeckModel.keys
        for (i in keys.indices) for (j in i + 1 until keys.size) {
            val a = keys[i]
            val b = keys[j]
            val overlap = a.zoneMin.first < b.zoneMax.first && b.zoneMin.first < a.zoneMax.first &&
                a.zoneMin.second < b.zoneMax.second && b.zoneMin.second < a.zoneMax.second
            assertFalse("${a.node} and ${b.node} zones overlap", overlap)
        }
        keys.forEach { k ->
            assertTrue("${k.node} cap inside its zone", k.centre.x in k.zoneMin.first..k.zoneMax.first && k.centre.z in k.zoneMin.second..k.zoneMax.second)
            // The keys sit on the right-hand module, past the platter and the tonearm's sweep.
            assertTrue("${k.node} zone clear of the platter", k.zoneMin.first > DeckModel.platterCentre.x + DeckModel.PLATTER_R)
            assertTrue("${k.node} zone clear of the tonearm", k.zoneMin.first > DeckModel.pivot.x + 0.5f)
        }
        for ((w, h) in listOf(380f to 245f, 1080f to 697f)) {
            val cam = DeckCamera(w, h)
            // Everything the camera frames is on screen.
            listOf(DeckModel.viewMin, DeckModel.viewMax).forEach { p ->
                val s = cam.project(p)
                assertTrue("$p on screen at ${w}x$h", s.x in -0.5f..w + 0.5f && s.y in -0.5f..h + 0.5f)
            }
            // A finger on the record's face finds the same point in the model.
            val p = V3(DeckModel.platterCentre.x + 0.7f, DeckModel.RECORD_Y, 0.3f)
            val back = cam.onPlane(cam.project(p), DeckModel.RECORD_Y)!!
            assertEquals(p.x, back.x, 1e-3f)
            assertEquals(p.z, back.z, 1e-3f)
            assertTrue(DeckModel.isOnRecord(back))
        }
    }
}
