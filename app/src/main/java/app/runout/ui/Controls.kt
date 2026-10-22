package app.runout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/*
 * The room's controls, Teenage Engineering style: square corners, 1 dp ink outlines, black when
 * selected, no gradients and no shadows. Every label is in the dot-matrix face (Room.Dot).
 */

/**
 * Keeps an element's drawn size but gives its touch target at least [min] of height, with the
 * drawing centred inside. Put it after `clickable` so the whole box answers the tap.
 */
internal fun Modifier.touchHeight(min: Dp = 48.dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val height = maxOf(placeable.height, min.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
}

/** A square-cornered key for panels: outlined, solid black when it's the main action or selected. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val tick = rememberTick()
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) { tick(); onClick() }
            .touchHeight()
            .height(32.dp)
            .widthIn(min = 48.dp)
            .background(if (active) Room.Ink else Room.Background)
            .border(1.dp, Room.Ink)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Room.Label.copy(color = if (active) Color.White else Room.Ink), maxLines = 1)
    }
}

/**
 * A row with a title, one short line and a square switch, under a hairline. [more] adds a "?" after
 * the title that opens the longer explanation underneath, so the row itself stays short.
 */
@Composable
fun ToggleRow(
    title: String,
    detail: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    more: String? = null,
) {
    var open by remember(title) { mutableStateOf(false) }
    val toggled = rememberTick(if (checked) androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOff else androidx.compose.ui.hapticfeedback.HapticFeedbackType.ToggleOn)
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .hairlineTop()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) { toggled(); onChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .testTag("toggle:$title")
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, style = Room.Label.copy(fontSize = 12.sp))
                if (more != null) InfoDot(title) { open = !open }
            }
            if (detail != null) BasicText(detail, style = Room.LabelQuiet.copy(fontSize = 10.sp))
            if (open && more != null) BasicText(more, style = Room.Label.copy(fontSize = 10.sp, color = Room.Body), modifier = Modifier.padding(top = 4.dp).testTag("more:$title"))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked)
    }
}

/** A small "?" square that reveals more about the thing beside it. */
@Composable
internal fun InfoDot(about: String, onClick: () -> Unit) {
    Box(
        Modifier
            .testTag("info:$about")
            .semantics { contentDescription = "More about $about" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(16.dp).border(1.dp, Room.Ink), contentAlignment = Alignment.Center) {
            BasicText("?", style = Room.Label.copy(fontSize = 10.sp, lineHeight = 10.sp))
        }
    }
}

/**
 * A flat hardware-style key: a square outline that goes down a hair when pressed, and solid black
 * when [selected]. Used for EQ presets and the download controls.
 */
@Composable
internal fun TeKey(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    description: String? = label,
    icon: (androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit)? = null,
) {
    val tick = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val ink = if (selected) Color.White else Room.Ink
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, interactionSource = source, indication = null, role = Role.Button) { tick(); onClick() }
            .semantics { if (description != null) contentDescription = description; if (selected) stateDescription = "Selected" }
            .touchHeight()
            .height(34.dp)
            .widthIn(min = 40.dp)
            .drawBehind {
                drawRect(if (selected) Room.Ink else if (pressed) Room.HairlineSoft else Room.Background)
                drawRect(Room.Ink, style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Canvas(Modifier.size(16.dp)) { icon(ink) }
        else if (label != null) BasicText(label, style = Room.Label.copy(fontSize = 10.5.sp, color = ink), maxLines = 1)
    }
}

/** On: a black square knob at the right. Off: a hollow one at the left. */
@Composable
private fun Switch(checked: Boolean) {
    Canvas(Modifier.size(44.dp, 22.dp)) {
        val line = 1.dp.toPx()
        drawRect(if (checked) Room.Ink else Room.Faint, style = Stroke(line))
        val pad = 2.dp.toPx()
        val knob = size.height - pad * 2
        val x = if (checked) size.width - pad - knob else pad
        if (checked) drawRect(Room.Ink, Offset(x, pad), Size(knob, knob))
        else drawRect(Room.Faint, Offset(x, pad), Size(knob, knob), style = Stroke(line))
    }
}

/** A wrapping row of choices; one is selected. */
@Composable
fun <T> Chips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> Pill(label(o), { onSelect(o) }, active = o == selected, enabled = enabled) }
    }
}

