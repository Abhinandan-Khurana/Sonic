package app.runout.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** An album found on disk, with the raw artwork it came with (if any) so the repository can cache it. */
class ScannedAlbum(
    val album: Album,
    val embeddedArt: ByteArray?,
    val folderImage: Uri?,
)

data class ScanProgress(val folders: Int, val albums: Int, val audioFiles: Int = 0)

/** What a scan found, shown when a folder turns up no albums so the user can see why. */
data class ScanReport(
    val folders: Int,
    val audioFiles: Int,
    val unreadable: Int,
    val albums: Int,
    val otherFiles: Map<String, Int>,
    val error: String? = null,
) {
    val summary: String
        get() = when {
            error != null -> "Couldn't read the folder: $error"
            albums > 0 -> "$albums albums from $audioFiles music files in $folders folders"
            audioFiles == 0 && otherFiles.isEmpty() -> "Looked in $folders folders and found no files"
            audioFiles == 0 -> "Looked in $folders folders and found no music files " +
                "(found ${otherFiles.entries.sortedByDescending { it.value }.take(3).joinToString { ".${it.key} ×${it.value}" }})"
            else -> "Found $audioFiles music files but couldn't read them"
        }
}

/**
 * Walks the folder the user picked (through Android's Storage Access Framework, so no broad storage
 * permission is needed) and turns every folder of audio files into one or more albums. Albums are
 * handed back one at a time as they're found, so the shelf fills while the scan runs.
 */
class LibraryScanner(private val context: Context) {

    private data class Entry(val docId: String, val name: String, val mime: String, val size: Long) {
        val isDir: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
        val ext: String get() = name.substringAfterLast('.', "").lowercase()
    }

