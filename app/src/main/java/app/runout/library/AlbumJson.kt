package app.runout.library

import android.content.Context
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.DiscStyle
import app.runout.model.Source
import app.runout.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One JSON shape for albums everywhere: the shelf cache, received records, pressings, drops and .vinyl files. */
object AlbumJson {

    fun toJson(a: Album, includeLocalPaths: Boolean = true): JSONObject = JSONObject().apply {
        put("id", a.id); put("title", a.title); put("artist", a.artist)
        a.year?.let { put("year", it) }
        // Paths on this phone never leave it inside a shared record.
        put("folder", if (includeLocalPaths) a.folderUri else "")
        if (includeLocalPaths) a.coverPath?.let { put("cover", it) }
        a.tint?.let { put("tint", it) }
        put("source", a.source.name)
        a.license?.let { put("license", it) }
        a.albumGainDb?.let { put("albumGain", it.toDouble()) }
        if (a.owned) put("owned", true)
        a.disc?.let { put("disc", it.name) }
        a.labelColor?.let { put("labelColor", it) }
        a.notes?.let { put("notes", it) }
        a.from?.let { put("from", it) }
        put("quality", JSONObject().apply {
            put("codec", a.quality.codec)
            a.quality.bitsPerSample?.let { put("bits", it) }
            a.quality.sampleRateHz?.let { put("rate", it) }
            a.quality.bitrateKbps?.let { put("kbps", it) }
        })
        put("tracks", JSONArray().apply {
            a.tracks.forEachIndexed { i, t ->
                put(JSONObject().apply {
                    put("id", if (includeLocalPaths) t.id else "t${i + 1}")
                    if (includeLocalPaths) put("uri", t.uri)
                    put("title", t.title)
                    put("n", t.trackNumber); put("disc", t.discNumber); put("ms", t.durationMs)
                    if (t.fileName.isNotEmpty()) put("file", t.fileName)
                    t.trackGainDb?.let { put("gain", it.toDouble()) }
                    if (!t.durationKnown) put("estimated", true)
                })
            }
        })
    }

    fun fromJson(o: JSONObject): Album {
        val q = o.getJSONObject("quality")
        val tracks = o.getJSONArray("tracks")
        return Album(
            id = o.getString("id"),
            title = o.getString("title"),
            artist = o.getString("artist"),
            year = if (o.has("year")) o.getInt("year") else null,
            folderUri = o.optString("folder"),
            coverPath = o.optString("cover").takeIf { it.isNotBlank() && File(it).exists() },
            tint = if (o.has("tint")) o.getInt("tint") else null,
            source = runCatching { Source.valueOf(o.optString("source", "FOLDER")) }.getOrDefault(Source.FOLDER),
            license = o.optString("license").takeIf { it.isNotBlank() },
            albumGainDb = if (o.has("albumGain")) o.getDouble("albumGain").toFloat() else null,
            owned = o.optBoolean("owned", false),
            disc = o.optString("disc").takeIf { it.isNotBlank() }?.let { runCatching { DiscStyle.valueOf(it) }.getOrNull() },
            labelColor = if (o.has("labelColor")) o.getInt("labelColor") else null,
            notes = o.optString("notes").takeIf { it.isNotBlank() },
            from = o.optString("from").takeIf { it.isNotBlank() },
            quality = AudioQuality(
                codec = q.getString("codec"),
                bitsPerSample = if (q.has("bits")) q.getInt("bits") else null,
                sampleRateHz = if (q.has("rate")) q.getInt("rate") else null,
                bitrateKbps = if (q.has("kbps")) q.getInt("kbps") else null,
            ),
            tracks = (0 until tracks.length()).map { j ->
                val t = tracks.getJSONObject(j)
                Track(
                    id = t.getString("id"),
                    uri = t.optString("uri"),
                    title = t.getString("title"),
                    trackNumber = t.getInt("n"),
                    discNumber = t.optInt("disc", 1),
                    durationMs = t.getLong("ms"),
                    fileName = t.optString("file"),
                    trackGainDb = if (t.has("gain")) t.getDouble("gain").toFloat() else null,
                    durationKnown = !t.optBoolean("estimated", false),
                )
            },
        )
    }

    /** Keep the original index once before removing unsupported legacy music from the shelf. */
    fun readValidatedList(context: Context, file: File): List<Album> {
        val original = readList(file)
        val valid = original.mapNotNull { FlacPolicy.filterAlbum(context, it) }
        if (valid != original && file.exists()) {
            val backup = File(file.parentFile, file.name + ".before-flac")
            // A full disk must not prevent offline playback or discard the old index.
            runCatching {
                if (!backup.exists()) {
                    val pending = File(backup.parentFile, backup.name + ".tmp")
                    file.copyTo(pending, overwrite = true)
                    check(pending.renameTo(backup)) { "Could not preserve the previous library index" }
                }
                writeList(file, valid)
            }
        }
        return valid
    }

    fun writeList(file: File, albums: List<Album>) {
        val arr = JSONArray()
        albums.forEach { arr.put(toJson(it)) }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { output -> output.write(arr.toString().toByteArray(Charsets.UTF_8)); output.fd.sync() }
        check(tmp.renameTo(file)) { "Could not save the music library index" }
    }

    fun readList(file: File): List<Album> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
    }.getOrElse { emptyList() }
}
