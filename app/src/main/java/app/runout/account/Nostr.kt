package app.runout.account

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** A signed Nostr event (NIP-01). */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    fun tag(name: String): String? = tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1)

    /** The id matches the content and the signature matches the author's key. */
    fun isValid(): Boolean = runCatching {
        computeId(pubkey, createdAt, kind, tags, content) == id && Schnorr.verify(id.unhex(), pubkey.unhex(), sig.unhex())
    }.getOrDefault(false)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("pubkey", pubkey); put("created_at", createdAt); put("kind", kind)
        put("tags", JSONArray().apply { tags.forEach { t -> put(JSONArray(t)) } })
        put("content", content); put("sig", sig)
    }

    companion object {
        /** The exact serialisation NIP-01 hashes for the id: no spaces, minimal escaping, UTF-8. */
        fun serialize(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String = buildString {
            append("[0,\"").append(pubkey).append("\",").append(createdAt).append(',').append(kind).append(",[")
            tags.forEachIndexed { i, t ->
                if (i > 0) append(',')
                append('[')
                t.forEachIndexed { j, v -> if (j > 0) append(','); quote(v) }
                append(']')
            }
            append("],")
            quote(content)
            append(']')
        }

        private fun StringBuilder.quote(s: String) {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }

        fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
            MessageDigest.getInstance("SHA-256").digest(serialize(pubkey, createdAt, kind, tags, content).toByteArray(Charsets.UTF_8)).hex()

        fun sign(secret: ByteArray, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): NostrEvent {
            val pubkey = Schnorr.publicKey(secret).hex()
            val id = computeId(pubkey, createdAt, kind, tags, content)
            val sig = Schnorr.sign(id.unhex(), secret).hex()
            return NostrEvent(id, pubkey, createdAt, kind, tags, content, sig)
        }

        fun fromJson(o: JSONObject): NostrEvent {
            val tags = o.optJSONArray("tags") ?: JSONArray()
            return NostrEvent(
                id = o.getString("id"),
                pubkey = o.getString("pubkey"),
                createdAt = o.getLong("created_at"),
                kind = o.getInt("kind"),
                tags = (0 until tags.length()).map { i -> tags.getJSONArray(i).let { a -> (0 until a.length()).map { a.getString(it) } } },
                content = o.getString("content"),
                sig = o.getString("sig"),
            )
        }
    }
}

data class PublishResult(val accepted: Int, val total: Int) {
    val ok: Boolean get() = accepted > 0
}

/** Somewhere profiles live. In the app these are public Nostr relays; tests use a local one. */
interface Relays {
    val urls: List<String>
    /** How many relays answered the last query; 0 means we're probably offline. */
    val lastReachable: Int
    suspend fun publish(event: NostrEvent): PublishResult
    /** Events matching a NIP-01 filter, from every relay, de-duplicated, with bad signatures dropped. */
    suspend fun query(filter: JSONObject): List<NostrEvent>
}

/**
 * A small Nostr relay client over WebSockets. Each call opens short-lived connections to every relay
 * in parallel, so nothing keeps running in the background.
 */
class RelayPool(
    override val urls: List<String>,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build(),
    private val timeoutMs: Long = 8_000,
) : Relays {

    override suspend fun publish(event: NostrEvent): PublishResult = coroutineScope {
        val results = urls.map { url -> async { publishOne(url, event) } }.awaitAll()
        PublishResult(results.count { it }, urls.size)
    }

    @Volatile override var lastReachable: Int = 0
        private set

    override suspend fun query(filter: JSONObject): List<NostrEvent> = coroutineScope {
        val answers = urls.map { url -> async { queryOne(url, filter) } }.awaitAll()
        lastReachable = answers.count { it != null }
        answers.filterNotNull()
            .flatten()
            .distinctBy { it.id }
            .filter { it.isValid() }
    }

    private suspend fun publishOne(url: String, event: NostrEvent): Boolean = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(JSONArray().put("EVENT").put(event.toJson()).toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val m = runCatching { JSONArray(text) }.getOrNull() ?: return
                    if (m.optString(0) == "OK" && m.optString(1) == event.id) {
                        webSocket.close(1000, null)
                        if (cont.isActive) cont.resume(m.optBoolean(2))
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resume(false)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (cont.isActive) cont.resume(false)
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
    } ?: false

    /** Events from one relay, or null if it couldn't be reached. */
    private suspend fun queryOne(url: String, filter: JSONObject): List<NostrEvent>? {
        val got = mutableListOf<NostrEvent>()
        var answered = false
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val sub = "r" + (System.nanoTime() % 1_000_000_000).toString(36)
                val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(JSONArray().put("REQ").put(sub).put(filter).toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val m = runCatching { JSONArray(text) }.getOrNull() ?: return
                        if (m.optString(1) != sub) return
                        when (m.optString(0)) {
                            "EVENT" -> runCatching { NostrEvent.fromJson(m.getJSONObject(2)) }.getOrNull()?.let { synchronized(got) { got += it } }
                            "EOSE", "CLOSED" -> {
                                answered = true
                                webSocket.send(JSONArray().put("CLOSE").put(sub).toString())
                                webSocket.close(1000, null)
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
                cont.invokeOnCancellation { ws.cancel() }
            }
        }
        val list = synchronized(got) { got.toList() }
        return if (answered || list.isNotEmpty()) list else null
    }

    companion object {
        /** Free public relays run by others; checked to be reachable and to accept tag searches. */
        val DEFAULT = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")
    }
}
