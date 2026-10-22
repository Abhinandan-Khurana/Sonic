package app.runout.ui

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The page furniture of runout-te.pen: the screen head, the nav bar and the help panel.
 */

/**
 * TE Screen Head: 22 dp in (11 at the foot), "02 / dig" in 10/400 grey and "close ×" in 12/400 on
 * one line, then the title in 44/700, 11 dp apart.
 */
@Composable
fun TeScreenHead(index: String, title: String, onClose: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 11.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(index, style = Room.LabelQuiet, modifier = Modifier.weight(1f))
            if (onClose != null) CloseKey(onClose)
        }
        BasicText(title.uppercase(), style = Room.Display, maxLines = 1, modifier = Modifier.testTag("panel-title").semantics { heading() })
    }
}

/** "close ×", 12/400, right-aligned in 64 dp, with a full-height touch target. */
@Composable
fun CloseKey(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val tick = rememberTick()
    BasicText(
        "close ×",
        style = Room.Text.copy(textAlign = TextAlign.End),
        modifier = modifier
            .testTag("action:Close")
            .semantics { contentDescription = "Close" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { tick(); onClose() }
            .touchHeight()
            .width(64.dp),
    )
}

/** The places in the nav bar, in its order. */
private val NAV = listOf(Panel.DIG, Panel.DROPS, Panel.FRIENDS, Panel.SETTINGS)

/**
 * TE Nav Bar: four equal cells, 11 dp above and below, a 20 dp Lucide icon over a 10/400 label,
 * 8 dp apart. The open place is a black cell with white ink. An orange square marks news.
 */
@Composable
fun TeNavBar(active: Panel?, badges: Set<Panel>, onOpen: (Panel) -> Unit, modifier: Modifier = Modifier) {
    val tick = rememberTick()
    Row(modifier.fillMaxWidth().background(Room.Background)) {
        NAV.forEach { p ->
            val on = p == active
            val ink = if (on) Color.White else Room.Ink
            Column(
                Modifier
                    .weight(1f)
                    .background(if (on) Room.Ink else Room.Background)
                    .testTag("nav:${p.name}")
                    .semantics(mergeDescendants = true) { contentDescription = p.nav + if (p in badges) ", new" else "" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { tick(); onOpen(p) }
                    .padding(vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    Canvas(Modifier.size(20.dp)) { drawNavIcon(p, ink) }
                    if (p in badges) Box(Modifier.size(6.dp).align(Alignment.TopEnd).background(Room.Signal))
                }
                BasicText(p.nav.lowercase(), style = Room.Label.copy(color = ink))
            }
        }
    }
}

/** Lucide's search, calendar, message-square and sliders-horizontal, drawn on their 24-unit grid. */
internal fun DrawScope.drawNavIcon(panel: Panel, color: Color) {
    val k = size.width / 24f
    // Lucide draws at 2 units on 24; the board renders them a touch lighter at 20 dp.
    val s = Stroke(width = 1.8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    run {
        when (panel) {
            Panel.DIG -> {
                drawCircle(color, 7.5f * k, Offset(11f * k, 11f * k), style = s)
                drawLine(color, Offset(21f * k, 21f * k), Offset(16.65f * k, 16.65f * k), s.width, StrokeCap.Round)
            }
            Panel.DROPS -> {
                drawRoundRect(color, Offset(3.5f * k, 4.5f * k), Size(17f * k, 17f * k), CornerRadius(2f * k), style = s)
                drawLine(color, Offset(16f * k, 2.5f * k), Offset(16f * k, 6f * k), s.width, StrokeCap.Round)
                drawLine(color, Offset(8f * k, 2.5f * k), Offset(8f * k, 6f * k), s.width, StrokeCap.Round)
                drawLine(color, Offset(3.5f * k, 10f * k), Offset(20.5f * k, 10f * k), s.width, StrokeCap.Round)
            }
            Panel.FRIENDS -> {
                val p = Path().apply {
                    moveTo(20.5f * k, 15f * k)
                    quadraticTo(20.5f * k, 16.5f * k, 19f * k, 16.5f * k)
                    lineTo(7f * k, 16.5f * k)
                    lineTo(3.5f * k, 20.5f * k)
                    lineTo(3.5f * k, 5f * k)
                    quadraticTo(3.5f * k, 3.5f * k, 5f * k, 3.5f * k)
                    lineTo(19f * k, 3.5f * k)
                    quadraticTo(20.5f * k, 3.5f * k, 20.5f * k, 5f * k)
                    close()
                }
                drawPath(p, color, style = s)
            }
            else -> {
                fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), s.width, StrokeCap.Round)
                l(21f, 4f, 14f, 4f); l(10f, 4f, 3f, 4f)
                l(21f, 12f, 12f, 12f); l(8f, 12f, 3f, 12f)
                l(21f, 20f, 16f, 20f); l(12f, 20f, 3f, 20f)
                l(14f, 2f, 14f, 6f); l(8f, 10f, 8f, 14f); l(16f, 18f, 16f, 22f)
            }
        }
    }
}

/**
 * TE Help Panel: white, a 2 dp ink rule along the top, 22 dp in (33 at the foot), 22 dp between
 * parts: [label] in blue ("help · sound › output") and "close ×", the 22/700 title, numbered points under hairlines (10/400,
 * 15 line height, #4D4D4D) and a black "got it" key across the width.
 */
@Composable
fun TeHelpPanel(title: String, points: List<String>, onClose: () -> Unit, modifier: Modifier = Modifier, tag: String = "help-panel", label: String = "info") {
    Column(
        modifier
            .fillMaxWidth()
            .background(Room.Background)
            .hairlineTop(Room.Ink, 2.dp)
            .testTag(tag)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 33.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(label, style = Room.Label.copy(color = Room.Accent), modifier = Modifier.weight(1f))
            CloseKey(onClose)
        }
        BasicText(title.uppercase(), style = Room.Title, modifier = Modifier.fillMaxWidth().semantics { heading() })
        HelpPoints(points)
        TeButton("got it", onClose, Modifier.fillMaxWidth())
    }
}

