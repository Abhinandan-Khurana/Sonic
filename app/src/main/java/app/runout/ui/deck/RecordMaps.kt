package app.runout.ui.deck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The 3D record's surface for one side, to go with its face (RecordArt.drawRecord): a normal map
 * with the grooves where the music is and smooth bands between the tracks, and a roughness map
 * where those bands, like on a real record, are glossier than the grooves around them. The radii
 * are the drawing's, as fractions of the record's radius, so the bands land on the painted gaps.
 *
 * Both are square, covering the disc the way its UVs do. Building them takes a moment: call off
 * the main thread.
 */
object RecordMaps {
    private const val SIZE = 1024
    private const val ROUGH_SIZE = 512

    private const val GROOVE_OUTER = 0.975f
    private const val GROOVE_INNER = 0.42f
    private const val PLAY_OUTER = 0.95f
    private const val PLAY_INNER = 0.43f
    private const val LABEL = 0.34f
    /** A gap between tracks, as wide as the painted one. */
    private const val GAP = 0.014f
    /** Groove spacing in pixels of the map, and how far a groove wall tilts the surface. */
    private const val PITCH_PX = 3.2f
    private const val TILT = 0.35f

    private const val ROUGH_GROOVES = 0.3f
    private const val ROUGH_SMOOTH = 0.22f
    private const val ROUGH_GAP = 0.12f
    private const val ROUGH_LABEL = 0.7f

    private val flat = Color.rgb(128, 128, 255)

    /** Builds the grooves ahead of the first record, so it lands without a pause. */
    fun warmUp() {
        grooves
    }

    /** The grooves across the whole playing surface, built once. */
    private val grooves: Bitmap by lazy {
        val c = SIZE / 2f
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            val dy = (y + 0.5f - c) / c
            for (x in 0 until SIZE) {
                val dx = (x + 0.5f - c) / c
                val r = hypot(dx, dy)
                px[y * SIZE + x] = if (r < GROOVE_INNER || r > GROOVE_OUTER) {
                    flat
                } else {
                    // A groove's walls lean towards and away from the centre in turn.
                    val slope = TILT * sin(r * c / PITCH_PX * 2f * PI.toFloat())
                    val nx = dx / r * slope
                    val ny = -dy / r * slope // +y is up the image, away from +v
                    Color.rgb(encode(nx), encode(ny), encode(1f))
                }
            }
        }
        Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    /** The normal map for a side whose tracks start at [boundaries] (fractions through the side). */
    fun normal(boundaries: List<Float>): Bitmap {
        val map = grooves.copy(Bitmap.Config.ARGB_8888, true)
        val c = SIZE / 2f
        val band = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = GAP * c; color = flat }
        val canvas = Canvas(map)
        boundaries.forEach { canvas.drawCircle(c, c, gapRadius(it) * c, band) }
        return map
    }

    /** Roughness (green) and metalness (blue, none) for the same side. */
    fun roughness(boundaries: List<Float>): Bitmap {
        val map = Bitmap.createBitmap(ROUGH_SIZE, ROUGH_SIZE, Bitmap.Config.ARGB_8888)
        val c = ROUGH_SIZE / 2f
        val canvas = Canvas(map)
        canvas.drawColor(rough(ROUGH_SMOOTH))
        val p = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        p.color = rough(ROUGH_GROOVES)
        p.strokeWidth = (GROOVE_OUTER - GROOVE_INNER) * c
        canvas.drawCircle(c, c, (GROOVE_OUTER + GROOVE_INNER) / 2f * c, p)
        p.color = rough(ROUGH_GAP)
        p.strokeWidth = GAP * c
        boundaries.forEach { canvas.drawCircle(c, c, gapRadius(it) * c, p) }
        p.style = Paint.Style.FILL
        p.color = rough(ROUGH_LABEL)
        canvas.drawCircle(c, c, LABEL * c, p)
        return map
    }

    private fun gapRadius(f: Float) = PLAY_OUTER - (PLAY_OUTER - PLAY_INNER) * f

    private fun encode(v: Float) = ((v * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)

    private fun rough(r: Float) = Color.rgb(0, (r * 255f).toInt(), 0)
}
