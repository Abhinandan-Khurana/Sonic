package app.runout.model

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val trackNumber: Int,
    val discNumber: Int,
    val durationMs: Long,
    val fileName: String = "",
    val trackGainDb: Float? = null,
    /** False when the length was estimated from the file size because the file couldn't be measured. */
    val durationKnown: Boolean = true,
)

/** Where a record on the shelf came from. Decides what can be shared. */
enum class Source { FOLDER, RECEIVED, DROP, PRESSING, SOULSEEK }

/** Vinyl styles for the Pressing Plant. Albums without one get theirs from their audio quality. */
enum class DiscStyle(val label: String) {
    BLACK("Black"),
    COLOUR("Colour"),
    CLEAR("Clear"),
    SPLATTER("Splatter"),
    MARBLE("Marble"),
    PICTURE("Picture disc"),
}

/** What the files actually are. Shown on the record label, never guessed upwards. */
data class AudioQuality(
    val codec: String,
    val bitsPerSample: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
) {
    val isLossless: Boolean get() = codec in LOSSLESS

    val isHiRes: Boolean
        get() = isLossless && ((bitsPerSample ?: 16) >= 24 || (sampleRateHz ?: 44_100) > 48_000)

    /** "FLAC · 24-bit · 96 kHz", "MP3 · 320 kbps", or just the codec when nothing else is known. */
    val label: String
        get() = buildList {
            add(codec)
            if (isLossless) {
                bitsPerSample?.let { add("$it-bit") }
                sampleRateHz?.let { add(formatRate(it)) }
            } else {
                bitrateKbps?.let { add("$it kbps") }
            }
        }.joinToString(" · ")

    /** "24/96" for lossless files with known depth and rate, otherwise the codec. */
    val shortLabel: String
        get() {
            val bits = bitsPerSample
            val rate = sampleRateHz
            return if (isLossless && bits != null && rate != null) "$bits/${formatRate(rate, withUnit = false)}" else codec
        }

    companion object {
        val LOSSLESS = setOf("FLAC", "WAV", "ALAC")

        fun formatRate(hz: Int, withUnit: Boolean = true): String {
            val khz = hz / 1000.0
            val text = if (khz == khz.roundToInt().toDouble()) khz.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", khz)
            return if (withUnit) "$text kHz" else text
        }
    }
}

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val folderUri: String,
    val tracks: List<Track>,
    val quality: AudioQuality,
    val coverPath: String? = null,
    /** Average colour of the cover, used to press coloured vinyl. ARGB. */
    val tint: Int? = null,
    val source: Source = Source.FOLDER,
    /** A Creative Commons (or similar) licence URL found in the tags or attached to the record. */
    val license: String? = null,
    val albumGainDb: Float? = null,
    /** The user has said this is their own music, so the audio can be shared. */
    val owned: Boolean = false,
    val disc: DiscStyle? = null,
    val labelColor: Int? = null,
    val notes: String? = null,
    /** Who sent it, for records received from friends. */
    val from: String? = null,
    /** For Soulseek downloads: the folder on [from]'s share it came from, with `/` separators. */
    val remoteFolder: String? = null,
    /** Local validation state; metadata remains on the shelf so access can be repaired. */
    val unavailableReason: String? = null,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }

    val isOpenLicence: Boolean
        get() = license?.let { l -> OPEN_LICENCE_HINTS.any { l.contains(it, ignoreCase = true) } } == true

    /** Whether the audio itself may travel with the record when shared. */
    val audioShareable: Boolean get() = owned || isOpenLicence

    /** The pressing this album gets: hi-res is clear coloured vinyl, lossless is solid colour, lossy is black. */
    val pressing: Pressing
        get() = when (disc) {
            DiscStyle.BLACK -> Pressing.BLACK
            DiscStyle.COLOUR, DiscStyle.MARBLE, DiscStyle.SPLATTER, DiscStyle.PICTURE -> Pressing.COLOUR
            DiscStyle.CLEAR -> Pressing.CLEAR
            null -> when {
                quality.isHiRes -> Pressing.CLEAR
                quality.isLossless -> Pressing.COLOUR
                else -> Pressing.BLACK
            }
        }

    companion object {
        val OPEN_LICENCE_HINTS = listOf("creativecommons.org", "publicdomain", "CC BY", "CC0", "CC-BY")
    }
}

