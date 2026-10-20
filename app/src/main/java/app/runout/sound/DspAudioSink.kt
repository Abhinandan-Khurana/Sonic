package app.runout.sound

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sits in front of Android's audio output and runs the [DspChain] on every PCM buffer in 32-bit float.
 *
 * Media3 only applies plug-in audio processors in its 16-bit path, so an EQ added the usual way
 * would switch itself off for hi-res files. Doing the work here keeps the EQ, ReplayGain and the
 * limiter active for every file, and hands the output to Android as float, so 24-bit and high
 * sample rates survive when hi-res output is on.
 */
@OptIn(UnstableApi::class)
class DspAudioSink(
    private val delegate: AudioSink,
    private val engine: SoundEngine,
    private val floatOutput: Boolean,
) : ForwardingAudioSink(delegate) {

    private var processing = false
    private var inEncoding = C.ENCODING_INVALID
    private var channels = 0
    private var sampleRate = 0
    private var chain: DspChain? = null
    private var chainVersion = -1

    private var pending: ByteBuffer? = null
    private var pendingPtsUs = 0L
    private var out: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    private var scratch = FloatArray(0)

    override fun getFormatSupport(format: Format): Int =
        if (canProcess(format) && !engine.settings.value.pure) delegate.getFormatSupport(asFloat(format)) else delegate.getFormatSupport(format)

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        if (canProcess(inputFormat) && !engine.settings.value.pure) {
            processing = true
            inEncoding = inputFormat.pcmEncoding
            channels = inputFormat.channelCount
            sampleRate = inputFormat.sampleRate
            chain = null
            val inBytes = bytesPerSample(inEncoding)
            val size = if (specifiedBufferSize > 0) specifiedBufferSize / inBytes * 4 else 0
            delegate.configure(asFloat(inputFormat), size, outputChannels)
            engine.onStreamConfigured(inputFormat, floatPath = floatOutput)
        } else {
            processing = false
            delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
            if (canProcess(inputFormat)) engine.onStreamConfigured(inputFormat, floatPath = false)
        }
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!processing) return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        var p = pending
        if (p == null) {
            p = process(buffer)
            pending = p
            pendingPtsUs = presentationTimeUs
        }
        val done = delegate.handleBuffer(p, pendingPtsUs, encodedAccessUnitCount)
        if (done) pending = null
        return done
    }

    override fun flush() {
        pending = null
        chain?.reset()
        super.flush()
    }

    override fun reset() {
        pending = null
        chain = null
        super.reset()
    }

    private fun process(input: ByteBuffer): ByteBuffer {
        val src = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val inBytes = bytesPerSample(inEncoding)
        val samples = src.remaining() / inBytes
        val frames = if (channels > 0) samples / channels else 0
        val n = frames * channels
        if (scratch.size < n) scratch = FloatArray(n)
        for (i in 0 until n) {
            scratch[i] = when (inEncoding) {
                C.ENCODING_PCM_16BIT -> src.short / 32768f
                C.ENCODING_PCM_24BIT -> {
                    val b0 = src.get().toInt() and 0xFF
                    val b1 = src.get().toInt() and 0xFF
                    val b2 = src.get().toInt()
                    ((b2 shl 16) or (b1 shl 8) or b0) / 8_388_608f
                }
                C.ENCODING_PCM_32BIT -> src.int / 2_147_483_648f
                else -> src.float
            }
        }
        // Mark the input as consumed.
        input.position(input.limit())

        val v = engine.version
        var c = chain
        if (c == null || chainVersion != v) {
            c = engine.buildChain(sampleRate, channels)
            chain = c
            chainVersion = v
        }
        c.process(scratch, frames)
        engine.framesProcessed.addAndGet(frames.toLong())

        val bytes = n * 4
        if (out.capacity() < bytes) out = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        out.clear()
        out.asFloatBuffer().put(scratch, 0, n)
        out.position(0)
        out.limit(bytes)
        return out
    }

    companion object {
        fun canProcess(f: Format): Boolean =
            MimeTypes.AUDIO_RAW == f.sampleMimeType && f.pcmEncoding in setOf(
                C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT,
            )

        fun asFloat(f: Format): Format = f.buildUpon().setPcmEncoding(C.ENCODING_PCM_FLOAT).build()

        fun bytesPerSample(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            else -> 4
        }
    }
}
