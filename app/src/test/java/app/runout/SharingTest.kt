package app.runout

import android.net.Uri
import app.runout.model.Source
import app.runout.sharing.ImportResult
import app.runout.sharing.ShareRepository
import app.runout.sharing.VinylPackage
import app.runout.social.Profile
import app.runout.social.ShelfItem
import app.runout.social.SocialRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Suite 6 — sending records with no server: .vinyl files packed on one phone and opened on another.
 * "Two phones" here are two profiles on the same test device, with storage cleared in between.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class SharingTest {

    private val context get() = Fixtures.context
    private val maya = Profile("maya-id", "Maya", Profile.ACCENTS[1])

    /** Wipes the "social" identity so the next SocialRepository is someone else. */
    private fun becomeSomeoneElse(): SocialRepository {
        context.getSharedPreferences("social", 0).edit().clear().commit()
        File(context.filesDir, "friends.json").delete()
        return SocialRepository(context)
    }

    private fun invalid(block: () -> Unit): String {
        try { block() } catch (e: VinylPackage.Invalid) { return e.message ?: "" }
        fail("expected the package to be refused"); error("")
    }

    @Test
    fun `a Creative Commons album travels with its audio, byte for byte`() = runBlocking {
        val senderSocial = SocialRepository(context).apply { rename("Maya") }
        val album = Fixtures.nightDrive()
        val file = ShareRepository(context, senderSocial).exportAlbum(album)
        assertTrue(file.name.endsWith(".vinyl"))
        ZipFile(file).use { z ->
            val names = z.entries().toList().map { it.name }
            assertEquals(listOf("manifest.json", "sleeve.jpg", "audio/01 Opening.flac", "audio/02 Lowlight.flac", "checksums.json"), names)
            assertEquals("ALBUM", JSONObject(z.getInputStream(z.getEntry("manifest.json")).readBytes().decodeToString()).getString("kind"))
        }

        val receiverSocial = becomeSomeoneElse()
        val receiver = ShareRepository(context, receiverSocial)
        val result = receiver.import(Uri.fromFile(file)) as ImportResult.AlbumAdded
        val got = result.album
        assertEquals("Night Drive", got.title)
        assertEquals(Source.RECEIVED, got.source)
        assertEquals("Maya", got.from)
        assertEquals("FLAC · 24-bit · 96 kHz", got.quality.label)
        assertNotNull(got.coverPath)
        got.tracks.zip(album.tracks).forEach { (r, o) ->
            assertArrayEquals(File(Uri.parse(o.uri).path!!).readBytes(), File(Uri.parse(r.uri).path!!).readBytes())
        }
        assertEquals(listOf(got.id), receiver.received.value.map { it.id })
        // Receiving a record also adds the sender as a friend.
        assertEquals(listOf("Maya"), receiverSocial.friends.value.map { it.profile.name })
    }

    @Test
    fun `other music goes as a card - sleeve and track list, never the audio`() = runBlocking {
        val sender = ShareRepository(context, SocialRepository(context).apply { rename("Maya") })
        val album = Fixtures.paperMoons()
        val file = sender.exportAlbum(album)
        ZipFile(file).use { z -> assertTrue(z.entries().toList().none { it.name.startsWith("audio/") }) }

        val receiverSocial = becomeSomeoneElse()
        val receiver = ShareRepository(context, receiverSocial)
        val result = receiver.import(Uri.fromFile(file)) as ImportResult.CardAdded
        assertEquals("Maya", result.fromName)
        assertTrue(result.album.tracks.all { it.uri.isEmpty() })
        assertTrue(receiver.received.value.isEmpty())
        assertEquals(listOf("Paper Moons"), receiverSocial.friends.value.single().cards.map { it.title })
    }

    @Test
    fun `your own music and pressings you made can travel with audio`() = runBlocking {
        val file = ShareRepository(context, SocialRepository(context)).exportAlbum(Fixtures.paperMoons().copy(owned = true))
        ZipFile(file).use { z -> assertEquals(3, z.entries().toList().count { it.name.startsWith("audio/") }) }
    }

    @Test
    fun `friend cards add friends, refresh their shelf, and your own card is refused`() = runBlocking {
        val me = SocialRepository(context).apply { rename("Maya") }
        val card = ShareRepository(context, me).exportFriendCard(listOf(Fixtures.nightDrive()))
        assertTrue(ShareRepository(context, me).import(Uri.fromFile(card)) is ImportResult.Failed)

        val other = becomeSomeoneElse()
        val repo = ShareRepository(context, other)
        val first = repo.import(Uri.fromFile(card)) as ImportResult.FriendAdded
        assertTrue(first.isNew)
        assertEquals("Maya", first.name)
        assertEquals(1, first.records)
        val again = repo.import(Uri.fromFile(card)) as ImportResult.FriendAdded
        assertFalse(again.isNew)
        assertEquals(1, other.friends.value.size)
        assertNotNull(other.friends.value.single().shelf.single().thumb)
    }

    @Test
    fun `the package format round-trips`() {
        val dir = File(context.cacheDir, "pkg").apply { mkdirs() }
        val audio = Fixtures.bytes("tones/tone_16_44.wav")
        val out = ByteArrayOutputStream()
        val album = Fixtures.nightDrive().let { a -> a.copy(tracks = a.tracks.take(1).map { it.copy(fileName = "audio/01 Tone.wav") }) }
        VinylPackage.write(out, VinylPackage.Kind.ALBUM, maya, album, listOf(ShelfItem("A", "B", 2020, "FLAC", null)), null, listOf("audio/01 Tone.wav" to { audio.inputStream() }))
        val c = VinylPackage.read(out.toByteArray().inputStream(), dir)
        assertEquals(VinylPackage.Kind.ALBUM, c.kind)
        assertEquals(maya, c.from)
        assertEquals("Night Drive", c.album!!.title)
        assertEquals(1, c.shelf.size)
        assertNull(c.sleeve)
        assertArrayEquals(audio, c.audio.getValue("audio/01 Tone.wav").readBytes())
        assertEquals("audio/03 AC DC Back.mp3", VinylPackage.entryNameFor(2, "AC/DC: Back?", "MP3"))
    }

    @Test
    fun `broken or hostile files are refused safely`() {
        val dir = File(context.cacheDir, "evil").apply { mkdirs() }
        // Not a zip at all.
        assertTrue(invalid { VinylPackage.read("hello".byteInputStream(), dir) }.contains("no manifest"))

        fun zip(manifest: String, vararg entries: Pair<String, ByteArray>): ByteArray {
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("manifest.json")); z.write(manifest.toByteArray()); z.closeEntry()
                entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
            }
            return bos.toByteArray()
        }
        val from = maya.toJson().toString()
        // A path that tries to climb out of the record's folder.
        invalid { VinylPackage.read(zip("""{"format":1,"kind":"FRIEND","from":$from}""", "audio/../../../escape.txt" to "x".toByteArray()).inputStream(), dir) }
        assertFalse(File(context.cacheDir, "escape.txt").exists())
        assertFalse(File(dir.parentFile!!.parentFile, "escape.txt").exists())
        // Made by a newer Runout.
        assertTrue(invalid { VinylPackage.read(zip("""{"format":99,"kind":"FRIEND","from":$from}""").inputStream(), dir) }.contains("newer"))
        // An album whose audio is missing.
        val albumJson = app.runout.library.AlbumJson.toJson(Fixtures.paperMoons().let { a -> a.copy(tracks = a.tracks.map { it.copy(fileName = "audio/x.mp3") }) }, false)
        assertTrue(invalid { VinylPackage.read(zip("""{"format":1,"kind":"ALBUM","from":$from,"album":$albumJson}""").inputStream(), dir) }.contains("missing"))
        // An unknown kind.
        invalid { VinylPackage.read(zip("""{"format":1,"kind":"VIRUS","from":$from}""").inputStream(), dir) }
        // And the repository turns all of this into a friendly failure instead of a crash.
        val bad = File(context.cacheDir, "bad.vinyl").apply { writeText("nope") }
        assertTrue(runBlocking { ShareRepository(context, SocialRepository(context)).import(Uri.fromFile(bad)) } is ImportResult.Failed)
    }

    @Test
    fun `a file damaged on the way is caught, not played`() {
        val dir = File(context.cacheDir, "damaged").apply { mkdirs() }
        val audio = Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac")
        val album = Fixtures.nightDrive().let { a -> a.copy(tracks = a.tracks.take(1).map { it.copy(fileName = "audio/01 Opening.flac") }) }
        val good = ByteArrayOutputStream().also {
            VinylPackage.write(it, VinylPackage.Kind.ALBUM, maya, album, audio = listOf("audio/01 Opening.flac" to { audio.inputStream() }))
        }.toByteArray()
        // Intact: arrives byte for byte.
        val ok = VinylPackage.read(good.inputStream(), dir)
        assertArrayEquals(audio, ok.audio.getValue("audio/01 Opening.flac").readBytes())
        // Rebuild the same record with one changed byte in the audio but the original checksum.
        val bad = ByteArrayOutputStream()
        ZipOutputStream(bad).use { out ->
            java.util.zip.ZipInputStream(good.inputStream()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    var bytes = zin.readBytes()
                    if (e.name.startsWith("audio/")) bytes = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
                    out.putNextEntry(ZipEntry(e.name)); out.write(bytes); out.closeEntry()
                }
            }
        }
        assertTrue(invalid { VinylPackage.read(bad.toByteArray().inputStream(), File(dir, "b")) }.contains("arrived damaged"))
    }

    @Test
    fun `received records can be taken off the shelf`() = runBlocking {
        val file = ShareRepository(context, SocialRepository(context).apply { rename("Maya") }).exportAlbum(Fixtures.nightDrive())
        val receiver = ShareRepository(context, becomeSomeoneElse())
        val added = (receiver.import(Uri.fromFile(file)) as ImportResult.AlbumAdded).album
        val folder = File(Uri.parse(added.tracks.first().uri).path!!).parentFile!!.parentFile!!
        assertTrue(folder.exists())
        receiver.delete(added.id)
        assertTrue(receiver.received.value.isEmpty())
        assertFalse(folder.exists())
        // And it stays gone after a restart.
        assertTrue(ShareRepository(context, SocialRepository(context)).received.value.isEmpty())
    }
}
