package app.runout.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import app.runout.library.AlbumJson
import app.runout.model.Album
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** You, as friends see you. Lives only on your phone; travels inside friend cards. */
data class Profile(val id: String, val name: String, val accent: Int, val username: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("accent", accent).apply {
        username?.let { put("u", it) }
    }

    /** Accounts use their public key as their id; offline-only profiles have a random one. */
    val isAccount: Boolean get() = id.length == 64 && id.all { it in '0'..'9' || it in 'a'..'f' }

    /** "@name·7F3A": the username plus the first characters of the key, which tell two "@name"s apart. */
    val handle: String? get() = username?.let { "@$it·" + id.take(4).uppercase() }

    companion object {
        fun fromJson(o: JSONObject) = Profile(
            o.getString("id"), o.optString("name", "Someone"), o.optInt("accent", ACCENTS[0]),
            o.optString("u").takeIf { it.isNotBlank() },
        )
        val ACCENTS = listOf(0xFF2B6BFF.toInt(), 0xFFE0569B.toInt(), 0xFF1E9E8A.toInt(), 0xFFF08A24.toInt(), 0xFF7A5CFF.toInt(), 0xFF111111.toInt())
    }
}

/** One record on a friend's shelf, as their card described it. */
data class ShelfItem(
    val title: String,
    val artist: String,
    val year: Int?,
    val quality: String,
    val thumb: String?,
    /** The sleeve's main colour, for drawing it before (or without) its cover. */
    val tint: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("artist", artist).put("quality", quality).apply {
        year?.let { put("year", it) }
        thumb?.let { put("thumb", it) }
        tint?.let { put("c", it) }
    }

    fun matches(album: Album) = norm(album.title) == norm(title) && norm(album.artist) == norm(artist)

    companion object {
        fun fromJson(o: JSONObject) = ShelfItem(
            o.getString("title"), o.getString("artist"),
            if (o.has("year")) o.getInt("year") else null,
            o.optString("quality"), o.optString("thumb").takeIf { it.isNotBlank() },
            if (o.has("c")) o.getInt("c") else null,
        )

        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        /** A shelf entry with a small cover thumbnail, so friends see your sleeves. */
        fun from(album: Album, thumbPx: Int = 96): ShelfItem {
            val thumb = album.coverPath?.let { path ->
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bmp = BitmapFactory.decodeFile(path, opts) ?: return@runCatching null
                    val small = Bitmap.createScaledBitmap(bmp, thumbPx, thumbPx, true)
                    val out = ByteArrayOutputStream()
                    small.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }.getOrNull()
            }
            return ShelfItem(album.title, album.artist, album.year, album.quality.label, thumb, album.tint)
        }
    }
}

data class Friend(
    val profile: Profile,
    val shelf: List<ShelfItem>,
    val updatedAt: Long,
    /** Records they sent as cards (sleeve and notes, no audio). */
    val cards: List<Album> = emptyList(),
)

/**
 * Friends without a server: you swap friend cards (a small .vinyl file) through any chat app or
 * Quick Share, and each card carries the sender's shelf. Records travel the same way.
 */
class SocialRepository(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val file = File(context.filesDir, "friends.json")

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _friends = MutableStateFlow(loadFriends())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    fun rename(name: String) {
        val clean = name.trim().take(40).ifBlank { return }
        _profile.value = _profile.value.copy(name = clean)
        saveProfile()
    }

    /** Becomes the identity of a Runout account. Friends and the records they sent are kept. */
    fun adopt(account: Profile) {
        _profile.value = account
        saveProfile()
        // An account can't be its own friend.
        if (_friends.value.any { it.profile.id == account.id }) removeFriend(account.id)
    }

    fun setAccent(accent: Int) {
        _profile.value = _profile.value.copy(accent = accent)
        saveProfile()
    }

    /** Adds a friend, or refreshes their shelf if you already have them. Your own card is ignored. */
    fun upsertFriend(profile: Profile, shelf: List<ShelfItem>): Boolean {
        if (profile.id == _profile.value.id) return false
        val existing = _friends.value.firstOrNull { it.profile.id == profile.id }
        val updated = Friend(profile, shelf, clock(), existing?.cards ?: emptyList())
        _friends.value = (_friends.value.filterNot { it.profile.id == profile.id } + updated).sortedBy { it.profile.name.lowercase() }
        saveFriends()
        return existing == null
    }

    fun addCard(from: Profile, album: Album) {
        if (_friends.value.none { it.profile.id == from.id }) upsertFriend(from, emptyList())
        _friends.value = _friends.value.map { f ->
            if (f.profile.id != from.id) f else f.copy(cards = (f.cards.filterNot { it.id == album.id } + album), updatedAt = clock())
        }
        saveFriends()
    }

    fun removeFriend(id: String) {
        _friends.value = _friends.value.filterNot { it.profile.id == id }
        saveFriends()
    }

    private fun loadProfile(): Profile {
        prefs.getString("profile", null)?.let { s -> runCatching { return Profile.fromJson(JSONObject(s)) } }
        val p = Profile(UUID.randomUUID().toString(), "You", Profile.ACCENTS.random())
        prefs.edit().putString("profile", p.toJson().toString()).apply()
        return p
    }

    private fun saveProfile() = prefs.edit().putString("profile", _profile.value.toJson().toString()).apply()

    private fun saveFriends() {
        val arr = JSONArray()
        _friends.value.forEach { f ->
            arr.put(JSONObject().apply {
                put("profile", f.profile.toJson())
                put("shelf", JSONArray().apply { f.shelf.forEach { put(it.toJson()) } })
                put("at", f.updatedAt)
                put("cards", JSONArray().apply { f.cards.forEach { put(AlbumJson.toJson(it)) } })
            })
        }
        file.writeText(arr.toString())
    }

    private fun loadFriends(): List<Friend> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val shelf = o.getJSONArray("shelf")
            val cards = o.optJSONArray("cards") ?: JSONArray()
            Friend(
                Profile.fromJson(o.getJSONObject("profile")),
                (0 until shelf.length()).map { ShelfItem.fromJson(shelf.getJSONObject(it)) },
                o.optLong("at"),
                (0 until cards.length()).map { AlbumJson.fromJson(cards.getJSONObject(it)) },
            )
        }
    }.getOrElse { emptyList() }
}
