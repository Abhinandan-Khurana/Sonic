package app.runout

import android.os.Looper
import androidx.room.Room
import app.runout.model.Source
import app.runout.soulseek.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/** Exercise the actual repositories and SAF publication without contacting the public network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, shadows = [ModernDocumentsResolver::class])
class SoulseekRepositoryTest {
    private lateinit var db: SoulseekDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var repo: SoulseekRepository
    private val transports = CopyOnWriteArrayList<FakeTransport>()
    private val transport get() = transports.last()
    private var opens = 0
    private val flac get() = Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac")

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(Fixtures.context, SoulseekDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repo = SoulseekRepository(Fixtures.context, scope, { opens++; FakeTransport().also { transports += it } }, db)
    }
    @After fun teardown() = runBlocking {
        repo.logout().join()
        scope.coroutineContext[Job]!!.cancelAndJoin()
        db.close()
    }
    private fun await(description: String, predicate: () -> Boolean) {
        val until = System.nanoTime() + 8_000_000_000L
        while (!predicate() && System.nanoTime() < until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
        assertTrue("$description (repository error: ${repo.error.value})", predicate())
    }
    private fun login(name: String = "runout-test") {
        runBlocking { repo.signIn(name, "local-test-password", false).join() }
        await("signed in") { repo.session.value.signedIn && repo.session.value.username == name }
    }
    private fun destination(): File {
        val folder = File(Fixtures.context.cacheDir, "destination").apply { mkdirs() }
        val uri = FolderProvider.install(folder, allowWrite = true)
        runBlocking { repo.setDestination(uri).join() }
        assertEquals(repo.error.value, uri, repo.destination.value)
        return folder
    }
    private fun incoming(id: Long = 7) = JSONObject().put("type", "message").put("id", id)
        .put("username", "maya").put("text", "A stored message").put("timestamp", 1_700_000_000L).put("replayed", true)

    @Test fun `guest does not load native client and cannot start either download provider`() = runBlocking {
        repo.search("Vela Park").join()
        var archiveCalled = false
        val result = repo.withArchiveDownload("example", "Example") { archiveCalled = true; Result.success(Fixtures.nightDrive()) }
        assertFalse(result.isSuccess)
        assertFalse(archiveCalled)
        assertFalse(repo.session.value.signedIn)
        assertEquals(0, opens)
        assertTrue(repo.downloadAlbums.value.isEmpty())
    }

    @Test fun `invalid usernames are rejected before transport creation`() = runBlocking {
        for (name in listOf(" bad", "bad ", "अ", "a".repeat(31))) repo.signIn(name, "p", false).join()
        assertEquals(0, opens)
        assertTrue(SoulseekPolicy.validUsername("A!"))
        assertTrue(SoulseekPolicy.validUsername("Mixed Case"))
    }

    @Test fun `incoming messages commit before ack and replay does not duplicate history`() = runBlocking {
        login()
        val persistedBeforeAck = java.util.concurrent.atomic.AtomicBoolean(false)
        transport.onCommand = { command ->
            if (command.optString("op") == "ack") {
                persistedBeforeAck.set(runBlocking { db.soulseek().messages("runout-test").first().size == 1 })
            }
        }
        transport.emit(incoming())
        await("first message persisted before acknowledgement") { persistedBeforeAck.get() }
        assertEquals(1, transport.commands.count { it.optString("op") == "ack" })
        transport.emit(incoming())
        await("replay acknowledged") { transport.commands.count { it.optString("op") == "ack" } == 2 }
        assertEquals(1, db.soulseek().messages("runout-test").first().size)
        await("unread count") { repo.conversations.value.singleOrNull()?.unread == 1 }
        repo.markRead("maya").join()
        await("marked read") { repo.conversations.value.singleOrNull()?.unread == 0 }
    }

    @Test fun `messages are pending until transport reports sent and failures remain visible`() = runBlocking {
        login()
        repo.sendMessage("maya", "Hello").join()
        val request = transport.commands.last { it.optString("op") == "message" }
        val id = request.getString("clientId")
        assertEquals("PENDING", db.soulseek().message(id)!!.state)
        transport.emit(JSONObject().put("type", "message_sent").put("clientId", id))
        await("sent state") { runBlocking { db.soulseek().message(id)?.state == "SENT" } }
        transport.failMessage = true
        repo.sendMessage("maya", "Offline").join()
        assertEquals("FAILED", db.soulseek().messages("runout-test").first().last().state)
    }

    @Test fun `logout clears account view and account switch isolates conversations`() = runBlocking {
        login("first")
        transport.emit(incoming())
        await("first account message") { repo.conversations.value.size == 1 }
        repo.logout().join()
        assertTrue(repo.conversations.value.isEmpty())
        assertFalse(repo.session.value.signedIn)
        assertTrue(transport.closed)
        login("second")
        assertEquals(1, db.soulseek().messages("first").first().size)
        assertTrue(db.soulseek().messages("second").first().isEmpty())
    }

    @Test fun `search ignores stale and non FLAC results without requiring quality metadata`() = runBlocking {
        login()
        repo.search("Vela Park").join()
        val id = transport.commands.last { it.optString("op") == "search" }.getString("id")
        fun result(path: String, token: String = id) = JSONObject().put("type", "search_result").put("id", token)
            .put("username", "maya").put("path", path).put("size", 66089L)
        transport.emit(result("Album\\ignored.mp3"))
        transport.emit(result("Album\\stale.flac", "old"))
        transport.emit(result("Album\\track.FLAC"))
        transport.emit(result("Album\\track.FLAC"))
        transport.emit(JSONObject().put("type", "search_done").put("id", id))
        await("search completed") { !repo.searching.value }
        assertEquals(listOf("track.FLAC"), repo.searchResults.value.map { it.fileName })
        assertNull(repo.searchResults.value.single().bitDepth)
    }

    @Test fun `completed FLAC publishes to selected folder and remains after logout`() = runBlocking {
        login(); val folder = destination()
        transport.onCommand = { command ->
            if (command.optString("op") == "download") {
                val stage = File(command.getString("localDirectory"), "01 Opening.flac").apply { parentFile!!.mkdirs(); writeBytes(flac) }
                transport.emit(JSONObject().put("type", "transfer").put("id", command.getString("id")).put("state", "completed")
                    .put("bytes", stage.length()).put("path", stage.absolutePath).put("queuePosition", JSONObject.NULL).put("error", JSONObject.NULL))
            }
        }
        repo.download(SoulseekSearchResult("maya", "Music\\Night Drive\\01 Opening.flac", flac.size.toLong())).join()
        await("published FLAC") { repo.transfers.value.singleOrNull()?.state == SoulseekTransferState.COMPLETED }
        await("crate updated") { repo.downloadAlbums.value.size == 1 }
        val album = repo.downloadAlbums.value.single()
        assertEquals(Source.SOULSEEK, album.source)
        assertEquals("FLAC", album.quality.codec)
        assertEquals(24, album.quality.bitsPerSample)
        assertFalse(album.owned)
        assertNull(repo.transfers.value.single().queuePosition)
        assertNull(repo.transfers.value.single().error)
        val saved = folder.walkTopDown().filter { it.isFile }.single()
        assertArrayEquals(flac, saved.readBytes())
        repo.logout().join()
        assertTrue(saved.exists())
        assertEquals(1, repo.downloadAlbums.value.size)
    }

    @Test fun `renamed MP3 download never publishes into music folder`() = runBlocking {
        login(); val folder = destination()
        val mp3 = Fixtures.bytes("Music/Ines Varga/Paper Moons/01 Paper Moons.mp3")
        transport.onCommand = { command ->
            if (command.optString("op") == "download") {
                val stage = File(command.getString("localDirectory"), "renamed.flac").apply { parentFile!!.mkdirs(); writeBytes(mp3) }
                transport.emit(JSONObject().put("type", "transfer").put("id", command.getString("id")).put("state", "completed").put("bytes", mp3.size).put("path", stage.absolutePath))
            }
        }
        repo.download(SoulseekSearchResult("maya", "Album\\renamed.flac", mp3.size.toLong())).join()
        await("invalid download failed") { repo.transfers.value.singleOrNull()?.state == SoulseekTransferState.FAILED }
        assertTrue(repo.downloadAlbums.value.isEmpty())
        assertTrue(folder.walkTopDown().none { it.isFile })
    }

    private class FakeTransport : SoulseekTransport {
        val commands = CopyOnWriteArrayList<JSONObject>()
        private val events = ConcurrentLinkedQueue<JSONObject>()
        var onCommand: (JSONObject) -> Unit = {}
        var failMessage = false
        var closed = false
        fun emit(event: JSONObject) { events += event }
        override fun command(request: String): String {
            val command = JSONObject(request); commands += command
            if (command.optString("op") == "login") emit(JSONObject().put("type", "session").put("state", "connected"))
            if (command.optString("op") == "message" && failMessage) return """{"ok":false,"error":"Connection closed"}"""
            onCommand(command)
            return """{"ok":true}"""
        }
        override fun poll(): String {
            val array = JSONArray()
            while (true) array.put(events.poll() ?: break)
            return array.toString()
        }
        override fun close() { closed = true }
    }
}
