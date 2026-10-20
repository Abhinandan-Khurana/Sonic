package app.runout.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

enum class BitPerfect(val label: String) {
    OFF("Off"),
    NO_USB("Plug in a USB DAC for bit-perfect"),
    UNSUPPORTED("This phone or DAC doesn't offer bit-perfect"),
    ON("Bit-perfect to USB DAC"),
}

/** What's actually reaching the speakers, shown in the Sound panel. */
data class OutputStatus(
    val sourceBits: Int = 0,
    val sampleRate: Int = 0,
    val floatPath: Boolean = false,
    val processing: Boolean = false,
    val bitPerfect: BitPerfect = BitPerfect.OFF,
) {
    val label: String
        get() = if (sampleRate == 0) "Nothing playing" else buildString {
            append(if (sourceBits > 0) "$sourceBits-bit" else "PCM")
            append(" · ")
            append(app.runout.model.AudioQuality.formatRate(sampleRate))
            append(if (floatPath) " → 32-bit float" else " → 16-bit")
            if (!processing) append(" · untouched")
        }
}

/**
 * The app's sound settings and the live state shared between the UI and the player.
 * The audio thread reads [settings] and [version] to rebuild its [DspChain] when anything changes.
 */
class SoundEngine(private val context: Context?) {

    private val prefs = context?.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    private val _status = MutableStateFlow(OutputStatus())
    val status: StateFlow<OutputStatus> = _status.asStateFlow()

    @Volatile var version: Int = 0
        private set

    @Volatile private var albumGainDb: Float? = null
    @Volatile private var trackGainDb: Float? = null

    /** Frames that went through the processing chain. Lets tests prove the audio path is live. */
    val framesProcessed = AtomicLong()

    fun update(transform: (SoundSettings) -> SoundSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs?.edit()?.putString(KEY, next.toJson().toString())?.apply()
        version++
        val st = _status.value
        if (st.sampleRate > 0) applyPure(st.sampleRate)
    }

    /** Called when a new track starts, with its ReplayGain values. */
    fun setGains(albumDb: Float?, trackDb: Float?) {
        // Also guard cached/imported metadata, which does not necessarily pass through TagReader.
        val album = albumDb?.takeIf { it.isFinite() }?.coerceIn(-24f, 12f)
        val track = trackDb?.takeIf { it.isFinite() }?.coerceIn(-24f, 12f)
        if (album == albumGainDb && track == trackGainDb) return
        albumGainDb = album
        trackGainDb = track
        version++
    }

    fun replayGainDb(s: SoundSettings = _settings.value): Float = when (s.replayGain) {
        ReplayGainMode.OFF -> 0f
        ReplayGainMode.ALBUM -> albumGainDb ?: trackGainDb ?: 0f
        ReplayGainMode.TRACK -> trackGainDb ?: albumGainDb ?: 0f
    }

    fun buildChain(sampleRate: Int, channels: Int): DspChain {
        val s = _settings.value
        val gain = if (s.pure) 0f else replayGainDb(s) + s.preampDb(sampleRate)
        return DspChain(sampleRate, channels, s.activeBands(), gain, limiterOn = s.limiter && !s.pure, bypass = s.pure)
    }

    /** The player has started a stream in this format. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun onStreamConfigured(format: Format, floatPath: Boolean) {
        val bits = when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
        _status.value = OutputStatus(bits, format.sampleRate, floatPath, processing = !_settings.value.pure)
        applyPure(format.sampleRate)
    }

    private fun applyPure(sampleRate: Int) {
        val ctx = context ?: return
        val state = runCatching { PureOutput.apply(ctx, _settings.value.pure, sampleRate) }.getOrDefault(BitPerfect.UNSUPPORTED)
        _status.value = _status.value.copy(bitPerfect = state, processing = !_settings.value.pure)
    }

    fun applyPreset(name: String) {
        val gains = Presets.all.firstOrNull { it.first == name }?.second ?: return
        update { it.copy(graphic = gains, presetName = name, useParametric = false, eqEnabled = true) }
    }

    fun importAutoEq(text: String): Boolean {
        val r = AutoEq.parse(text) ?: return false
        update { it.copy(parametric = r.bands, parametricPreampDb = r.preampDb, useParametric = true, eqEnabled = true, presetName = "AutoEq") }
        return true
    }

    private fun load(): SoundSettings = runCatching {
        prefs?.getString(KEY, null)?.let { SoundSettings.fromJson(JSONObject(it)) }
    }.getOrNull() ?: SoundSettings()

    private companion object {
        const val KEY = "settings"
    }
}

/** Android 14+ bit-perfect output to USB DACs, when the phone and the DAC both support it. */
object PureOutput {
    fun apply(context: Context, enable: Boolean, sampleRate: Int): BitPerfect {
        if (Build.VERSION.SDK_INT < 34) return if (enable) BitPerfect.UNSUPPORTED else BitPerfect.OFF
        val am = context.getSystemService(AudioManager::class.java) ?: return BitPerfect.UNSUPPORTED
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val usb = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (usb == null) return if (enable) BitPerfect.NO_USB else BitPerfect.OFF
        if (!enable) {
            runCatching { am.clearPreferredMixerAttributes(attrs, usb) }
            return BitPerfect.OFF
        }
        val options = runCatching { am.getSupportedMixerAttributes(usb) }.getOrDefault(emptyList())
        val bitPerfect = options.filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        val match = bitPerfect.firstOrNull { it.format.sampleRate == sampleRate } ?: bitPerfect.firstOrNull()
            ?: return BitPerfect.UNSUPPORTED
        return if (runCatching { am.setPreferredMixerAttributes(attrs, usb, match) }.getOrDefault(false)) BitPerfect.ON else BitPerfect.UNSUPPORTED
    }
}
