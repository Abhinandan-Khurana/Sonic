package app.runout.ui

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
 * parts: "info" in blue and "close ×", the 22/700 title, numbered points under hairlines (10/400,
 * 15 line height, #4D4D4D) and a black "got it" key across the width.
 */
@Composable
fun TeHelpPanel(title: String, points: List<String>, onClose: () -> Unit, modifier: Modifier = Modifier, tag: String = "help-panel") {
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
            BasicText("info", style = Room.Label.copy(color = Room.Accent), modifier = Modifier.weight(1f))
            CloseKey(onClose)
        }
        BasicText(title.uppercase(), style = Room.Title, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Column(Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, text ->
                Row(Modifier.fillMaxWidth().hairlineTop().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    BasicText("%02d".format(i + 1), style = Room.LabelQuiet)
                    BasicText(text, style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp), modifier = Modifier.weight(1f))
                }
            }
        }
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
