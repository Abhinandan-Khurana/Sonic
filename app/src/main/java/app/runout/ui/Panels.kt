package app.runout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.drops.DropItem
import app.runout.drops.DropState
import app.runout.drops.DropsRepository
import app.runout.model.Album
import app.runout.model.Licences
import app.runout.model.Source
import app.runout.sound.Presets
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The room's places. Those [inStrip] get a tab; Downloads opens from the ↓ count. [fullPage] ones
 * take the whole screen; the rest slide up as glass sheets.
 */
/**
 * The room's places. Each numbered one is a full page under the nav bar, headed "02 / dig" and so
 * on (the room is 01); Audio quality is a panel over the room.
 */
enum class Panel(val title: String, val nav: String, val index: String, val inStrip: Boolean = true, val fullPage: Boolean = true) {
    DIG("Dig", "Dig", "02 / dig"),
    DROPS("Drops", "Drops", "03 / friday drops"),
    FRIENDS("Friends", "Friends", "04 / friends"),
    SETTINGS("Settings", "Settings", "05 / settings"),
    DOWNLOADS("Downloads", "Downloads", "06 / downloads", inStrip = false),
    SIGNAL("Audio quality", "Audio", "", inStrip = false, fullPage = false),
}

/** The sections of the Settings sheet. */
enum class SettingsSection(val label: String) { SOUND("Sound"), ACCOUNT("Soulseek"), LIBRARY("Library"), ABOUT("About") }

// ---------------------------------------------------------------------------------------------
// Page parts shared by Settings and Drops

/**
 * Clickable with a touch target at least [min] tall that doesn't add to the layout: the element
 * keeps the height drawn on the boards and the rest of the target overhangs it, centred.
 */
internal fun Modifier.overhangTap(role: Role = Role.Button, enabled: Boolean = true, min: Dp = 48.dp, onClick: () -> Unit): Modifier = composed {
    val tick = rememberTick()
    val natural = remember { IntArray(1) }
    this
        .layout { m, c ->
            val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val h = natural[0].coerceAtMost(p.height)
            layout(p.width, h) { p.place(0, -(p.height - h) / 2) }
        }
        .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, role = role) { tick(); onClick() }
        .layout { m, c ->
            val p = m.measure(c.copy(minHeight = 0))
            natural[0] = p.height
            val h = maxOf(p.height, min.roundToPx())
            layout(p.width, h) { p.place(0, (h - p.height) / 2) }
        }
}

/**
 * At least the height left in the page under the head, so a body can stand its rows at the foot
 * (Settings, About) inside the page's scroll. Takes the unscrolled top: the lowest one seen.
 */
internal fun Modifier.fillPageHeight(): Modifier = composed {
    val density = LocalDensity.current
    val bottomInset = WindowInsets.safeDrawing.union(WindowInsets.ime).getBottom(density)
    var top by remember { mutableIntStateOf(-1) }
    var rootHeight by remember { mutableIntStateOf(0) }
    val min = if (top < 0) 0.dp else with(density) {
        (rootHeight - top - bottomInset - NAV_HEIGHT.roundToPx() - 22.dp.roundToPx()).coerceAtLeast(0).toDp()
    }
    this
        .onGloballyPositioned { c ->
            val y = c.positionInRoot().y.roundToInt()
            if (y > top) top = y
            rootHeight = c.findRootCoordinates().size.height
        }
        .heightIn(min = min)
}

/** "← settings", "← all drops": 12/400 ink at the top of a body. */
@Composable
internal fun PageBackLink(text: String, tag: String, onClick: () -> Unit) {
    BasicText(text, style = Room.Text, modifier = Modifier.testTag(tag).semantics { contentDescription = text.removePrefix("← ") }.overhangTap(onClick = onClick))
}

/** A TE Link that keeps its drawn height: 12/400 text and an arrow, 8 apart. */
@Composable
internal fun FlatLink(text: String, onClick: () -> Unit, tag: String = "link:$text", enabled: Boolean = true) {
    Row(
        Modifier.alpha(if (enabled) 1f else 0.4f).testTag(tag).semantics(mergeDescendants = true) {}.overhangTap(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = Room.Text)
        BasicText("→", style = Room.Text)
    }
}