/** A stats strip ruled in ink above and below: big 22/700 values over 10/400 grey labels. */
@Composable
fun TeStats(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().hairlineTop(Room.Ink).hairlineBottom(Room.Ink)) {
        stats.forEach { (value, label) ->
            Column(Modifier.weight(1f).padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(value, style = Room.Title)
                BasicText(label, style = Room.LabelQuiet)
            }
        }
    }
}

/**
 * A help panel opened by a TE Help "?": the grey scrim over everything and the panel rising from
 * the foot. Back or a tap on the scrim closes it.
 */
@Composable
fun TeHelpSheet(help: TeHelp?, onClose: () -> Unit) {
    if (help == null) return
    // Through the page's sheet host, so inside a page it covers the whole page, edge to edge.
    PageSheet("help:${help.title}", onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(help.label, style = Room.Label.copy(color = Room.Accent), modifier = Modifier.weight(1f))
            CloseKey(onClose)
        }
        BasicText(help.title.uppercase(), style = Room.Title, modifier = Modifier.fillMaxWidth().semantics { heading() })
        HelpPoints(help.points)
        TeButton("got it", onClose, Modifier.fillMaxWidth())
    }
}

@Composable
private fun HelpPoints(points: List<String>) {
    Column(Modifier.fillMaxWidth()) {
        points.forEachIndexed { i, text ->
            Row(Modifier.fillMaxWidth().hairlineTop().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                BasicText("%02d".format(i + 1), style = Room.LabelQuiet)
                BasicText(text, style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One help panel's words, from the TE · Help panels board. */
data class TeHelp(val label: String, val title: String, val points: List<String>)

/** The grey scrim behind every panel and dialog (#767676 at 60 %). */
@Composable
fun TeScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    Box(
        modifier
            .background(Room.Scrim)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
}

/**
 * A vinyl record drawn in round dots, like the dotted cut of Array: rings of dots around a
 * label and a spindle hole. [lit] (0 to 1) is how much of each ring is inked from 12 o'clock
 * clockwise, starting at [phase] turns; the rest is drawn in [dim].
 */
@Composable
fun DotVinyl(
    modifier: Modifier = Modifier,
    phase: Float = 0f,
    lit: Float = 1f,
    ink: Color = Room.Ink,
    dim: Color = Room.HairlineSoft,
    label: Color = Room.Signal,
) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val dot = r * 0.055f
        val rings = 6
        for (k in 0 until rings) {
            val rr = r * (0.42f + 0.55f * k / (rings - 1))
            val n = (2 * Math.PI * rr / (dot * 2.6f)).toInt().coerceAtLeast(8)
            for (i in 0 until n) {
                val f = i.toFloat() / n
                val a = ((f + phase) * 2 * Math.PI - Math.PI / 2).toFloat()
                val on = f <= lit
                drawCircle(if (on) ink else dim, dot, Offset(c.x + rr * kotlin.math.cos(a), c.y + rr * kotlin.math.sin(a)))
            }
        }
        // The label: a disc of dots in the accent, and the spindle hole.
        val lr = r * 0.28f
        var y = -lr
        while (y <= lr) {
            var x = -lr
            while (x <= lr) {
                if (x * x + y * y <= lr * lr && x * x + y * y > (dot * 1.6f) * (dot * 1.6f)) drawCircle(label, dot * 0.8f, Offset(c.x + x, c.y + y))
                x += dot * 2.4f
            }
            y += dot * 2.4f
        }
    }
}

/** [DotVinyl] turning, its rings inking round and round: the waiting animation (Dig's search). */
@Composable
fun DotVinylSpinner(modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dot-vinyl")
    val spin by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val ink by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "ink")
    DotVinyl(modifier.semantics { contentDescription = "Searching" }, phase = spin, lit = 0.15f + 0.85f * ink)
}
