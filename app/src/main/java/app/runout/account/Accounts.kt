package app.runout.account

import android.content.Context
import app.runout.model.Album
import app.runout.social.Profile
import app.runout.social.ShelfItem
import app.runout.social.SocialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Your Runout account. The secret key never leaves the phone; everything else is public. */
data class Account(
    val pubkey: String,
    val username: String,
    val name: String,
    val accent: Int,
    val shareShelf: Boolean = true,
) {
    val code: String get() = pubkey.take(4).uppercase()
    val handle: String get() = "@$username·$code"
    fun asProfile() = Profile(pubkey, name, accent, username)
}

/** Someone's public Runout profile, as found on the relays. */
data class RemoteProfile(
    val pubkey: String,
    val username: String,
    val name: String,
    val accent: Int,
    val shelf: List<ShelfItem>,
    val shelfCount: Int,
    val updatedAt: Long,
) {
    val handle: String get() = "@$username·" + pubkey.take(4).uppercase()
    fun asProfile() = Profile(pubkey, name, accent, username)
}

data class AccountStatus(val message: String, val ok: Boolean)

/**
 * Accounts without a server of our own. An account is a key pair made on the phone; the profile
 * (username, name, colour and shelf) is a signed Nostr event stored on free public relays, so anyone
 * can search for a username and see that person's records, and nobody can forge a profile.
 *
 * Usernames can't be reserved without a central server, so every handle carries the first four
 * characters of its key: two people called "abhi" show up as @abhi·7F3A and @abhi·C21B.
 */
