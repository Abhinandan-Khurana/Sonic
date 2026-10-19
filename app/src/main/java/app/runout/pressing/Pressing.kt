package app.runout.pressing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import app.runout.library.AlbumJson
import app.runout.library.ArtworkRepository
import app.runout.library.FlacPolicy
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.DiscStyle
import app.runout.model.Source
import app.runout.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.random.Random

/** What the Pressing Plant needs to press a record. */
data class PressingDraft(
    val title: String,
    val artist: String,
    val tracks: List<Track>,
    val quality: AudioQuality,
    val disc: DiscStyle = DiscStyle.BLACK,
    val labelColor: Int = 0xFF2B6BFF.toInt(),
    /** An image the user picked for the sleeve; when null a pattern is generated. */
    val coverImage: Uri? = null,
    val pattern: Int = 0,
    val owned: Boolean = false,
    val notes: String? = null,
)

/** Your own records: any tracks from your shelf, your sleeve, your vinyl. */
class PressingRepository(private val context: Context) {
    private val file = File(context.filesDir, "pressings.json")
    private val coverDir = File(context.filesDir, "covers").apply { mkdirs() }

    private val _records = MutableStateFlow(AlbumJson.readValidatedList(context, file))
    val records: StateFlow<List<Album>> = _records.asStateFlow()

    suspend fun press(draft: PressingDraft): Album = withContext(Dispatchers.IO) {
        require(draft.title.isNotBlank()) { "A record needs a title" }
        require(draft.tracks.isNotEmpty()) { "A record needs at least one track" }
        val infos = draft.tracks.map { FlacPolicy.read(context, it) ?: error("Only verified FLAC tracks can be pressed") }
        val id = "press-" + UUID.randomUUID().toString().take(12)
        val coverFile = File(coverDir, "$id.jpg")
        val picked = draft.coverImage?.let { uri ->
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        val art = picked?.let { cropSquare(it, 1000) } ?: SleeveArtist.render(draft.pattern, draft.title, draft.artist, draft.labelColor)
        FileOutputStream(coverFile).use { art.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val album = Album(
            id = id,
            title = draft.title.trim(),
            artist = draft.artist.trim().ifBlank { "Various" },
            year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
            folderUri = "",
            tracks = draft.tracks.mapIndexed { i, t -> t.copy(trackNumber = i + 1, discNumber = 1) },
            quality = FlacPolicy.quality(infos),
            coverPath = coverFile.absolutePath,
            tint = ArtworkRepository.averageColor(coverFile),
            source = Source.PRESSING,
            owned = draft.owned,
            disc = draft.disc,
            labelColor = draft.labelColor,
            notes = draft.notes?.takeIf { it.isNotBlank() },
        )
        _records.value = _records.value + album
        AlbumJson.writeList(file, _records.value)
        album
    }

    fun delete(id: String) {
        _records.value = _records.value.filterNot { it.id == id }
        AlbumJson.writeList(file, _records.value)
        File(coverDir, "$id.jpg").delete()
    }

    private fun cropSquare(src: Bitmap, max: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return if (side > max) Bitmap.createScaledBitmap(square, max, max, true) else square
    }

    companion object {
        /** The best quality the chosen tracks share, so the label is honest. */
        fun qualityFor(sources: List<Album>): AudioQuality {
            if (sources.isEmpty()) return AudioQuality("MIXED")
            val codecs = sources.map { it.quality.codec }.distinct()
            if (codecs.size == 1) {
                val bits = sources.mapNotNull { it.quality.bitsPerSample }.minOrNull()
                val rate = sources.mapNotNull { it.quality.sampleRateHz }.minOrNull()
                return AudioQuality(codecs[0], bits, rate, sources.mapNotNull { it.quality.bitrateKbps }.minOrNull())
            }
            return AudioQuality(if (sources.all { it.quality.isLossless }) "LOSSLESS MIX" else "MIXED")
        }
    }
}

/** Generative sleeve art for pressings without a picture: six Y2K-ish patterns. */
object SleeveArtist {
    const val PATTERNS = 6
    val PATTERN_NAMES = listOf("Orbit", "Stripes", "Bloom", "Grid", "Sunset", "Type")

    fun render(pattern: Int, title: String, artist: String, accent: Int, size: Int = 1000): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = size.toFloat()
        val rnd = Random((title + artist).hashCode())
        val light = Color.rgb(244, 244, 242)
        val soft = blend(accent, Color.WHITE, 0.72f)
        when (pattern.mod(PATTERNS)) {
            0 -> { // Orbit
                c.drawColor(light)
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.012f; p.color = accent
                for (i in 1..7) c.drawCircle(s * 0.5f, s * 0.5f, s * 0.06f * i, p)
                p.style = Paint.Style.FILL
                c.drawCircle(s * (0.3f + rnd.nextFloat() * 0.4f), s * 0.28f, s * 0.05f, p)
            }
            1 -> { // Stripes
                c.drawColor(soft)
                p.color = accent
                for (i in 0 until 14 step 2) c.drawRect(0f, i * s / 14f, s, (i + 1) * s / 14f, p)
            }
            2 -> { // Bloom
                p.shader = RadialGradient(s * 0.5f, s * 0.55f, s * 0.6f, accent, light, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, p)
                p.shader = null; p.color = Color.WHITE
                repeat(9) { c.drawCircle(rnd.nextFloat() * s, rnd.nextFloat() * s, s * (0.02f + rnd.nextFloat() * 0.05f), p) }
            }
            3 -> { // Grid
                c.drawColor(light)
                p.color = accent; p.strokeWidth = s * 0.004f
                for (i in 0..12) { val x = i * s / 12f; c.drawLine(x, 0f, x, s, p); c.drawLine(0f, x, s, x, p) }
                p.style = Paint.Style.FILL
                c.drawRect(s * 0.25f, s * 0.25f, s * 0.58f, s * 0.58f, p)
            }
            4 -> { // Sunset
                p.shader = LinearGradient(0f, 0f, 0f, s, blend(accent, Color.BLACK, 0.4f), soft, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, p)
                p.shader = null; p.color = Color.rgb(255, 214, 120)
                c.drawCircle(s * 0.5f, s * 0.62f, s * 0.2f, p)
                p.color = blend(accent, Color.BLACK, 0.6f)
                c.drawRect(0f, s * 0.72f, s, s, p)
            }
            else -> c.drawColor(accent)
        }
        // Title type, set small like the room's labels.
        p.shader = null; p.style = Paint.Style.FILL
        p.color = if (pattern.mod(PATTERNS) == 5) Color.WHITE else Color.rgb(17, 17, 17)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = s * 0.07f
        c.drawText(title.take(28), s * 0.06f, s * 0.12f, p)
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        p.textSize = s * 0.04f
        c.drawText(artist.take(40), s * 0.06f, s * 0.18f, p)
        return bmp
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