/** TE Help: the 16 dp outlined "?" square, drawn at its own size (the touch target overhangs). */
@Composable
internal fun HelpSquare(about: String, onClick: () -> Unit) {
    Box(
        Modifier
            .testTag("info:$about")
            .semantics { contentDescription = "More about $about" }
            .overhangTap(min = 32.dp, onClick = onClick)
            .size(16.dp)
            .border(1.dp, Room.Ink),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("?", style = Room.Label)
    }
}

/** A numbered section of a page, "01 signal": the 10/400 grey label, then its parts 11 dp apart. */
@Composable
internal fun PageSection(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        BasicText(label, style = Room.LabelQuiet)
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Sound

/**
 * The help panels behind the Sound page's "?" squares, worded as on the TE · Help panels board.
 * The verdict's opens from the signal section at the head of the Settings list.
 */
internal object SoundHelp {
    val PURE = TeHelp("help · sound › output", "pure", listOf(
        "pure sends each file to the output untouched: no eq, no replaygain, no limiter, no warmth, no crackle.",
        "use it with a usb dac or amp that does its own shaping. on android 14 and later, a usb dac that offers it plays bit-perfect. the other sound settings rest while pure is on.",
    ))
    val HI_RES = TeHelp("help · sound › output", "hi-res output", listOf(
        "keeps 24-bit files at full depth on the way to android, as 32-bit float. android's mixer still sets the output rate, usually 48 khz, unless pure and a usb dac play bit-perfect; the signal section says which.",
        "when off, runout sends 16-bit: a 16-bit file goes out as it is, and a deeper or processed one is dithered once.",
    ))
    val VERDICT = TeHelp("help · sound › signal", "the signal verdict", listOf(
        "one line on what reaches your ears: the track, what runout does to it, and what the output takes.",
        "green: bit-perfect, nothing changed. amber: a lossless source, but resampled, processed or cut to 16-bit on the way. red: lossy, because bluetooth re-encodes the music.",
        "hold the deck's led window to see this chain from the room.",
    ))
    val EQ = TeHelp("help · sound › equaliser", "eq", listOf(
        "ten bands from 31 hz to 16 khz, applied after replaygain.",
        "pick a preset, drag a band, or import an autoeq profile. runout lowers the level a little first, so boosts never clip.",
    ))
    val REPLAYGAIN = TeHelp("help · sound › loudness", "replaygain", listOf(
        "evens out loudness, so you don't reach for the volume between records.",
        "album keeps quiet and loud songs as the record intends. track makes every song as loud as the next.",
        "off plays each file at its mastered level.",
    ))
    val LIMITER = TeHelp("help · sound › loudness", "limiter", listOf(
        "catches the loudest peaks before they distort when eq, warmth or replaygain lift the level.",
        "it only switches in when the level could go over, and then only acts on those peaks; most records never touch it.",
    ))
    val WARMTH = TeHelp("help · sound › loudness", "warmth", listOf(
        "a gentle low lift and high roll-off, like a warm cartridge.",
        "it changes the sound, so the signal verdict stops saying bit-perfect while it's on.",
    ))
}

/**
 * Settings › Sound, as the TE · Settings · Sound board less its signal section (that now heads the
 * Settings list): 01 output (crackle first), 02 equaliser and 03 loudness, 22 dp apart. Each "?"
 * opens its help panel.
 */
@Composable
fun SoundPanel(s: SoundSettings, crackleOn: Boolean, actions: RoomActions) {
    var help by remember { mutableStateOf<TeHelp?>(null) }
    val dsp = !s.pure
    Column(Modifier.fillMaxWidth().testTag("panel:sound"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageSection("01 output") {
            // Crackle moved here from the home screen: it's part of how the deck sounds.
            ToggleRow("Crackle", "Surface noise under the music.", crackleOn, { actions.toggleCrackle() })
            HelpToggleRow("Pure", "The music, untouched.", s.pure, { on -> actions.updateSound { it.copy(pure = on) } }) { help = SoundHelp.PURE }
            HelpToggleRow("Hi-res output", "Keeps 24-bit detail all the way to the output.", s.hiResOutput, { on -> actions.updateSound { it.copy(hiResOutput = on) } }) { help = SoundHelp.HI_RES }
        }
        PageSection("02 equaliser") {
            HelpToggleRow(
                "EQ",
                if (s.useParametric) "AutoEq headphone profile · ${s.parametric.size} filters" else "10-band graphic EQ · ${s.presetName}",
                s.eqEnabled,
                { on -> actions.updateSound { it.copy(eqEnabled = on) } },
                enabled = dsp,
            ) { help = SoundHelp.EQ }
            PresetGrid(s, actions, dsp)
            Row(
                Modifier.fillMaxWidth().hairlineTop().hairlineBottom().padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SoundSettings.GRAPHIC_LABELS.forEachIndexed { i, label ->
                    BandFader(
                        label = label,
                        gainDb = s.graphic.getOrElse(i) { 0f },
                        maxDb = SoundSettings.MAX_GAIN_DB,
                        enabled = dsp && !s.useParametric,
                        onChange = { g ->
                            actions.updateSound { cur ->
                                val next = cur.graphic.toMutableList().also { it[i] = g }
                                val named = Presets.all.firstOrNull { it.second == next }?.first ?: "Custom"
                                cur.copy(graphic = next, presetName = named, eqEnabled = true, useParametric = false)
                            }
                        },
                    )
                }
            }
            val pre = if (s.eqEnabled) s.preampDb() else 0f
            BasicText(
                if (pre < -0.05f) "automatic headroom ${"%.1f".format(pre)} db, so boosted bands never clip" else "no boost, no headroom needed",
                style = Room.LabelQuiet.copy(lineHeight = 15.sp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                TeButton("import autoeq…", actions::importAutoEq, Modifier.testTag("import-autoeq"), kind = KeyKind.OUTLINE, enabled = dsp)
                if (s.useParametric) FlatLink("use graphic eq", { actions.updateSound { it.copy(useParametric = false, presetName = "Custom") } }, tag = "use-graphic-eq", enabled = dsp)
            }
        }
        PageSection("03 loudness") {
            Column(
                Modifier.fillMaxWidth().alpha(if (dsp) 1f else 0.4f).hairlineTop().padding(vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    BasicText("replaygain", style = Room.Text)
                    HelpSquare("ReplayGain") { help = SoundHelp.REPLAYGAIN }
                }
                Segments(ReplayGainMode.entries.toList(), s.replayGain, { it.label.lowercase() }, { m -> actions.updateSound { it.copy(replayGain = m) } }, enabled = dsp)
            }
            HelpToggleRow("Limiter", "Catches peaks.", s.limiter, { on -> actions.updateSound { it.copy(limiter = on) } }, enabled = dsp) { help = SoundHelp.LIMITER }
            HelpToggleRow("Warmth", "Like a warm cartridge.", s.warmth, { on -> actions.updateSound { it.copy(warmth = on) } }, enabled = dsp) { help = SoundHelp.WARMTH }
        }
    }
    TeHelpSheet(help) { help = null }
}

/**
 * TE Toggle Row with its TE Help: a hairline above, 16 dp above and below, the 12/400 title and the
 * "?" 8 dp after it, the 10/400 grey line 6 dp under, and the switch. The "?" opens a help panel.
 */
@Composable
private fun HelpToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onHelp: () -> Unit,
) {
    val toggled = rememberTick(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .hairlineTop()
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { toggled(); onChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .testTag("toggle:$title")
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(title.lowercase(), style = Room.Text)
                HelpSquare(title, onHelp)
            }
            BasicText(detail.lowercase(), style = Room.LabelQuiet)
        }
        Switch(checked)
    }
}

/**
 * The EQ presets as one outlined block of keys, four to a row, 11 dp above and below each 10/400
 * label; the one in use is black. "custom" lights when the bands match no preset.
 */
@Composable
private fun PresetGrid(s: SoundSettings, actions: RoomActions, enabled: Boolean) {
    val names = Presets.all.map { it.first } + "Custom"
    val custom = !s.useParametric && Presets.all.none { it.first == s.presetName }
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).border(1.dp, Room.Ink)) {
        names.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { name ->
                    val on = if (name == "Custom") custom else name == s.presetName && !s.useParametric
                    val pick = if (name == "Custom") ({ actions.updateSound { it.copy(useParametric = false, presetName = "Custom", eqEnabled = true) } }) else ({ actions.applyPreset(name) })
                    Box(
                        Modifier
                            .weight(1f)
                            .background(if (on) Room.Ink else Room.Background)
                            .testTag("preset:$name")
                            .semantics { if (on) stateDescription = "Selected" }
                            .overhangTap(enabled = enabled, onClick = pick)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(name.lowercase(), style = Room.Label.copy(color = if (on) Color.White else Room.Ink), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A row of choices in one 1 dp outline, equal cells 11 dp above and below; the chosen one black. */
@Composable
private fun <T> Segments(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().border(1.dp, Room.Ink)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) Room.Ink else Room.Background)
                    .semantics { if (on) stateDescription = "Selected" }
                    .overhangTap(enabled = enabled) { onSelect(o) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label(o), style = Room.Label.copy(color = if (on) Color.White else Room.Ink), maxLines = 1)
            }
        }
    }
}

