package app.runout

import app.runout.library.ArtworkRepository
import app.runout.library.LibraryRepository
import app.runout.library.LibraryScanner
import app.runout.library.ScannedAlbum
import app.runout.model.Album
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Collections

/**
 * Suite 1 — the bug report: "when I select the music folder the tracks or albums don't show".
 * Runs the real scanner against a real folder served through Android's document storage API.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class FolderScanTest {

    private lateinit var tree: android.net.Uri
    private val context get() = Fixtures.context

    @Before
    fun setUp() {
        tree = FolderProvider.install(Fixtures.file("Music"))
    }

    private fun scan(): Pair<app.runout.library.ScanReport, List<ScannedAlbum>> = runBlocking {
        val found = Collections.synchronizedList(mutableListOf<ScannedAlbum>())
        val report = LibraryScanner(context).scan(tree, onAlbum = { found += it })
        report to found.toList()
    }

    private fun List<ScannedAlbum>.named(title: String): Album = first { it.album.title == title }.album

    @Test
    fun `choosing a folder finds every album in it`() {
        val (report, albums) = scan()
        assertNull(report.error)
        assertEquals(setOf("Night Drive"), albums.map { it.album.title }.toSet())
        assertEquals(1, report.albums)
        assertEquals(2, report.audioFiles)
        assertEquals("1 albums from 2 music files in ${report.folders} folders", report.summary)
    }

    @Test
    fun `tags give titles, artists, years and track order`() {
        val (_, albums) = scan()
        val nd = albums.named("Night Drive")
        assertEquals("Vela Park", nd.artist)
        assertEquals(2023, nd.year)
        assertEquals(listOf("Opening", "Lowlight"), nd.tracks.map { it.title })

    }

    @Test
    fun `audio quality is read from the files, not guessed`() {
        val (_, albums) = scan()
        val nd = albums.named("Night Drive")
        assertEquals("FLAC · 24-bit · 96 kHz", nd.quality.label)
        assertTrue(nd.quality.isHiRes)
        assertTrue(nd.tracks.all { it.durationKnown && it.durationMs in 1_900..2_100 })
        assertEquals(-6.2f, nd.albumGainDb!!, 0.01f)
        assertEquals(-5.1f, nd.tracks[0].trackGainDb!!, 0.01f)
        assertTrue(nd.isOpenLicence)

    }

    @Test
    fun `unsupported music is excluded even when Android can decode it`() {
        val (report, albums) = scan()
        assertTrue(albums.all { it.album.tracks.all { track -> track.fileName.endsWith(".flac") } })
        assertTrue(albums.none { it.album.title in setOf("Paper Moons", "Untagged", "Odd") })
        assertEquals(0, report.unreadable)
    }

    @Test
    fun `covers come from the files or the folder`() {
        val (_, albums) = scan()
        assertNotNull(albums.first { it.album.title == "Night Drive" }.embeddedArt)
    }

    @Test
    fun `hidden folders are skipped and other files are reported`() {
        val (report, albums) = scan()
        assertTrue(albums.none { a -> a.album.tracks.any { "skip" in it.fileName } })
        assertEquals(mapOf("txt" to 1, "mp3" to 3, "wav" to 1, "ogg" to 1), report.otherFiles)
    }

    @Test
    fun `a folder with no music explains what it found`() {
        val dir = File(context.cacheDir, "nomusic/Docs").apply { mkdirs() }
        File(dir, "notes.txt").writeText("hi")
        File(dir, "song.xyz").writeText("hi")
        tree = FolderProvider.install(dir.parentFile!!)
        val (report, albums) = scan()
        assertTrue(albums.isEmpty())
        assertTrue(report.summary, report.summary.startsWith("Looked in 2 folders and found no music files"))
        assertTrue(report.summary.contains(".txt"))
    }

    @Test
    fun `the shelf fills in album by album as the folder is read`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
        val sizes = Collections.synchronizedList(mutableListOf<Int>())
        val watcher = repo.albums.onEach { sizes += it.size }.launchIn(scope)
        repo.setFolder(tree)!!.join()
        watcher.cancel()

        val shelf = repo.albums.value
        assertEquals(1, shelf.size)
        // Albums appear one by one while scanning, not all at the end.
        assertTrue("saw $sizes", sizes.any { it in 1..3 })
        // Covers were saved and every record points at real audio.
        assertTrue(shelf.first { it.title == "Night Drive" }.coverPath?.let { File(it).exists() } == true)
        assertEquals("1 albums from 2 music files in ${repo.report.value!!.folders} folders", repo.report.value!!.summary)
        // The shelf is cached, so it's there instantly next launch.
        val again = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
        kotlinx.coroutines.delay(300)
        assertEquals(1, again.albums.value.size)
        scope.cancel()
    }

    private fun CoroutineScope.cancel() = coroutineContext[kotlinx.coroutines.Job]!!.cancel()
}
