package app.runout

import app.runout.update.UpdateState
import app.runout.update.Updates
import app.runout.update.Version
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpdatesTest {

    private fun release(tag: String, pre: Boolean = false, draft: Boolean = false, apk: Boolean = true) = JSONObject().apply {
        put("tag_name", tag)
        put("name", "Sonic $tag")
        put("prerelease", pre)
        put("draft", draft)
        put("html_url", "https://github.com/Abhiroop1122/runout/releases/tag/$tag")
        put("body", "## What's new\n- A dark room\n- Three decks\n- More")
        put("assets", JSONArray().apply {
            if (apk) put(JSONObject().put("name", "Sonic-$tag.apk").put("size", 74_000_000).put("browser_download_url", "https://github.com/x/$tag.apk"))
            put(JSONObject().put("name", "Sonic-$tag.apk.sha256").put("size", 90).put("browser_download_url", "https://github.com/x/$tag.sha256"))
        })
    }

    private fun list(vararg r: JSONObject) = JSONArray().apply { r.forEach { put(it) } }.toString()

    @Test
    fun `versions order releases after their development builds`() {
        val v = { s: String -> Version.parse(s)!! }
        assertTrue(v("v0.8.0") > v("v0.8.0-dev.3"))
        assertTrue(v("v0.8.0-dev.3") > v("v0.8.0-dev.2"))
        assertTrue(v("v0.8.0-dev.1") > v("0.7.0"))
        assertTrue(v("v0.10.0") > v("v0.9.9"))
        assertEquals(Version(1, 2, 0), v("v1.2"))
        assertNull(Version.parse("nightly"))
    }

    @Test
    fun `the newest release above the installed version is offered, drafts and older ones never`() {
        val json = list(
            release("v0.6.0-dev.1", pre = true),
            release("v0.9.0", draft = true),
            release("v0.8.0-dev.2", pre = true),
            release("v0.8.0-dev.1", pre = true),
            release("v0.7.0"),
        )
        val r = Updates.newest(json, "0.7.0")!!
        assertEquals("0.8.0-dev.2", r.version.toString())
        assertTrue(r.development)
        assertEquals("https://github.com/x/v0.8.0-dev.2.apk", r.apk)
        assertEquals("What's new A dark room Three decks", r.notes)
        assertNull(Updates.newest(json, "0.8.0"))
        // A release without an APK still shows, with only its notes to open.
        assertNull(Updates.newest(list(release("v0.9.0", apk = false)), "0.7.0")!!.apk)
    }

    @Test
    fun `a check says up to date, offers a build, or says github couldn't be reached`() = runBlocking {
        var body: ByteArray? = list(release("v0.7.0")).toByteArray()
        val updates = Updates(Fixtures.context, FakeHttp(mapOf("/releases" to { body })), "0.7.0", clock = { 1_000L })
        updates.check()
        assertEquals(UpdateState.UpToDate(1_000L), updates.state.value)
        body = list(release("v0.8.0")).toByteArray()
        updates.check()
        assertEquals("0.8.0", (updates.state.value as UpdateState.Available).release.version.toString())
        body = null
        updates.check()
        assertTrue(updates.state.value is UpdateState.Failed)
    }
}