/**
 * One band of the graphic EQ, as on the board: the 10/400 gain, 8 dp, a 120 dp hairline rail with an
 * 8 dp zero mark and a 14 dp black knob, 8 dp, the grey frequency. Drag to set, double-tap for 0 dB.
 */
@Composable
private fun BandFader(label: String, gainDb: Float, maxDb: Float, onChange: (Float) -> Unit, enabled: Boolean = true) {
    val current by rememberUpdatedState(gainDb)
    val change by rememberUpdatedState(onChange)
    Column(
        Modifier.alpha(if (enabled) 1f else 0.4f).semantics { contentDescription = "$label Hz ${"%.1f".format(gainDb)} dB" }.testTag("fader:$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            (if (gainDb > 0.05f) "+" else "") + if (gainDb.roundToInt().toFloat() == gainDb) gainDb.roundToInt().toString() else "%.1f".format(gainDb),
            style = Room.Label.copy(color = if (abs(gainDb) < 0.05f) Room.Quiet else Room.Ink),
        )
        // 14 dp drawn, a little wider to grab.
        Canvas(
            Modifier
                .width(24.dp)
                .height(120.dp)
                .pointerInput(enabled, maxDb) {
                    if (!enabled) return@pointerInput
                    var start = 0f
                    var travelled = 0f
                    detectVerticalDragGestures(onDragStart = { start = current; travelled = 0f }) { ch, dy ->
                        ch.consume()
                        travelled += dy
                        val perPx = (2 * maxDb) / size.height
                        val next = ((start - travelled * perPx) * 2f).roundToInt() / 2f
                        change(next.coerceIn(-maxDb, maxDb))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onDoubleTap = { change(0f) })
                },
        ) {
            val cx = size.width / 2f
            val line = 1.dp.toPx()
            val knob = 14.dp.toPx()
            drawRect(Room.Hairline, Offset(cx - line / 2, 0f), Size(line, size.height))
            drawRect(Room.Faint, Offset(cx - 4.dp.toPx(), size.height / 2f - line / 2), Size(8.dp.toPx(), line))
            // The knob travels the rail's length less its own size, centred on 0 dB.
            val y = (size.height - knob) / 2f * (1f - (gainDb / maxDb).coerceIn(-1f, 1f))
            drawRect(Room.Ink, Offset(cx - knob / 2, y), Size(knob, knob))
        }
        BasicText(label, style = Room.LabelQuiet)
    }
}

