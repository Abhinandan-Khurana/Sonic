package app.runout

import android.content.Intent
import android.provider.DocumentsContract
import app.runout.library.AlbumJson
import app.runout.library.ArtworkRepository
import app.runout.library.LibraryRepository
import app.runout.library.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Settings › Library › change folder, end to end in the library: grants, shelf and saved index. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FolderChangeTest {
    private val context = Fixtures.context
    private val night = "Music/Vela Park - Night Drive (2023)"

    /** Two music folders side by side under the test provider's root: A, and B with a broken file. */
    private fun folders(): Pair<android.net.Uri, android.net.Uri> {
        val root = File(context.cacheDir, "change-root-${System.nanoTime()}").apply { mkdirs() }
        File(root, "A/Night Drive").apply { mkdirs() }.let { File(it, "01 Opening.flac").writeBytes(Fixtures.bytes("$night/01 Opening.flac")) }
        File(root, "B/Night Drive").apply { mkdirs() }.let {
            File(it, "01 Opening.flac").writeBytes(Fixtures.bytes("$night/01 Opening.flac"))
            File(it, "02 Broken.flac").writeText("not FLAC")
        }
        FolderProvider.install(root)
        fun tree(sub: String) = DocumentsContract.buildTreeDocumentUri(FolderProvider.AUTHORITY, FolderProvider.ROOT_ID + "/" + sub)
        return tree("A") to tree("B")
    }

    @Test fun `a new folder replaces the shelf and the saved index, even when its scan is partial`() = runBlocking {
        val (a, b) = folders()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            repo.setFolder(a)!!.join()
            val fromA = repo.albums.value.single().id
            val job = repo.setFolder(b)!!
            // The old folder's records leave the crate at once.
            assertTrue(repo.albums.value.none { it.id == fromA })
            job.join()
            // B has an unreadable file, so its scan is partial; A's record still must not come back.
            assertEquals(1, repo.report.value!!.unreadable)
            val fromB = repo.albums.value.single().id
            assertFalse(fromA == fromB)
            assertEquals(listOf(fromB), AlbumJson.readList(File(context.filesDir, "library.json")).map { it.id })
            assertEquals(b, repo.folder.value)
        } finally { scope.cancel() }
    }

    @Test fun `the old folder's grant is released unless something else still uses it`() = runBlocking {
        val (a, b) = folders()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val resolver = context.contentResolver
        fun held() = resolver.persistedUriPermissions.map { it.uri }
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            repo.setFolder(a)!!.join()
            assertTrue(a in held())
            repo.setFolder(b)!!.join()
            assertFalse("the old folder's read grant is released", a in held())
            assertTrue(b in held())
            // Back to A while B is (say) the downloads folder: B keeps its grant.
            repo.setFolder(a, keepGrant = { it == b })!!.join()
            assertTrue(b in held())
            assertTrue(a in held())
        } finally {
            resolver.persistedUriPermissions.forEach { runCatching { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            scope.cancel()
        }
    }

    @Test fun `picking the same folder again is a rescan that keeps the shelf`() = runBlocking {
        val (a, _) = folders()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope, lookupCoversOnline = false)
            repo.setFolder(a)!!.join()
            val before = repo.albums.value.single().id
            val job = repo.setFolder(a)!!
            assertEquals(listOf(before), repo.albums.value.map { it.id })
            job.join()
            assertEquals(listOf(before), repo.albums.value.map { it.id })
        } finally { scope.cancel() }
    }
}