class AccountRepository(
    context: Context,
    private val relays: Relays,
    private val social: SocialRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _status = MutableStateFlow<AccountStatus?>(null)
    val status: StateFlow<AccountStatus?> = _status.asStateFlow()

    /** Creates an account and publishes its profile. Works offline too; publishing retries later. */
    suspend fun create(username: String, name: String, accent: Int, shelf: List<Album>): Result<Account> = withContext(Dispatchers.Default) {
        runCatching {
            check(_account.value == null) { "You already have an account on this phone" }
            val u = normalize(username)
            validateUsername(u)?.let { throw IllegalArgumentException(it) }
            val secret = Schnorr.newSecretKey()
            val acc = Account(Schnorr.publicKey(secret).hex(), u, name.trim().ifBlank { u }.take(40), accent)
            save(acc, secret)
            publish(shelf)
            acc
        }
    }

    /**
     * Restores an account from its key (nsec1… or 64 hex characters) and fetches its profile.
     * If no profile is found, [username] is used to make one.
     */
    suspend fun restore(keyText: String, shelf: List<Album>, username: String? = null): Result<Account> = withContext(Dispatchers.Default) {
        runCatching {
            val secret = parseSecret(keyText) ?: throw IllegalArgumentException("That isn't an account key")
            val pubkey = Schnorr.publicKey(secret).hex()
            val existing = profile(pubkey)
            val acc = when {
                existing != null -> Account(pubkey, existing.username, existing.name, existing.accent)
                username != null && validateUsername(normalize(username)) == null -> Account(pubkey, normalize(username), normalize(username), social.profile.value.accent)
                else -> throw IllegalStateException("No profile found for this key. Pick a username to start it again.")
            }
            save(acc, secret)
            publish(shelf)
            acc
        }
    }

    /** The account key for backup, as an nsec1… string. Anyone with it can act as you. */
    fun backupKey(): String? = prefs.getString(KEY_SECRET, null)?.let { Bech32.encode("nsec", it.unhex()) }

    fun publicKeyText(): String? = _account.value?.let { Bech32.encode("npub", it.pubkey.unhex()) }

    fun rename(name: String) = update { it.copy(name = name.trim().take(40).ifBlank { it.username }) }

    fun setAccent(accent: Int) = update { it.copy(accent = accent) }

    fun setShareShelf(on: Boolean) = update { it.copy(shareShelf = on) }

    private fun update(change: (Account) -> Account) {
        val acc = _account.value ?: return
        val next = change(acc)
        if (next == acc) return
        _account.value = next
        prefs.edit().putString(KEY_ACCOUNT, toJson(next).toString()).apply()
        social.adopt(next.asProfile())
    }

    /** Publishes (or refreshes) your profile and shelf on the relays. */
    suspend fun publish(shelf: List<Album>): PublishResult {
        val acc = _account.value ?: return PublishResult(0, 0)
        val secret = prefs.getString(KEY_SECRET, null)?.unhex() ?: return PublishResult(0, 0)
        val event = withContext(Dispatchers.Default) { NostrEvent.sign(secret, clock() / 1000, KIND, profileTags(acc.username), profileContent(acc, shelf)) }
        val result = withContext(Dispatchers.IO) { relays.publish(event) }
        _status.value = if (result.ok) AccountStatus("Profile is up on ${result.accepted} of ${result.total} relays", true)
        else AccountStatus("Couldn't reach the relays. Your profile will go up next time you're online.", false)
        return result
    }

    /** Finds people by username. "abh" finds @abhi and @abhiroop; "@abhi·7F3A" finds that exact one. */
    suspend fun search(query: String): List<RemoteProfile> {
        val q = parseQuery(query) ?: return emptyList()
        val (prefix, code) = q
        val filter = JSONObject().put("kinds", JSONArray().put(KIND)).put("#n", JSONArray().put(prefix)).put("limit", 60)
        val events = withContext(Dispatchers.IO) { relays.query(filter) }
        if (events.isEmpty() && relays.lastReachable == 0) throw java.io.IOException("Couldn't reach the relays. Check your connection.")
        val found = events
            .mapNotNull { parseProfile(it) }
            .groupBy { it.pubkey }.map { (_, v) -> v.maxBy { it.updatedAt } }
            .filter { it.username.startsWith(prefix) && (code == null || it.pubkey.startsWith(code)) }
            .filter { it.pubkey != _account.value?.pubkey }
        return found.sortedWith(compareBy({ it.username != prefix }, { it.username }, { it.name.lowercase() }))
    }

    /** One person's latest profile. */
    suspend fun profile(pubkey: String): RemoteProfile? {
        val filter = JSONObject().put("kinds", JSONArray().put(KIND)).put("authors", JSONArray().put(pubkey)).put("#d", JSONArray().put(D_TAG))
        return withContext(Dispatchers.IO) { relays.query(filter) }.mapNotNull { parseProfile(it) }.filter { it.pubkey == pubkey }.maxByOrNull { it.updatedAt }
    }

    /** Adds someone you found as a friend. Their shelf comes with them. */
    fun addFriend(p: RemoteProfile): Boolean = social.upsertFriend(p.asProfile(), p.shelf)

    /** Fetches the latest shelves of friends who have accounts. Returns how many were updated. */
    suspend fun refreshFriends(): Int {
        val keys = social.friends.value.map { it.profile }.filter { it.isAccount }.map { it.id }
        if (keys.isEmpty()) return 0
        val filter = JSONObject().put("kinds", JSONArray().put(KIND)).put("authors", JSONArray(keys)).put("#d", JSONArray().put(D_TAG))
        val latest = withContext(Dispatchers.IO) { relays.query(filter) }.mapNotNull { parseProfile(it) }
            .groupBy { it.pubkey }.map { (_, v) -> v.maxBy { it.updatedAt } }
        latest.forEach { social.upsertFriend(it.asProfile(), it.shelf) }
        return latest.size
    }

    private fun save(acc: Account, secret: ByteArray) {
        prefs.edit().putString(KEY_SECRET, secret.hex()).putString(KEY_ACCOUNT, toJson(acc).toString()).apply()
        _account.value = acc
        social.adopt(acc.asProfile())
    }

    private fun load(): Account? = runCatching {
        if (prefs.getString(KEY_SECRET, null) == null) return null
        prefs.getString(KEY_ACCOUNT, null)?.let { fromJson(JSONObject(it)) }
    }.getOrNull()

    private fun parseProfile(e: NostrEvent): RemoteProfile? = runCatching {
        if (e.kind != KIND || e.tag("d") != D_TAG) return null
        val o = JSONObject(e.content)
        val username = o.getString("username")
        if (validateUsername(username) != null) return null
        val shelf = o.optJSONArray("shelf") ?: JSONArray()
        RemoteProfile(
            pubkey = e.pubkey,
            username = username,
            name = o.optString("name", username).take(40),
            accent = o.optInt("accent", Profile.ACCENTS[0]),
            shelf = (0 until minOf(shelf.length(), MAX_SHELF)).map { i ->
                val s = shelf.getJSONObject(i)
                ShelfItem(s.getString("t"), s.optString("a"), if (s.has("y")) s.getInt("y") else null, s.optString("q"), null, s.optInt("c", 0).takeIf { it != 0 })
            },
            shelfCount = o.optInt("shelfCount", shelf.length()),
            updatedAt = e.createdAt,
        )
    }.getOrNull()

    companion object {
        /** An addressable event kind for Runout profiles (unregistered, so nothing else uses it). */
        const val KIND = 33760
        const val D_TAG = "runout"
        const val MAX_SHELF = 600
        private const val KEY_SECRET = "secret"
        private const val KEY_ACCOUNT = "account"
        private val USERNAME = Regex("^[a-z0-9_][a-z0-9_.]{1,18}[a-z0-9_]$")

        fun normalize(u: String) = u.trim().removePrefix("@").lowercase()

        /** Null when fine, otherwise what's wrong, in words. */
        fun validateUsername(u: String): String? = when {
            u.length < 3 -> "At least 3 characters"
            u.length > 20 -> "At most 20 characters"
            !USERNAME.matches(u) -> "Letters, numbers, _ and . only (not at the ends)"
            ".." in u -> "No two dots in a row"
            else -> null
        }

        /** Tags that make prefix search work: every prefix of the username from 2 letters up. */
        fun profileTags(username: String): List<List<String>> =
            listOf(listOf("d", D_TAG), listOf("t", "runout")) + (2..username.length).map { listOf("n", username.take(it)) }

        fun profileContent(acc: Account, shelf: List<Album>): String = JSONObject().apply {
            put("v", 1); put("username", acc.username); put("name", acc.name); put("accent", acc.accent)
            val shown = if (acc.shareShelf) shelf.take(MAX_SHELF) else emptyList()
            put("shelfCount", if (acc.shareShelf) shelf.size else 0)
            put("shelf", JSONArray().apply {
                shown.forEach { a ->
                    put(JSONObject().put("t", a.title).put("a", a.artist).put("q", a.quality.label).apply {
                        a.year?.let { put("y", it) }
                        a.tint?.let { put("c", it) }
                    })
                }
            })
        }.toString()

        /** "@Abhi·7F3A", "abhi#7f3a", "abhi" → ("abhi", "7f3a" or null). */
        fun parseQuery(text: String): Pair<String, String?>? {
            val t = text.trim().removePrefix("@").lowercase()
            val parts = t.split('·', '#', ' ').filter { it.isNotEmpty() }
            val name = parts.firstOrNull()?.takeIf { it.length >= 2 } ?: return null
            if (!name.all { it.isLetterOrDigit() || it == '_' || it == '.' }) return null
            val code = parts.getOrNull(1)?.takeIf { c -> c.length in 1..8 && c.all { it in '0'..'9' || it in 'a'..'f' } }
            return name.take(20) to code
        }

        fun parseSecret(text: String): ByteArray? {
            val t = text.trim()
            val bytes = when {
                t.startsWith("nsec1", ignoreCase = true) -> Bech32.decode(t)?.takeIf { it.first == "nsec" }?.second
                t.length == 64 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> t.lowercase().unhex()
                else -> null
            } ?: return null
            if (bytes.size != 32) return null
            val d = java.math.BigInteger(1, bytes)
            return bytes.takeIf { d.signum() > 0 && d < Schnorr.N }
        }

        private fun toJson(a: Account) = JSONObject().put("pubkey", a.pubkey).put("username", a.username).put("name", a.name)
            .put("accent", a.accent).put("shareShelf", a.shareShelf)

        private fun fromJson(o: JSONObject) = Account(o.getString("pubkey"), o.getString("username"), o.getString("name"), o.getInt("accent"), o.optBoolean("shareShelf", true))
    }
}