// ---------------------------------------------------------------------------------------------
// Friends: see People.kt

// ---------------------------------------------------------------------------------------------
// Friday Drops

private val CLAIMS_HELP = TeHelp("help · drops", "claims", listOf(
    "you get three claims a week. each one adds a drop to your crate for good.",
    "everyone sees the same ten albums each friday.",
    "listening to the 30 s previews never uses a claim.",
))

/**
 * Friday Drops, as the TE · Drops boards: the week and claims left, the line about drops, then the
 * ten albums as grey tiles two to a row. A tile opens the drop's preview. Signed out, a note and a
 * key to sign in come first; loading and errors stand in for the shelf.
 */
@Composable
fun DropsPanel(
    drops: DropState,
    claimed: Set<String>,
    claiming: Map<String, Float>,
    actions: RoomActions,
    signedIn: Boolean = false,
    onAccount: () -> Unit = {},
) {
    LaunchedEffect(drops is DropState.Idle) { if (drops is DropState.Idle) actions.refreshDrops() }
    val left = (DropsRepository.CLAIMS_PER_WEEK - claimed.size).coerceAtLeast(0)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf<TeHelp?>(null) }
    Column(Modifier.fillMaxWidth().testTag("panel:drops"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val ready = drops as? DropState.Ready
        val item = ready?.week?.items?.firstOrNull { it.identifier == open }
        if (item != null) {
            DropPreview(ready.week.items.indexOf(item) + 1, item, isClaimed =item.identifier in claimed, progress = claiming[item.identifier], left = left, canClaim = left > 0 && signedIn, actions = actions) { open = null }
            return@Column
        }
        if (!signedIn) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                BasicText(
                    "sign in with soulseek to download from the internet archive. local flac playback is always available without an account.",
                    style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp),
                )
                TeButton("sign in with soulseek", onAccount, Modifier.testTag("drops-sign-in"))
            }
        }
        when (drops) {
            DropState.Idle, DropState.Loading -> BasicText("digging through the archive…", style = Room.LabelQuiet)
            is DropState.Failed -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                BasicText(drops.reason.lowercase(), style = Room.Label.copy(color = Room.Danger, lineHeight = 15.sp))
                TeButton("try again", actions::refreshDrops, kind = KeyKind.OUTLINE)
            }
            is DropState.Ready -> {
                val w = drops.week
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("week of ${w.opens.dayOfMonth} ${w.opens.month.name.lowercase()}", style = Room.LabelQuiet, modifier = Modifier.weight(1f))
                        ClaimLights(left) { help = CLAIMS_HELP }
                    }
                    if (left == 0) BasicText("all claimed this week · next drop friday ${w.closes.dayOfMonth}", style = Room.LabelQuiet)
                }
            }
        }
        BasicText(
            "ten creative commons albums from the internet archive every friday, the same for everyone. claim three a week.",
            style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp),
        )
        if (ready != null) {
            // The shelf: the ten drops as tiles, two to a row, 22 dp across and 16 dp down.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ready.week.items.withIndex().chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        pair.forEach { (i, d) ->
                            DropSleeve(i + 1, d, d.identifier in claimed, claiming[d.identifier], canClaim = left > 0, actions, Modifier.weight(1f).fillMaxHeight()) { open = d.identifier }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    TeHelpSheet(help) { help = null }
}

