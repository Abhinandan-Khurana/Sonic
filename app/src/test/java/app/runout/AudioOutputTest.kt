package app.runout

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import app.runout.playback.Synth
import app.runout.sound.DspAudioSink
import app.runout.sound.PlayerFactory
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Suite 5 — the hi-res output path. Media3 skips plug-in processors for float audio, so Runout puts its
 * own sink in front of Android's. These tests feed real 16-bit and 24-bit PCM through it, and play the
 * fixture files end to end through ExoPlayer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AudioOutputTest {

    private val context: Context get() = Fixtures.context

    /** Stands in for the phone's audio output: accepts float PCM and records exactly what it was given. */
    class CapturingOutput(context: Context, private val consumeInHalves: Boolean = false) :
        ForwardingAudioSink(DefaultAudioSink.Builder(context).build()) {
        var format: Format? = null
        var lastFormat: Format? = null
        val bytes = ByteArrayOutputStream()
        private var frames = 0L
        private var ended = false
        private var half = false

        override fun getFormatSupport(format: Format): Int =
            if (format.pcmEncoding == C.ENCODING_PCM_FLOAT || format.pcmEncoding == C.ENCODING_PCM_16BIT) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            else AudioSink.SINK_FORMAT_UNSUPPORTED

        override fun supportsFormat(format: Format) = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

        override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
            format = inputFormat
            lastFormat = inputFormat
        }

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            val f = format ?: return false
            var n = buffer.remaining()
            if (consumeInHalves && !half && n > 8) { n = (n / 2) / 4 * 4; half = true } else half = false
            val chunk = ByteArray(n)
            buffer.get(chunk)
            bytes.write(chunk)
            frames += n / (4 * f.channelCount)
            return !buffer.hasRemaining()
        }

        override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
            val f = format ?: return AudioSink.CURRENT_POSITION_NOT_SET
            return frames * 1_000_000L / f.sampleRate
        }

        override fun playToEndOfStream() { ended = true }
        override fun isEnded() = ended
        override fun hasPendingData() = false
        override fun play() {}
        override fun pause() {}
        override fun flush() { ended = false }
        override fun reset() { format = null }

        fun floats(): FloatArray {
            val b = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.nativeOrder()).asFloatBuffer()
            return FloatArray(b.remaining()).also { b.get(it) }
        }
    }

    private fun pcmFormat(encoding: Int, rate: Int, channels: Int = 1) = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(encoding).setSampleRate(rate).setChannelCount(channels).build()

    private fun pcm24(vararg v: Float): ByteBuffer {
        val b = ByteBuffer.allocateDirect(v.size * 3).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { x ->
            val i = (x * 8_388_608f).toInt().coerceIn(-8_388_608, 8_388_607)
            b.put((i and 0xFF).toByte()); b.put((i shr 8 and 0xFF).toByte()); b.put((i shr 16 and 0xFF).toByte())
        }
        b.flip(); return b
    }

    private fun pcm16(vararg v: Float): ByteBuffer {
        val b = ByteBuffer.allocateDirect(v.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { b.putShort((it * 32768f).toInt().coerceIn(-32768, 32767).toShort()) }
        b.flip(); return b
    }

    private fun neutral() = SoundEngine(null).apply { update { it.copy(limiter = false) } }

    @Test
    fun `24-bit 96 kHz reaches the output as 32-bit float at 96 kHz, sample for sample`() {
        val out = CapturingOutput(context)
        val engine = neutral()
        val sink = DspAudioSink(out, engine, floatOutput = true)
        val input = floatArrayOf(0.5f, -0.25f, 0.125f, 0.9999f, -1f, 1f / 8_388_608f)
        sink.configure(pcmFormat(C.ENCODING_PCM_24BIT, 96_000), 0, null)
        assertTrue(sink.handleBuffer(pcm24(*input), 0, 1))
        assertEquals(C.ENCODING_PCM_FLOAT, out.format!!.pcmEncoding)
        assertEquals(96_000, out.format!!.sampleRate)
        val got = out.floats()
        assertEquals(input.size, got.size)
        input.indices.forEach { assertEquals(input[it], got[it], 1.5f / 8_388_608f) }
        // The smallest 24-bit step survives: nothing was folded down to 16-bit.
        assertTrue(got[5] != 0f)
        assertEquals("24-bit · 96 kHz → 32-bit float", engine.status.value.label)
    }

    @Test
    fun `16-bit files go through the same path`() {
        val out = CapturingOutput(context)
        val sink = DspAudioSink(out, neutral(), floatOutput = true)
        sink.configure(pcmFormat(C.ENCODING_PCM_16BIT, 44_100, channels = 2), 0, null)
        assertTrue(sink.handleBuffer(pcm16(0.5f, -0.5f, 0.25f, -0.25f), 0, 1))
        assertEquals(44_100, out.format!!.sampleRate)
        assertEquals(2, out.format!!.channelCount)
        val got = out.floats()
        assertEquals(listOf(0.5f, -0.5f, 0.25f, -0.25f), got.toList())
    }

    @Test
    fun `ReplayGain and EQ act on the audio, and Pure mode turns them off`() {
        val engine = neutral()
        engine.update { it.copy(replayGain = ReplayGainMode.TRACK) }
        engine.setGains(null, -6.0206f) // half amplitude
        val out = CapturingOutput(context)
        val sink = DspAudioSink(out, engine, floatOutput = true)
        sink.configure(pcmFormat(C.ENCODING_PCM_24BIT, 96_000), 0, null)
        sink.handleBuffer(pcm24(0.5f, -0.5f, 0.8f), 0, 1)
        out.floats().zip(listOf(0.25f, -0.25f, 0.4f)).forEach { (a, b) -> assertEquals(b, a, 1e-4f) }

        engine.update { it.copy(pure = true, eqEnabled = true, graphic = List(10) { 6f }) }
        out.bytes.reset()
        sink.handleBuffer(pcm24(0.5f, -0.5f, 0.8f), 1000, 1)
        out.floats().zip(listOf(0.5f, -0.5f, 0.8f)).forEach { (a, b) -> assertEquals(b, a, 1e-6f) }
    }

    @Test
    fun `when Android takes a buffer in pieces, nothing is lost or processed twice`() {
        val engine = neutral()
        val out = CapturingOutput(context, consumeInHalves = true)
        val sink = DspAudioSink(out, engine, floatOutput = true)
        sink.configure(pcmFormat(C.ENCODING_PCM_24BIT, 48_000), 0, null)
        val input = FloatArray(64) { (it - 32) / 64f }
        val buf = pcm24(*input)
        assertFalse(sink.handleBuffer(buf, 0, 1)) // half taken
        assertTrue(sink.handleBuffer(buf, 0, 1)) // the rest
        assertEquals(64L, engine.framesProcessed.get())
        val got = out.floats()
        assertEquals(64, got.size)
        input.indices.forEach { assertEquals(input[it], got[it], 1e-6f) }
    }

    @Test
    fun `hi-res files play end to end through ExoPlayer and the sound engine`() {
        for ((file, rate, bits) in listOf(Triple("tones/tone_24_96.wav", 96_000, 24), Triple("tones/tone_16_44.wav", 44_100, 16))) {
            val engine = neutral()
            var output: CapturingOutput? = null
            val player = PlayerFactory.create(context, engine, handleAudioFocus = false, clock = FakeClock(true)) { ctx, _ ->
                CapturingOutput(ctx).also { output = it }
            }
            val local = File(context.cacheDir, File(file).name).also { Fixtures.file(file).copyTo(it, overwrite = true) }
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(local)))
            player.prepare()
            player.play()
            TestPlayerRunHelper.run(player).untilState(Player.STATE_ENDED)
            player.release()

            val status = engine.status.value
            assertEquals(rate, status.sampleRate)
            assertEquals(bits, status.sourceBits)
            assertTrue(status.floatPath)
            // One second of audio went through the processing chain and out as float.
            assertEquals(rate.toLong(), engine.framesProcessed.get())
            val out = output!!.floats()
            assertEquals(rate, out.size)
            assertEquals(C.ENCODING_PCM_FLOAT, output!!.lastFormat!!.pcmEncoding)
            assertTrue("a real tone came through", out.maxOf { abs(it) } > 0.1f)
        }
    }

    @Test
    fun `turntable sounds render at the right length without clipping`() {
        val drop = Synth.needleDrop()
        assertEquals((0.9 * Synth.RATE).toInt(), drop.size)
        assertTrue(drop.any { abs(it.toInt()) > 1000 })
        val loop = Synth.crackleLoop()
        assertEquals(4 * Synth.RATE, loop.size)
        assertTrue(loop.count { abs(it.toInt()) > 500 } > 20)
    }
}
