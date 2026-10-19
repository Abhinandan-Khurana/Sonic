package app.runout.drops

import android.content.Context
import android.net.Uri
import app.runout.library.AlbumJson
import app.runout.library.ArtworkRepository
import app.runout.library.LibraryScanner
import app.runout.library.TagReader
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Source
import app.runout.model.Track
import app.runout.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DropItem(
    val identifier: String,
    val title: String,
    val creator: String,
    val year: Int?,
    val license: String?,
) {
    val coverUrl: String get() = "https://archive.org/services/img/$identifier"
}

data class DropWeek(val index: Long, val opens: LocalDate, val closes: LocalDate, val items: List<DropItem>)

sealed interface DropState {
    data object Idle : DropState
    data object Loading : DropState
    data class Ready(val week: DropWeek) : DropState
    data class Failed(val reason: String) : DropState
}

/**
 * Friday Drops: every Friday, ten Creative Commons albums from Internet Archive's netlabel
 * collection, picked from the week number so everyone sees the same ten. You can claim three a
 * week; a claimed album downloads straight from archive.org, so there's no server to run.
 */
class DropsRepository(
    private val context: Context,
    private val http: Http,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val prefs = context.getSharedPreferences("drops", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "drops")
    private val listFile = File(context.filesDir, "drops.json")

    private val _state = MutableStateFlow<DropState>(DropState.Idle)
    val state: StateFlow<DropState> = _state.asStateFlow()

    private val _albums = MutableStateFlow(AlbumJson.readList(listFile))
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _claimed = MutableStateFlow(claimedThisWeek())
    val claimed: StateFlow<Set<String>> = _claimed.asStateFlow()

    val claimsLeft: Int get() = (CLAIMS_PER_WEEK - _claimed.value.size).coerceAtLeast(0)

    fun currentIndex(): Long = weekIndex(Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate())

    suspend fun refresh(): DropState = withContext(Dispatchers.IO) {
        val index = currentIndex()
        _claimed.value = claimedThisWeek()
        prefs.getString("week-$index", null)?.let { cached ->
            runCatching { parseCached(index, cached) }.getOrNull()?.let {
                _state.value = DropState.Ready(it)
                return@withContext _state.value
            }
        }
        _state.value = DropState.Loading
        val page = 1 + (seed(index) % PAGES).toInt()
        val body = http.get(searchUrl(page))?.toString(Charsets.UTF_8)
        if (body == null) {
            _state.value = DropState.Failed("Couldn't reach Internet Archive. Check your connection.")
            return@withContext _state.value
        }
        val docs = runCatching { parseSearch(body) }.getOrElse {
            _state.value = DropState.Failed("Internet Archive sent something unexpected.")
            return@withContext _state.value
        }
        val week = DropWeek(index, opens(index), opens(index).plusDays(7), pick(docs, seed(index)))
        prefs.edit().putString("week-$index", JSONArray(week.items.map { it.toJson() }).toString()).apply()
        _state.value = DropState.Ready(week)
        _state.value
    }

    /** Downloads one of this week's albums onto the shelf. */
    suspend fun claim(item: DropItem, onProgress: (Float) -> Unit = {}): Result<Album> = withContext(Dispatchers.IO) {
        runCatching {
            val week = (state.value as? DropState.Ready)?.week ?: error("This week's drop hasn't loaded yet")
            require(week.items.any { it.identifier == item.identifier }) { "That album isn't in this week's drop" }
            if (item.identifier !in _claimed.value) check(claimsLeft > 0) { "You've claimed all $CLAIMS_PER_WEEK records this week" }

            val meta = http.get("https://archive.org/metadata/${item.identifier}")?.toString(Charsets.UTF_8)
                ?: error("Couldn't load the album from Internet Archive")
            val plan = planDownload(JSONObject(meta))
            require(plan.tracks.isNotEmpty()) { "This album has no playable files" }
            val albumDir = File(dir, item.identifier).apply { mkdirs() }
            val totalBytes = plan.tracks.sumOf { it.size }.coerceAtLeast(1)
            var doneBytes = 0L
            val tracks = plan.tracks.mapIndexed { i, f ->
                val target = File(albumDir, f.name.substringAfterLast('/'))
                if (!target.exists()) {
                    val ok = http.download(fileUrl(item.identifier, f.name), target) { b -> onProgress(((doneBytes + b).toFloat() / totalBytes).coerceAtMost(0.99f)) }
                    check(ok) { "Download failed for ${f.title}" }
                }
                doneBytes += f.size
                val info = target.inputStream().use { TagReader.read(it, target.extension, target.length(), wantPicture = false) }
                Track(
                    id = "drop-${item.identifier}/${target.name}",
                    uri = Uri.fromFile(target).toString(),
                    title = f.title ?: info?.title ?: target.nameWithoutExtension,
                    trackNumber = f.track ?: (i + 1),
                    discNumber = 1,
                    durationMs = info?.durationMs?.takeIf { it > 0 } ?: f.lengthMs ?: LibraryScanner.estimateDurationMs(target.length(), plan.codec),
                    fileName = target.name,
                    trackGainDb = info?.trackGainDb,
                )
            }
            val cover = plan.coverName?.let { name ->
                val f = File(albumDir, "cover." + name.substringAfterLast('.', "jpg").lowercase())
                if (f.exists() || http.download(fileUrl(item.identifier, name), f)) f else null
            } ?: File(albumDir, "cover.jpg").takeIf { it.exists() || http.download(item.coverUrl, it) }
            val first = tracks.firstOrNull()?.let { File(Uri.parse(it.uri).path!!) }
            val info = first?.inputStream()?.use { TagReader.read(it, first.extension, first.length(), false) }
            val album = Album(
                id = "drop-${item.identifier}",
                title = item.title,
                artist = item.creator,
                year = item.year,
                folderUri = Uri.fromFile(albumDir).toString(),
                tracks = tracks,
                quality = AudioQuality(plan.codec, info?.bits, info?.sampleRate, info?.bitrateKbps),
                coverPath = cover?.absolutePath,
                tint = cover?.let { ArtworkRepository.averageColor(it) },
                source = Source.DROP,
                license = item.license ?: plan.license,
                notes = "Friday Drop · week of ${week.opens}",
            )
            _albums.value = _albums.value.filterNot { it.id == album.id } + album
            AlbumJson.writeList(listFile, _albums.value)
            val claimed = _claimed.value + item.identifier
            _claimed.value = claimed
            prefs.edit().putStringSet("claims-${week.index}", claimed).apply()
            onProgress(1f)
            album
        }
    }

    /** Takes a claimed album off the shelf. The claim still counts for the week. */
    fun remove(albumId: String) {
        val album = _albums.value.firstOrNull { it.id == albumId } ?: return
        _albums.value = _albums.value - album
        AlbumJson.writeList(listFile, _albums.value)
        File(dir, albumId.removePrefix("drop-")).deleteRecursively()
    }

    /** A small cover for the drop list, cached so it's only fetched once. */
    suspend fun thumbnail(item: DropItem): File? = withContext(Dispatchers.IO) {
        val f = File(context.cacheDir, "drop-thumbs/${item.identifier}.jpg")
        if (f.exists() && f.length() > 0) return@withContext f
        f.parentFile?.mkdirs()
        if (http.download(item.coverUrl, f)) f else null
    }

    private fun claimedThisWeek(): Set<String> = prefs.getStringSet("claims-${currentIndex()}", emptySet())?.toSet() ?: emptySet()

    private fun parseCached(index: Long, json: String): DropWeek {
        val arr = JSONArray(json)
        return DropWeek(index, opens(index), opens(index).plusDays(7), (0 until arr.length()).map { dropItemFromJson(arr.getJSONObject(it)) })
    }

    data class PlannedFile(val name: String, val title: String?, val track: Int?, val lengthMs: Long?, val size: Long)
    data class Plan(val codec: String, val tracks: List<PlannedFile>, val coverName: String?, val license: String?)

    companion object {
        const val CLAIMS_PER_WEEK = 3
        const val PER_DROP = 10
        const val PAGES = 20L
        private const val ROWS = 100

        /** Drops open on Fridays: the index counts Fridays since 2 Jan 1970 (a Friday). */
        fun weekIndex(date: LocalDate): Long {
            val friday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
            return ChronoUnit.WEEKS.between(LocalDate.of(1970, 1, 2), friday)
        }

        fun opens(index: Long): LocalDate = LocalDate.of(1970, 1, 2).plusWeeks(index)

        fun seed(index: Long): Long = (index * 2_654_435_761L) and 0x7FFFFFFF

        fun searchUrl(page: Int): String {
            val q = "collection:netlabels AND mediatype:audio AND licenseurl:*creativecommons*"
            return "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
                listOf("identifier", "title", "creator", "year", "licenseurl").joinToString("") { "&fl%5B%5D=$it" } +
                "&sort%5B%5D=downloads+desc&rows=$ROWS&page=$page&output=json"
        }

        fun fileUrl(identifier: String, name: String): String =
            "https://archive.org/download/$identifier/" + name.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

        fun parseSearch(body: String): List<DropItem> {
            val docs = JSONObject(body).getJSONObject("response").getJSONArray("docs")
            return (0 until docs.length()).mapNotNull { i ->
                val d = docs.getJSONObject(i)
                val id = d.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DropItem(
                    identifier = id,
                    title = textOf(d.opt("title")) ?: id,
                    creator = textOf(d.opt("creator")) ?: "Unknown artist",
                    year = textOf(d.opt("year"))?.take(4)?.toIntOrNull(),
                    license = textOf(d.opt("licenseurl")),
                )
            }
        }

        /** Ten albums for the week, in the same order for everyone. */
        fun pick(docs: List<DropItem>, seed: Long): List<DropItem> =
            docs.distinctBy { it.identifier }.shuffled(java.util.Random(seed)).take(PER_DROP)

        /** Chooses which files to fetch: FLAC originals first, then MP3, then Ogg; and a cover. */
        fun planDownload(meta: JSONObject): Plan {
            val files = meta.getJSONArray("files")
            val all = (0 until files.length()).map { files.getJSONObject(it) }
            val families = listOf(
                "FLAC" to setOf("Flac", "24bit Flac", "FLAC"),
                "MP3" to setOf("VBR MP3", "MP3", "128Kbps MP3", "64Kbps MP3", "320Kbps MP3"),
                "OGG" to setOf("Ogg Vorbis"),
            )
            for ((codec, formats) in families) {
                val chosen = all.filter { it.optString("format") in formats }
                    .let { list -> list.filter { it.optString("source") == "original" }.ifEmpty { list } }
                    .sortedWith(compareBy({ trackNo(it.optString("track")) ?: Int.MAX_VALUE }, { it.optString("name") }))
                if (chosen.isNotEmpty()) {
                    val cover = all.firstOrNull { it.optString("format") == "Book Cover" }
                        ?: all.firstOrNull { f ->
                            val n = f.optString("name").lowercase()
                            (n.endsWith(".jpg") || n.endsWith(".png")) && ("cover" in n || "front" in n) && f.optString("source") == "original"
                        }
                        ?: all.firstOrNull { f -> f.optString("format") == "JPEG" && f.optString("source") == "original" }
                    return Plan(
                        codec = codec,
                        tracks = chosen.map { f ->
                            PlannedFile(
                                name = f.getString("name"),
                                title = f.optString("title").takeIf { it.isNotBlank() },
                                track = trackNo(f.optString("track")),
                                lengthMs = lengthMs(f.optString("length")),
                                size = f.optString("size").toLongOrNull() ?: 0L,
                            )
                        },
                        coverName = cover?.optString("name"),
                        license = meta.optJSONObject("metadata")?.let { textOf(it.opt("licenseurl")) },
                    )
                }
            }
            return Plan("NONE", emptyList(), null, null)
        }

        fun trackNo(s: String?): Int? = s?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

        /** "240.9" seconds or "4:01" → milliseconds. */
        fun lengthMs(s: String?): Long? {
            val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (':' in t) {
                val parts = t.split(':').mapNotNull { it.toDoubleOrNull() }
                return (parts.fold(0.0) { acc, v -> acc * 60 + v } * 1000).toLong()
            }
            return t.toDoubleOrNull()?.let { (it * 1000).toLong() }
        }

        private fun textOf(v: Any?): String? = when (v) {
            null, JSONObject.NULL -> null
            is JSONArray -> if (v.length() > 0) v.optString(0) else null
            else -> v.toString()
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
}

private fun DropItem.toJson(): JSONObject = JSONObject().apply {
    put("id", identifier); put("title", title); put("creator", creator)
    year?.let { put("year", it) }
    license?.let { put("license", it) }
}

private fun dropItemFromJson(o: JSONObject) = DropItem(
    o.getString("id"), o.getString("title"), o.getString("creator"),
    if (o.has("year")) o.getInt("year") else null, o.optString("license").takeIf { it.isNotBlank() },
)
