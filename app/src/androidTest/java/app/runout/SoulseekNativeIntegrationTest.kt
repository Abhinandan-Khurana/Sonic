package app.runout

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.runout.soulseek.NativeSoulseek
import app.runout.soulseek.SoulseekCredentialVault
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Track
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Uses the packaged Android JNI library and real local TCP peer/file sockets. */
@RunWith(AndroidJUnit4::class)
class SoulseekNativeIntegrationTest {
    @Test fun savedCredentialsUseAndroidKeystoreAndLogoutStorageCanBeCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SoulseekCredentialVault(context)
        try {
            vault.save("local-vault-test", "local-vault-password")
            assertEquals("local-vault-test" to "local-vault-password", vault.load())
            val ciphertext = File(context.noBackupFilesDir, "soulseek-account.enc").readText()
            assertFalse(ciphertext.contains("local-vault-password"))
            assertFalse(ciphertext.contains("local-vault-test"))
            vault.clear()
            assertNull(vault.load())
        } finally { vault.clear() }
    }

    @Test fun localFlacPlaysThroughTheActualDeckWithoutSoulseekSignIn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as RunoutApp
        assertFalse(app.graph.soulseek.session.value.signedIn)
        val file = File(app.cacheDir, "offline-playback.flac")
        instrumentation.context.assets.open("Music/Vela Park - Night Drive (2023)/01 Opening.flac").use { input -> file.outputStream().use { input.copyTo(it) } }
        val track = Track("offline-fixture", android.net.Uri.fromFile(file).toString(), "Opening", 1, 1, 2000, file.name)
        val album = Album("offline-fixture", "Night Drive", "Vela Park", 2023, "", listOf(track), AudioQuality("FLAC", 24, 96000))
        try {
            instrumentation.runOnMainSync { app.graph.deck.load(album); app.graph.deck.needleDrop(0f) }
            val until = System.nanoTime() + 8_000_000_000L
            while (!app.graph.deck.state.value.isPlaying && System.nanoTime() < until) Thread.sleep(20)
            assertTrue("Local FLAC must reach the actual Android player", app.graph.deck.state.value.isPlaying)
            assertFalse(app.graph.soulseek.session.value.signedIn)
        } finally {
            instrumentation.runOnMainSync { app.graph.deck.eject() }
            file.delete()
        }
    }

    private fun command(client: NativeSoulseek, op: String, body: JSONObject.() -> Unit = {}) {
        val response = JSONObject(client.command(JSONObject().put("op", op).apply(body).toString()))
        assertTrue(response.optString("error"), response.optBoolean("ok"))
    }
    private fun awaitEvent(client: NativeSoulseek, description: String, accept: (JSONObject) -> Boolean): JSONObject {
        val until = System.nanoTime() + 20_000_000_000L
        val observed = mutableListOf<String>()
        while (System.nanoTime() < until) {
            val batch = JSONArray(client.poll())
            for (i in 0 until batch.length()) {
                val event = batch.getJSONObject(i)
                observed += event.toString()
                if (accept(event)) return event
            }
            Thread.sleep(30)
        }
        error("Timed out: $description; events: $observed")
    }

    @Test fun packagedNativeCoreSearchesDownloadsSharesAndExchangesPrivateMessages() {
        assertTrue("Packaged native library must load: ${NativeSoulseek.loadError}", NativeSoulseek.isAvailable)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "native-integration-${System.nanoTime()}").apply { mkdirs() }
        val shared = File(root, "01 Opening.flac")
        instrumentation.context.assets.open("Music/Vela Park - Night Drive (2023)/01 Opening.flac").use { input -> shared.outputStream().use(input::copyTo) }
        LocalServer().use { server ->
            NativeSoulseek { key ->
                if (key == "approved-track") ParcelFileDescriptor.open(shared, ParcelFileDescriptor.MODE_READ_ONLY).detachFd() else -1
            }.use { uploader ->
                NativeSoulseek().use { downloader ->
                    fun login(client: NativeSoulseek, name: String) {
                        command(client, "login") { put("username", name); put("password", "local-fixture"); put("host", "127.0.0.1"); put("port", server.port) }
                        awaitEvent(client, "local login") { it.optString("type") == "session" && it.optString("state") == "connected" }
                    }
                    login(uploader, "runout-uploader")
                    login(downloader, "runout-downloader")
                    val until = System.nanoTime() + 5_000_000_000L
                    while (server.peers.values.count { it.port > 0 } < 2 && System.nanoTime() < until) Thread.sleep(20)
                    assertEquals(2, server.peers.values.count { it.port > 0 })
                    command(uploader, "shares") {
                        put("files", JSONArray().put(JSONObject().put("key", "approved-track").put("path", "Music\\Night Drive\\01 Opening.flac").put("size", shared.length())))
                    }
                    command(downloader, "search") { put("id", "search-one"); put("query", "Night Drive"); put("timeoutMs", 15000) }
                    val result = awaitEvent(downloader, "FLAC search result from local peer") { it.optString("type") == "search_result" }
                    assertEquals("runout-uploader", result.getString("username"))
                    assertTrue(result.getString("path").endsWith(".flac"))
                    assertEquals(shared.length(), result.getLong("size"))
                    command(downloader, "browse") { put("id", "folder-one"); put("username", "runout-uploader"); put("directory", "Music/Night Drive") }
                    val listing = awaitEvent(downloader, "peer folder listing") { it.optString("type") == "browse_result" }
                    assertEquals(1, listing.getJSONArray("files").length())
                    val staging = File(root, "staging").apply { mkdirs() }
                    command(downloader, "download") { put("id", "download-one"); put("username", result.getString("username")); put("path", result.getString("path")); put("size", result.getLong("size")); put("localDirectory", staging.absolutePath) }
                    val completed = awaitEvent(downloader, "peer download completion") { it.optString("type") == "transfer" && it.optString("state") == "completed" }
                    assertArrayEquals(shared.readBytes(), File(completed.getString("path")).readBytes())
                    // Retrying a terminal transfer must create a fresh native transfer,
                    // rather than leaving the application stuck in Queued forever.
                    command(downloader, "download") { put("id", "download-one"); put("username", result.getString("username")); put("path", result.getString("path")); put("size", result.getLong("size")); put("localDirectory", staging.absolutePath) }
                    val retried = awaitEvent(downloader, "explicit retry completion") { it.optString("type") == "transfer" && it.optString("state") == "completed" }
                    assertArrayEquals(shared.readBytes(), File(retried.getString("path")).readBytes())
                    command(downloader, "message") { put("username", "runout-uploader"); put("text", "Local integration message"); put("clientId", "message-one") }
                    val message = awaitEvent(uploader, "private message") { it.optString("type") == "message" }
                    assertEquals("Local integration message", message.getString("text"))
                    assertEquals(0, server.acknowledged.get())
                    command(uploader, "ack") { put("id", message.getLong("id")) }
                    val ackUntil = System.nanoTime() + 3_000_000_000L
                    while (server.acknowledged.get() == 0 && System.nanoTime() < ackUntil) Thread.sleep(20)
                    assertEquals(1, server.acknowledged.get())
                    command(uploader, "shares") { put("files", JSONArray()) }
                }
            }
        }
        root.deleteRecursively()
    }

    /** Just enough central-server protocol to broker two real embedded peers. */
    private class LocalServer : AutoCloseable {
        data class Peer(val name: String, val socket: Socket, @Volatile var port: Int = 0)
        private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = listener.localPort
        val peers = ConcurrentHashMap<String, Peer>()
        val acknowledged = AtomicInteger()
        private val messageIds = AtomicInteger(1)
        private val sockets = CopyOnWriteArrayList<Socket>()
        init {
            thread(isDaemon = true, name = "runout-test-server") {
                while (!listener.isClosed) {
                    val socket = runCatching { listener.accept() }.getOrNull() ?: break
                    sockets += socket
                    thread(isDaemon = true) { runCatching { serve(socket) } }
                }
            }
        }
        private fun serve(socket: Socket) {
            val input = DataInputStream(socket.getInputStream())
            var peer: Peer? = null
            while (!socket.isClosed) {
                val size = Integer.reverseBytes(input.readInt())
                require(size in 4..1_048_576)
                val code = Integer.reverseBytes(input.readInt())
                val payload = ByteArray(size - 4).also(input::readFully)
                val data = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                when (code) {
                    1 -> {
                        val name = readString(data)
                        peer = Peer(name, socket); peers[name] = peer
                        send(socket, 1, byteArrayOf(1) + string("Runout local test") + byteArrayOf(1, 0, 0, 127) + string("") + byteArrayOf(0))
                    }
                    2 -> peer?.port = data.int
                    3 -> {
                        val name = readString(data)
                        val address = peers[name]
                        send(socket, 3, string(name) + byteArrayOf(1, 0, 0, 127) + number(address?.port ?: 0) + number(0) + number(0))
                    }
                    5 -> { val name = readString(data); send(socket, 5, string(name) + byteArrayOf(0)) }
                    22 -> {
                        val recipient = readString(data); val body = readString(data)
                        peers[recipient]?.let { destination -> send(destination.socket, 22, number(messageIds.getAndIncrement()) + number((System.currentTimeMillis() / 1000).toInt()) + string(peer!!.name) + string(body) + byteArrayOf(1)) }
                    }
                    23 -> acknowledged.incrementAndGet()
                    26 -> peers.values.filter { it.name != peer?.name }.forEach { send(it.socket, 26, string(peer!!.name) + payload) }
                }
            }
        }
        private fun send(socket: Socket, code: Int, payload: ByteArray) = synchronized(socket) {
            socket.getOutputStream().apply { write(number(payload.size + 4)); write(number(code)); write(payload); flush() }
        }
        private fun readString(data: ByteBuffer): String {
            val size = data.int; require(size in 0..data.remaining())
            return String(ByteArray(size).also(data::get), Charsets.UTF_8)
        }
        private fun number(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        private fun string(value: String): ByteArray = value.toByteArray(Charsets.UTF_8).let { number(it.size) + it }
        override fun close() { listener.close(); sockets.forEach { runCatching { it.close() } } }
    }
}