/**
 * Claims left this week: "2 claims left" in orange, a 10 dp square for each claim (solid once
 * spent, outlined while still free) and the TE Help "?".
 */
@Composable
private fun ClaimLights(left: Int, onHelp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = "$left of ${DropsRepository.CLAIMS_PER_WEEK} claims left" }.testTag("claim-lights"),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(if (left == 1) "1 claim left" else "$left claims left", style = Room.Label.copy(color = Room.Signal))
            val spent = DropsRepository.CLAIMS_PER_WEEK - left
            repeat(DropsRepository.CLAIMS_PER_WEEK) { i ->
                Box(Modifier.size(10.dp).then(if (i < spent) Modifier.background(Room.Signal) else Modifier.border(1.dp, Room.Signal)))
            }
        }
        HelpSquare("claims", onHelp)
    }
}

/** The drop tile's pastel steel: a cool light grey, darker at the corners and lit across the middle. */
private val STEEL_BASE = Color(0xFFE1E4E8)
private val STEEL_SHADE = Color(0xFFCDD2D8)
private val STEEL_LIGHT = Color(0xFFF3F4F6)

/**
 * Fine horizontal brushing and grain, as a small tile that repeats: each row a little lighter or
 * darker than the next, each pixel a speck of light or shadow. Built once, from a fixed seed.
 */