/** A row of colour swatches. */
@Composable
fun ColourDots(colours: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colours.forEach { c ->
            Box(
                Modifier
                    .size(24.dp)
                    .border(if (c == selected) 2.dp else 0.dp, if (c == selected) Room.Ink else Color.Transparent, CircleShape)
                    .padding(3.dp)
                    .background(Color(c), CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(c) }
                    .semantics { contentDescription = "Colour ${Integer.toHexString(c)}" },
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text.uppercase(),
        style = Room.LabelQuiet.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
        modifier = modifier.padding(top = 22.dp, bottom = 8.dp),
    )
}

/** A single-line text field over a 1 dp ink rule, in the room's type. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    secret: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = when {
                secret -> KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                onSubmit != null -> KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search)
                else -> KeyboardOptions.Default
            },
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit?.invoke() }),
            textStyle = Room.Label.copy(fontSize = 16.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(Room.Signal),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field:$placeholder").semantics { contentDescription = placeholder }.padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = Room.Label.copy(fontSize = 16.sp, lineHeight = 20.sp, color = Room.Faint))
                    inner()
                }
            },
        )
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Room.Ink))
    }
}

/**
 * One band of the graphic EQ: a hairline rail with a square black knob. Drag to set the gain,
 * double-tap to reset to 0 dB.
 */
@Composable
fun EqFader(
    label: String,
    gainDb: Float,
    maxDb: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val current by rememberUpdatedState(gainDb)
    val change by rememberUpdatedState(onChange)
    Column(
        modifier.alpha(if (enabled) 1f else 0.4f).semantics { contentDescription = "$label Hz ${"%.1f".format(gainDb)} dB" }.testTag("fader:$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            (if (gainDb > 0.05f) "+" else "") + if (gainDb.roundToInt().toFloat() == gainDb) gainDb.roundToInt().toString() else "%.1f".format(gainDb),
            style = Room.Label.copy(fontSize = 9.sp, textAlign = TextAlign.Center, color = if (kotlin.math.abs(gainDb) < 0.05f) Room.Quiet else Room.Ink),
        )
        Canvas(
            Modifier
                .width(26.dp)
                .height(118.dp)
                .pointerInput(enabled, maxDb) {
                    if (!enabled) return@pointerInput
                    var start = 0f
                    var travelled = 0f
                    detectVerticalDragGestures(
                        onDragStart = { start = current; travelled = 0f },
                    ) { ch, dy ->
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
            drawLine(Room.Hairline, Offset(cx, 0f), Offset(cx, size.height), line)
            drawLine(Room.Faint, Offset(cx - 5.dp.toPx(), size.height / 2f), Offset(cx + 5.dp.toPx(), size.height / 2f), line)
            val y = size.height / 2f - (gainDb / maxDb).coerceIn(-1f, 1f) * size.height / 2f
            val knob = 14.dp.toPx()
            drawRect(Room.Ink, Offset(cx - knob / 2, y - knob / 2), Size(knob, knob))
        }
        BasicText(label, style = Room.LabelQuiet.copy(fontSize = 9.sp, textAlign = TextAlign.Center))
    }
}

/** A boxed progress bar. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, colour: Color = Room.Ink) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRect(colour, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
        drawRect(Room.Ink, style = Stroke(1.dp.toPx()))
    }
}

/** A section heading: a quiet kicker, a bold title and an optional line under it. */
@Composable
internal fun ConsoleTitle(kicker: String, title: String, detail: String? = null) {
    BasicText(kicker.lowercase(), style = Room.LabelQuiet.copy(fontSize = 10.sp), modifier = Modifier.padding(bottom = 6.dp))
    BasicText(title, style = Room.Title.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold))
    if (detail != null) BasicText(detail, style = Room.LabelQuiet.copy(color = Room.Body), modifier = Modifier.padding(top = 8.dp, bottom = 11.dp))
}

/** A flat grey tile for one item, like a catalogue cell. */
@Composable
internal fun ConsoleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Room.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A 1 dp hairline along the top edge, as between list rows. */
internal fun Modifier.hairlineTop(colour: Color = Room.Hairline): Modifier = drawBehind {
    drawLine(colour, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}
