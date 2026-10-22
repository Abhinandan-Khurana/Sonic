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

    /**
     * Why a .flac file was refused, in words a listener can act on. Only reads the start of the
     * file; it never changes it.
     */
    fun diagnose(context: Context, uri: Uri): String = runCatching {
        val head = context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            var n = 0
            while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
            buf.copyOf(n)
        } ?: return "Couldn't open the file. Choose the folder again in Settings › Library."
        val text = String(head, Charsets.ISO_8859_1)
        when {
            head.isEmpty() -> "The file is empty."
            text.startsWith("fLaC") || text.startsWith("ID3") && text.indexOf("fLaC") in 10..head.size -> "Damaged FLAC: the header or the first audio frame is broken, or the download was cut off."
            text.startsWith("ID3") -> "An MP3 (or other format) renamed to .flac. Only real FLAC plays in Runout."
            text.indexOf("fLaC") in 1..head.size -> "Extra data before the FLAC stream. Re-save or re-rip the file."
            text.startsWith("RIFF") -> "A WAV file renamed to .flac. Only real FLAC plays in Runout."
            text.contains("ftyp") -> "An M4A/ALAC file renamed to .flac. Only real FLAC plays in Runout."
            text.startsWith("OggS") -> "An Ogg file renamed to .flac. Only real FLAC plays in Runout."
            else -> "Not FLAC inside, despite the .flac name."
        }
    }.getOrDefault("Couldn't open the file. Choose the folder again in Settings › Library.")

    /**
     * Why Android's own FLAC decoder can't play this valid FLAC on this phone, or null when it
     * can. Android decodes 8, 16 and 24-bit (and 32-bit from Android 10); 12 and 20-bit never.
     * Before Android 12, audio output stops at 192 kHz.
     */
    fun unplayableReason(info: AudioInfo, sdk: Int = android.os.Build.VERSION.SDK_INT): String? {
        val bits = info.bits
        val rate = info.sampleRate
        return when {
            bits != null && bits !in setOf(8, 16, 24, 32) -> "$bits-bit FLAC isn't supported by Android's decoder. Convert it to 16 or 24-bit."
            bits == 32 && sdk < 29 -> "32-bit FLAC needs Android 10 or later. Convert it to 24-bit."
            rate != null && rate > 192_000 && sdk < 31 ->
                "${AudioQuality.formatRate(rate)} FLAC needs Android 12 or later; this phone plays up to 192 kHz."
            else -> null
        }
    }

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

    /** The record's quality: its lowest bit depth and rate, never guessed upwards from one track. */
    fun quality(infos: List<AudioInfo>): AudioQuality = AudioQuality(
        "FLAC", infos.mapNotNull { it.bits }.minOrNull(), infos.mapNotNull { it.sampleRate }.minOrNull(),
        infos.mapNotNull { it.bitrateKbps }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
    )
}
