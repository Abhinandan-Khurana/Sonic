package app.runout.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Keeps an element's drawn size but gives its touch target at least [min] of height, with the
 * drawing centred inside. Put it after `clickable` so the whole box answers the tap.
 */
internal fun Modifier.touchHeight(min: Dp = 48.dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val height = maxOf(placeable.height, min.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
}

/** A flat pill button for panels. Solid blue when it's the main action or selected. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
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
            .height(30.dp)
            .widthIn(min = 48.dp)
            .background(if (active) Room.Accent else Color.White.copy(alpha = 0.78f), shape)
            .border(1.dp, if (active) Room.Accent else Room.Ink.copy(alpha = 0.10f), shape)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Room.Label.copy(color = if (active) Color.White else Room.Ink, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/**
 * A row with a title, one short line and a small switch. [more] adds an ⓘ after the title that
 * opens the longer explanation underneath, so the row itself stays one line.
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
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) { toggled(); onChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .testTag("toggle:$title")
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, style = Room.Title.copy(fontSize = 13.sp))
                if (more != null) InfoDot(title) { open = !open }
            }
            if (detail != null) BasicText(detail, style = Room.LabelQuiet)
            if (open && more != null) BasicText(more, style = Room.LabelQuiet.copy(color = Room.Ink.copy(alpha = 0.7f)), modifier = Modifier.padding(top = 4.dp).testTag("more:$title"))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked)
    }
}

/** A small ⓘ that reveals more about the thing beside it. */
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
        Box(Modifier.size(15.dp).border(1.dp, Room.Quiet, CircleShape), contentAlignment = Alignment.Center) {
            BasicText("i", style = Room.Label.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Room.Quiet))
        }
    }
}

/**
 * A small hardware key, Teenage Engineering style: a satin cap on a darker skirt; the selected one
 * is anodised orange. Used for EQ presets and the download controls.
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
                val r = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                val travel = 2.5.dp.toPx()
                drawRoundRect(Color.Black.copy(alpha = 0.12f), topLeft = Offset(0f, travel * 1.6f), size = size, cornerRadius = r)
                drawRoundRect(if (selected) Room.SignalDark else Color(0xFFB4B7BF), topLeft = Offset(0f, travel), size = size, cornerRadius = r)
                val y = if (pressed) travel * 0.8f else 0f
                drawRoundRect(
                    if (selected) Brush.verticalGradient(listOf(Room.SignalLight, Room.Signal), startY = y, endY = y + size.height)
                    else Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFE3E5E9)), startY = y, endY = y + size.height),
                    topLeft = Offset(0f, y), size = size, cornerRadius = r,
                )
                drawRoundRect(Color.White.copy(alpha = 0.7f), topLeft = Offset(0f, y), size = size, cornerRadius = r, style = Stroke(1f))
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Canvas(Modifier.size(16.dp)) { icon(ink) }
        else if (label != null) BasicText(label, style = Room.Label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = ink), maxLines = 1)
    }
}

@Composable
private fun Switch(checked: Boolean) {
    Canvas(Modifier.size(38.dp, 22.dp)) {
        val r = size.height / 2f
        drawRoundRect(if (checked) Room.Accent else Room.Ink.copy(alpha = 0.14f), cornerRadius = CornerRadius(r))
        val x = if (checked) size.width - r else r
        drawCircle(Color.Black.copy(alpha = 0.12f), r - 2.dp.toPx(), Offset(x, r + 1.dp.toPx()))
        drawCircle(Color.White, r - 2.5.dp.toPx(), Offset(x, r))
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

/** A row of colour dots. */
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
        style = Room.LabelQuiet.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/** A single-line text field with a hairline underline, in the room's type. */
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
            textStyle = Room.Title.copy(fontSize = 14.sp),
            cursorBrush = SolidColor(Room.Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field:$placeholder").semantics { contentDescription = placeholder }.padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = Room.Title.copy(fontSize = 14.sp, color = Room.Faint))
                    inner()
                }
            },
        )
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Room.Ink.copy(alpha = 0.12f)))
    }
}

/**
 * One band of the graphic EQ: a vertical fader. Drag to set the gain, double-tap to reset to 0 dB.
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            (if (gainDb > 0.05f) "+" else "") + if (gainDb.roundToInt().toFloat() == gainDb) gainDb.roundToInt().toString() else "%.1f".format(gainDb),
            style = Room.LabelQuiet.copy(fontSize = 8.5.sp, textAlign = TextAlign.Center),
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
            val track = 3.dp.toPx()
            // A printed scale beside the slot: a tick every 3 dB, a longer one at 0 dB.
            val steps = (2 * maxDb / 3f).roundToInt().coerceAtLeast(2)
            for (i in 0..steps) {
                val ty = size.height * i / steps
                val long = i == steps / 2
                drawLine(Room.Ink.copy(alpha = if (long) 0.35f else 0.16f), Offset(cx + 6.dp.toPx(), ty), Offset(cx + (if (long) 12.dp else 9.dp).toPx(), ty), 1f)
            }
            drawRoundRect(Room.Ink.copy(alpha = 0.14f), Offset(cx - track / 2, 0f), Size(track, size.height), CornerRadius(track))
            val y = size.height / 2f - (gainDb / maxDb).coerceIn(-1f, 1f) * size.height / 2f
            val top = minOf(y, size.height / 2f)
            drawRoundRect(Room.Signal, Offset(cx - track / 2, top), Size(track, kotlin.math.abs(y - size.height / 2f)), CornerRadius(track))
            // The cap: a small anodised-orange slider knob with a white index line, like hardware.
            val capW = 18.dp.toPx(); val capH = 11.dp.toPx()
            drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(cx - capW / 2, y - capH / 2 + 1.5.dp.toPx()), Size(capW, capH), CornerRadius(2.5.dp.toPx()))
            drawRoundRect(
                Brush.verticalGradient(listOf(Room.SignalLight, Room.Signal, Room.SignalDark), startY = y - capH / 2, endY = y + capH / 2),
                Offset(cx - capW / 2, y - capH / 2), Size(capW, capH), CornerRadius(2.5.dp.toPx()),
            )
            drawLine(Color.White.copy(alpha = 0.9f), Offset(cx - capW / 2 + 3.dp.toPx(), y), Offset(cx + capW / 2 - 3.dp.toPx(), y), 1.2.dp.toPx())
        }
        BasicText(label, style = Room.LabelQuiet.copy(fontSize = 8.5.sp, textAlign = TextAlign.Center))
    }
}

/** A thin progress bar. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawRoundRect(Room.Ink.copy(alpha = 0.08f), cornerRadius = CornerRadius(size.height))
        drawRoundRect(Room.Accent, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(size.height))
    }
}

/** A small heading inside a glass sheet: a quiet kicker, a title and an optional line under it. */
@Composable
internal fun ConsoleTitle(kicker: String, title: String, detail: String? = null) {
    BasicText(kicker.uppercase(), style = Room.LabelQuiet.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(bottom = 4.dp))
    BasicText(title, style = Room.Title.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))
    if (detail != null) BasicText(detail, style = Room.LabelQuiet, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
}

/** A frosted card for one item inside a glass sheet. */
@Composable
internal fun ConsoleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.55f), shape).border(1.dp, Room.Ink.copy(alpha = 0.08f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
