package app.runout.sharing

import app.runout.library.AlbumJson
import app.runout.library.FlacPolicy
import app.runout.model.Album
import app.runout.social.Profile
import app.runout.social.ShelfItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A `.vinyl` file: one zip holding a manifest, the sleeve and (when the record may be shared with
 * its audio) the original audio files, untouched. Three kinds:
 * - ALBUM: a record with its audio,
 * - CARD: the record without audio (sleeve, notes, track list) for music that can't travel,
 * - FRIEND: your profile and shelf, to add you as a friend.
 */
object VinylPackage {
    const val FORMAT = 1
    const val EXTENSION = "vinyl"
    const val MIME = "application/zip"
    private const val MANIFEST = "manifest.json"
    private const val SLEEVE = "sleeve.jpg"
    private const val CHECKSUMS = "checksums.json"
    private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    enum class Kind { ALBUM, CARD, FRIEND }

    class Contents(
        val kind: Kind,
        val from: Profile,
        val album: Album?,
        val shelf: List<ShelfItem>,
        val sleeve: File?,
        /** Entry name in the package → extracted file. */
        val audio: Map<String, File>,
    )

    class Invalid(message: String) : Exception(message)

    /** Audio entry for a track: a stable, safe name inside the zip. */
    fun entryNameFor(index: Int, title: String, ext: String): String {
        val safe = title.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "Track" }
        return "audio/%02d %s.%s".format(index + 1, safe, ext.lowercase().ifBlank { "audio" })
    }

    fun write(
        out: OutputStream,
        kind: Kind,
        from: Profile,
        album: Album?,
        shelf: List<ShelfItem> = emptyList(),
        sleeve: File? = null,
        audio: List<Pair<String, () -> InputStream?>> = emptyList(),
    ) {
        if (kind != Kind.ALBUM && audio.isNotEmpty()) throw Invalid("Only album records may contain audio")
        if (kind == Kind.ALBUM && (album == null || album.tracks.isEmpty())) throw Invalid("Record has no FLAC tracks")
        audio.forEach { (name, open) ->
            if (!name.startsWith("audio/") || !FlacPolicy.isFlacName(name)) throw Invalid("Only FLAC audio can travel with a record")
            val valid = open()?.use { FlacPolicy.isValid(it) } == true
            if (!valid) throw Invalid("“${name.substringAfterLast('/')}” is not valid FLAC audio")
        }
        if (kind == Kind.ALBUM && album!!.tracks.any { t -> audio.none { it.first == t.fileName } })
            throw Invalid("Record is missing audio files")
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("app", "Runout")
                put("kind", kind.name)
                put("from", from.toJson())
                album?.let { put("album", AlbumJson.toJson(it, includeLocalPaths = false)) }
                if (shelf.isNotEmpty()) put("shelf", JSONArray().apply { shelf.forEach { put(it.toJson()) } })
            }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            if (sleeve != null && sleeve.exists()) {
                zip.putNextEntry(ZipEntry(SLEEVE))
                sleeve.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            // Audio is already compressed; store it without deflating to keep exports fast. The bytes
            // are the original files, untouched, and each one's SHA-256 travels with it.
            zip.setLevel(0)
            val sums = JSONObject()
            audio.forEach { (name, open) ->
                val input = open() ?: throw Invalid("Couldn't open $name")
                zip.putNextEntry(ZipEntry(name))
                val md = MessageDigest.getInstance("SHA-256")
                input.use { inp ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                        zip.write(buf, 0, n)
                    }
                }
                zip.closeEntry()
                sums.put(name, md.digest().joinToString("") { "%02x".format(it) })
            }
            if (audio.isNotEmpty()) {
                zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                zip.putNextEntry(ZipEntry(CHECKSUMS))
                zip.write(sums.toString().toByteArray())
                zip.closeEntry()
            }
        }
    }

    fun read(input: InputStream, destDir: File): Contents {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalFile
        var manifest: JSONObject? = null
        var checksums: JSONObject? = null
        var sleeve: File? = null
        val audio = mutableMapOf<String, File>()
        val digests = mutableMapOf<String, String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory) continue
                when {
                    name == MANIFEST -> manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    name == CHECKSUMS -> checksums = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    name == SLEEVE || name.startsWith("audio/") -> {
                        val target = File(destDir, name).canonicalFile
                        // Refuse anything that tries to write outside the record's folder.
                        if (!target.path.startsWith(canonicalDest.path + File.separator)) throw Invalid("Unsafe entry $name")
                        if (name.startsWith("audio/") && !FlacPolicy.isFlacName(name)) throw Invalid("Only FLAC audio can be imported")
                        if (name in audio) throw Invalid("Duplicate audio entry $name")
                        target.parentFile?.mkdirs()
                        val md = MessageDigest.getInstance("SHA-256")
                        FileOutputStream(target).use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > MAX_TOTAL_BYTES) throw Invalid("Record is too large")
                                md.update(buf, 0, n)
                                out.write(buf, 0, n)
                            }
                        }
                        if (name == SLEEVE) sleeve = target else {
                            audio[name] = target
                            digests[name] = md.digest().joinToString("") { "%02x".format(it) }
                        }
                    }
                    else -> Unit // ignore anything else
                }
            }
        }
        val m = manifest ?: throw Invalid("Not a Runout record (no manifest)")
        if (m.optInt("format", 0) > FORMAT) throw Invalid("Made by a newer version of Runout")
        val kind = runCatching { Kind.valueOf(m.getString("kind")) }.getOrElse { throw Invalid("Unknown record kind") }
        val from = Profile.fromJson(m.getJSONObject("from"))
        val album = m.optJSONObject("album")?.let { AlbumJson.fromJson(it) }
        val shelfJson = m.optJSONArray("shelf") ?: JSONArray()
        val shelf = (0 until shelfJson.length()).map { ShelfItem.fromJson(shelfJson.getJSONObject(it)) }
        if (kind != Kind.FRIEND && album == null) throw Invalid("Record has no album")
        if (kind == Kind.ALBUM && album != null) {
            val missing = album.tracks.filter { it.fileName !in audio }
            if (missing.isNotEmpty()) throw Invalid("Record is missing ${missing.size} audio files")
        }
        // Every file must match the checksum it was sent with: the audio arrives bit for bit.
        checksums?.let { sums ->
            for (name in sums.keys()) {
                val got = digests[name] ?: throw Invalid("Record is missing ${name.substringAfter('/')}")
                if (!got.equals(sums.getString(name), ignoreCase = true)) throw Invalid("“${name.substringAfter('/')}” arrived damaged. Ask for the record to be sent again.")
            }
        }
        if (kind != Kind.ALBUM && audio.isNotEmpty()) throw Invalid("Only album records may contain audio")
        if (kind == Kind.ALBUM && album!!.tracks.isEmpty()) throw Invalid("Record has no FLAC tracks")
        audio.forEach { (name, file) ->
            if (!FlacPolicy.isValid(file)) throw Invalid("“${name.substringAfterLast('/')}” is not valid FLAC audio")
        }
        return Contents(kind, from, album, shelf, sleeve, audio)
    }
}
