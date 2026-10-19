package app.runout.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Track
import java.io.File
import java.io.InputStream

/** The single admission rule for music. Artwork and record manifests are not music files. */
object FlacPolicy {
    fun isFlacName(name: String): Boolean =
        name.substringAfterLast('/').substringAfterLast('\\').endsWith(".flac", ignoreCase = true)

    /** Consumes a stream, without closing it. A filename alone never establishes the format. */
    fun read(input: InputStream, fileSize: Long = -1, wantPicture: Boolean = false): AudioInfo? =
        TagReader.read(input, "flac", fileSize, wantPicture)

    fun read(file: File, wantPicture: Boolean = false): AudioInfo? =
        if (!isFlacName(file.name)) null else runCatching {
            file.inputStream().use { read(it, file.length(), wantPicture) }
        }.getOrNull()

    fun read(context: Context, uri: Uri, fileName: String? = null, wantPicture: Boolean = false): AudioInfo? = runCatching {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            if (fileName != null && !isFlacName(fileName)) return null
            return read(file, wantPicture)
        }
        if (uri.scheme != "content") return null
        var displayName: String? = null
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null as android.os.Bundle?, null)?.use { c ->
            if (c.moveToFirst()) {
                displayName = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        // Require the provider's actual name too, when available, rather than trusting cached tags.
        if (!isFlacName(displayName ?: fileName ?: return null)) return null
        if (fileName != null && !isFlacName(fileName)) return null
        context.contentResolver.openInputStream(uri)?.use { read(it, size, wantPicture) }
    }.getOrNull()

    fun isValid(input: InputStream): Boolean = read(input) != null
    fun isValid(file: File): Boolean = read(file) != null

    fun read(context: Context, track: Track): AudioInfo? =
        read(context, Uri.parse(track.uri), track.fileName.takeIf { it.isNotBlank() })

    /** Retain verified tracks in a mixed legacy record while preserving record/track identities. */
    fun filterAlbum(context: Context, album: Album, refreshMetadata: Boolean = true): Album? {
        val verified = album.tracks.mapNotNull { track ->
            val info = read(context, track) ?: return@mapNotNull null
            (if (refreshMetadata) track.copy(durationMs = info.durationMs.takeIf { it > 0 } ?: track.durationMs,
                durationKnown = info.durationMs > 0, trackGainDb = info.trackGainDb ?: track.trackGainDb) else track) to info
        }
        if (verified.isEmpty()) return null
        return album.copy(tracks = verified.map { it.first }, quality = quality(verified.map { it.second }))
    }

    fun quality(infos: List<AudioInfo>): AudioQuality = AudioQuality(
        "FLAC", infos.mapNotNull { it.bits }.minOrNull(), infos.mapNotNull { it.sampleRate }.minOrNull(),
        infos.mapNotNull { it.bitrateKbps }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
    )
}
