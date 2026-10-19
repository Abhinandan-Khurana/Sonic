package app.runout.sound

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType(val code: String) {
    PEAK("PK"), LOW_SHELF("LSC"), HIGH_SHELF("HSC"), LOW_PASS("LPQ"), HIGH_PASS("HPQ");
}

data class EqBand(
    val type: FilterType,
    val freqHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 0.707f,
    val enabled: Boolean = true,
)

/** Normalised biquad coefficients (a0 = 1), from Robert Bristow-Johnson's Audio EQ Cookbook. */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** Gain in dB at [freqHz] for a stream at [sampleRate]. */
    fun responseDb(freqHz: Double, sampleRate: Int): Double {
        val w = 2 * PI * freqHz / sampleRate
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
        val mag = sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
        return 20 * log10(mag.coerceAtLeast(1e-12))
    }

    companion object {
        fun design(band: EqBand, sampleRate: Int): Biquad {
            val f = band.freqHz.toDouble().coerceIn(10.0, sampleRate * 0.49)
            val q = band.q.toDouble().coerceIn(0.1, 24.0)
            val a = 10.0.pow(band.gainDb / 40.0)
            val w0 = 2 * PI * f / sampleRate
            val cs = cos(w0)
            val alpha = sin(w0) / (2 * q)
            val sqA = sqrt(a)
            val (b0, b1, b2, a0, a1, a2) = when (band.type) {
                FilterType.PEAK -> Six(1 + alpha * a, -2 * cs, 1 - alpha * a, 1 + alpha / a, -2 * cs, 1 - alpha / a)
                FilterType.LOW_SHELF -> Six(
                    a * ((a + 1) - (a - 1) * cs + 2 * sqA * alpha),
                    2 * a * ((a - 1) - (a + 1) * cs),
                    a * ((a + 1) - (a - 1) * cs - 2 * sqA * alpha),
                    (a + 1) + (a - 1) * cs + 2 * sqA * alpha,
                    -2 * ((a - 1) + (a + 1) * cs),
                    (a + 1) + (a - 1) * cs - 2 * sqA * alpha,
                )
                FilterType.HIGH_SHELF -> Six(
                    a * ((a + 1) + (a - 1) * cs + 2 * sqA * alpha),
                    -2 * a * ((a - 1) + (a + 1) * cs),
                    a * ((a + 1) + (a - 1) * cs - 2 * sqA * alpha),
                    (a + 1) - (a - 1) * cs + 2 * sqA * alpha,
                    2 * ((a - 1) - (a + 1) * cs),
                    (a + 1) - (a - 1) * cs - 2 * sqA * alpha,
                )
                FilterType.LOW_PASS -> Six((1 - cs) / 2, 1 - cs, (1 - cs) / 2, 1 + alpha, -2 * cs, 1 - alpha)
                FilterType.HIGH_PASS -> Six((1 + cs) / 2, -(1 + cs), (1 + cs) / 2, 1 + alpha, -2 * cs, 1 - alpha)
            }
            return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }
    }

    private data class Six(val b0: Double, val b1: Double, val b2: Double, val a0: Double, val a1: Double, val a2: Double)
}

enum class ReplayGainMode(val label: String) { OFF("Off"), ALBUM("Album"), TRACK("Track") }

