package app.runout

import android.net.Uri
import app.runout.library.AlbumJson
import app.runout.library.FlacPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class Slice0RecordListTest {
    @Test fun `unreadable records and tracks stay on shelf without rewriting index`() {
        val album = Fixtures.nightDrive(owned = true)
        val file = File(Fixtures.context.filesDir, "pressings.json")
        AlbumJson.writeList(file, listOf(album))
        val before = file.readBytes()
        val track = File(Uri.parse(album.tracks.first().uri).path!!)
        val audio = track.readBytes()
        track.delete()
        val shelf = AlbumJson.readValidatedList(Fixtures.context, file)
        assertEquals(album.tracks.map { it.id }, shelf.single().tracks.map { it.id })
        assertArrayEquals(before, file.readBytes())
        assertNotNull(shelf.single().unavailableReason)
        assertNull(FlacPolicy.read(Fixtures.context, shelf.single().tracks.first()))
        track.writeBytes(audio)
        assertEquals(album.tracks.size, FlacPolicy.filterAlbum(Fixtures.context, AlbumJson.readValidatedList(Fixtures.context, file).single())!!.tracks.size)
    }

    @Test fun `invalid legacy music is retained for repair but not admitted as FLAC`() {
        val album = Fixtures.paperMoons()
        val file = File(Fixtures.context.filesDir, "received.json")
        AlbumJson.writeList(file, listOf(album))
        val before = file.readBytes()
        assertEquals(album.id, AlbumJson.readValidatedList(Fixtures.context, file).single().id)
        assertArrayEquals(before, file.readBytes())
        assertNull(FlacPolicy.filterAlbum(Fixtures.context, album))
    }

    @Test fun `every rewrite preserves distinct dated copy of previous index`() {
        val file = File(Fixtures.context.filesDir, "backups.json")
        val album = Fixtures.nightDrive()
        AlbumJson.writeList(file, listOf(album))
        val first = file.readText()
        AlbumJson.writeList(file, listOf(album.copy(title = "Second")))
        val second = file.readText()
        AlbumJson.writeList(file, listOf(album.copy(title = "Third")))
        val backups = file.parentFile!!.listFiles()!!.filter { it.name.startsWith(file.name + ".backup-") }
        assertEquals(2, backups.size)
        assertEquals(setOf(first, second), backups.map { it.readText() }.toSet())
        assertTrue(backups.all { Regex(".*backup-\\d{8}T.*").matches(it.name) })
    }

    @Test fun `failed or partial JSON read cannot be overwritten by later list operation`() {
        val file = File(Fixtures.context.filesDir, "broken.json")
        file.writeText("[{broken")
        assertTrue(AlbumJson.readValidatedList(Fixtures.context, file).isEmpty())
        val before = file.readText()
        assertThrows(Exception::class.java) { AlbumJson.writeList(file, listOf(Fixtures.nightDrive())) }
        assertEquals(before, file.readText())
        file.writeText("[${AlbumJson.toJson(Fixtures.nightDrive())},{\"id\":\"unreadable-row\"}]")
        val partial = file.readText()
        assertThrows(Exception::class.java) { AlbumJson.writeList(file, AlbumJson.readValidatedList(Fixtures.context, file)) }
        assertEquals(partial, file.readText())
    }
}