    suspend fun scan(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
        onAlbum: suspend (ScannedAlbum) -> Unit = {},
    ): ScanReport = withContext(Dispatchers.IO) {
        var folders = 0
        var audioFiles = 0
        var unreadable = 0
        var albums = 0
        val other = mutableMapOf<String, Int>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrElse {
            return@withContext ScanReport(0, 0, 0, 0, emptyMap(), error = "not a folder")
        }
        val queue = ArrayDeque<Pair<String, Int>>().apply { add(rootId to 0) }
        var listedAnything = false
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (docId, depth) = queue.removeFirst()
            val children = list(treeUri, docId) ?: continue
            listedAnything = true
            folders++
            children.filter { it.isDir && depth < MAX_DEPTH && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
                .forEach { queue.add(it.docId to depth + 1) }
            val files = children.filter { !it.isDir }
            val audio = files.filter { it.ext in AUDIO_EXTENSIONS }
            files.filter { it.ext !in AUDIO_EXTENSIONS && it.ext !in IMAGE_EXTENSIONS && it.ext.isNotEmpty() }
                .forEach { other[it.ext] = (other[it.ext] ?: 0) + 1 }
            audioFiles += audio.size
            if (audio.isNotEmpty()) {
                val image = pickFolderImage(files)?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.docId) }
                val (found, failed) = readFolder(treeUri, docId, nameOf(children, docId), audio, image)
                unreadable += failed
                found.forEach {
                    albums++
                    onAlbum(it)
                }
            }
            onProgress(ScanProgress(folders, albums, audioFiles))
        }
        ScanReport(
            folders = folders,
            audioFiles = audioFiles,
            unreadable = unreadable,
            albums = albums,
            otherFiles = other,
            error = if (!listedAnything) "Android didn't let the app list this folder. Try choosing it again." else null,
        )
    }

    private fun list(treeUri: Uri, parentId: String): List<Entry>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        return runCatching {
            val out = mutableListOf<Entry>()
            // The Bundle form is what document providers actually implement (API 26+).
            context.contentResolver.query(childrenUri, projection, null as android.os.Bundle?, null)?.use { c ->
                while (c.moveToNext()) {
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    out += Entry(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", size)
                }
            } ?: return null
            out
        }.getOrNull()
    }

    private fun nameOf(@Suppress("UNUSED_PARAMETER") children: List<Entry>, docId: String): String =
        docId.substringAfterLast('/').substringAfterLast(':').ifBlank { "Untitled" }

    private fun pickFolderImage(files: List<Entry>): Entry? {
        val images = files.filter { it.ext in IMAGE_EXTENSIONS }
        return images.firstOrNull { it.name.substringBeforeLast('.').lowercase() in PREFERRED_IMAGE_NAMES }
            ?: images.firstOrNull()
    }

    /** Reads one file: our own parser first, Android's retriever to fill gaps, then a size estimate. */
    private fun readFile(uri: Uri, entry: Entry, wantPicture: Boolean): AudioInfo? {
        val parsed = runCatching {
            context.contentResolver.openInputStream(uri)?.use { TagReader.read(it, entry.ext, entry.size, wantPicture) }
        }.getOrNull()
        val needsRetriever = parsed == null || parsed.durationMs <= 0 || parsed.title == null
        val retrieved = if (needsRetriever) readWithRetriever(uri, entry.ext, wantPicture && parsed?.picture == null) else null
        if (parsed == null && retrieved == null) return null
        val merged = merge(parsed, retrieved, entry.ext)
        return merged
    }

    private fun merge(a: AudioInfo?, b: AudioInfo?, ext: String): AudioInfo {
        if (a == null) return b!!
        if (b == null) return a
        return a.copy(
            title = a.title ?: b.title,
            artist = a.artist ?: b.artist,
            albumArtist = a.albumArtist ?: b.albumArtist,
            album = a.album ?: b.album,
            track = a.track ?: b.track,
            disc = a.disc ?: b.disc,
            year = a.year ?: b.year,
            durationMs = if (a.durationMs > 0) a.durationMs else b.durationMs,
            sampleRate = a.sampleRate ?: b.sampleRate,
            bits = a.bits ?: b.bits,
            bitrateKbps = a.bitrateKbps ?: b.bitrateKbps,
            picture = a.picture ?: b.picture,
            codec = a.codec.ifBlank { codecFor(ext, b.bitrateKbps) },
        )
    }

    private fun readWithRetriever(uri: Uri, ext: String, wantPicture: Boolean): AudioInfo? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            fun key(k: Int) = mmr.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            val bitrate = key(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { (it / 1000).toInt() }
            val info = AudioInfo(
                codec = codecFor(ext, bitrate),
                title = key(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                track = TagReader.leadingInt(key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                disc = TagReader.leadingInt(key(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                year = TagReader.year(key(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: key(MediaMetadataRetriever.METADATA_KEY_DATE)),
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                sampleRate = if (Build.VERSION.SDK_INT >= 31) key(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() else null,
                bits = if (Build.VERSION.SDK_INT >= 31) key(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() else null,
                bitrateKbps = bitrate,
                picture = if (wantPicture) mmr.embeddedPicture else null,
            )
            // Nothing at all came back: treat as a failed read rather than an empty track.
            if (info.title == null && info.durationMs == 0L && info.album == null) null else info
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private suspend fun readFolder(
        treeUri: Uri,
        folderId: String,
        folderName: String,
        audio: List<Entry>,
        folderImage: Uri?,
    ): Pair<List<ScannedAlbum>, Int> {
        data class Read(val entry: Entry, val uri: Uri, val info: AudioInfo, val estimated: Boolean)

        var failed = 0
        var pictureFound = false
        val reads = audio.sortedBy { it.name.lowercase() }.map { entry ->
            coroutineContext.ensureActive()
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.docId)
            val info = readFile(uri, entry, wantPicture = !pictureFound)
            if (info?.picture != null) pictureFound = true
            if (info == null) failed++
            // A file we can't measure still goes on the shelf; its length is estimated from its size.
            val base = info ?: AudioInfo(codec = codecFor(entry.ext, null))
            val estimated = base.durationMs <= 0
            val withDuration = if (estimated) base.copy(durationMs = estimateDurationMs(entry.size, base.codec)) else base
            Read(entry, uri, withDuration, estimated)
        }

        val groups = reads.groupBy { it.info.album?.takeIf { a -> a.isNotBlank() } ?: folderName }
        val albums = groups.map { (albumTitle, items) ->
            val artist = items.firstNotNullOfOrNull { it.info.albumArtist?.takeIf { a -> a.isNotBlank() } }
                ?: items.groupingBy { it.info.artist ?: "" }.eachCount().maxByOrNull { it.value }?.key?.takeIf { it.isNotEmpty() }
                ?: "Unknown artist"
            val sorted = items.sortedWith(compareBy({ it.info.disc ?: 1 }, { it.info.track ?: Int.MAX_VALUE }, { it.entry.name.lowercase() }))
            val tracks = sorted.mapIndexed { i, r ->
                Track(
                    id = r.uri.toString(),
                    uri = r.uri.toString(),
                    title = r.info.title?.takeIf { it.isNotBlank() } ?: r.entry.name.substringBeforeLast('.'),
                    trackNumber = r.info.track ?: (i + 1),
                    discNumber = r.info.disc ?: 1,
                    durationMs = r.info.durationMs,
                    fileName = r.entry.name,
                    trackGainDb = r.info.trackGainDb,
                    durationKnown = !r.estimated,
                )
            }
            val first = sorted.first().info
            val licences = sorted.mapNotNull { it.info.license }.distinct()
            ScannedAlbum(
                album = Album(
                    id = stableId("$folderId|$albumTitle"),
                    title = albumTitle,
                    artist = artist,
                    year = items.firstNotNullOfOrNull { it.info.year },
                    folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderId).toString(),
                    tracks = tracks,
                    quality = AudioQuality(
                        codec = first.codec,
                        bitsPerSample = first.bits,
                        sampleRateHz = first.sampleRate,
                        bitrateKbps = sorted.mapNotNull { it.info.bitrateKbps }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                    ),
                    license = licences.singleOrNull()?.takeIf { licences.size == 1 && sorted.all { it.info.license != null } },
                    albumGainDb = sorted.firstNotNullOfOrNull { it.info.albumGainDb },
                ),
                embeddedArt = items.firstNotNullOfOrNull { it.info.picture },
                folderImage = folderImage,
            )
        }
        return albums to failed
    }

    companion object {
        const val MAX_DEPTH = 8
        val AUDIO_EXTENSIONS = setOf("flac", "mp3", "m4a", "mp4", "aac", "wav", "ogg", "oga", "opus")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val PREFERRED_IMAGE_NAMES = setOf("cover", "folder", "front", "album", "albumart", "artwork")

        fun codecFor(ext: String, bitrateKbps: Int?): String = when (ext) {
            "flac" -> "FLAC"
            "mp3" -> "MP3"
            "wav" -> "WAV"
            "ogg", "oga" -> "OGG"
            "opus" -> "OPUS"
            "aac" -> "AAC"
            // .m4a can hold AAC or Apple Lossless; lossless runs far above any AAC bitrate.
            "m4a", "mp4" -> if ((bitrateKbps ?: 0) > 500) "ALAC" else "AAC"
            else -> ext.uppercase()
        }

        /** Rough length from file size, used only when a file can't be measured. */
        fun estimateDurationMs(sizeBytes: Long, codec: String): Long {
            val kbps = when (codec) { "FLAC", "ALAC" -> 900; "WAV" -> 1411; else -> 256 }
            return if (sizeBytes > 0) (sizeBytes * 8 / kbps).coerceAtLeast(1_000) else 180_000
        }

        fun stableId(seed: String): String =
            MessageDigest.getInstance("SHA-1").digest(seed.toByteArray())
                .take(10).joinToString("") { "%02x".format(it) }

        /** Parses a FLAC STREAMINFO block or a canonical WAV fmt chunk. Kept for older callers and tests. */
        fun parseStreamHeader(b: ByteArray, ext: String): StreamHeader? {
            val info = TagReader.read(b.inputStream(), ext, b.size.toLong(), wantPicture = false)
            val rate = info?.sampleRate ?: return fallbackHeader(b, ext)
            return StreamHeader(rate, info.bits ?: 16, info.channels ?: 2)
        }

        private fun fallbackHeader(b: ByteArray, ext: String): StreamHeader? {
            fun u(i: Int) = b[i].toInt() and 0xFF
            return when (ext) {
                "flac" -> {
                    if (b.size < 26 || String(b, 0, 4, Charsets.US_ASCII) != "fLaC") return null
                    val s = 18
                    val rate = (u(s) shl 12) or (u(s + 1) shl 4) or (u(s + 2) shr 4)
                    val channels = ((u(s + 2) shr 1) and 0x7) + 1
                    val bits = (((u(s + 2) and 0x1) shl 4) or (u(s + 3) shr 4)) + 1
                    if (rate <= 0) null else StreamHeader(rate, bits, channels)
                }
                "wav" -> {
                    if (b.size < 36 || String(b, 0, 4, Charsets.US_ASCII) != "RIFF") return null
                    val rate = u(24) or (u(25) shl 8) or (u(26) shl 16) or (u(27) shl 24)
                    if (rate <= 0) null else StreamHeader(rate, u(34) or (u(35) shl 8), u(22) or (u(23) shl 8))
                }
                else -> null
            }
        }
    }

    data class StreamHeader(val sampleRate: Int, val bits: Int, val channels: Int)
}
