package app.runout

import app.runout.library.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class Slice0CoverPrivacyTest {
    @Test fun `new install never enables online covers without opt in`() = runBlocking { checkDefault(false) }
    @Test fun `existing install never enables online covers without opt in`() = runBlocking { checkDefault(true) }

    @Test fun `opt in is persisted and existing user notice is acknowledged only once`() {
        val context = Fixtures.context
        AlbumJson.writeList(File(context.filesDir, "library.json"), listOf(Fixtures.nightDrive()))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope)
            assertFalse(repo.coverLookupEnabled.value)
            assertTrue(repo.coverLookupNoticePending.value)
            repo.setCoverLookupEnabled(true)
            repo.dismissCoverLookupNotice()
            val again = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope)
            assertTrue(again.coverLookupEnabled.value)
            assertFalse(again.coverLookupNoticePending.value)
            again.setCoverLookupEnabled(false)
            val last = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope)
            assertFalse(last.coverLookupEnabled.value)
        } finally { scope.cancel() }
    }

    @Test fun `new user has no migration notice`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(Fixtures.context, LibraryScanner(Fixtures.context), ArtworkRepository(Fixtures.context), scope)
            assertFalse(repo.coverLookupNoticePending.value)
        } finally { scope.cancel() }
    }

    private fun checkDefault(existing: Boolean) {
        val context = Fixtures.context
        if (existing) AlbumJson.writeList(File(context.filesDir, "library.json"), listOf(Fixtures.nightDrive()))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LibraryRepository(context, LibraryScanner(context), ArtworkRepository(context), scope)
            val field = LibraryRepository::class.java.getDeclaredField("lookupCoversOnline").apply { isAccessible = true }
            assertFalse("Artist/title must not leave the device without opt in", field.getBoolean(repo))
        } finally { scope.cancel() }
    }
}
