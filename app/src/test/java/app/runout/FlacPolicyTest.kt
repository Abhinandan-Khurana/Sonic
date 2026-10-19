package app.runout

import android.net.Uri
import app.runout.library.AlbumJson
import app.runout.library.FlacPolicy
import app.runout.library.LibraryScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FlacPolicyTest {
    private val flac get() = Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac")

    @Test fun `FLAC names are case insensitive and cannot be disguised by suffixes`() {
        assertTrue(FlacPolicy.isFlacName("Artist\\Album\\Track.FLAC"))
        assertTrue(FlacPolicy.isFlacName("audio/Track.flac"))
        assertFalse(FlacPolicy.isFlacName("track.flac.mp3"))
        assertFalse(FlacPolicy.isFlacName("track.flac "))
        assertFalse(FlacPolicy.isFlacName("track.wav"))
    }

    @Test fun `actual bytes must be native FLAC even when filename claims FLAC`() {
        assertNotNull(FlacPolicy.read(flac.inputStream()))
        assertFalse(FlacPolicy.isValid(Fixtures.bytes("Music/Ines Varga/Paper Moons/01 Paper Moons.mp3").inputStream()))
        assertFalse(FlacPolicy.isValid(Fixtures.bytes("tones/tone_16_44.wav").inputStream()))
        val fake = File(Fixtures.context.cacheDir, "renamed.flac")
        fake.writeBytes(Fixtures.bytes("tones/tone_16_44.wav"))
        assertFalse(FlacPolicy.isValid(fake))
        val wrongName = File(Fixtures.context.cacheDir, "native.mp3").also { it.writeBytes(flac) }
        assertFalse(FlacPolicy.isValid(wrongName))
    }

    @Test fun `valid ID3 prefixed native FLAC is admitted`() {
        val id3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 3, 1, 2, 3)
        val info = FlacPolicy.read((id3 + flac).inputStream())!!
        assertEquals(96_000, info.sampleRate)
        assertEquals(24, info.bits)
    }

    @Test fun `malformed or truncated STREAMINFO and metadata only files are refused`() {
        for (length in listOf(0, 4, 8, 26, 41, 42, 120, 400))
            assertNull("cut at $length", FlacPolicy.read(flac.copyOf(length).inputStream()))
        val badLength = flac.copyOf().also { it[7] = 33 }
        assertNull(FlacPolicy.read(badLength.inputStream()))
        val badBlock = flac.copyOf().also { it[8] = 0; it[9] = 1 }
        assertNull(FlacPolicy.read(badBlock.inputStream()))
        val missingInfo = flac.copyOf().also { it[4] = 1 }
        assertNull(FlacPolicy.read(missingInfo.inputStream()))
        val rateZero = flac.copyOf().also { it[18] = 0; it[19] = 0; it[20] = (it[20].toInt() and 15).toByte() }
        assertNull(FlacPolicy.read(rateZero.inputStream()))
        val metadataOnly = flac.copyOf(42).also { it[4] = 0x80.toByte() }
        assertNull(FlacPolicy.read(metadataOnly.inputStream()))
        var frameStart = 4
        while (true) {
            val header = flac[frameStart].toInt() and 0xFF
            val length = (flac[frameStart + 1].toInt() and 0xFF shl 16) or
                (flac[frameStart + 2].toInt() and 0xFF shl 8) or (flac[frameStart + 3].toInt() and 0xFF)
            frameStart += 4 + length
            if (header and 0x80 != 0) break
        }
        val invalidCrc = flac.copyOf().also { it[frameStart + 5] = (it[frameStart + 5].toInt() xor 1).toByte() }
        assertNull(FlacPolicy.read(invalidCrc.inputStream()))
        assertNull(FlacPolicy.read(flac.copyOf(frameStart + 9).inputStream()))
    }

    @Test fun `migration preserves valid FLAC identities and original files with an index backup`() {
        val good = Fixtures.nightDrive()
        val unsupported = Fixtures.paperMoons()
        val mixed = good.copy(tracks = good.tracks + unsupported.tracks)
        val index = File(Fixtures.context.filesDir, "legacy-mixed.json")
        AlbumJson.writeList(index, listOf(mixed, unsupported))
        val before = index.readText()
        val result = AlbumJson.readValidatedList(Fixtures.context, index)
        assertEquals(listOf(good.id), result.map { it.id })
        assertEquals(good.tracks.map { it.id }, result.single().tracks.map { it.id })
        assertEquals("FLAC", result.single().quality.codec)
        assertEquals(before, File(index.parentFile, index.name + ".before-flac").readText())
        assertTrue(unsupported.tracks.all { File(Uri.parse(it.uri).path!!).exists() })
        assertEquals(result, AlbumJson.readValidatedList(Fixtures.context, index))
    }

    @Test fun `scanner rejects renamed MP3 and truncated FLAC without estimating them onto shelf`() = runBlocking {
        val dir = File(Fixtures.context.cacheDir, "invalid-flac").apply { mkdirs() }
        File(dir, "renamed.flac").writeBytes(Fixtures.bytes("Music/Ines Varga/Paper Moons/01 Paper Moons.mp3"))
        File(dir, "truncated.FLAC").writeBytes(flac.copyOf(42))
        val tree = FolderProvider.install(dir)
        val found = mutableListOf<app.runout.library.ScannedAlbum>()
        val report = LibraryScanner(Fixtures.context).scan(tree, onAlbum = { found += it })
        assertEquals(2, report.audioFiles)
        assertEquals(2, report.unreadable)
        assertTrue(found.isEmpty())
    }
}