/** Everything the sound engine does, as the user set it. */
data class SoundSettings(
    /** Pure mode: no processing at all, no crackle, no speed effects; bit-perfect to a USB DAC where supported. */
    val pure: Boolean = false,
    /** Keep 24-bit and high sample rates intact in a 32-bit float path instead of mixing down to 16-bit. */
    val hiResOutput: Boolean = true,
    val eqEnabled: Boolean = false,
    val graphic: List<Float> = List(GRAPHIC_FREQS.size) { 0f },
    val parametric: List<EqBand> = emptyList(),
    val parametricPreampDb: Float? = null,
    val useParametric: Boolean = false,
    val presetName: String = "Flat",
    val replayGain: ReplayGainMode = ReplayGainMode.ALBUM,
    val limiter: Boolean = true,
    /** A gentle low lift and high roll-off, like a warm cartridge. */
    val warmth: Boolean = false,
) {
    /** The filters that actually run, in order. Empty when the EQ is off or in Pure mode. */
    fun activeBands(): List<EqBand> {
        if (pure) return emptyList()
        val eq = when {
            !eqEnabled -> emptyList()
            useParametric -> parametric.filter { it.enabled }
            else -> graphic.mapIndexedNotNull { i, g ->
                if (abs(g) < 0.05f) null else EqBand(FilterType.PEAK, GRAPHIC_FREQS[i], g, GRAPHIC_Q)
            }
        }
        val warm = if (warmth) listOf(
            EqBand(FilterType.LOW_SHELF, 110f, 1.5f, 0.7f),
            EqBand(FilterType.HIGH_SHELF, 9_000f, -2f, 0.7f),
        ) else emptyList()
        return eq + warm
    }

    /** Preamp in dB: AutoEq's own value when it gave one, otherwise just enough to cancel the largest boost. */
    fun preampDb(sampleRate: Int = 48_000): Float {
        if (pure) return 0f
        if (eqEnabled && useParametric && parametricPreampDb != null) return parametricPreampDb + (if (warmth) -1.5f else 0f)
        val peak = maxBoostDb(activeBands(), sampleRate)
        return -peak.coerceAtLeast(0f)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("pure", pure); put("hiRes", hiResOutput); put("eq", eqEnabled)
        put("graphic", JSONArray(graphic.map { it.toDouble() }))
        put("parametric", JSONArray().apply {
            parametric.forEach { b ->
                put(JSONObject().put("t", b.type.name).put("f", b.freqHz.toDouble()).put("g", b.gainDb.toDouble()).put("q", b.q.toDouble()).put("on", b.enabled))
            }
        })
        parametricPreampDb?.let { put("pPre", it.toDouble()) }
        put("useP", useParametric); put("preset", presetName); put("rg", replayGain.name)
        put("limiter", limiter); put("warmth", warmth)
    }

    companion object {
        val GRAPHIC_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
        val GRAPHIC_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        const val GRAPHIC_Q = 1.41f
        const val MAX_GAIN_DB = 12f

        fun maxBoostDb(bands: List<EqBand>, sampleRate: Int): Float {
            if (bands.isEmpty()) return 0f
            val filters = bands.map { Biquad.design(it, sampleRate) }
            var max = -100.0
            var f = 20.0
            while (f < 20_000.0) {
                val db = filters.sumOf { it.responseDb(f, sampleRate) }
                if (db > max) max = db
                f *= 1.03
            }
            return max.toFloat()
        }

        fun fromJson(o: JSONObject): SoundSettings {
            val g = o.optJSONArray("graphic")
            val p = o.optJSONArray("parametric")
            return SoundSettings(
                pure = o.optBoolean("pure"),
                hiResOutput = o.optBoolean("hiRes", true),
                eqEnabled = o.optBoolean("eq"),
                graphic = if (g != null && g.length() == GRAPHIC_FREQS.size) List(g.length()) { g.getDouble(it).toFloat() } else List(GRAPHIC_FREQS.size) { 0f },
                parametric = if (p == null) emptyList() else List(p.length()) {
                    val b = p.getJSONObject(it)
                    EqBand(FilterType.valueOf(b.getString("t")), b.getDouble("f").toFloat(), b.getDouble("g").toFloat(), b.getDouble("q").toFloat(), b.optBoolean("on", true))
                },
                parametricPreampDb = if (o.has("pPre")) o.getDouble("pPre").toFloat() else null,
                useParametric = o.optBoolean("useP"),
                presetName = o.optString("preset", "Flat"),
                replayGain = runCatching { ReplayGainMode.valueOf(o.optString("rg", "ALBUM")) }.getOrDefault(ReplayGainMode.ALBUM),
                limiter = o.optBoolean("limiter", true),
                warmth = o.optBoolean("warmth"),
            )
        }
    }
}

/** Starting points for the graphic EQ, in dB for 31 Hz … 16 kHz. */
object Presets {
    val all: List<Pair<String, List<Float>>> = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Warm vinyl" to listOf(2f, 2.5f, 1.5f, 0.5f, 0f, 0f, -0.5f, -1f, -2f, -3f),
        "Bass" to listOf(5f, 4.5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Vocal" to listOf(-2f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 2.5f, 1.5f, 0f, -0.5f),
        "Bright" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 1.5f, 2.5f, 3.5f, 3.5f),
        "Loudness" to listOf(4.5f, 3.5f, 1.5f, 0f, -1f, -0.5f, 0f, 1f, 3f, 4f),
        "Late night" to listOf(-3f, -2f, -1f, 0f, 0.5f, 1f, 1f, 0f, -1f, -2f),
    )
}

/**
 * Reads AutoEq "ParametricEQ.txt" files (autoeq.app), e.g.
 * `Preamp: -6.4 dB` and `Filter 1: ON PK Fc 105 Hz Gain -2.3 dB Q 0.70`.
 */
object AutoEq {
    data class Result(val preampDb: Float?, val bands: List<EqBand>)

    private val filterLine = Regex(
        """Filter\s*\d*\s*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val preampLine = Regex("""Preamp\s*:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)

    fun parse(text: String): Result? {
        val preamp = preampLine.find(text)?.groupValues?.get(1)?.toFloatOrNull()
        val bands = filterLine.findAll(text).mapNotNull { m ->
            val on = m.groupValues[1].equals("ON", ignoreCase = true)
            val type = when (m.groupValues[2].uppercase()) {
                "PK", "PEQ" -> FilterType.PEAK
                "LS", "LSC", "LSQ" -> FilterType.LOW_SHELF
                "HS", "HSC", "HSQ" -> FilterType.HIGH_SHELF
                "LP", "LPQ" -> FilterType.LOW_PASS
                "HP", "HPQ" -> FilterType.HIGH_PASS
                else -> return@mapNotNull null
            }
            val f = m.groupValues[3].toFloatOrNull() ?: return@mapNotNull null
            val g = m.groupValues[4].toFloatOrNull() ?: 0f
            val q = m.groupValues[5].toFloatOrNull() ?: 0.707f
            EqBand(type, f, g, q, on)
        }.toList()
        if (bands.isEmpty() && preamp == null) return null
        return Result(preamp, bands)
    }
}
