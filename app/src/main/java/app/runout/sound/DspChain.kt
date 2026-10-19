package app.runout.sound

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * The processing for one stream, all in 32-bit float: gain (ReplayGain + preamp), the EQ filters
 * and a lookahead peak limiter. Built for a sample rate and channel count; rebuilt when settings change.
 */
class DspChain(
    val sampleRate: Int,
    val channels: Int,
    bands: List<EqBand>,
    gainDb: Float,
    private val limiterOn: Boolean,
    val bypass: Boolean,
) {
    private val gain = 10.0.pow(gainDb / 20.0)
    private val filters = bands.map { Biquad.design(it, sampleRate) }.toTypedArray()

    // Transposed direct form II state, per filter per channel.
    private val z1 = Array(filters.size) { DoubleArray(channels) }
    private val z2 = Array(filters.size) { DoubleArray(channels) }

    // Lookahead limiter: 1.5 ms of delay so the gain comes down before a peak arrives.
    private val ceiling = 10.0.pow(-0.3 / 20.0)
    private val lookahead = (sampleRate * 0.0015).toInt().coerceAtLeast(1)
    private val delay = DoubleArray(lookahead * channels)
    private var delayPos = 0
    private val needQueue = DoubleArray(lookahead + 1)
    private val needIndex = LongArray(lookahead + 1)
    private var qHead = 0
    private var qSize = 0
    private var frameIndex = 0L
    private var currentGain = 1.0
    private val release = 1.0 - exp(-1.0 / (sampleRate * 0.08))

    /** Whether this chain changes the samples at all. When false, audio passes through untouched. */
    val isIdentity: Boolean = bypass || (filters.isEmpty() && abs(gainDb) < 0.001f && !limiterOn)

    var framesProcessed = 0L
        private set

    fun reset() {
        z1.forEach { it.fill(0.0) }
        z2.forEach { it.fill(0.0) }
        delay.fill(0.0)
        delayPos = 0
        qHead = 0; qSize = 0
        currentGain = 1.0
    }

    /** Processes [frames] interleaved frames of [data] in place. */
    fun process(data: FloatArray, frames: Int) {
        framesProcessed += frames
        if (isIdentity) return
        val ch = channels
        var i = 0
        for (f in 0 until frames) {
            var peak = 0.0
            for (c in 0 until ch) {
                var x = data[i + c] * gain
                for (k in filters.indices) {
                    val b = filters[k]
                    val y = b.b0 * x + z1[k][c]
                    z1[k][c] = b.b1 * x - b.a1 * y + z2[k][c]
                    z2[k][c] = b.b2 * x - b.a2 * y
                    x = y
                }
                if (limiterOn) {
                    // Swap the new sample into the delay line; the delayed one comes out below.
                    val slot = delayPos * ch + c
                    val out = delay[slot]
                    delay[slot] = x
                    data[i + c] = out.toFloat()
                    val ax = abs(x)
                    if (ax > peak) peak = ax
                } else {
                    data[i + c] = x.toFloat()
                }
            }
            if (limiterOn) {
                val need = if (peak > ceiling) ceiling / peak else 1.0
                pushNeed(need)
                val target = windowMin()
                currentGain = if (target < currentGain) target else currentGain + (target - currentGain) * release
                // The frame that just left the delay line gets the gain for the window it sits in.
                for (c in 0 until ch) data[i + c] = (data[i + c] * currentGain).toFloat()
                delayPos = (delayPos + 1) % lookahead
                frameIndex++
            }
            i += ch
        }
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

}
