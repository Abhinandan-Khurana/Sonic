package app.runout

import app.runout.library.AlbumJson
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.DiscStyle
import app.runout.model.Licences
import app.runout.model.Pressing
import app.runout.model.SideSplitter
import app.runout.model.Source
import app.runout.model.Track
import app.runout.ui.formatLength
import app.runout.ui.provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

/** Suite 3 — records: sides, labels, pressings, licences, and how a record is saved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RecordModelTest {

    private fun tracks(vararg minutes: Double) = minutes.mapIndexed { i, m ->
        Track("t$i", "content://t/$i", "Track ${i + 1}", i + 1, 1, (m * 60_000).toLong())
    }

    private fun album(q: AudioQuality = AudioQuality("FLAC", 16, 44_100), disc: DiscStyle? = null, license: String? = null, owned: Boolean = false) =
        Album("a", "Title", "Artist", 2020, "", tracks(4.0, 4.0), q, disc = disc, license = license, owned = owned)

    @Test
    fun `a 40 minute album becomes two balanced sides with every track in order`() {
        val album = tracks(4.0, 3.5, 5.0, 4.2, 3.8, 4.5, 3.9, 4.1, 3.2, 3.8)
        val sides = SideSplitter.split(album)
        assertEquals(2, sides.size)
        assertEquals(album, sides.flatMap { it.tracks })
        assertTrue(abs(sides[0].durationMs - sides[1].durationMs) < 5 * 60_000)
    }

    @Test
    fun `long albums become double LPs and short ones stay single`() {
        val sides = SideSplitter.split(tracks(*DoubleArray(16) { 5.0 }))
        assertEquals(4, sides.size)
        assertEquals(listOf('A', 'B', 'C', 'D'), sides.map { it.letter })
        assertTrue(sides.all { it.durationMs <= SideSplitter.MAX_SIDE_MS })
        assertEquals(1, SideSplitter.split(tracks(12.0)).size)
        assertEquals(2, SideSplitter.split(tracks(12.0, 13.0)).size)
        assertTrue(SideSplitter.split(emptyList()).isEmpty() || SideSplitter.split(emptyList()).all { it.tracks.isEmpty() })
    }

    @Test
    fun `needle position maps to the right track and back`() {
        val side = SideSplitter.split(tracks(4.0, 4.0, 4.0, 4.0))[0]
        val (index, offset) = side.locate(0.75f)
        assertEquals(1, index)
        assertEquals(side.durationMs * 3 / 4, side.positionOf(index, offset))
        assertEquals(listOf(0.5f), side.trackBoundaries())
        assertEquals(0 to 0L, side.locate(0f))
    }

    @Test
    fun `quality labels say exactly what the file is`() {
        val hiRes = AudioQuality("FLAC", 24, 96_000)
        assertEquals("FLAC · 24-bit · 96 kHz", hiRes.label)
        assertEquals("24/96", hiRes.shortLabel)
        assertTrue(hiRes.isHiRes)
        val cd = AudioQuality("FLAC", 16, 44_100)
        assertEquals("FLAC · 16-bit · 44.1 kHz", cd.label)
        assertTrue(cd.isLossless && !cd.isHiRes)
        val mp3 = AudioQuality("MP3", bitrateKbps = 320)
        assertEquals("MP3 · 320 kbps", mp3.label)
        assertFalse(mp3.isLossless)
    }

    @Test
    fun `the vinyl follows the sound unless you pressed your own`() {
        assertEquals(Pressing.CLEAR, album(AudioQuality("FLAC", 24, 96_000)).pressing)
        assertEquals(Pressing.COLOUR, album(AudioQuality("FLAC", 16, 44_100)).pressing)
        assertEquals(Pressing.BLACK, album(AudioQuality("MP3", bitrateKbps = 320)).pressing)
        assertEquals(Pressing.BLACK, album(AudioQuality("FLAC", 24, 96_000), disc = DiscStyle.BLACK).pressing)
        assertEquals(Pressing.COLOUR, album(disc = DiscStyle.SPLATTER).pressing)
        assertEquals(DiscStyle.MARBLE, app.runout.ui.discLookFor(album(disc = DiscStyle.MARBLE)).style)
        assertEquals(DiscStyle.PICTURE, app.runout.ui.discLookFor(album(disc = DiscStyle.PICTURE)).style)
        assertNull(app.runout.ui.discLookFor(album()).style)
        // A cover sits in the centre label. The disc itself stays black vinyl.
        assertEquals(DiscStyle.BLACK, app.runout.ui.discLookFor(album().copy(coverPath = "/covers/x.jpg")).style)
        assertEquals(DiscStyle.SPLATTER, app.runout.ui.discLookFor(album(disc = DiscStyle.SPLATTER).copy(coverPath = "/covers/x.jpg")).style)
    }

    @Test
    fun `audio only travels for your own music or open licences`() {
        assertFalse(album().audioShareable)
        assertTrue(album(owned = true).audioShareable)
        assertTrue(album(license = "https://creativecommons.org/licenses/by-nc-sa/4.0/").audioShareable)
        assertTrue(album(license = "http://creativecommons.org/publicdomain/zero/1.0/").audioShareable)
        assertFalse(album(license = "All rights reserved").audioShareable)
    }

    @Test
    fun `licence names are short and readable`() {
        assertEquals("CC BY-NC-SA 4.0", Licences.label("http://creativecommons.org/licenses/by-nc-sa/4.0/"))
        assertEquals("CC BY 3.0", Licences.label("https://creativecommons.org/licenses/by/3.0/de/"))
        assertEquals("CC0", Licences.label("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("Public domain", Licences.label("http://creativecommons.org/publicdomain/mark/1.0/"))
        assertNull(Licences.label(null))
        assertNull(Licences.label("  "))
    }

    @Test
    fun `a record survives being saved and loaded with every detail`() {
        val a = Album(
            id = "x1", title = "Night Drive", artist = "Vela Park", year = 2023, folderUri = "content://tree/Music",
            tracks = listOf(Track("t1", "content://a/1", "Opening", 1, 1, 2000, "01 Opening.flac", -5.1f, durationKnown = false)),
            quality = AudioQuality("FLAC", 24, 96_000), tint = 0xFF336699.toInt(), source = Source.PRESSING,
            license = "https://creativecommons.org/licenses/by/4.0/", albumGainDb = -6.2f, owned = true,
            disc = DiscStyle.SPLATTER, labelColor = 0xFFE0569B.toInt(), notes = "Liner notes", from = "Maya",
        )
        val back = AlbumJson.fromJson(AlbumJson.toJson(a))
        assertEquals(a, back)
        val f = File(Fixtures.context.filesDir, "list.json")
        AlbumJson.writeList(f, listOf(a, a.copy(id = "x2")))
        assertEquals(listOf("x1", "x2"), AlbumJson.readList(f).map { it.id })
        f.writeText("not json")
        assertTrue(AlbumJson.readList(f).isEmpty())
    }

    @Test
    fun `shared records carry no paths from your phone`() {
        val a = album().copy(folderUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FPrivate")
        val json = AlbumJson.toJson(a, includeLocalPaths = false).toString()
        assertFalse(json.contains("externalstorage"))
        assertFalse(json.contains("content://t/"))
    }

    @Test
    fun `one crate from every source, without duplicates`() {
        val a = album().copy(id = "1", artist = "Zed")
        val b = album().copy(id = "2", artist = "Ada")
        val merged = AppGraph.mergeShelf(listOf(a), listOf(b), listOf(a))
        assertEquals(listOf("2", "1"), merged.map { it.id })
    }

    @Test
    fun `captions say where a record came from`() {
        assertEquals("Sent by Maya", provenance(album().copy(source = Source.RECEIVED, from = "Maya")))
        assertEquals("Friday Drop · CC BY 4.0", provenance(album(license = "https://creativecommons.org/licenses/by/4.0/").copy(source = Source.DROP)))
        assertEquals("Your pressing · splatter vinyl", provenance(album(disc = DiscStyle.SPLATTER).copy(source = Source.PRESSING)))
        assertNull(provenance(album()))
        assertEquals("4:05", formatLength(245_000))
        assertEquals("1:02:03", formatLength(3_723_000))
    }
}
