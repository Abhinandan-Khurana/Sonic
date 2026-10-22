package app.runout.ui

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
data class SignalPath(val track: Step, val runout: Step, val output: Step, val verdict: String, val short: String, val note: String?) {
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
    return SignalPath(track, runout, output, verdict, short, note)
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
        Canvas(Modifier.size(7.dp)) { drawCircle(if (good) Room.Online else Room.Signal) }
        BasicText(path.short, style = Room.LabelQuiet.copy(fontSize = 10.sp, color = Room.Ink), maxLines = 1)
    }
}

/** The whole chain: Track → Runout → Output, each a node on one line, with its spec on the right. */
@Composable
internal fun SignalChain(path: SignalPath?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("signal-path")) {
        if (path == null) {
            BasicText("Nothing playing", style = Room.Title.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Lower the needle on a record to see how it reaches your ears.", style = Room.LabelQuiet, modifier = Modifier.padding(top = 4.dp))
            return@Column
        }
        BasicText(path.verdict, style = Room.Title.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.testTag("signal-verdict"))
        Spacer(Modifier.height(12.dp))
        StepRow("Track", path.track, first = true)
        StepRow("Runout", path.runout)
        StepRow("Output", path.output, last = true)
        path.note?.let { BasicText(it, style = Room.LabelQuiet, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun StepRow(kicker: String, step: SignalPath.Step, first: Boolean = false, last: Boolean = false) {
    val dot = if (step.ok) Room.Online else Room.Signal
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // The wire: a line through every step, with a ring at each.
        Canvas(Modifier.width(18.dp).fillMaxHeight()) {
            val x = size.width / 2f
            val y = 16.dp.toPx()
            val line = Room.Ink.copy(alpha = 0.15f)
            if (!first) drawLine(line, Offset(x, 0f), Offset(x, y), 1.5.dp.toPx())
            if (!last) drawLine(line, Offset(x, y), Offset(x, size.height), 1.5.dp.toPx())
            drawCircle(Color.White, 5.5.dp.toPx(), Offset(x, y))
            drawCircle(dot, 5.5.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
            drawCircle(dot, 2.dp.toPx(), Offset(x, y))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(kicker.uppercase(), style = Room.LabelQuiet.copy(fontSize = 9.sp, letterSpacing = 1.2.sp))
                    BasicText(step.title, style = Room.Title.copy(fontSize = 14.sp), maxLines = 1)
                }
                SpecTag(step.spec, Modifier.testTag("spec:$kicker"))
            }
            BasicText(step.detail, style = Room.LabelQuiet, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** The Audio quality sheet: the chain, then the two switches that shape it, explained. */
@Composable
internal fun SignalPanel(state: RoomState, actions: RoomActions, onSoundSettings: () -> Unit) {
    val s = state.sound
    Column(Modifier.fillMaxWidth().testTag("panel:signal")) {
        SignalChain(signalPath(state.output, s))
        Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(Room.Ink.copy(alpha = 0.08f)))
        ToggleRow(
            "Pure",
            "Nothing is done to the sound.",
            s.pure,
            { on -> actions.updateSound { it.copy(pure = on) } },
            more = "Pure is about processing. It switches off EQ, ReplayGain, the limiter, crackle and the platter wind-down, so the decoded samples pass untouched. " +
                "With a USB DAC on Android 14 and later it also asks Android for bit-perfect output.",
        )
        ToggleRow(
            "Hi-res output",
            "Keeps 24-bit and high sample rates.",
            s.hiResOutput,
            { on -> actions.updateSound { it.copy(hiResOutput = on) } },
            more = "Hi-res output is about the format. Each file is played at its own sample rate in 32-bit float, so 24-bit/96 kHz stays 24-bit/96 kHz instead of being cut to 16-bit. " +
                "The two work together: Pure keeps the samples untouched, Hi-res keeps their full depth and rate.",
        )
        BasicText(
            "Sound settings ›",
            style = Room.Label.copy(color = Room.Accent),
            modifier = Modifier
                .testTag("signal-sound-settings")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onSoundSettings)
                .touchHeight(),
        )
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
        BasicText(label, style = Room.LabelQuiet.copy(fontSize = 10.sp, color = if (checked) Room.Ink else Room.Quiet))
        Box(
            Modifier
                .size(26.dp, 15.dp)
                .background(if (checked) Room.Ink else Room.Background, RectangleShape)
                .border(1.dp, Room.Ink, RectangleShape)
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(9.dp).background(if (checked) Room.Background else Room.Ink, RectangleShape))
        }
    }
}
