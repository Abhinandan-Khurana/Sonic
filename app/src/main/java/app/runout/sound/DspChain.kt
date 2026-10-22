package app.runout.sound

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * The processing for one stream, all in 32-bit float: gain (ReplayGain + preamp), the EQ filters
 * and a lookahead peak limiter. Built for a sample rate and channel count; rebuilt when settings
 * change, and [carryFrom] hands it the old chain's state so a rebuild never drops or clicks.
 */
class DspChain(
    val sampleRate: Int,
    val channels: Int,
    bands: List<EqBand>,
    gainDb: Float,
    val limiterOn: Boolean,
    val bypass: Boolean,
) {
    private val gain = 10.0.pow(gainDb / 20.0)
    private val filters = bands.map { Biquad.design(it, sampleRate) }.toTypedArray()

    // The gain actually applied: it glides to [gain] in about 10 ms after a change, so a new
    // ReplayGain or preamp never steps.
    private var gainNow = gain
    private val glide = 1.0 - exp(-1.0 / (sampleRate * 0.002))

    // Transposed direct form II state, per filter per channel.
    private val z1 = Array(filters.size) { DoubleArray(channels) }
    private val z2 = Array(filters.size) { DoubleArray(channels) }

    // Lookahead limiter: 1.5 ms of delay so the gain comes down before a peak arrives.
    private val ceiling = 10.0.pow(-0.3 / 20.0)
    val lookahead = (sampleRate * 0.0015).toInt().coerceAtLeast(1)
    private val delay = DoubleArray(lookahead * channels)
    private var delayPos = 0
    private val needQueue = DoubleArray(lookahead + 1)
    private val needIndex = LongArray(lookahead + 1)
    private var qHead = 0
    private var qSize = 0
    private var frameIndex = 0L
    // The held minimum is smoothed by a box average over the lookahead, so the gain ramps down
    // and still reaches the peak's gain exactly as the peak leaves the delay line.
    private val box = DoubleArray(lookahead) { 1.0 }
    private var boxPos = 0
    private var boxSum = lookahead.toDouble()
    private var currentGain = 1.0
    private val release = 1.0 - exp(-1.0 / (sampleRate * 0.08))

    // When the limiter switches in or out, the delay comes or goes over one lookahead of crossfade.
    private var fadeLeft = 0
    private var fadeIn = false

    /** Whether this chain changes the samples at all. When false, audio passes through untouched. */
    val isIdentity: Boolean = bypass || (filters.isEmpty() && abs(gainDb) < 0.001f && !limiterOn)

    /** True while a carried-over gain or delay is still settling: even an identity chain works until then. */
    private val settling: Boolean get() = fadeLeft > 0 || gainNow != gain

    /** Nothing in this chain will change the next samples: they can go out exactly as they came. */
    fun passesThrough(): Boolean = isIdentity && !settling

    var framesProcessed = 0L
        private set

    /**
     * Takes over [old]'s running state (filter memory, the limiter's delay line and gains), so the
     * music runs on without a gap when a new track starts or a setting changes mid-song.
     */
    fun carryFrom(old: DspChain) {
        if (bypass || old.bypass || old.sampleRate != sampleRate || old.channels != channels || old.lookahead != lookahead) return
        if (old.filters.size == filters.size) {
            for (k in filters.indices) { old.z1[k].copyInto(z1[k]); old.z2[k].copyInto(z2[k]) }
        }
        gainNow = old.gainNow
        old.delay.copyInto(delay)
        delayPos = old.delayPos
        currentGain = old.currentGain
        if (limiterOn && old.limiterOn) {
            old.needQueue.copyInto(needQueue); old.needIndex.copyInto(needIndex)
            qHead = old.qHead; qSize = old.qSize; frameIndex = old.frameIndex
            old.box.copyInto(box); boxPos = old.boxPos; boxSum = old.boxSum
            fadeLeft = old.fadeLeft; fadeIn = old.fadeIn
        } else if (limiterOn != old.limiterOn) {
            fadeLeft = lookahead
            fadeIn = limiterOn
            if (limiterOn) { delay.fill(0.0); currentGain = 1.0 }
        }
        snapGain()
    }

    fun reset() {
        z1.forEach { it.fill(0.0) }
        z2.forEach { it.fill(0.0) }
        delay.fill(0.0)
        delayPos = 0
        qHead = 0; qSize = 0
        box.fill(1.0); boxPos = 0; boxSum = lookahead.toDouble()
        currentGain = 1.0
        gainNow = gain
        fadeLeft = 0
    }

    /** Processes [frames] interleaved frames of [data] in place. */
    fun process(data: FloatArray, frames: Int) {
        framesProcessed += frames
        if (isIdentity && !settling) return
        val ch = channels
        var i = 0
        for (f in 0 until frames) {
            val delayed = limiterOn || (fadeLeft > 0 && !fadeIn)
            if (gainNow != gain) { gainNow += (gain - gainNow) * glide; snapGain() }
            // Where the crossfade between the delayed and the direct path stands (1 = all delayed).
            val w = if (fadeLeft > 0) {
                val t = 1.0 - fadeLeft.toDouble() / lookahead
                if (fadeIn) t else 1.0 - t
            } else 1.0
            var peak = 0.0
            for (c in 0 until ch) {
                var x = data[i + c] * gainNow
                for (k in filters.indices) {
                    val b = filters[k]
                    val y = b.b0 * x + z1[k][c]
                    z1[k][c] = flush(b.b1 * x - b.a1 * y + z2[k][c])
                    z2[k][c] = flush(b.b2 * x - b.a2 * y)
                    x = y
                }
                if (delayed) {
                    // Swap the new sample into the delay line; the delayed one comes out below.
                    val slot = delayPos * ch + c
                    val out = delay[slot]
                    delay[slot] = x
                    data[i + c] = out.toFloat()
                    if (fadeLeft > 0) scratch[c] = x
                    val ax = abs(x)
                    if (ax > peak) peak = ax
                } else {
                    data[i + c] = x.toFloat()
                }
            }
            if (delayed) {
                if (limiterOn) {
                    pushNeed(if (peak > ceiling) ceiling / peak else 1.0)
                    val held = windowMin()
                    boxSum += held - box[boxPos]
                    box[boxPos] = held
                    boxPos = (boxPos + 1) % lookahead
                    if (boxPos == 0) boxSum = box.sum() // keep the running sum from drifting
                    val target = boxSum / lookahead
                    currentGain = if (target < currentGain) target else currentGain + (target - currentGain) * release
                    frameIndex++
                } else {
                    // The limiter has just been switched off: its last gain lifts back to unity.
                    currentGain += (1.0 - currentGain) * release
                }
                // The frame that just left the delay line gets the gain for the window it sits in.
                for (c in 0 until ch) {
                    val wet = data[i + c] * currentGain
                    data[i + c] = (if (fadeLeft > 0) wet * w + scratch[c] * (1.0 - w) else wet).toFloat()
                }
                delayPos = (delayPos + 1) % lookahead
            }
            if (fadeLeft > 0) fadeLeft--
            i += ch
        }
    }

    private val scratch = DoubleArray(channels)

    private fun snapGain() {
        if (abs(gainNow - gain) < 1e-7) gainNow = gain
    }

    // Monotonic queue of the smallest gain needed over the lookahead window.
    private fun pushNeed(need: Double) {
        val cap = needQueue.size
        // Drop entries that have slid out of the window, then keep the queue increasing.
        while (qSize > 0 && needIndex[qHead] <= frameIndex - (lookahead + 1)) {
            qHead = (qHead + 1) % cap
            qSize--
        }
        while (qSize > 0) {
            val lastIdx = (qHead + qSize - 1) % cap
            if (needQueue[lastIdx] >= need) qSize-- else break
        }
        val at = (qHead + qSize) % cap
        needQueue[at] = need
        needIndex[at] = frameIndex
        qSize++
    }

    private fun windowMin(): Double = if (qSize == 0) 1.0 else needQueue[qHead]

    private companion object {
        /** Filter memory decaying through silence would turn subnormal, which is slow on some CPUs. */
        fun flush(z: Double): Double = if (abs(z) < 1e-30) 0.0 else z
    }
}