enum class Pressing { BLACK, COLOUR, CLEAR }

data class Side(val index: Int, val tracks: List<Track>) {
    val letter: Char get() = 'A' + index
    val durationMs: Long get() = tracks.sumOf { it.durationMs }

    /** Where each track after the first begins, as a fraction of the side (0..1). Drawn as gaps in the grooves. */
    fun trackBoundaries(): List<Float> {
        val total = durationMs.toFloat().coerceAtLeast(1f)
        var acc = 0L
        return tracks.dropLast(1).map { acc += it.durationMs; acc / total }
    }

    /** The track index and offset for a needle dropped at [fraction] of the side. */
    fun locate(fraction: Float): Pair<Int, Long> {
        if (tracks.isEmpty()) return 0 to 0L
        val target = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
        var acc = 0L
        tracks.forEachIndexed { i, t ->
            if (target < acc + t.durationMs || i == tracks.lastIndex) {
                return i to (target - acc).coerceIn(0L, (t.durationMs - 500L).coerceAtLeast(0L))
            }
            acc += t.durationMs
        }
        return tracks.lastIndex to 0L
    }

    /** Position within the side for a given track and offset. */
    fun positionOf(trackIndex: Int, offsetMs: Long): Long =
        tracks.take(trackIndex.coerceIn(0, tracks.size)).sumOf { it.durationMs } + offsetMs
}

object SideSplitter {
    /** A 12" side at 33 rpm holds about 22 minutes. */
    const val MAX_SIDE_MS = 22 * 60_000L

    /**
     * Splits an album into vinyl sides: two sides when it fits on one LP, more (always an even
     * number) when it needs a double album. Tracks are never cut; sides are balanced by time.
     */
    fun split(tracks: List<Track>, maxSideMs: Long = MAX_SIDE_MS): List<Side> {
        if (tracks.isEmpty()) return emptyList()
        if (tracks.size == 1) return listOf(Side(0, tracks))
        val total = tracks.sumOf { it.durationMs }
        var count = max(2, ceil(total / maxSideMs.toDouble()).toInt())
        if (count % 2 == 1) count++
        count = count.coerceAtMost(tracks.size)
        val target = total / count.toDouble()

        val sides = mutableListOf<MutableList<Track>>(mutableListOf())
        var elapsed = 0L
        tracks.forEachIndexed { i, track ->
            val sidesLeft = count - sides.size
            val tracksLeft = tracks.size - i
            val current = sides.last()
            val midpoint = elapsed + track.durationMs / 2.0
            val shouldBreak = current.isNotEmpty() && sidesLeft > 0 &&
                (midpoint > target * sides.size || tracksLeft <= sidesLeft)
            if (shouldBreak) sides.add(mutableListOf())
            sides.last().add(track)
            elapsed += track.durationMs
        }
        return sides.mapIndexed { i, list -> Side(i, list) }
    }
}

/** Short names for licence URLs, for labels and sleeves. */
object Licences {
    private val cc = Regex("""creativecommons\.org/licenses/([a-z-]+)/([0-9.]+)""")

    fun label(url: String?): String? {
        val u = url?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if ("publicdomain/zero" in u) return "CC0"
        if ("publicdomain/mark" in u) return "Public domain"
        cc.find(u)?.let { m -> return "CC " + m.groupValues[1].uppercase() + " " + m.groupValues[2] }
        return if ("creativecommons" in u) "Creative Commons" else url.take(32)
    }
}
