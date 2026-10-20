package app.runout

import app.runout.library.ArtworkRepository
import app.runout.library.LibraryRepository
import app.runout.library.LibraryScanner
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class Slice0CoverCacheTest {
    @Test fun `missing cover cache survives restart expires and keys artist title`() = runBlocking {
        var now = 1_000L
        val http = FakeHttp(mapOf("musicbrainz" to { "{\"release-groups\":[]}".toByteArray() }))
        var artwork = ArtworkRepository(Fixtures.context, http = http, now = { now })
        assertNull(artwork.lookupOnline("missing", "Artist", "Title"))
        artwork = ArtworkRepository(Fixtures.context, http = http, now = { now })
        assertNull(artwork.lookupOnline("missing", "Artist", "Title"))
        assertEquals(1, http.calls.size)
        assertNull(artwork.lookupOnline("missing", "Artist", "New title"))
        assertEquals(2, http.calls.size)
        now += 8L * 24 * 60 * 60 * 1000
        assertNull(artwork.lookupOnline("missing", "Artist", "Title"))
        assertEquals(3, http.calls.size)
    }

    @Test fun `scan sends no queries by default opt in sends one and disabling stops them`() = runBlocking {
        val context = Fixtures.context
        val http = FakeHttp(mapOf("musicbrainz" to { "{\"release-groups\":[]}".toByteArray() }))
        val dir = File(context.cacheDir, "no-art-root").apply { mkdirs() }
        val bytes = Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac")
        // Remove only the FLAC picture metadata; keep STREAMINFO, comments and frame bytes intact.
        val blocks = mutableListOf<ByteArray>()
        var pos = 4
        while (true) {
            val header = bytes[pos].toInt() and 255
            val length = ((bytes[pos + 1].toInt() and 255) shl 16) or ((bytes[pos + 2].toInt() and 255) shl 8) or (bytes[pos + 3].toInt() and 255)
            if (header and 127 != 6) blocks += bytes.copyOfRange(pos, pos + 4 + length).also { it[0] = (header and 127).toByte() }
            pos += 4 + length
            if (header and 128 != 0) break
        }
        blocks.last()[0] = (blocks.last()[0].toInt() or 128).toByte()
        File(dir, "01 Opening.flac").writeBytes(bytes.copyOfRange(0, 4) + blocks.reduce { a, b -> a + b } + bytes.copyOfRange(pos, bytes.size))
        val tree = FolderProvider.install(dir)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context, http = http), scope)
            repo.setFolder(tree)!!.join()
            assertEquals(1, repo.albums.value.size)
            assertNull(repo.albums.value.single().coverPath)
            assertTrue(http.calls.isEmpty())
            repo.setCoverLookupEnabled(true)
            repo.rescan()!!.join()
            assertEquals(1, http.calls.size)
            repo.rescan()!!.join()
            assertEquals(1, http.calls.size)
            repo.setCoverLookupEnabled(false)
            repo.rescan()!!.join()
            assertEquals(1, http.calls.size)
        } finally { scope.cancel() }
    }
}
