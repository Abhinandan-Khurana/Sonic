package app.runout

import app.runout.library.AudioInfo
import app.runout.library.LibraryScanner
import app.runout.library.TagReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Suite 2 — Runout's own tag and stream readers for FLAC, MP3 and WAV, run against real files. */
class TagReaderTest {

    private fun read(path: String, picture: Boolean = true): AudioInfo {
        val f = Fixtures.file(path)
        return f.inputStream().use { TagReader.read(it, f.extension, f.length(), picture) } ?: error("no info for $path: " + TagReader.lastError?.stackTraceToString()?.take(1500))
    }

    @Test
    fun `FLAC tags, stream info and ReplayGain`() {
        val i = read("Music/Vela Park - Night Drive (2023)/01 Opening.flac")
        assertEquals("FLAC", i.codec)
        assertEquals("Opening", i.title)
        assertEquals("Vela Park", i.artist)
        assertEquals("Vela Park", i.albumArtist)
        assertEquals("Night Drive", i.album)
        assertEquals(1, i.track)
        assertEquals(1, i.disc)
        assertEquals(2023, i.year)
        assertEquals(96_000, i.sampleRate)
        assertEquals(24, i.bits)
        assertEquals(1, i.channels)
        assertEquals(2_000L, i.durationMs)
        assertEquals(-6.2f, i.albumGainDb!!, 0.001f)
        assertEquals(-5.1f, i.trackGainDb!!, 0.001f)
        assertTrue(i.license!!.contains("creativecommons.org/licenses/by/4.0"))
    }

    @Test
    fun `FLAC cover art is read only when asked for`() {
        val with = read("Music/Vela Park - Night Drive (2023)/02 Lowlight.flac", picture = true)
        val png = with.picture!!
        assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), png.copyOf(4))
        assertNull(read("Music/Vela Park - Night Drive (2023)/02 Lowlight.flac", picture = false).picture)
    }

    @Test
    fun `MP3 ID3 tags, cover and length`() {
        val i = read("Music/Ines Varga/Paper Moons/01 Paper Moons.mp3")
        assertEquals("MP3", i.codec)
        assertEquals("Paper Moons", i.title)
        assertEquals("Ines Varga", i.artist)
        assertEquals("Paper Moons", i.album)
        assertEquals(1, i.track)
        assertEquals(2021, i.year)
        assertEquals(44_100, i.sampleRate)
        assertTrue("was ${i.durationMs}", kotlin.math.abs(i.durationMs - 2_038) < 80)
        val jpeg = i.picture!!
        assertEquals(0xFF.toByte(), jpeg[0]); assertEquals(0xD8.toByte(), jpeg[1])
        assertNotNull(i.bitrateKbps)
    }

    @Test
    fun `MP3 at 48 kHz without a year`() {
        val i = read("Music/Ines Varga/Paper Moons/03 Signal.mp3")
        assertEquals("Signal", i.title)
        assertEquals(3, i.track)
        assertNull(i.year)
        assertEquals(48_000, i.sampleRate)
        assertTrue("was ${i.durationMs}", kotlin.math.abs(i.durationMs - 3_024) < 80)
    }

    @Test
    fun `WAV format and exact length`() {
        val i = read("Music/Untagged/track one.wav")
        assertEquals("WAV", i.codec)
        assertEquals(44_100, i.sampleRate)
        assertEquals(16, i.bits)
        assertEquals(1, i.channels)
        assertEquals(1_500L, i.durationMs)
        assertNull(i.title)
        val hi = read("tones/tone_24_96.wav")
        assertEquals(96_000, hi.sampleRate)
        assertEquals(24, hi.bits)
        assertEquals(1_000L, hi.durationMs)
    }

    @Test
    fun `junk and truncated files never crash the scan`() {
        val junk = Random(7).nextBytes(4096)
        listOf("flac", "mp3", "wav", "ogg").forEach { ext ->
            TagReader.read(junk.inputStream(), ext, junk.size.toLong(), true) // must not throw
        }
        val flac = Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac")
        for (cut in listOf(0, 3, 10, 42, 120, 400)) {
            TagReader.read(flac.copyOf(cut).inputStream(), "flac", cut.toLong(), true)
        }
        val mp3 = Fixtures.bytes("Music/Ines Varga/Paper Moons/01 Paper Moons.mp3")
        for (cut in listOf(0, 5, 10, 300, 2000)) {
            TagReader.read(mp3.copyOf(cut).inputStream(), "mp3", cut.toLong(), true)
        }
        assertNull(TagReader.read(ByteArray(0).inputStream(), "flac", 0, false))
    }

    @Test
    fun `tag value helpers`() {
        assertEquals(3, TagReader.leadingInt("3/12"))
        assertEquals(7, TagReader.leadingInt(" 07 "))
        assertNull(TagReader.leadingInt("A1"))
        assertEquals(2021, TagReader.year("2021-05-02"))
        assertNull(TagReader.year("0000"))
        assertEquals(-6.2f, TagReader.gain("-6.20 dB")!!, 0.0001f)
        assertEquals(1.5f, TagReader.gain("+1.50 dB")!!, 0.0001f)
    }

    @Test
    fun `stream headers from raw bytes`() {
        val bytes = ByteArray(42)
        "fLaC".forEachIndexed { i, c -> bytes[i] = c.code.toByte() }
        bytes[7] = 0x22
        bytes[18] = 0x17; bytes[19] = 0x70; bytes[20] = 0x03; bytes[21] = 0x70 // 96 kHz, 2 ch, 24-bit
        val h = LibraryScanner.parseStreamHeader(bytes, "flac")!!
        assertEquals(96_000, h.sampleRate); assertEquals(24, h.bits); assertEquals(2, h.channels)
        assertNull(LibraryScanner.parseStreamHeader(ByteArray(42), "flac"))

        val b = ByteArray(44)
        "RIFF".forEachIndexed { i, c -> b[i] = c.code.toByte() }
        "WAVE".forEachIndexed { i, c -> b[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> b[12 + i] = c.code.toByte() }
        b[22] = 2
        val rate = 192_000
        b[24] = (rate and 0xFF).toByte(); b[25] = (rate shr 8 and 0xFF).toByte(); b[26] = (rate shr 16 and 0xFF).toByte()
        b[34] = 24
        val w = LibraryScanner.parseStreamHeader(b, "wav")!!
        assertEquals(192_000, w.sampleRate); assertEquals(24, w.bits)
    }

    @Test
    fun `unreadable files get an honest length estimate`() {
        assertEquals(180_000L, LibraryScanner.estimateDurationMs(0, "MP3"))
        // 8 MB at 256 kbps is about 4 minutes
        assertEquals(250_000L, LibraryScanner.estimateDurationMs(8_000_000, "MP3"))
        assertEquals("ALAC", LibraryScanner.codecFor("m4a", 900))
        assertEquals("AAC", LibraryScanner.codecFor("m4a", 256))
        assertEquals(LibraryScanner.stableId("x"), LibraryScanner.stableId("x"))
        assertTrue(LibraryScanner.stableId("x") != LibraryScanner.stableId("y"))
    }
}