private val steelGrain: ImageBitmap by lazy {
    val n = 192
    val random = java.util.Random(1977)
    // Brushing: a smoothed random brightness for each row.
    val rows = FloatArray(n) { random.nextGaussian().toFloat() }
    val brushed = FloatArray(n) { y -> (rows[y] * 2f + rows[(y + 1) % n] + rows[(y + n - 1) % n]) / 4f }
    val pixels = IntArray(n * n) { i ->
        val v = brushed[i / n] * 0.55f + random.nextGaussian().toFloat() * 0.45f
        val alpha = (kotlin.math.abs(v) * 22f).toInt().coerceIn(0, 60)
        if (v > 0) android.graphics.Color.argb(alpha, 255, 255, 255) else android.graphics.Color.argb(alpha, 0, 0, 0)
    }
    android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun DrawScope.drawBrushedSteel() {
    drawRect(STEEL_BASE)
    // The metal's sheen: shade at the corners, a soft band of light across the middle.
    drawRect(Brush.linearGradient(0f to STEEL_SHADE, 0.42f to STEEL_LIGHT, 0.58f to STEEL_BASE, 1f to STEEL_SHADE, start = Offset.Zero, end = Offset(size.width, size.height)))
    drawRect(ShaderBrush(ImageShader(steelGrain, TileMode.Repeated, TileMode.Repeated)))
}

/** A licence for a tile: "cc by", without its version. */
private fun shortLicence(label: String) = label.lowercase().replace(Regex(" \\d+(\\.\\d+)*$"), "")

/**
 * One drop on the shelf: a brushed pastel-steel tile with grain, in a 2 dp ink outline, 16 dp in and 11 between parts: the sleeve, the number and
 * licence (blue), the 10/400 title and grey artist · year, and what you can do (green once it's in
 * your crate, the claiming percentage while it downloads, "claim →" otherwise).
 */
@Composable
private fun DropSleeve(number: Int, item: DropItem, isClaimed: Boolean, progress: Float?, canClaim: Boolean, actions: RoomActions, modifier: Modifier, onOpen: () -> Unit) {
    val cover by produceState<ImageBitmap?>(null, item.identifier) {
        value = actions.dropCover(item)?.let { CoverCache.load(it) }
    }
    val tick = rememberTick()
    Column(
        modifier
            .testTag("drop:${item.identifier}")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { tick(); onOpen() }
            .drawBehind { drawBrushedSteel() }
            .border(2.dp, Room.Ink)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // 140 of the tile's 151 dp on the 412 dp board, square.
        DropCover(number, item, cover, Modifier.fillMaxWidth(140f / 151f).aspectRatio(1f)) {
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(6.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            BasicText("%02d".format(number), style = Room.LabelQuiet, modifier = Modifier.weight(1f))
            Licences.label(item.license)?.let { BasicText(shortLicence(it), style = Room.Label.copy(color = Room.Accent), maxLines = 1) }
        }
        BasicText(item.title.lowercase(), style = Room.Label.copy(lineHeight = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicText(listOfNotNull(item.creator, item.year?.toString()).joinToString(" · ").lowercase(), style = Room.LabelQuiet.copy(lineHeight = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        // The action sits at the foot, level across the row.
        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) { when {
            isClaimed -> BasicText("in your crate", style = Room.Label.copy(color = Room.Online))
            progress != null -> BasicText("claiming · ${(progress * 100).roundToInt()}%", style = Room.Label.copy(color = Room.Signal))
            canClaim -> BasicText("claim →", style = Room.Label)
            else -> BasicText("listen →", style = Room.Label)
        } }
    }
}

/**
 * A drop up close, as the TE · Drops · preview board: "← all drops", the 132 dp sleeve beside the
 * 16/700 title, artist, blue licence tag and the black claim key; then the tracks, each with a 30 s
 * listen, under #E5E5E5 hairlines; then the note that listening uses no claim.
 */
@Composable
private fun DropPreview(number: Int, item: DropItem, isClaimed: Boolean, progress: Float?, left: Int, canClaim: Boolean, actions: RoomActions, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { actions.listen(null); onBack() }
    PageStep { actions.listen(null); onBack() }
    DisposableEffect(item.identifier) { onDispose { actions.listen(null) } }
    val cover by produceState<ImageBitmap?>(null, item.identifier) { value = actions.dropCover(item)?.let { CoverCache.load(it) } }
    // null while loading; then the list, or null again if the archive couldn't be reached.
    var loaded by remember(item.identifier) { mutableStateOf(false) }
    val tracks by produceState<List<app.runout.drops.DropTrack>?>(null, item.identifier) { value = actions.dropTracks(item); loaded = true }
    val noFlac = loaded && tracks?.isEmpty() == true
    var playing by remember { mutableStateOf<String?>(null) }
    // The listen stops by itself after thirty seconds; mirror that here.
    LaunchedEffect(playing) { if (playing != null) { kotlinx.coroutines.delay(30_000); playing = null } }
    Column(Modifier.fillMaxWidth().testTag("drop-preview"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageBackLink("← all drops", "drops-back") { actions.listen(null); onBack() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DropCover(number, item, cover, Modifier.size(132.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                BasicText(item.title.uppercase(), style = Room.Heading.copy(fontWeight = FontWeight.Bold, lineHeight = 19.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                listOfNotNull(item.creator, item.year?.toString()).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    BasicText(it.lowercase(), style = Room.Label.copy(color = Room.Body))
                }
                Licences.label(item.license)?.let { TeTag(it.lowercase(), color = Room.Accent) }
                when {
                    isClaimed -> BasicText("in your crate", style = Room.Label.copy(color = Room.Online))
                    progress != null -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasicText("claiming · ${(progress * 100).roundToInt()}%", style = Room.Label.copy(color = Room.Signal))
                        ProgressLine(progress)
                    }
                    // An album with no FLAC can't be kept, so there's nothing to claim.
                    else -> TeButton(
                        when {
                            noFlac -> "no flac to claim"
                            canClaim -> "claim · 1 of $left"
                            else -> "claim"
                        },
                        { actions.listen(null); actions.claim(item) },
                        Modifier.testTag("claim:${item.identifier}"),
                        enabled = canClaim && !noFlac,
                    )
                }
            }
        }
        BasicText("tracks · tap to hear 30 s", style = Room.LabelQuiet)
        val list = tracks
        when {
            list == null && !loaded -> BasicText("listing the tracks…", style = Room.LabelQuiet)
            list == null -> BasicText("couldn't reach the archive for the track list. you can still claim it.", style = Room.LabelQuiet.copy(lineHeight = 15.sp))
            list.isEmpty() -> BasicText("this album has no flac files, so it can't be claimed. it uses no allowance.", style = Room.LabelQuiet.copy(lineHeight = 15.sp))
            else -> Column(Modifier.fillMaxWidth()) {
                list.forEachIndexed { i, t ->
                    val on = playing == t.url
                    val tick = rememberTick()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hairlineTop(Room.HairlineSoft)
                            .testTag("listen:$i")
                            .semantics(mergeDescendants = true) { contentDescription = if (on) "Stop listening" else "Listen to ${t.title}" }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                                tick()
                                if (on) { actions.listen(null); playing = null } else { actions.listen(t.url); playing = t.url }
                            }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        BasicText((i + 1).toString().padStart(2, '0'), style = Room.LabelQuiet)
                        BasicText(t.title.lowercase(), style = Room.Label.copy(color = if (on) Room.Signal else Room.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        // Lucide play, or an orange stop square while it plays.
                        Canvas(Modifier.size(12.dp)) {
                            val w = size.width
                            val line = 1.dp.toPx()
                            if (on) drawRect(Room.Signal, Offset(line / 2, line / 2), Size(w - line, w - line), style = Stroke(line))
                            else drawPath(Path().apply { moveTo(w * 0.17f, w * 0.08f); lineTo(w * 0.92f, w * 0.5f); lineTo(w * 0.17f, w * 0.92f); close() }, Room.Ink, style = Stroke(line, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
        BasicText("listening plays thirty seconds from the archive. it doesn't use a claim.", style = Room.LabelQuiet.copy(lineHeight = 15.sp))
    }
}

/**
 * A drop's sleeve: while its cover loads, or when it has none, a dot-matrix record on the #F5F5F5
 * tile with the drop's number in its corner; the real cover fades in over it when it arrives.
 */
@Composable
private fun DropCover(number: Int, item: DropItem, cover: ImageBitmap?, modifier: Modifier, overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}) {
    val shown by androidx.compose.animation.core.animateFloatAsState(
        if (cover != null) 1f else 0f, androidx.compose.animation.core.tween(400), label = "drop-cover",
    )
    Box(modifier.background(Room.Surface)) {
        if (shown < 1f) Box(Modifier.fillMaxSize().testTag("drop-placeholder:${item.identifier}")) {
            DotVinyl(Modifier.fillMaxSize().padding(11.dp))
            BasicText("%02d".format(number), style = Room.Label, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        cover?.let { Image(it, item.title, Modifier.fillMaxSize().alpha(shown), contentScale = ContentScale.Crop) }
        overlay()
    }
}

fun formatLength(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Where a record came from, for the crate caption. */
fun provenance(album: Album): String? = when (album.source) {
    Source.RECEIVED -> "Sent by ${album.from ?: "a friend"}"
    Source.DROP -> listOfNotNull("Friday Drop", Licences.label(album.license)).joinToString(" · ")
    Source.SOULSEEK -> "Soulseek · ${album.from ?: "FLAC download"}"
    Source.PRESSING -> "Your pressing" + (album.disc?.let { " · ${it.label.lowercase()} vinyl" } ?: "")
    Source.FOLDER -> Licences.label(album.license)
}
