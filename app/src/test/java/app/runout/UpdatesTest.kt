package app.runout

import app.runout.net.Http
import app.runout.update.UpdateState
import app.runout.update.Updates
import app.runout.update.Version
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpdatesTest {
    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false) =
        """{"tag_name":"$tag","name":"Sonic. $tag","prerelease":$prerelease,"draft":$draft,"html_url":"https://github.com/Abhiroop1122/Sonic/releases/tag/$tag"}"""

    @Test fun `versions order as the tags do`() {
        assertTrue(Version.parse("v1.0.1")!! > Version.parse("1.0.0")!!)
        assertTrue(Version.parse("v1.1.0-dev.2")!! < Version.parse("v1.1.0")!!)
        assertTrue(Version.parse("v1.1.0-dev.2")!! > Version.parse("v1.0.9")!!)
        assertEquals(Version(1, 2, 0), Version.parse("v1.2"))
    }

    @Test fun `only a newer stable release is offered`() {
        val newer = Updates.latest(release("v1.0.1"), "1.0.0")
        assertEquals("1.0.1", newer?.version.toString())
        assertEquals("https://github.com/Abhiroop1122/Sonic/releases/tag/v1.0.1", newer?.page)
        assertNull("same version", Updates.latest(release("v1.0.0"), "1.0.0"))
        assertNull("older", Updates.latest(release("v0.7.0"), "1.0.0"))
        assertNull("a development build isn't latest", Updates.latest(release("v1.1.0-dev.1", prerelease = true), "1.0.0"))
        assertNull("a draft", Updates.latest(release("v2.0.0", draft = true), "1.0.0"))
    }

    @Test fun `the development channel also takes pre-releases, newest first`() {
        val list = "[" + listOf(release("v1.2.0-dev.2", prerelease = true), release("v1.1.0"), release("v1.2.0-dev.1", prerelease = true), release("v1.3.0-dev.1", draft = true)).joinToString(",") + "]"
        val newer = Updates.newest(list, "1.1.0")
        assertEquals("1.2.0-dev.2", newer?.version.toString())
        assertTrue(newer!!.development)
        assertNull("nothing newer", Updates.newest(list, "1.2.0-dev.2"))
        // A release outranks its own development builds.
        assertEquals("1.2.0", Updates.newest("[" + release("v1.2.0") + "," + release("v1.2.0-dev.2", prerelease = true) + "]", "1.2.0-dev.2")?.version.toString())
    }

    @Test fun `stable asks for the latest release, development for the list, and a dev build starts on development`() = runBlocking {
        val asked = mutableListOf<String>()
        val http = object : Http {
            override fun get(url: String): ByteArray? { asked += url; return (if (url.endsWith("/latest")) release("v1.1.0") else "[" + release("v1.2.0-dev.1", prerelease = true) + "]").toByteArray() }
            override fun download(url: String, to: File, onBytes: (Long) -> Unit) = false
        }
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("updates", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val dev = Updates(context, http, "1.2.0-dev.0")
        dev.check()
        assertTrue(asked.last().endsWith("releases?per_page=30"))
        assertEquals("1.2.0-dev.1", (dev.state.value as UpdateState.Available).release.version.toString())
        val stable = Updates(context, http, "1.0.0")
        stable.setChannel(app.runout.update.UpdateChannel.STABLE)
        stable.check()
        assertTrue(asked.last().endsWith("releases/latest"))
        assertEquals("1.1.0", (stable.state.value as UpdateState.Available).release.version.toString())
    }

    @Test fun `the daily check runs once a day, keeps quiet when github can't be reached, and can be switched off`() = runBlocking {
        var now = 1_000_000_000L
        var body: String? = release("v1.0.1")
        var asked = 0
        val http = object : Http {
            override fun get(url: String): ByteArray? { asked++; return body?.toByteArray() }
            override fun download(url: String, to: File, onBytes: (Long) -> Unit) = false
        }
        val updates = Updates(RuntimeEnvironment.getApplication(), http, "1.0.0") { now }
        updates.checkIfDue()
        assertTrue(updates.state.value is UpdateState.Available)
        updates.checkIfDue() // the same day
        assertEquals(1, asked)
        now += 25 * 60 * 60 * 1000L
        body = null
        updates.checkIfDue()
        assertEquals(2, asked)
        assertTrue("a quiet failure keeps what was there", updates.state.value is UpdateState.Available)
        updates.setAuto(false)
        now += 25 * 60 * 60 * 1000L
        updates.checkIfDue()
        assertEquals(2, asked)
        updates.check()
        assertTrue("asked for, a failure says so", updates.state.value is UpdateState.Failed)
    }
}
