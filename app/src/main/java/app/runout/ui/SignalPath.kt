package app.runout.ui

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.model.AudioQuality
import app.runout.sound.BitPerfect
import app.runout.sound.OutputKind
import app.runout.sound.OutputStatus
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundSettings

/**
 * The way the music travels, in three steps like a hi-fi signal chain: the file on the record, what
 * Runout does to it, and what the phone sends out. Each step has a short spec, so you can see where
 * quality is kept and where it's changed.
 */
data class SignalPath(
    val track: Step,
    val runout: Step,
    val output: Step,
    val verdict: String,
    val short: String,
    val note: String?,
    /** The file's own samples reach the DAC: the verdict's lamp is green. */
    val bitPerfect: Boolean = false,
    /** Lossy on the way out (Bluetooth): the lamp is red. */
    val lossy: Boolean = false,
) {
    data class Step(val title: String, val spec: String, val detail: String, val ok: Boolean = true)
}

private fun rate(hz: Int) = AudioQuality.formatRate(hz)

fun signalPath(o: OutputStatus, s: SoundSettings): SignalPath? {
    if (o.sampleRate == 0) return null
    val bits = if (o.sourceBits > 0) "${o.sourceBits}-bit" else "PCM"
    val source = "$bits · ${rate(o.sampleRate)}"
    val hiResFile = o.sourceBits > 16 || o.sampleRate > 48_000
    val track = SignalPath.Step("FLAC", source, if (hiResFile) "Hi-res lossless" else "CD-quality lossless")

    val carried = if (o.floatPath) "32-bit float · ${rate(o.sampleRate)}" else "16-bit · ${rate(o.sampleRate)}"
    val folded = !o.floatPath && o.sourceBits > 16
    val runout = when {
        s.pure -> SignalPath.Step("Pure", carried, "Untouched: no EQ, ReplayGain, crackle or wind-down." + if (folded) " Hi-res output is off, so 24-bit is cut to 16-bit." else "", ok = !folded)
        else -> {
            val work = buildList {
                if (s.eqEnabled) add(if (s.useParametric) "AutoEq" else "EQ ${s.presetName}")
                if (s.replayGain != ReplayGainMode.OFF) add("ReplayGain")
                if (s.warmth) add("warmth")
                if (s.limiter) add("limiter")
            }
            SignalPath.Step(
                if (work.isEmpty()) "Clean" else "Processing",
                carried,
                (if (work.isEmpty()) "Nothing changes the sound." else work.joinToString(", ").replaceFirstChar { it.uppercase() } + " in ${if (o.floatPath) "32-bit float" else "16-bit"}.") +
                    if (folded) " Hi-res output is off, so 24-bit is cut to 16-bit." else "",
                ok = !folded,
            )
        }
    }

    val d = o.device
    val mixer = d.mixerRate
    val resampled = mixer > 0 && mixer != o.sampleRate
    val output = when {
        o.bitPerfect == BitPerfect.ON -> SignalPath.Step(d.label, source, "Bit-perfect: the DAC gets the file's own samples.")
        d.kind == OutputKind.BLUETOOTH -> SignalPath.Step(d.label, "Lossy", "Bluetooth re-encodes the music (SBC, AAC, aptX or LDAC, chosen by the phone), so it isn't lossless here.", ok = false)
        d.kind == OutputKind.USB -> SignalPath.Step(
            d.label,
            if (mixer > 0) "Mixer · ${rate(mixer)}" else "Mixer",
            if (s.pure) o.bitPerfect.label + "." else "Through Android's mixer. Turn on Pure for bit-perfect (Android 14 and later).",
            ok = !resampled,
        )
        else -> SignalPath.Step(
            d.label,
            if (mixer > 0) "Mixer · ${rate(mixer)}" else "Mixer",
            if (resampled) "Android plays this output at ${rate(mixer)}, so ${rate(o.sampleRate)} is resampled on the way out." else "Android's mixer plays it at the file's own rate.",
            ok = !resampled,
        )
    }

    val verdict = when {
        o.bitPerfect == BitPerfect.ON -> "Bit-perfect"
        d.kind == OutputKind.BLUETOOTH -> "Lossy over Bluetooth"
        folded -> "Cut to 16-bit"
        resampled -> "Lossless, resampled to ${rate(mixer)}"
        else -> "Lossless"
    }
    val short = "${o.sourceBits.takeIf { it > 0 }?.let { "$it/" } ?: ""}${rate(o.sampleRate, withUnit = false)} → ${d.kind.label}"
    val note = when {
        o.bitPerfect == BitPerfect.ON -> null
        hiResFile && d.kind != OutputKind.USB -> "For the full ${rate(o.sampleRate)}, play through a USB DAC with Pure on (Android 14 and later)."
        else -> null
    }
    return SignalPath(
        track, runout, output, verdict, short, note,
        bitPerfect = o.bitPerfect == BitPerfect.ON,
        lossy = o.bitPerfect != BitPerfect.ON && d.kind == OutputKind.BLUETOOTH,
    )
}

private fun rate(hz: Int, withUnit: Boolean) = AudioQuality.formatRate(hz, withUnit)

