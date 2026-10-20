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
            val before = File(context.filesDir, "library.json").readBytes()
            second.writeText("not FLAC")
            repo.rescan()!!.join()
            assertEquals(2, repo.albums.value.single().tracks.size)
            assertArrayEquals(before, File(context.filesDir, "library.json").readBytes())
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
