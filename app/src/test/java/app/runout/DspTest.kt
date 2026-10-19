package app.runout

import app.runout.sound.AutoEq
import app.runout.sound.Biquad
import app.runout.sound.DspChain
import app.runout.sound.EqBand
import app.runout.sound.FilterType
import app.runout.sound.Presets
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundEngine
import app.runout.sound.SoundSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Suite 4 — the sound processing: EQ filters, headroom, ReplayGain, limiter and Pure mode, measured on real signals. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DspTest {

    private fun sine(freq: Double, rate: Int, seconds: Double, amp: Double = 0.25, channels: Int = 2): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * channels) { i -> (amp * sin(2 * PI * freq * (i / channels) / rate)).toFloat() }
    }

    private fun rms(x: FloatArray, from: Int = x.size / 2): Double = sqrt(x.drop(from).sumOf { it.toDouble() * it } / (x.size - from))
    private fun db(ratio: Double) = 20 * log10(ratio)

    /** Gain in dB a chain applies to a sine, measured after it settles. */
    private fun measuredGainDb(chain: DspChain, freq: Double): Double {
        val x = sine(freq, chain.sampleRate, 0.5, channels = chain.channels)
        val y = x.copyOf()
        chain.process(y, y.size / chain.channels)
        return db(rms(y) / rms(x))
    }

    @Test
    fun `peak filter boosts its centre and leaves the rest alone`() {
        val f = Biquad.design(EqBand(FilterType.PEAK, 1_000f, 6f, 1.41f), 48_000)
        assertEquals(6.0, f.responseDb(1_000.0, 48_000), 0.05)
        assertEquals(0.0, f.responseDb(50.0, 48_000), 0.2)
        assertEquals(0.0, f.responseDb(15_000.0, 48_000), 0.2)
    }

    @Test
    fun `shelves and passes have the right shape`() {
        val low = Biquad.design(EqBand(FilterType.LOW_SHELF, 200f, 4f, 0.707f), 44_100)
        assertEquals(4.0, low.responseDb(30.0, 44_100), 0.3)
        assertEquals(0.0, low.responseDb(8_000.0, 44_100), 0.2)
        val high = Biquad.design(EqBand(FilterType.HIGH_SHELF, 6_000f, -3f, 0.707f), 96_000)
        assertEquals(-3.0, high.responseDb(30_000.0, 96_000), 0.3)
        val hp = Biquad.design(EqBand(FilterType.HIGH_PASS, 100f, 0f, 0.707f), 48_000)
        assertTrue(hp.responseDb(20.0, 48_000) < -20)
        assertEquals(0.0, hp.responseDb(2_000.0, 48_000), 0.1)
    }

    @Test
    fun `the chain applies the EQ to actual audio at hi-res rates`() {
        for (rate in listOf(44_100, 96_000, 192_000)) {
            val chain = DspChain(rate, 2, listOf(EqBand(FilterType.PEAK, 1_000f, 6f, 1.41f)), 0f, limiterOn = false, bypass = false)
            assertEquals("at $rate Hz", 6.0, measuredGainDb(chain, 1_000.0), 0.15)
            val flatElsewhere = DspChain(rate, 2, listOf(EqBand(FilterType.PEAK, 1_000f, 6f, 1.41f)), 0f, limiterOn = false, bypass = false)
            assertEquals(0.0, measuredGainDb(flatElsewhere, 10_000.0), 0.3)
        }
    }

    @Test
    fun `graphic EQ boosts get automatic headroom so nothing clips`() {
        val s = SoundSettings(eqEnabled = true, graphic = List(10) { if (it == 5) 12f else 0f }, limiter = false)
        val pre = s.preampDb(48_000)
        assertEquals(-12.0, pre.toDouble(), 0.6)
        val chain = DspChain(48_000, 2, s.activeBands(), pre, limiterOn = false, bypass = false)
        val y = sine(1_000.0, 48_000, 0.5, amp = 0.9)
        chain.process(y, y.size / 2)
        assertTrue("peak ${y.maxOf { abs(it) }}", y.maxOf { abs(it) } <= 1.0f)
        // A flat EQ needs no headroom.
        assertEquals(0f, SoundSettings(eqEnabled = true).preampDb(48_000), 0.01f)
    }

    @Test
    fun `the limiter holds peaks under -0_3 dBFS`() {
        val chain = DspChain(48_000, 2, emptyList(), 12f, limiterOn = true, bypass = false)
        val y = sine(440.0, 48_000, 1.0, amp = 0.9)
        chain.process(y, y.size / 2)
        val ceiling = 10.0.pow(-0.3 / 20).toFloat()
        assertTrue("peak ${y.maxOf { abs(it) }}", y.maxOf { abs(it) } <= ceiling + 1e-4f)
        // ... and still plays loud, it doesn't just mute.
        assertTrue(rms(y) > 0.5)
    }

    @Test
    fun `Pure mode and a flat chain leave the samples bit-identical`() {
        val x = sine(997.0, 96_000, 0.2, amp = 0.5)
        val bypass = DspChain(96_000, 2, listOf(EqBand(FilterType.PEAK, 1_000f, 9f, 1f)), -6f, limiterOn = true, bypass = true)
        val y = x.copyOf(); bypass.process(y, y.size / 2)
        assertTrue(x.contentEquals(y))
        val flat = DspChain(96_000, 2, emptyList(), 0f, limiterOn = false, bypass = false)
        assertTrue(flat.isIdentity)
        val z = x.copyOf(); flat.process(z, z.size / 2)
        assertTrue(x.contentEquals(z))
        assertEquals((x.size / 2).toLong(), flat.framesProcessed)
    }

    @Test
    fun `ReplayGain uses album or track gain from the tags`() {
        val engine = SoundEngine(null)
        engine.setGains(-6.2f, -5.1f)
        assertEquals(-6.2f, engine.replayGainDb(), 0.001f)
        engine.update { it.copy(replayGain = ReplayGainMode.TRACK) }
        assertEquals(-5.1f, engine.replayGainDb(), 0.001f)
        engine.update { it.copy(replayGain = ReplayGainMode.OFF) }
        assertEquals(0f, engine.replayGainDb(), 0.001f)
        engine.update { it.copy(replayGain = ReplayGainMode.ALBUM, limiter = false) }
        assertEquals(-6.2, measuredGainDb(engine.buildChain(44_100, 2), 1_000.0), 0.05)
        // Pure ignores it.
        engine.update { it.copy(pure = true) }
        assertTrue(engine.buildChain(44_100, 2).bypass)
    }

    @Test
    fun `every settings change bumps the version the audio thread watches`() {
        val engine = SoundEngine(null)
        val v0 = engine.version
        engine.applyPreset("Warm vinyl")
        assertEquals(v0 + 1, engine.version)
        assertEquals("Warm vinyl", engine.settings.value.presetName)
        assertTrue(engine.settings.value.eqEnabled)
        engine.update { it } // no change, no bump
        assertEquals(v0 + 1, engine.version)
        engine.setGains(-1f, null)
        assertEquals(v0 + 2, engine.version)
    }

    @Test
    fun `AutoEq headphone profiles import`() {
        val text = """
            Preamp: -6.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
            Filter 2: ON PK Fc 3200 Hz Gain -2.3 dB Q 1.41
            Filter 3: OFF PK Fc 6000 Hz Gain 4.0 dB Q 2.00
            Filter 4: ON HSC Fc 10000 Hz Gain -1.5 dB Q 0.70
        """.trimIndent()
        val r = AutoEq.parse(text)!!
        assertEquals(-6.4f, r.preampDb!!, 0.001f)
        assertEquals(4, r.bands.size)
        assertEquals(listOf(FilterType.LOW_SHELF, FilterType.PEAK, FilterType.PEAK, FilterType.HIGH_SHELF), r.bands.map { it.type })
        assertFalse(r.bands[2].enabled)
        assertNull(AutoEq.parse("hello"))
        val engine = SoundEngine(null)
        assertTrue(engine.importAutoEq(text))
        assertTrue(engine.settings.value.useParametric)
        assertEquals(-6.4f, engine.settings.value.preampDb(48_000), 0.001f)
    }

    @Test
    fun `sound settings and presets are well formed and survive a restart`() {
        assertTrue(Presets.all.all { (_, g) -> g.size == 10 && g.all { abs(it) <= SoundSettings.MAX_GAIN_DB } })
        assertEquals("Flat", Presets.all.first().first)
        val s = SoundSettings(
            pure = true, hiResOutput = false, eqEnabled = true, graphic = List(10) { it - 5f },
            parametric = listOf(EqBand(FilterType.PEAK, 3_200f, -2.3f, 1.41f, enabled = false)), parametricPreampDb = -6.4f,
            useParametric = true, presetName = "AutoEq", replayGain = ReplayGainMode.TRACK, limiter = false, warmth = true,
        )
        assertEquals(s, SoundSettings.fromJson(JSONObject(s.toJson().toString())))
        assertNotNull(SoundSettings.fromJson(JSONObject("{}")))
        // Warmth adds its two gentle shelves.
        assertEquals(2, SoundSettings(warmth = true).activeBands().size)
    }
}
