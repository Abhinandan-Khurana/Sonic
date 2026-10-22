package app.runout

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import app.runout.library.AudioInfo
import app.runout.library.FlacPolicy
import app.runout.library.LibraryScanner
import app.runout.library.TagReader
import app.runout.sound.BitPerfect
import app.runout.sound.DspAudioSink
import app.runout.sound.DspChain
import app.runout.sound.EqBand
import app.runout.sound.FilterType
import app.runout.sound.OutputDevice
import app.runout.sound.OutputDevices
import app.runout.sound.OutputKind
import app.runout.sound.OutputStatus
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundEngine
import app.runout.sound.SoundSettings
import app.runout.sound.StreamFormat
import app.runout.ui.signalPath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Suite 5b — the audio path tells the truth and keeps the music whole: the file's own bit depth,
 * exact 16-bit output, dither only where it's due, gapless chains, ReplayGain on the first sample,
 * an honest verdict, and FLAC this phone can't decode kept off the shelf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AudioHonestyTest {

    private val context: Context get() = Fixtures.context

    private fun pcm(encoding: Int, rate: Int, channels: Int = 2, metadata: Metadata? = null) = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(encoding).setSampleRate(rate).setChannelCount(channels)
        .setMetadata(metadata).build()

    /** The compressed FLAC stream as the extractor describes it: its encoding is the file's depth. */
    private fun flac(bits: Int, rate: Int) = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_FLAC).setPcmEncoding(if (bits == 24) C.ENCODING_PCM_24BIT else C.ENCODING_PCM_16BIT)
        .setSampleRate(rate).setChannelCount(2).build()

    private fun shorts(vararg v: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(v.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { b.putShort(it.toShort()) }
        b.flip(); return b
    }

    private fun floats(vararg v: Float): ByteBuffer {
        val b = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder())
        v.forEach { b.putFloat(it) }
        b.flip(); return b
    }

    private fun AudioOutputTest.CapturingOutput.shorts(): IntArray {
        val b = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.nativeOrder()).asShortBuffer()
        return IntArray(b.remaining()) { b.get(it).toInt() }
    }

    private fun clean() = SoundEngine(null).apply { update { it.copy(replayGain = ReplayGainMode.OFF) } }

    // ------------------------------------------------------------------ hi-res off: exact 16-bit

    @Test fun `hi-res off, nothing on, a 16-bit file leaves as its own bytes`() {
        val out = AudioOutputTest.CapturingOutput(context)
        val sink = DspAudioSink(out, clean(), floatOutput = false)
        sink.configure(pcm(C.ENCODING_PCM_16BIT, 44_100), 0, null)
        assertEquals(C.ENCODING_PCM_16BIT, out.format!!.pcmEncoding)
        val input = intArrayOf(0, 1, -1, 2, -2, 100, -100, 32767, -32768, 12345)
        assertTrue(sink.handleBuffer(shorts(*input), 0, 1))
        // Media3's own float → 16-bit step made these 0, 0, 0, 1, -1, 99, -99, 32766, -32767, 12344.
        assertArrayEquals(input, out.shorts())
    }

    @Test fun `hi-res off, float from a 16-bit file rounds back to the very same samples, undithered`() {
        val out = AudioOutputTest.CapturingOutput(context)
        val sink = DspAudioSink(out, clean(), floatOutput = false)
        sink.onSourceFormat(flac(16, 44_100))
        // The decoder keeps full precision: float is taken directly.
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(pcm(C.ENCODING_PCM_FLOAT, 44_100)))
        sink.configure(pcm(C.ENCODING_PCM_FLOAT, 44_100), 0, null)
        val input = intArrayOf(1, -1, 7, -32768, 32767, 4242)
        sink.handleBuffer(floats(*FloatArray(input.size) { input[it] / 32768f }), 0, 1)
        assertArrayEquals(input, out.shorts())
    }

    @Test fun `hi-res off, 24-bit is rounded to 16-bit once, with TPDF dither, at the right scale`() {
        val engine = clean()
        val out = AudioOutputTest.CapturingOutput(context)
        val sink = DspAudioSink(out, engine, floatOutput = false)
        sink.onSourceFormat(flac(24, 96_000))
        sink.configure(pcm(C.ENCODING_PCM_FLOAT, 96_000), 0, null)
        val n = 20_000
        val x = FloatArray(n) { (0.25 * sin(2 * PI * 997 * (it / 2) / 96_000.0)).toFloat() + 0.3f / 32768f }
        sink.handleBuffer(floats(*x), 0, 1)
        val y = out.shorts()
        // Never more than a step off, centred on the true value (no 32767 scale, no truncation).
        var err = 0.0
        for (i in 0 until n) {
            val d = y[i] - x[i] * 32768.0
            assertTrue("sample $i off by $d", abs(d) <= 1.5)
            err += d
        }
        assertEquals(0.0, err / n, 0.05)
        assertTrue("dither is present", (0 until n).any { y[it] != (x[it] * 32768.0).roundToInt() })
        assertTrue(engine.status.value.dithered)
        assertEquals(24, engine.status.value.sourceBits)
        assertFalse(engine.status.value.floatPath)
    }

    // ------------------------------------------------------------------ Pure

    @Test fun `Pure asks the decoder for 16-bit samples from a 16-bit file, and float from a 24-bit one`() {
        val engine = SoundEngine(null).apply { update { it.copy(pure = true) } }
        val out = AudioOutputTest.CapturingOutput(context)
        val sink = DspAudioSink(out, engine, floatOutput = true)
        val float = pcm(C.ENCODING_PCM_FLOAT, 44_100)
        sink.onSourceFormat(flac(16, 44_100))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING, sink.getFormatSupport(float))
        sink.onSourceFormat(flac(24, 96_000))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, sink.getFormatSupport(pcm(C.ENCODING_PCM_FLOAT, 96_000)))
        // 24-bit in Pure keeps every bit as float; it is untouched, not cut to 16-bit.
        sink.configure(pcm(C.ENCODING_PCM_FLOAT, 96_000), 0, null)
        assertEquals(C.ENCODING_PCM_FLOAT, out.format!!.pcmEncoding)
        val input = floatArrayOf(0.5f, 1f / 8_388_608f, -0.25f, 3f / 8_388_608f)
        sink.handleBuffer(floats(*input), 0, 1)
        assertArrayEquals(input, out.floats(), 0f)
        val st = engine.status.value
        assertEquals(24, st.sourceBits)
        assertTrue(st.floatPath)
        assertFalse(st.processing)
        val path = signalPath(st.copy(device = OutputDevice(OutputKind.USB, "DAC")), engine.settings.value)!!
        assertEquals("Pure", path.runout.title)
        assertTrue(path.runout.ok)
        assertFalse("not bit-perfect unless Android confirmed an exact mode", path.bitPerfect)
    }

    @Test fun `bit-perfect counts only the file's own samples`() {
        fun st(file: Int, decoded: Int, enc: Int) = StreamFormat(file, decoded, 44_100, 2, enc, deep = file > 16)
        assertTrue(SoundEngine.samplesExact(st(16, 16, C.ENCODING_PCM_16BIT)))
        assertTrue(SoundEngine.samplesExact(st(16, 32, C.ENCODING_PCM_16BIT)))
        assertTrue(SoundEngine.samplesExact(st(24, 32, C.ENCODING_PCM_FLOAT)))
        assertFalse("24-bit cut to 16", SoundEngine.samplesExact(st(24, 32, C.ENCODING_PCM_16BIT)))
        assertFalse("Android 9's decoder truncated it", SoundEngine.samplesExact(st(24, 16, C.ENCODING_PCM_FLOAT)))
        assertFalse("float can't hold 32-bit", SoundEngine.samplesExact(st(32, 32, C.ENCODING_PCM_FLOAT)))
        assertFalse(SoundEngine.samplesExact(st(0, 32, C.ENCODING_PCM_FLOAT)))
    }

    // ------------------------------------------------------------------ the file's depth

    @Test fun `a CD FLAC decoded to float still reads as 16-bit, never 32-bit or hi-res`() {
        val engine = clean()
        val sink = DspAudioSink(AudioOutputTest.CapturingOutput(context), engine, floatOutput = true)
        sink.onSourceFormat(flac(16, 44_100))
        sink.configure(pcm(C.ENCODING_PCM_FLOAT, 44_100), 0, null)
        val st = engine.status.value
        assertEquals(16, st.sourceBits)
        assertEquals(32, st.decodedBits)
        assertEquals("16-bit · 44.1 kHz → 32-bit float · untouched", st.label)
        val path = signalPath(st.copy(device = OutputDevice(OutputKind.SPEAKER, mixerRate = 48_000)), engine.settings.value)!!
        assertEquals("16-bit · 44.1 kHz", path.track.spec)
        assertEquals("CD-quality lossless", path.track.detail)
        assertEquals("16/44.1 → Phone speaker", path.short)
        assertNull(path.note)
    }

    @Test fun `a 24-bit file the decoder cut to 16-bit says so`() {
        val o = OutputStatus(24, 96_000, floatPath = false, decodedBits = 16, device = OutputDevice(OutputKind.WIRED, mixerRate = 48_000))
        val path = signalPath(o, SoundSettings(pure = true))!!
        assertEquals("Cut to 16-bit", path.verdict)
        assertFalse(path.runout.ok)
        assertTrue(path.runout.detail.contains("decoder"))
    }

    // ------------------------------------------------------------------ gapless and settings

    /** Plays [x] through a sink in buffers, reconfiguring (a new track) or changing settings at [cut]. */
    private fun run(x: FloatArray, cut: Int, engine: SoundEngine, between: (DspAudioSink) -> Unit): FloatArray {
        val out = AudioOutputTest.CapturingOutput(context)
        val sink = DspAudioSink(out, engine, floatOutput = true)
        sink.configure(pcm(C.ENCODING_PCM_FLOAT, 44_100), 0, null)
        sink.handleBuffer(floats(*x.copyOfRange(0, cut)), 0, 1)
        between(sink)
        sink.handleBuffer(floats(*x.copyOfRange(cut, x.size)), 1, 1)
        return out.floats()
    }

    private fun tone(frames: Int) = FloatArray(frames * 2) { (0.3 * sin(2 * PI * 440 * (it / 2) / 44_100.0)).toFloat() }

    @Test fun `a gapless track change keeps every sample, no 1_5 ms lost, no silence inserted`() {
        val eq = { SoundEngine(null).apply { update { it.copy(replayGain = ReplayGainMode.OFF, eqEnabled = true, graphic = List(10) { i -> if (i == 3) 4f else 0f }) } } }
        val x = tone(4_000)
        val whole = run(x, 2_000 * 2, eq()) { }
        val joined = run(x, 2_000 * 2, eq()) { it.configure(pcm(C.ENCODING_PCM_FLOAT, 44_100), 0, null) }
        assertEquals(whole.size, joined.size)
        // The join is seamless: the same samples as one long track.
        whole.indices.forEach { assertEquals("sample $it", whole[it], joined[it], 1e-6f) }
    }

    @Test fun `a settings change mid-song neither drops audio nor clicks`() {
        val engine = SoundEngine(null).apply { update { it.copy(replayGain = ReplayGainMode.OFF, eqEnabled = true, graphic = List(10) { i -> if (i == 3) 4f else 0f }) } }
        val x = tone(4_000)
        val y = run(x, 2_000 * 2, engine) { engine.update { it.copy(graphic = List(10) { i -> if (i == 3) 5f else 0f }) } }
        assertEquals(x.size, y.size)
        // No run of silence at the change (the old rebuild inserted 66 zero frames)...
        val zeros = (2_000 * 2 until 2_100 * 2).count { y[it] == 0f }
        assertTrue("$zeros zero samples", zeros < 4)
        // ...and no step: the largest sample-to-sample jump stays that of the tone itself.
        val jump = (2_000 * 2 - 40 until 2_100 * 2 step 2).maxOf { abs(y[it + 2] - y[it]) }
        assertTrue("jump $jump", jump < 0.03f)
    }

    @Test fun `ReplayGain comes from the track's own tags on its first sample`() {
        val engine = SoundEngine(null).apply { update { it.copy(replayGain = ReplayGainMode.TRACK, limiter = false) } }
        val out = AudioOutputTest.CapturingOutput(context)
        val sink = DspAudioSink(out, engine, floatOutput = true)
        val tags = Metadata(VorbisComment("REPLAYGAIN_TRACK_GAIN", "-6.0206 dB"), VorbisComment("TITLE", "Opening"))
        sink.configure(pcm(C.ENCODING_PCM_FLOAT, 44_100, metadata = tags), 0, null)
        sink.handleBuffer(floats(0.5f, -0.5f), 0, 1)
        out.floats().zip(listOf(0.25f, -0.25f)).forEach { (a, b) -> assertEquals(b, a, 1e-4f) }
        // The media item's values arrive later and don't override the stream's own.
        engine.setItemGains(null, 0f)
        assertEquals(-6.0206f, engine.replayGainDb(), 1e-4f)
    }

    // ------------------------------------------------------------------ the limiter

    @Test fun `by default a full-scale master passes untouched, the limiter waits for a lift`() {
        val engine = SoundEngine(null) // limiter on, flat, no ReplayGain tags
        val chain = engine.buildChain(44_100, 2)
        assertFalse(chain.limiterOn)
        assertTrue(chain.isIdentity)
        val x = floatArrayOf(1f, -1f, 0.1f, -0.1f, 0.999f, -0.999f)
        val y = x.copyOf(); chain.process(y, 3)
        assertArrayEquals(x, y, 0f)
        engine.setGains(null, 3f)
        assertTrue(engine.buildChain(44_100, 2).limiterOn)
        engine.update { it.copy(warmth = true) }
        engine.setGains(null, -3f)
        assertTrue("filters can overshoot, so they bring it in", engine.buildChain(44_100, 2).limiterOn)
    }

    @Test fun `the limiter ramps its gain down before a peak instead of stepping`() {
        val rate = 48_000
        val chain = DspChain(rate, 1, emptyList(), 0f, limiterOn = true, bypass = false)
        val n = 4_000
        val x = FloatArray(n) { if (it == 2_000) 1f else 0.5f }
        val y = x.copyOf(); chain.process(y, n)
        val l = chain.lookahead
        val gain = DoubleArray(n - l) { y[it + l] / x[it].toDouble() }
        val ceiling = 10.0.pow(-0.3 / 20)
        assertTrue("peak held", y.maxOf { abs(it) } <= ceiling + 1e-6)
        val step = (1 until gain.size).maxOf { abs(gain[it] - gain[it - 1]) }
        // The full reduction is 1 − 0.966; one step would be all of it at once.
        assertTrue("largest gain step $step", step < (1 - ceiling) / 10)
    }

    // ------------------------------------------------------------------ outputs and the verdict

    @Test fun `every Bluetooth link is lossy, and a DAC that isn't playing never reads bit-perfect`() {
        for (type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 23, 26, 27, 30)) {
            assertEquals("type $type", OutputKind.BLUETOOTH, OutputDevices.kindOf(type))
        }
        // Pure set up on a DAC, but media goes to Bluetooth headphones: lossy, red, never green.
        val o = OutputStatus(16, 44_100, bitPerfect = BitPerfect.ON, device = OutputDevice(OutputKind.BLUETOOTH, "Buds", type = 30))
        val path = signalPath(o, SoundSettings(pure = true))!!
        assertEquals("Lossy over Bluetooth", path.verdict)
        assertFalse(path.bitPerfect)
        assertTrue(path.lossy)
        assertTrue(path.output.detail.contains("LC3"))
        val a2dp = signalPath(OutputStatus(24, 96_000, floatPath = true, device = OutputDevice(OutputKind.BLUETOOTH, "Buds", type = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)), SoundSettings())!!
        assertEquals("Lossy · resampled by Android", a2dp.output.spec)
        assertTrue(a2dp.output.detail.contains("agreed by the phone and headphones"))
        // An HDMI or cast output isn't claimed as lossless.
        val other = signalPath(OutputStatus(16, 44_100, device = OutputDevice(OutputKind.OTHER)), SoundSettings())!!
        assertEquals("Through Android's mixer", other.verdict)
    }

    @Test fun `processing, ReplayGain and crackle are listed, and the verdict says the sound was changed`() {
        val speaker = OutputDevice(OutputKind.SPEAKER, mixerRate = 44_100)
        val plain = signalPath(OutputStatus(16, 44_100, floatPath = true, device = speaker), SoundSettings())!!
        assertEquals("Clean", plain.runout.title)
        assertEquals("Lossless", plain.verdict)
        // ReplayGain only counts when it does something; crackle is mixed in, so it counts too.
        val crackle = signalPath(OutputStatus(16, 44_100, floatPath = true, device = speaker, crackle = true), SoundSettings())!!
        assertEquals("Processing", crackle.runout.title)
        assertTrue(crackle.runout.detail.contains("Crackle"))
        assertEquals("Lossless source, processed", crackle.verdict)
        val gained = signalPath(OutputStatus(16, 44_100, floatPath = true, device = speaker, replayGainDb = -4f, limiting = false), SoundSettings())!!
        assertTrue(gained.runout.detail.startsWith("ReplayGain"))
        assertFalse(gained.runout.detail.contains("limiter"))
        // Pure leaves crackle out.
        assertEquals("Lossless", signalPath(OutputStatus(16, 44_100, device = speaker, crackle = true), SoundSettings(pure = true))!!.verdict)
    }

    // ------------------------------------------------------------------ what this phone can decode

    @Test fun `FLAC Android can't decode is named, by bit depth and by Android version`() {
        fun info(bits: Int, rate: Int = 44_100) = AudioInfo("FLAC", bits = bits, sampleRate = rate)
        assertNull(FlacPolicy.unplayableReason(info(16), sdk = 28))
        assertNull(FlacPolicy.unplayableReason(info(24, 192_000), sdk = 28))
        assertTrue(FlacPolicy.unplayableReason(info(20), sdk = 36)!!.startsWith("20-bit FLAC"))
        assertNotNull(FlacPolicy.unplayableReason(info(12), sdk = 36))
        assertNotNull(FlacPolicy.unplayableReason(info(32), sdk = 28))
        assertNull(FlacPolicy.unplayableReason(info(32), sdk = 29))
        assertTrue(FlacPolicy.unplayableReason(info(24, 384_000), sdk = 30)!!.contains("384 kHz"))
        assertNull(FlacPolicy.unplayableReason(info(24, 384_000), sdk = 31))
    }

    @Test fun `the scanner keeps a 20-bit FLAC off the shelf and says why`() = runBlocking {
        val flac = Fixtures.bytes("Music/Vela Park - Night Drive (2023)/01 Opening.flac")
        // STREAMINFO bits per sample: 24 → 20 (the low nibble is the sample count; it stays).
        val twenty = flac.copyOf().also { it[21] = ((it[21].toInt() and 0x0F) or 0x30).toByte() }
        assertEquals(20, FlacPolicy.read(twenty.inputStream())!!.bits)
        val dir = File(context.cacheDir, "twenty-bit").apply { deleteRecursively(); mkdirs() }
        File(dir, "01 Studio.flac").writeBytes(twenty)
        val tree = FolderProvider.install(dir)
        val found = mutableListOf<app.runout.library.ScannedAlbum>()
        val report = LibraryScanner(context).scan(tree, onAlbum = { found += it })
        assertTrue(found.isEmpty())
        assertEquals(1, report.unreadable)
        assertTrue(report.unreadableFiles.single().reason.contains("20-bit FLAC isn't supported"))
    }

    @Test fun `ReplayGain tags written with a decimal comma are read`() {
        assertEquals(-6.5f, TagReader.gain("-6,50 dB")!!, 1e-4f)
        assertEquals(2.25f, TagReader.gain("+2.25 dB")!!, 1e-4f)
    }

    @Test fun `filter memory never decays into subnormals`() {
        val chain = DspChain(48_000, 1, listOf(EqBand(FilterType.PEAK, 100f, 6f, 4f)), -6f, limiterOn = false, bypass = false)
        val x = FloatArray(200_000) { if (it < 10) 1f else 0f }
        chain.process(x, x.size)
        assertTrue(x.takeLast(1_000).all { it == 0f || abs(it) >= java.lang.Float.MIN_NORMAL })
    }
}
