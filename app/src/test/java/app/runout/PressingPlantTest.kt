package app.runout

import android.graphics.BitmapFactory
import android.net.Uri
import app.runout.model.AudioQuality
import app.runout.model.DiscStyle
import app.runout.model.Source
import app.runout.pressing.PressingDraft
import app.runout.pressing.PressingRepository
import app.runout.pressing.SleeveArtist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Suite 8 — the Pressing Plant: your own records with your own sleeve and vinyl. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class PressingPlantTest {

    private val context get() = Fixtures.context

    private fun draft(title: String = "Summer Tape", tracks: Int = 3, owned: Boolean = false) = PressingDraft(
        title = title, artist = "Abhi",
        tracks = Fixtures.paperMoons().tracks.take(tracks).mapIndexed { i, t -> t.copy(trackNumber = 9 - i, discNumber = 2) },
        quality = AudioQuality("MP3", bitrateKbps = 128), disc = DiscStyle.SPLATTER, labelColor = 0xFFE0569B.toInt(), pattern = 2, owned = owned,
        notes = "for the drive",
    )

    @Test
    fun `pressing makes a record with a generated sleeve`() = runBlocking {
        val repo = PressingRepository(context)
        val a = repo.press(draft())
        assertTrue(a.id.startsWith("press-"))
        assertEquals(Source.PRESSING, a.source)
        assertEquals("Summer Tape", a.title)
        assertEquals(DiscStyle.SPLATTER, a.disc)
        assertEquals(0xFFE0569B.toInt(), a.labelColor)
        assertEquals("for the drive", a.notes)
        // Tracks are renumbered as one side-ordered record.
        assertEquals(listOf(1, 2, 3), a.tracks.map { it.trackNumber })
        assertTrue(a.tracks.all { it.discNumber == 1 })
        val cover = BitmapFactory.decodeFile(a.coverPath)
        assertEquals(1000, cover.width); assertEquals(1000, cover.height)
        assertTrue(a.tint != null)
    }

    @Test
    fun `a picture you choose becomes a square sleeve`() = runBlocking {
        val wide = Fixtures.cover("wide", w = 1600, h = 900)
        val a = PressingRepository(context).press(draft().copy(coverImage = Uri.fromFile(wide)))
        val cover = BitmapFactory.decodeFile(a.coverPath)
        assertEquals(cover.width, cover.height)
        assertEquals(900, cover.width)
    }

    @Test
    fun `a record needs a title and tracks`() = runBlocking {
        val repo = PressingRepository(context)
        for (bad in listOf(draft(title = "  "), draft(tracks = 0))) {
            try { repo.press(bad); fail("should refuse $bad") } catch (_: IllegalArgumentException) {}
        }
        assertTrue(repo.records.value.isEmpty())
    }

    @Test
    fun `only music you made travels with audio`() = runBlocking {
        val repo = PressingRepository(context)
        assertFalse(repo.press(draft(owned = false)).audioShareable)
        assertTrue(repo.press(draft(owned = true)).audioShareable)
        assertEquals("Various", repo.press(draft().copy(artist = " ")).artist)
    }

    @Test
    fun `pressings stay on the shelf and can be deleted`() = runBlocking {
        val repo = PressingRepository(context)
        val a = repo.press(draft("One"))
        val b = repo.press(draft("Two"))
        assertEquals(listOf("One", "Two"), PressingRepository(context).records.value.map { it.title })
        repo.delete(a.id)
        assertFalse(File(a.coverPath!!).exists())
        assertEquals(listOf(b.id), PressingRepository(context).records.value.map { it.id })
    }

    @Test
    fun `the label tells the truth about mixed sources`() {
        val flac24 = Fixtures.nightDrive()
        val flac16 = flac24.copy(id = "b", quality = AudioQuality("FLAC", 16, 44_100))
        val wav = flac24.copy(id = "c", quality = AudioQuality("WAV", 16, 48_000))
        val mp3 = Fixtures.paperMoons()
        assertEquals(AudioQuality("FLAC", 16, 44_100), PressingRepository.qualityFor(listOf(flac24, flac16)))
        assertEquals("LOSSLESS MIX", PressingRepository.qualityFor(listOf(flac24, wav)).codec)
        assertEquals("MIXED", PressingRepository.qualityFor(listOf(flac24, mp3)).codec)
        assertFalse(PressingRepository.qualityFor(listOf(flac24, mp3)).isLossless)
    }

    @Test
    fun `all six sleeve patterns are distinct and full size`() {
        val hashes = (0 until SleeveArtist.PATTERNS).map { p ->
            val bmp = SleeveArtist.render(p, "Title", "Artist", 0xFF2B6BFF.toInt(), size = 200)
            assertEquals(200, bmp.width)
            val px = IntArray(200 * 200).also { bmp.getPixels(it, 0, 200, 0, 0, 200, 200) }
            px.contentHashCode()
        }
        assertEquals(SleeveArtist.PATTERNS, hashes.toSet().size)
        assertEquals(SleeveArtist.PATTERNS, SleeveArtist.PATTERN_NAMES.size)
    }
}
