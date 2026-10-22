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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekSession
import app.runout.soulseek.SoulseekTransfer
import app.runout.soulseek.SoulseekTransferState
import com.kyant.backdrop.Backdrop

/** A light tick for keys, tabs and toggles: the room is meant to feel like hardware. */
@Composable
internal fun rememberTick(type: HapticFeedbackType = HapticFeedbackType.SegmentTick): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics, type) { { haptics.performHapticFeedback(type) } }
}

/**
 * The room's footer: one glass bar of icon tabs that always fits. A small signal-orange dot marks
 * a new Friday Drop or an unread message.
 */
@Composable
internal fun TabBar(
    backdrop: Backdrop,
    badges: Set<Panel>,
    onOpen: (Panel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = rememberTick()
    Row(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(58.dp)
            // Flat and ruled, like the footer of a TE manual page.
            .background(Room.Background)
            .hairlineTop(Room.Ink),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Panel.entries.filter { it.inStrip }.forEach { p ->
            Column(
                Modifier
                    .weight(1f)
                    .testTag("nav:${p.name}")
                    .semantics(mergeDescendants = true) { contentDescription = p.nav + if (p in badges) ", new" else "" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        tick(); onOpen(p)
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box {
                    Canvas(Modifier.size(22.dp)) { drawTabIcon(p, Room.Ink) }
                    if (p in badges) Canvas(Modifier.size(7.dp).align(Alignment.TopEnd)) {
                        drawRect(Room.Signal)
                    }
                }
                BasicText(p.nav.lowercase(), style = Room.Label.copy(fontSize = 9.sp, letterSpacing = 0.3.sp))
            }
        }
    }
}

/** Thin line icons drawn in code, so they stay crisp at any size. */
private fun DrawScope.drawTabIcon(panel: Panel, color: Color) {
    val w = size.width
    val s = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (panel) {
        // A magnifier: digging through the crates.
        Panel.DIG -> {
            drawCircle(color, w * 0.28f, Offset(w * 0.43f, w * 0.43f), style = s)
            drawLine(color, Offset(w * 0.63f, w * 0.63f), Offset(w * 0.86f, w * 0.86f), s.width, StrokeCap.Round)
        }
        // A record dropping into a tray.
        Panel.DROPS -> {
            drawCircle(color, w * 0.2f, Offset(w * 0.5f, w * 0.32f), style = s)
            drawCircle(color, w * 0.04f, Offset(w * 0.5f, w * 0.32f))
            val tray = Path().apply {
                moveTo(w * 0.14f, w * 0.62f); lineTo(w * 0.14f, w * 0.84f); lineTo(w * 0.86f, w * 0.84f); lineTo(w * 0.86f, w * 0.62f)
            }
            drawPath(tray, color, style = s)
        }
        // A speech bubble.
        Panel.FRIENDS -> {
            val bubble = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset(w * 0.12f, w * 0.16f), Size(w * 0.76f, w * 0.52f)), w * 0.14f, w * 0.14f))
                moveTo(w * 0.32f, w * 0.68f); lineTo(w * 0.26f, w * 0.88f); lineTo(w * 0.48f, w * 0.68f)
            }
            drawPath(bubble, color, style = s)
        }
        // Three sliders, like a mixer.
        else -> {
            listOf(0.25f to 0.62f, 0.5f to 0.32f, 0.75f to 0.55f).forEach { (y, knob) ->
                drawLine(color, Offset(w * 0.12f, w * y), Offset(w * 0.88f, w * y), s.width, StrokeCap.Round)
                drawCircle(Room.Background, w * 0.09f, Offset(w * knob, w * y))
                drawCircle(color, w * 0.09f, Offset(w * knob, w * y), style = s)
            }
        }
    }
}

/** Rescan, as a small circular-arrow key in signal orange. */
@Composable
internal fun RefreshKey(onClick: () -> Unit) {
    val tick = rememberTick()
    Box(
        Modifier
            .testTag("action:Rescan")
            .semantics { contentDescription = "Rescan" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { tick(); onClick() }
            .touchHeight()
            .size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val s = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
            drawArc(Room.Signal, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * 0.12f, w * 0.12f), size = Size(w * 0.76f, w * 0.76f), style = s)
            val head = Path().apply {
                moveTo(w * 0.62f, w * 0.02f); lineTo(w * 0.86f, w * 0.18f); lineTo(w * 0.6f, w * 0.34f); close()
            }
            drawPath(head, Room.Signal)
        }
    }
}

/** Downloads waiting: grey when idle or paused, green while bytes move, red when one needs you. */
internal fun downloadsColor(transfers: List<SoulseekTransfer>): Color = when {
    transfers.any { it.state == SoulseekTransferState.FAILED } -> Room.Danger
    transfers.any {
        it.state == SoulseekTransferState.DOWNLOADING || it.state == SoulseekTransferState.VALIDATING || it.state == SoulseekTransferState.PUBLISHING
    } -> Room.Online
    else -> Room.Quiet
}

/** Green when connected, red when an account is offline or in trouble, grey otherwise. */
internal fun connectionColor(session: SoulseekSession): Color = when (session.state) {
    SoulseekConnection.CONNECTED -> Room.Online
    SoulseekConnection.ERROR -> Room.Danger
    SoulseekConnection.OFFLINE -> if (session.hasAccount) Room.Danger else Room.Quiet
    else -> Room.Quiet
}

/** A square status lamp followed by a quiet label, tappable as one; [description] reads it out when there's no label. */
@Composable
internal fun StatusDot(text: String, color: Color, tag: String, onClick: () -> Unit, description: String? = null) {
    Row(
        Modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .touchHeight()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // A square lamp: filled when something's live, hollow when idle.
        Canvas(Modifier.size(8.dp)) {
            if (color == Room.Quiet) drawRect(Room.Ink, style = Stroke(1.dp.toPx())) else drawRect(color)
        }
        if (text.isNotEmpty()) BasicText(text, style = Room.Label.copy(color = if (color == Room.Quiet) Room.Quiet else Room.Ink))
    }
}
