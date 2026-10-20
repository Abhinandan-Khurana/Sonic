package app.runout

import app.runout.library.TagReader
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundEngine
import org.junit.Assert.*
import org.junit.Test

class Slice0ReplayGainTest {
    @Test fun `tag gains reject nonfinite values clamp extremes and accept db suffixes`() {
        for (text in listOf("NaN", "Infinity", "+Infinity", "-Infinity", "NaN dB")) {
            assertNull(text, TagReader.gain(text))
        }
        assertEquals(12f, TagReader.gain("+60 dB")!!, 0f)
        assertEquals(-24f, TagReader.gain("-90 DB")!!, 0f)
        for (suffix in listOf("dB", "DB", "db")) assertEquals(-5.1f, TagReader.gain(" -5.1 $suffix ")!!, 0f)
    }

    @Test fun `cached or imported gains cannot bypass playback safety`() {
        val engine = SoundEngine(null)
        engine.update { it.copy(replayGain = ReplayGainMode.ALBUM, limiter = false) }
        engine.setGains(Float.NaN, 6f)
        assertEquals(6f, engine.replayGainDb(), 0f)
        engine.setGains(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        assertEquals(0f, engine.replayGainDb(), 0f)
        engine.setGains(60f, -100f)
        assertEquals(12f, engine.replayGainDb(), 0f)
        engine.update { it.copy(replayGain = ReplayGainMode.TRACK) }
        assertEquals(-24f, engine.replayGainDb(), 0f)
        val data = floatArrayOf(0.25f, -0.25f)
        engine.buildChain(48_000, 2).process(data, 1)
        assertTrue(data.all { it.isFinite() })
    }
}
