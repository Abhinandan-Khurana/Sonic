package app.runout.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turntable sounds, synthesised in code (no sampled audio to license): the needle landing,
 * the lift, the bed of surface crackle, and the soft thump of the run-out groove.
 * These play on their own audio track, mixed by Android with the music.
 */
class VinylSfx(private val scope: CoroutineScope) {

    var crackleLevel: Float = 0.5f
    var crackleEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) stopCrackle()
        }

    private var crackleTrack: AudioTrack? = null

    // Rendered once and kept: synthesising them is too much work for the main thread every time.
    private val needleDropPcm by lazy { Synth.needleDrop() }
    private val liftPcm by lazy { Synth.lift() }
    private val runoutPcm by lazy { Synth.runout() }
    private val platterDropPcm by lazy { Synth.platterDrop() }
    private val cracklePcm by lazy { Synth.crackleLoop() }

    fun needleDrop() = playOnce(needleDropPcm)

    fun lift() = playOnce(liftPcm)

    fun runout() = playOnce(runoutPcm)

    fun platterDrop() = playOnce(platterDropPcm)

    fun startCrackle() {
        if (!crackleEnabled || crackleTrack != null) return
        val pcm = cracklePcm
        val track = buildTrack(pcm.size, AudioTrack.MODE_STATIC) ?: return
        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size, -1)
        track.setVolume(0.35f * crackleLevel)
        runCatching { track.play() }
        crackleTrack = track
    }

    fun stopCrackle() {
        crackleTrack?.let { t ->
            runCatching { t.stop() }
            t.release()
        }
        crackleTrack = null
    }

    private fun playOnce(pcm: ShortArray) {
        scope.launch(Dispatchers.IO) {
            val track = buildTrack(pcm.size, AudioTrack.MODE_STATIC) ?: return@launch
            track.write(pcm, 0, pcm.size)
            runCatching { track.play() }
            delay(pcm.size * 1000L / Synth.RATE + 200)
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun buildTrack(samples: Int, mode: Int): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Synth.RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(mode)
            .setBufferSizeInBytes(samples * 2)
            .build()
    }.getOrNull()
}

/** Pure functions that render the sounds. Kept separate so they can be unit tested. */
object Synth {
    const val RATE = 44_100

    private fun buffer(seconds: Double) = DoubleArray((seconds * RATE).toInt())

    /** Random clicks and pops with a faint hiss: the sound of a record's surface. */
    private fun addCrackle(buf: DoubleArray, rng: Random, popsPerSecond: Double, popGain: Double, hissGain: Double) {
        var lp = 0.0
        for (i in buf.indices) {
            val white = rng.nextDouble() * 2 - 1
            lp += 0.08 * (white - lp)
            buf[i] += lp * hissGain
        }
        val pops = (buf.size / RATE.toDouble() * popsPerSecond).toInt()
        repeat(pops) {
            val at = rng.nextInt(buf.size)
            val amp = popGain * (0.25 + rng.nextDouble() * 0.75) * if (rng.nextDouble() < 0.06) 2.2 else 1.0
            val decay = 0.00035 + rng.nextDouble() * 0.0009
            val sign = if (rng.nextBoolean()) 1 else -1
            var j = 0
            while (at + j < buf.size && j < RATE / 100) {
                val t = j / RATE.toDouble()
                val ring = if (j % 2 == 0) 1.0 else -0.6
                buf[at + j] += sign * amp * exp(-t / decay) * ring
                j++
            }
        }
    }

    private fun addThump(buf: DoubleArray, at: Int, freq: Double, gain: Double, decaySec: Double) {
        var j = 0
        while (at + j < buf.size && j < (decaySec * 6 * RATE)) {
            val t = j / RATE.toDouble()
            val attack = (t / 0.003).coerceAtMost(1.0)
            buf[at + j] += gain * attack * exp(-t / decaySec) * sin(2 * PI * freq * t)
            j++
        }
    }

    private fun addClick(buf: DoubleArray, at: Int, rng: Random, gain: Double) {
        for (j in 0 until (0.004 * RATE).toInt()) {
            if (at + j >= buf.size) break
            val t = j / RATE.toDouble()
            buf[at + j] += gain * (rng.nextDouble() * 2 - 1) * exp(-t / 0.0008)
        }
    }

    private fun toPcm(buf: DoubleArray, fadeOutSec: Double = 0.0): ShortArray {
        val fade = (fadeOutSec * RATE).toInt()
        return ShortArray(buf.size) { i ->
            val f = if (fade > 0 && i > buf.size - fade) (buf.size - i) / fade.toDouble() else 1.0
            (buf[i] * f).coerceIn(-1.0, 1.0).times(Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun needleDrop(seed: Int = 7): ShortArray {
        val rng = Random(seed)
        val buf = buffer(0.9)
        addClick(buf, 0, rng, 0.35)
        addThump(buf, 0, 52.0, 0.55, 0.05)
        addCrackle(buf, rng, popsPerSecond = 26.0, popGain = 0.16, hissGain = 0.012)
        return toPcm(buf, fadeOutSec = 0.35)
    }

    fun lift(seed: Int = 11): ShortArray {
        val rng = Random(seed)
        val buf = buffer(0.25)
        addClick(buf, 0, rng, 0.22)
        addThump(buf, 0, 70.0, 0.18, 0.03)
        addCrackle(buf, rng, popsPerSecond = 12.0, popGain = 0.08, hissGain = 0.008)
        return toPcm(buf, fadeOutSec = 0.15)
    }

    /** A record seating on the spindle and platter mat: a soft, low thud with a faint tick. */
    fun platterDrop(seed: Int = 31): ShortArray {
        val rng = Random(seed)
        val buf = buffer(0.32)
        addClick(buf, 0, rng, 0.08)
        addThump(buf, 0, 46.0, 0.5, 0.06)
        addThump(buf, (0.012 * RATE).toInt(), 92.0, 0.12, 0.025)
        return toPcm(buf, fadeOutSec = 0.12)
    }

    /** Two turns of the run-out groove at 33⅓ rpm (1.8 s a turn). */
    fun runout(seed: Int = 23): ShortArray {
        val rng = Random(seed)
        val buf = buffer(3.9)
        addCrackle(buf, rng, popsPerSecond = 14.0, popGain = 0.07, hissGain = 0.01)
        listOf(0.15, 1.95).forEach { s ->
            val at = (s * RATE).toInt()
            addClick(buf, at, rng, 0.12)
            addThump(buf, at, 45.0, 0.28, 0.06)
        }
        return toPcm(buf, fadeOutSec = 0.6)
    }

    /** Four seconds of surface noise that loops seamlessly under the music. */
    fun crackleLoop(seed: Int = 3): ShortArray {
        val rng = Random(seed)
        val buf = buffer(4.0)
        addCrackle(buf, rng, popsPerSecond = 9.0, popGain = 0.11, hissGain = 0.006)
        return toPcm(buf)
    }
}
