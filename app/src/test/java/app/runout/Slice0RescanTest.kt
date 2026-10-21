package app.runout

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import app.runout.library.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

class UnlistableLibraryProvider : ContentProvider() {
    companion object { var throws = false }
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? {
        if (throws) throw SecurityException("Grant revoked")
        return null
    }
    override fun getType(uri: Uri) = "vnd.android.document/directory"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class Slice0RescanTest {
    @Test fun `null root listing keeps cached shelf and index`() = checkFailedRoot(false)
    @Test fun `revoked root listing keeps cached shelf and index`() = checkFailedRoot(true)

    private fun checkFailedRoot(throws: Boolean) = runBlocking {
        val context = Fixtures.context
        val album = Fixtures.nightDrive()
        val file = File(context.filesDir, "library.json")
        AlbumJson.writeList(file, listOf(album))
        val before = file.readBytes()
        val authority = "app.runout.test.unlistable"
        UnlistableLibraryProvider.throws = throws
        Robolectric.buildContentProvider(UnlistableLibraryProvider::class.java).create(ProviderInfo().apply {
            this.authority = authority; exported = true; grantUriPermissions = true
        })
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root")
        context.getSharedPreferences("library", 0).edit().putString("folder", tree.toString()).commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            withTimeout(5_000) { while (repo.albums.value.isEmpty()) delay(10) }
            repo.rescan()!!.join()
            assertEquals(listOf(album.id), repo.albums.value.map { it.id })
            assertNotNull(repo.report.value!!.error)
            assertArrayEquals(before, file.readBytes())
            assertNull(repo.scan.value)
        } finally { scope.cancel() }
    }

    @Test fun `partial album read preserves previous tracks and index`() = runBlocking {
        val context = Fixtures.context
        val dir = File(context.cacheDir, "partial-root").apply { mkdirs() }
        val first = File(dir, "01 Opening.flac").also { it.writeBytes(Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac")) }
        val second = File(dir, "02 Lowlight.flac").also { it.writeBytes(Fixtures.bytes("Music/Vela Park - Night Drive (2023)/02 Lowlight.flac")) }
        val tree = FolderProvider.install(dir)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            repo.setFolder(tree)!!.join()
            second.writeText("not FLAC")
            repo.rescan()!!.join()
            assertEquals(2, repo.albums.value.single().tracks.size)
            // The saved shelf keeps both tracks too, and the report names the file it couldn't read.
            assertEquals(2, AlbumJson.readList(File(context.filesDir, "library.json")).single().tracks.size)
            assertEquals(listOf("02 Lowlight.flac"), repo.report.value!!.unreadableFiles.map { it.path.substringAfterLast('/') })
        } finally { scope.cancel() }
    }

    @Test fun `after a reinstall, a scan with an unreadable file still fills the shelf and saves it`() = runBlocking {
        // The phone bug: nothing cached, one broken file, and the albums appeared then vanished.
        val context = Fixtures.context
        File(context.filesDir, "library.json").delete()
        val dir = File(context.cacheDir, "reinstall-root").apply { mkdirs() }
        File(dir, "01 Opening.flac").writeBytes(Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac"))
        File(dir, "02 Broken.flac").writeBytes(Fixtures.bytes("Music/Ines Varga/Paper Moons/01 Paper Moons.mp3"))
        val tree = FolderProvider.install(dir)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            repo.setFolder(tree)!!.join()
            assertEquals(1, repo.albums.value.size)
            assertEquals(1, AlbumJson.readList(File(context.filesDir, "library.json")).size)
            val broken = repo.report.value!!.unreadableFiles.single()
            assertTrue(broken.path.endsWith("02 Broken.flac"))
            assertTrue(broken.reason, broken.reason.contains("MP3"))
        } finally { scope.cancel() }
    }

    @Test fun `the startup refresh does not cancel a scan the caller started`() = runBlocking {
        // Startup wants to refresh a saved folder quietly. It must never cancel a scan that
        // the caller started afterwards: a cancelled scan leaves the shelf empty, which is
        // the phone bug this release set out to fix.
        val context = Fixtures.context
        // A slow, unreadable cache. Reading it takes long enough that startup's check for a
        // saved folder lands after the caller's scan has started, which is the ordering that
        // used to cancel the caller's scan. It is not a valid index, so the shelf stays empty.
        val cache = File(context.filesDir, "library.json")
        cache.writeText(" ".repeat(2 * 1024 * 1024))
        val dir = File(context.cacheDir, "startup-race-root").apply { mkdirs() }
        File(dir, "01 Opening.flac").writeBytes(Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac"))
        val tree = FolderProvider.install(dir)
        val gate = java.util.concurrent.CountDownLatch(1)
        FolderProvider.block = gate
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            val job = repo.setFolder(tree)!!
            // The scan is parked inside the provider now, so it is provably in flight while
            // startup gets its chance to refresh.
            delay(500)
            // Startup has read the cache by now. Take it away so the scan can save the shelf.
            cache.delete()
            gate.countDown()
            job.join()
            assertFalse("the startup refresh cancelled the caller's scan", job.isCancelled)
            assertEquals(1, repo.albums.value.size)
            assertEquals(1, AlbumJson.readList(File(context.filesDir, "library.json")).size)
        } finally {
            FolderProvider.block = null
            scope.cancel()
        }
    }

    @Test fun `repair forgets albums that are gone, even when a file is unreadable`() = runBlocking {
        val context = Fixtures.context
        // An index from before, holding an album this folder doesn't have.
        val stale = Fixtures.nightDrive().copy(id = "gone-album", title = "Gone")
        AlbumJson.writeList(File(context.filesDir, "library.json"), listOf(stale))
        val dir = File(context.cacheDir, "repair-root").apply { mkdirs() }
        File(dir, "01 Opening.flac").writeBytes(Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac"))
        File(dir, "02 Broken.flac").writeText("not FLAC")
        val tree = FolderProvider.install(dir)
        context.getSharedPreferences("library", 0).edit().putString("folder", tree.toString()).commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            withTimeout(5_000) { while (repo.albums.value.isEmpty()) delay(10) }
            // A plain rescan can't prove the stale album is gone: one file was unreadable.
            repo.rescan()!!.join()
            assertTrue(repo.albums.value.any { it.id == "gone-album" })
            // Repair rebuilds from the folder alone.
            repo.repair()!!.join()
            assertFalse(repo.albums.value.any { it.id == "gone-album" })
            assertEquals(1, repo.albums.value.size)
            assertEquals(1, repo.report.value!!.unreadable)
        } finally { scope.cancel() }
    }

    @Test fun `successfully listed empty root may replace shelf`() = runBlocking {
        val context = Fixtures.context
        val file = File(context.filesDir, "library.json")
        AlbumJson.writeList(file, listOf(Fixtures.nightDrive()))
        val tree = FolderProvider.install(File(context.cacheDir, "empty-root").apply { mkdirs() })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            withTimeout(5_000) { while (repo.albums.value.isEmpty()) delay(10) }
            repo.setFolder(tree)!!.join()
            assertNull(repo.report.value!!.error)
            assertTrue(repo.albums.value.isEmpty())
            assertTrue(AlbumJson.readList(file).isEmpty())
        } finally { scope.cancel() }
    }
}