/** The home screen's small readout: "24/96 → Phone speaker", tap for the whole chain. */
@Composable
internal fun SignalChip(path: SignalPath, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val good = path.track.ok && path.runout.ok && path.output.ok
    Row(
        modifier
            .testTag("signal-chip")
            .semantics(mergeDescendants = true) { contentDescription = "Audio quality: ${path.verdict}" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            // Shorter than a full 48 dp key so it fits between Crackle and the deck.
            .heightIn(min = 32.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(if (good) Room.Online else Room.Signal))
        BasicText(path.short, style = Room.LabelQuiet.copy(fontSize = 10.sp, color = Room.Ink), maxLines = 1)
    }
}


/**
 * The whole chain, as on the TE boards: the verdict on a grey strip with its lamp (green
 * bit-perfect, amber lossless but changed, red lossy), then Track → Runout → Output as stations on
 * a black rail, then the tip in blue. [gap] is the space between those three parts (11 dp in
 * Settings › Sound, 16 dp in the Audio quality panel). [onVerdictHelp] adds the "?" to the verdict.
 */
@Composable
internal fun SignalChain(path: SignalPath?, modifier: Modifier = Modifier, gap: Dp = 11.dp, onVerdictHelp: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().testTag("signal-path"), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (path == null) {
            Verdict("nothing playing", Room.Faint, onVerdictHelp)
            BasicText("lower the needle on a record to see how it reaches your ears.", style = Room.LabelQuiet.copy(lineHeight = 15.sp))
            return@Column
        }
        val lamp = when {
            path.bitPerfect -> Room.Online
            path.lossy -> Room.Danger
            else -> Room.Warning
        }
        Verdict(path.verdict.lowercase(), lamp, onVerdictHelp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            StepRow("Track", path.track)
            StepRow("Runout", path.runout)
            StepRow("Output", path.output, last = true)
        }
        path.note?.let { BasicText(it.lowercase(), style = Room.Label.copy(color = Room.Accent, lineHeight = 15.sp)) }
    }
}

/** The verdict strip: #F5F5F5, 11 dp in, a 10 dp lamp, the 12/400 verdict (17 line height) and the TE Help "?". */
@Composable
private fun Verdict(text: String, lamp: Color, onHelp: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Room.Surface).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(10.dp).background(lamp))
        BasicText(text, style = Room.Text.copy(lineHeight = 17.sp), modifier = Modifier.weight(1f).testTag("signal-verdict"))
        if (onHelp != null) HelpSquare("signal verdict", onHelp)
    }
}

/**
 * One station: a 22 dp rail with a 12 dp square node (black, amber where quality changes) and a
 * 1 dp line down to the next, then the key, the 12/700 name, the 10/400 format and the grey detail.
 */
@Composable
private fun StepRow(kicker: String, step: SignalPath.Step, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(12.dp).background(if (step.ok) Room.Ink else Room.Warning))
            if (!last) Box(Modifier.width(1.dp).heightIn(min = 90.dp).weight(1f).background(Room.Ink))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(kicker.lowercase(), style = Room.LabelQuiet)
            BasicText(step.title.lowercase(), style = Room.Strong, maxLines = 1)
            BasicText(step.spec.lowercase(), style = Room.Label, modifier = Modifier.testTag("spec:$kicker"))
            BasicText(step.detail.lowercase(), style = Room.LabelQuiet.copy(lineHeight = 15.sp))
        }
    }
}

/**
 * The Audio quality panel's body, under its head: the chain, the two switches that shape it, and
 * a black key across the width to Settings › Sound. 16 dp between parts.
 */
@Composable
internal fun SignalPanel(state: RoomState, actions: RoomActions, onSoundSettings: () -> Unit) {
    val s = state.sound
    Column(Modifier.fillMaxWidth().testTag("panel:signal"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SignalChain(signalPath(state.output, s), gap = 16.dp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleRow("Pure", "Nothing is done to the sound.", s.pure, { on -> actions.updateSound { it.copy(pure = on) } })
            ToggleRow("Hi-res output", "Keeps 24-bit and high sample rates.", s.hiResOutput, { on -> actions.updateSound { it.copy(hiResOutput = on) } })
        }
        TeButton("open sound settings", onSoundSettings, Modifier.fillMaxWidth().testTag("signal-sound-settings"))
    }
}

/** A small on/off switch with its name, for the home screen's corner. */
@Composable
internal fun MiniToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    val tick = rememberTick()
    Row(
        Modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = "$label ${if (checked) "on" else "off"}" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { tick(); onChange(!checked) }
            .touchHeight()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(label.lowercase(), style = Room.Label.copy(color = if (checked) Room.Ink else Room.Quiet))
        Box(
            Modifier
                .size(26.dp, 15.dp)
                .background(if (checked) Room.Ink else Room.Background, RectangleShape)
                .border(1.dp, if (checked) Room.Ink else Room.Faint, RectangleShape)
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(9.dp).background(if (checked) Room.Background else Room.Faint, RectangleShape))
        }
    }
}
