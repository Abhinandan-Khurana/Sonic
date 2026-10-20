package app.runout

import app.runout.sharing.ImportResult
import app.runout.sharing.ShareRepository
import app.runout.sharing.VinylPackage

import app.runout.social.SocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class VinylImportSafetyTest {
    private val context get() = Fixtures.context

    private fun oversizedEntry(name: String, limit: Int): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write("{}".toByteArray())
            val spaces = ByteArray(8192) { ' '.code.toByte() }
            var remaining = limit - 1 // valid JSON followed by whitespace, total limit + 1
            while (remaining > 0) {
                val n = minOf(spaces.size, remaining)
                zip.write(spaces, 0, n)
                remaining -= n
            }
            zip.closeEntry()
        }
    }.toByteArray()

    private fun assertEntryBound(name: String, limit: Int) {
        try {
            VinylPackage.read(oversizedEntry(name, limit).inputStream(), File(context.cacheDir, "bounded"))
            fail("Oversized $name must be refused")
        } catch (e: VinylPackage.Invalid) {
            assertTrue("Must reject $name for its size, not later validation: ${e.message}",
                e.message.orEmpty().contains("too large", ignoreCase = true))
        }
    }

    @Test fun `manifest uncompressed bytes are bounded even without a zip size header`() {
        assertEntryBound("manifest.json", 4 * 1024 * 1024)
    }

    @Test fun `checksums uncompressed bytes are bounded even without a zip size header`() {
        assertEntryBound("checksums.json", 1024 * 1024)
    }

    private fun brokenInput(failure: Throwable): InputStream = object : InputStream() {
        override fun read(): Int = throw failure
        override fun read(b: ByteArray, off: Int, len: Int): Int = throw failure
    }

    private fun incoming(): List<File> = File(context.filesDir, "received").listFiles().orEmpty()
        .filter { it.name.startsWith("incoming-") }

    @Test fun `fatal read errors clean staging and propagate unchanged`() = runBlocking {
        val repo = ShareRepository(context, SocialRepository(context))
        val failure = OutOfMemoryError("injected; no large allocation")
        try {
            repo.importStream(brokenInput(failure))
            fail("Fatal errors must propagate")
        } catch (got: OutOfMemoryError) {
            // Coroutine debug stack recovery may copy the throwable across withContext.
            assertTrue(got === failure || got.cause === failure)
        }
        assertTrue("Fatal read left incoming staging", incoming().isEmpty())
    }

    @Test fun `read cancellation cleans staging and is not a friendly failure`() = runBlocking {
        val repo = ShareRepository(context, SocialRepository(context))
        val failure = CancellationException("cancel import")
        try {
            repo.importStream(brokenInput(failure))
            fail("Cancellation must propagate")
        } catch (got: CancellationException) {
            // Coroutine debug stack recovery may copy the throwable across withContext.
            assertTrue(got === failure || got.cause === failure)
        }
        assertTrue(incoming().isEmpty())
    }

    @Test fun `ordinary invalid imports clean staging and remain friendly failures`() = runBlocking {
        val repo = ShareRepository(context, SocialRepository(context))
        assertTrue(repo.importStream("not a record".byteInputStream()) is ImportResult.Failed)
        assertTrue(incoming().isEmpty())
    }

    @Test fun `startup sweeps only incoming staging and retains published music`() {
        val received = File(context.filesDir, "received").apply { mkdirs() }
        val stale = File(received, "incoming-abandoned/audio/track.flac").apply { parentFile!!.mkdirs(); writeText("staging") }
        val published = File(received, "rx-published/audio/track.flac").apply { parentFile!!.mkdirs(); writeText("published") }
        val card = File(received, "card-published/sleeve.jpg").apply { parentFile!!.mkdirs(); writeText("sleeve") }
        val unrelated = File(context.filesDir, "incoming-unrelated/keep").apply { parentFile!!.mkdirs(); writeText("keep") }
        ShareRepository(context, SocialRepository(context))
        assertFalse("Stale incoming folder survived startup", stale.parentFile!!.parentFile!!.exists())
        assertEquals("published", published.readText())
        assertEquals("sleeve", card.readText())
        assertEquals("keep", unrelated.readText())
    }

    @Test fun `staging sweep does not follow links into published music`() {
        val received = File(context.filesDir, "received").apply { mkdirs() }
        val published = File(received, "rx-published/audio/track.flac").apply { parentFile!!.mkdirs(); writeText("published") }
        val staging = File(received, "incoming-abandoned").apply { mkdirs() }
        Files.createSymbolicLink(File(staging, "audio").toPath(), published.parentFile!!.toPath())
        ShareRepository(context, SocialRepository(context))
        assertEquals("published", published.readText())
        assertFalse(staging.exists())
    }
}
