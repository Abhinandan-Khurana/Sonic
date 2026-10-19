package app.runout.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import app.runout.playback.DeckState
import app.runout.playback.Speed
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Callbacks from the turntable to the deck. */
interface DeckActions {
    fun needleDrop(fraction: Float)
    fun liftNeedle()
    fun holdPlatter()
    fun releasePlatter()
    fun toggleSpeed()
    fun flip()
    fun startStop() {}
    fun nextTrack() {}
    fun previousTrack() {}
    fun setPitch(percent: Float) {}
}

/**
 * Where everything sits on the plinth, in the canvas's own pixels. The layout follows the classic
 * direct-drive DJ deck: platter left, S-shaped tonearm on its base at the top right, pitch fader on
 * the right, start/stop and 33/45 at the bottom left. The tonearm is solved with the law of cosines
 * so the stylus follows a true arc across the grooves.
 */
class DeckGeometry(val size: Size) {
    val h = size.height
    private val w = size.width

    val center = Offset(h * 0.50f, h * 0.455f)
    val ringR = h * 0.435f
    val platterR = h * 0.400f
    val recordR = h * 0.390f

    val pivot = Offset(w - h * 0.205f, h * 0.165f)
    val armBase = Offset(w - h * 0.245f, h * 0.235f)
    val armBaseR = h * 0.125f
    val antiSkate = Offset(w - h * 0.115f, h * 0.27f)
    val adapterWell = Offset(h * 0.105f, h * 0.10f)
    val powerKnob = Offset(h * 0.075f, h * 0.78f)

    val pitchTrack = Rect(w - h * 0.105f, h * 0.43f, w - h * 0.055f, h * 0.755f)
    val screen = Rect(w - h * 0.47f, h * 0.855f, w - h * 0.04f, h * 0.965f)
    val startStop = Rect(h * 0.03f, h * 0.86f, h * 0.165f, h * 0.965f)
    val rpm33 = Rect(h * 0.19f, h * 0.922f, h * 0.29f, h * 0.962f)
    val rpm45 = Rect(h * 0.305f, h * 0.922f, h * 0.405f, h * 0.962f)
    val prev = Rect(h * 0.585f, h * 0.915f, h * 0.675f, h * 0.962f)
    val next = Rect(h * 0.69f, h * 0.915f, h * 0.78f, h * 0.962f)

    val armLength = (pivot - center).getDistance()
    private val phi = atan2(center.y - pivot.y, center.x - pivot.x)

    private fun alphaForRadius(r: Float): Float =
        acos((1f - r * r / (2f * armLength * armLength)).coerceIn(-1f, 1f))

    val alphaOut = alphaForRadius(recordR * 0.95f)
    val alphaIn = alphaForRadius(recordR * 0.43f)
    val alphaRest = alphaForRadius(ringR * 1.05f) + 0.13f

    fun direction(alpha: Float): Offset {
        val th = phi - alpha
        return Offset(cos(th), sin(th))
    }

    fun stylus(alpha: Float): Offset = pivot + direction(alpha) * armLength

    fun alphaForPointer(p: Offset): Float {
        var a = phi - atan2(p.y - pivot.y, p.x - pivot.x)
        while (a > PI) a -= (2 * PI).toFloat()
        while (a < -PI) a += (2 * PI).toFloat()
        return a
    }

    fun alphaForFraction(f: Float) = alphaOut + (alphaIn - alphaOut) * f.coerceIn(0f, 1f)
    fun fractionForAlpha(a: Float) = ((alphaOut - a) / (alphaOut - alphaIn)).coerceIn(0f, 1f)
    fun isOnRecord(p: Offset) = (p - center).getDistance() <= recordR

    /** Pitch fader: −8 % at the top, +8 % at the bottom, like the decks it's modelled on. */
    fun pitchY(percent: Float) = pitchTrack.center.y + (percent / 8f).coerceIn(-1f, 1f) * (pitchTrack.height / 2f - h * 0.02f)
    fun pitchForY(y: Float) = ((y - pitchTrack.center.y) / (pitchTrack.height / 2f - h * 0.02f) * 8f).coerceIn(-8f, 8f)

    fun nearArm(p: Offset, alpha: Float, slop: Float): Boolean {
        val a = pivot
        val b = stylus(alpha)
        val ab = b - a
        val t = (((p - a).x * ab.x + (p - a).y * ab.y) / (ab.x * ab.x + ab.y * ab.y)).coerceIn(0f, 1f)
        val closest = a + ab * t
        return (p - closest).getDistance() < slop || (p - b).getDistance() < slop * 1.6f
    }

    companion object {
        /** Width to height of the plinth. */
        const val ASPECT = 1.28f
    }
}

/** The two lines on the deck's little pixel screen. */
fun screenLines(deck: DeckState): Pair<String, String> {
    val album = deck.album ?: return "RUNOUT · DROP A RECORD ON THE PLATTER" to "NO RECORD"
    val track = deck.currentTrack
    val title = "${deck.trackCode} ${track?.title ?: album.title} · ${album.artist}"
    val s = deck.trackPositionMs / 1000
    val time = "%d:%02d".format(s / 60, s % 60)
    val speed = deck.speed.label + if (deck.pitch != 0f) " " + (if (deck.pitch > 0) "+" else "") + "%.1f".format(deck.pitch) else ""
    val status = when {
        deck.sideFinished -> "SIDE ${deck.side?.letter} END"
        !deck.needleDown -> "CUE ${deck.trackCode}  $speed"
        deck.held -> "STOP $time  $speed"
        else -> "$time  $speed"
    }
    return title to status
}

@Composable
fun Turntable(
    deck: DeckState,
    backdrop: Backdrop,
    dropTargetActive: Boolean,
    onPlatterPlaced: (center: Offset, radius: Float) -> Unit,
    actions: DeckActions,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    pitchEnabled: Boolean = true,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val cover by rememberCover(deck.album)
    val look = remember(deck.album) { discLookFor(deck.album) }
    val labelFallback = remember(deck.album) { fallbackColor(deck.album) }
    val boundaries = remember(deck.side) { deck.side?.trackBoundaries() ?: emptyList() }
    val labelStyle = Room.Label.copy(color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
    val sideText = remember(deck.side) { deck.side?.let { textMeasurer.measure("SIDE ${it.letter}", labelStyle) } }
    val qualityText = remember(deck.album) { deck.album?.let { textMeasurer.measure(it.quality.shortLabel.uppercase(), labelStyle) } }

    // Platter: spins up when the needle goes down, winds down when stopped or lifted.
    var rotation by remember { mutableFloatStateOf(0f) }
    var omega by remember { mutableFloatStateOf(0f) }
    val target = if (deck.album != null && deck.needleDown && !deck.held) 200f * deck.speed.factor * (1f + deck.pitch / 100f) else 0f
    val held = deck.held
    LaunchedEffect(target) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val tau = when {
                target > omega -> 0.45f
                held -> 0.16f
                else -> 0.9f
            }
            omega += (target - omega) * (1f - exp(-dt / tau))
            rotation = (rotation + omega * dt) % 360f
            if (target == 0f && omega < 0.4f) {
                omega = 0f
                break
            }
        }
    }

    // The pixel screen scrolls long titles while the record plays.
    val (line1, line2) = screenLines(deck)
    val pixels1 = remember(line1) { PixelFont.render(context, line1) }
    val pixels2 = remember(line2) { PixelFont.render(context, line2) }
    var scrollStep by remember { mutableLongStateOf(0L) }
    val scrolling = deck.isPlaying && deck.needleDown && !deck.held
    LaunchedEffect(scrolling, line1) {
        scrollStep = 0
        if (!scrolling) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            scrollStep = (now - start) / 90_000_000L // one dot every 90 ms
        }
    }

    // Tonearm: tracks the needle through the side, or follows the finger while dragged.
    var geom by remember { mutableStateOf<DeckGeometry?>(null) }
    var dragAlpha by remember { mutableStateOf<Float?>(null) }
    val g = geom
    val playAlpha = when {
        g == null -> 0f
        deck.needleDown -> g.alphaForFraction(deck.fraction)
        else -> g.alphaRest
    }
    val animAlpha by animateFloatAsState(
        targetValue = playAlpha,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow),
        label = "tonearm",
    )
    val armAlpha = dragAlpha ?: animAlpha
    val currentDeck by rememberUpdatedState(deck)
    val currentArm by rememberUpdatedState(armAlpha)
    var dragPitch by remember { mutableStateOf<Float?>(null) }
    val pitchShown = dragPitch ?: deck.pitch

    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        val plinthH = min(maxHeight.value, maxWidth.value / DeckGeometry.ASPECT).dp
        val plinthW = plinthH * DeckGeometry.ASPECT
        val gm = remember(plinthW, plinthH, density) {
            with(density) { DeckGeometry(Size(plinthW.toPx(), plinthH.toPx())) }
        }
        Box(
            Modifier
                .size(plinthW, plinthH)
                .testTag("turntable")
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(plinthH * 0.07f) },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(18.dp.toPx(), 40.dp.toPx(), chromaticAberration = true)
                    },
                    onDrawSurface = {
                        // Frosted, faintly silver glass.
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.26f), Color(0xFFE9ECF2).copy(alpha = 0.10f), Color.White.copy(alpha = 0.22f)),
                                start = Offset.Zero, end = Offset(size.width, size.height),
                            ),
                        )
                    },
                ),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        // Only a new size makes new geometry; writing state on every callback would
                        // keep the turntable recomposing forever (and burn battery).
                        val size = coords.size.toSize()
                        val geometry = geom?.takeIf { it.size == size } ?: DeckGeometry(size).also { geom = it }
                        onPlatterPlaced(coords.positionInRoot() + geometry.center, geometry.recordR)
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val gmx = geom ?: return@awaitEachGesture
                            val d = currentDeck
                            val slop = 26.dp.toPx()
                            if (d.album != null && gmx.nearArm(down.position, currentArm, slop)) {
                                // Pick up the tonearm.
                                down.consume()
                                val wasDown = d.needleDown
                                if (wasDown) actions.liftNeedle()
                                var moved = 0f
                                var last = down.position
                                dragAlpha = currentArm
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    moved += (change.position - last).getDistance()
                                    last = change.position
                                    dragAlpha = gmx.alphaForPointer(change.position).coerceIn(gmx.alphaIn, gmx.alphaRest)
                                    change.consume()
                                }
                                val a = dragAlpha ?: gmx.alphaRest
                                dragAlpha = null
                                if (moved < viewConfiguration.touchSlop) {
                                    if (!wasDown) actions.needleDrop(0f)
                                } else if (a < gmx.alphaRest - 0.06f) {
                                    actions.needleDrop(gmx.fractionForAlpha(a))
                                }
                            } else if (d.album != null && gmx.isOnRecord(down.position)) {
                                // A quick tap flips the record; a press on a spinning record slows it down.
                                val quickRelease = withTimeoutOrNull(170L) { waitForUpOrCancellation() }
                                if (quickRelease != null) {
                                    if (!d.needleDown) actions.flip()
                                } else if (d.needleDown) {
                                    actions.holdPlatter()
                                    waitForUpOrCancellation()
                                    actions.releasePlatter()
                                } else {
                                    waitForUpOrCancellation()
                                }
                            }
                        }
                    },
            ) {
                val gx = DeckGeometry(size)
                drawPlinth(gx, textMeasurer, deck)
                drawPlatter(gx, rotation, hasRecord = deck.album != null)
                if (deck.album != null) {
                    drawRecord(
                        center = gx.center,
                        radius = gx.recordR,
                        rotation = rotation,
                        look = look,
                        label = cover,
                        labelFallback = labelFallback,
                        trackBoundaries = boundaries,
                        sideText = sideText,
                        qualityText = qualityText,
                    )
                }
                if (dropTargetActive) {
                    drawCircle(
                        Room.Accent.copy(alpha = 0.75f),
                        gx.recordR * 1.03f,
                        gx.center,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                    )
                }
                drawPitchFader(gx, textMeasurer, pitchShown, pitchEnabled)
                drawButtons(gx, textMeasurer, deck)
                drawPixelScreen(gx.screen, pixels1, pixels2, scrollStep)
                drawArmBase(gx)
                drawTonearm(gx, armAlpha)
            }

            // Touch targets for the controls, with names for TalkBack.
            val (l1, l2) = screenLines(deck)
            ControlArea(gm.startStop, "Start or stop", "deck:startstop", stateText = if (deck.needleDown && !deck.held) "Playing" else "Stopped") { actions.startStop() }
            ControlArea(gm.rpm33, "33 rpm", "deck:33", stateText = if (deck.speed == Speed.RPM33) "Selected" else null) {
                if (deck.speed != Speed.RPM33) actions.toggleSpeed()
            }
            ControlArea(gm.rpm45, "45 rpm", "deck:45", stateText = if (deck.speed == Speed.RPM45) "Selected" else null) {
                if (deck.speed != Speed.RPM45) actions.toggleSpeed()
            }
            ControlArea(gm.prev, "Previous track", "deck:prev") { actions.previousTrack() }
            ControlArea(gm.next, "Next track", "deck:next") { actions.nextTrack() }
            Box(
                Modifier
                    .rectOffset(gm.screen)
                    .testTag("deck:screen")
                    .semantics { contentDescription = "$l1. $l2" },
            )
            // The pitch fader: drag the cap; double-tap to snap back to zero.
            val pitchArea = Rect(gm.pitchTrack.left - gm.h * 0.03f, gm.pitchTrack.top, gm.pitchTrack.right + gm.h * 0.03f, gm.pitchTrack.bottom)
            Box(
                Modifier
                    .rectOffset(pitchArea)
                    .testTag("deck:pitch")
                    .semantics { contentDescription = "Pitch"; stateDescription = "%.1f percent".format(deck.pitch) }
                    .pointerInput(pitchEnabled) {
                        if (!pitchEnabled) return@pointerInput
                        detectVerticalDragGestures(
                            onDragStart = { p -> dragPitch = gm.pitchForY(p.y + pitchArea.top) },
                            onDragEnd = { dragPitch?.let(actions::setPitch); dragPitch = null },
                            onDragCancel = { dragPitch = null },
                        ) { ch, _ ->
                            ch.consume()
                            dragPitch = gm.pitchForY(ch.position.y + pitchArea.top)
                        }
                    }
                    .pointerInput(pitchEnabled) {
                        if (!pitchEnabled) return@pointerInput
                        detectTapGestures(onDoubleTap = { actions.setPitch(0f) })
                    },
            )
        }
    }
}

/** Places a box exactly over a rect of the plinth canvas. */
@Composable
private fun Modifier.rectOffset(r: Rect): Modifier {
    val d = LocalDensity.current
    return this
        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
        .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
}

@Composable
private fun BoxScope.ControlArea(r: Rect, label: String, tag: String, stateText: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .rectOffset(r)
            .testTag(tag)
            .semantics { contentDescription = label; if (stateText != null) stateDescription = stateText }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
    )
}

// ---------------------------------------------------------------------------------------------
// Drawing

private val Chrome = Brush.linearGradient(listOf(Color(0xFFF7F8FA), Color(0xFFB9BCC4), Color(0xFFEDEFF3), Color(0xFFA9ADB6)).map { it.copy(alpha = 0.88f) })
private val SmokedGlass = Color(0xFF16171C)

/** Draws small printed text on the plinth; returns its width. */
private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, size: Float, color: Color = Room.Ink.copy(alpha = 0.55f), weight: FontWeight = FontWeight.Medium, centered: Boolean = false): Float {
    val r = tm.measure(text, TextStyle(fontFamily = Room.Inter, fontSize = (size / density).sp, fontWeight = weight, color = color, letterSpacing = (size * 0.08f / density).sp))
    val x = if (centered) at.x - r.size.width / 2f else at.x
    drawText(r, topLeft = Offset(x, at.y))
    return r.size.width.toFloat()
}

private fun DrawScope.drawPlinth(g: DeckGeometry, tm: TextMeasurer, deck: DeckState) {
    val h = g.h
    // A hairline inset, like the edge of a cast top plate.
    drawRoundRect(Color.White.copy(alpha = 0.55f), topLeft = Offset(h * 0.012f, h * 0.012f), size = Size(size.width - h * 0.024f, size.height - h * 0.024f), cornerRadius = CornerRadius(h * 0.06f), style = Stroke(1.2f))

    // 45 adapter well, top left.
    drawCircle(SmokedGlass.copy(alpha = 0.10f), h * 0.052f, g.adapterWell)
    drawCircle(Color.White.copy(alpha = 0.7f), h * 0.052f, g.adapterWell, style = Stroke(1.2f))
    drawLine(Color.Black.copy(alpha = 0.12f), g.adapterWell - Offset(h * 0.03f, 0f), g.adapterWell + Offset(h * 0.03f, 0f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)

    // Power / strobe knob with its light.
    drawCircle(Color.Black.copy(alpha = 0.10f), h * 0.046f, g.powerKnob + Offset(0f, h * 0.005f))
    drawCircle(Chrome, h * 0.042f, g.powerKnob)
    drawCircle(Color.Black.copy(alpha = 0.15f), h * 0.042f, g.powerKnob, style = Stroke(1f))
    drawCircle(SmokedGlass.copy(alpha = 0.85f), h * 0.028f, g.powerKnob)
    drawLine(Color.White.copy(alpha = 0.9f), g.powerKnob, g.powerKnob + Offset(0f, -h * 0.024f), strokeWidth = h * 0.006f, cap = StrokeCap.Round)
    val on = deck.album != null
    drawCircle(if (on) Color(0xFFFF4D4D) else Color(0xFF8C8C8C).copy(alpha = 0.5f), h * 0.007f, g.powerKnob + Offset(h * 0.058f, -h * 0.03f))
    if (on) drawCircle(Color(0xFFFF4D4D).copy(alpha = 0.25f), h * 0.016f, g.powerKnob + Offset(h * 0.058f, -h * 0.03f))
    label(tm, "POWER", g.powerKnob + Offset(h * 0.05f, -h * 0.004f), h * 0.022f)

    // Name plate above the screen.
    val nameW = label(tm, "RUNOUT", Offset(g.screen.left, g.screen.top - h * 0.056f), h * 0.032f, Room.Ink.copy(alpha = 0.85f), FontWeight.SemiBold)
    label(tm, "GLASS DRIVE  RG-1", Offset(g.screen.left + nameW + h * 0.02f, g.screen.top - h * 0.046f), h * 0.018f)
}

private fun DrawScope.drawPlatter(g: DeckGeometry, rotation: Float, hasRecord: Boolean) {
    val h = g.h
    // Shadow under the platter.
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), center = g.center + Offset(0f, h * 0.02f), radius = g.ringR * 1.1f),
        radius = g.ringR * 1.1f, center = g.center + Offset(0f, h * 0.02f),
    )
    // Platter rim: translucent machined aluminium.
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(Color(0xFFD9DBE0), Color(0xFFF8F9FB), Color(0xFFC6C9D0), Color(0xFFF4F5F8), Color(0xFFCFD2D8), Color(0xFFF7F8FA), Color(0xFFD9DBE0)).map { it.copy(alpha = 0.72f) },
            center = g.center,
        ),
        radius = g.ringR, center = g.center,
    )
    drawCircle(Color.Black.copy(alpha = 0.16f), g.ringR, g.center, style = Stroke(1.2f))
    // Four rows of strobe dots on the rim: they appear to stand still at exactly 33 or 45 rpm.
    rotate(rotation, g.center) {
        val rows = 4
        val band = g.ringR - g.platterR
        for (row in 0 until rows) {
            val r = g.platterR + band * (0.2f + row * 0.2f)
            val count = 180
            val pts = List(count) { i ->
                val a = i * (2 * PI / count).toFloat() + row * 0.008f
                g.center + Offset(cos(a), sin(a)) * r
            }
            drawPoints(pts, PointMode.Points, Color(0xFF6E717A).copy(alpha = 0.85f), strokeWidth = h * 0.0072f, cap = StrokeCap.Round)
        }
    }
    drawCircle(Color.Black.copy(alpha = 0.10f), g.platterR, g.center, style = Stroke(1f))
    if (!hasRecord) {
        // A smoked, see-through rubber mat with its concentric ribs.
        drawCircle(SmokedGlass.copy(alpha = 0.50f), g.platterR * 0.985f, g.center)
        for (i in 1..9) {
            drawCircle(Color.White.copy(alpha = 0.06f), g.platterR * (0.16f + i * 0.09f), g.center, style = Stroke(h * 0.004f))
        }
        rotate(rotation, g.center) {
            drawLine(Color.White.copy(alpha = 0.12f), g.center, g.center + Offset(g.platterR * 0.9f, 0f), strokeWidth = 1.5f)
        }
        drawCircle(Color.White.copy(alpha = 0.9f), g.platterR * 0.022f, g.center)
    } else {
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), center = g.center + Offset(0f, h * 0.008f), radius = g.recordR * 1.04f),
            radius = g.recordR * 1.04f, center = g.center + Offset(0f, h * 0.008f),
        )
    }
}

private fun DrawScope.drawArmBase(g: DeckGeometry) {
    val h = g.h
    // Smoked-glass base disc under the arm, with a chrome trim ring.
    drawCircle(Color.Black.copy(alpha = 0.10f), g.armBaseR * 1.02f, g.armBase + Offset(0f, h * 0.012f))
    drawCircle(SmokedGlass.copy(alpha = 0.42f), g.armBaseR, g.armBase)
    drawCircle(Color.White.copy(alpha = 0.55f), g.armBaseR, g.armBase, style = Stroke(h * 0.005f))
    // Height-adjust collar around the pivot.
    drawCircle(Chrome, h * 0.062f, g.pivot)
    drawCircle(Color.Black.copy(alpha = 0.18f), h * 0.062f, g.pivot, style = Stroke(1f))
    for (i in 0 until 24) {
        val a = i * (2 * PI / 24).toFloat()
        drawLine(Color.Black.copy(alpha = 0.18f), g.pivot + Offset(cos(a), sin(a)) * (h * 0.050f), g.pivot + Offset(cos(a), sin(a)) * (h * 0.060f), strokeWidth = 1f)
    }
    // Anti-skate dial.
    drawCircle(Chrome, h * 0.024f, g.antiSkate)
    drawCircle(SmokedGlass.copy(alpha = 0.8f), h * 0.016f, g.antiSkate)
    drawLine(Color.White, g.antiSkate, g.antiSkate + Offset(h * 0.010f, -h * 0.010f), strokeWidth = h * 0.004f)
    // Arm rest post.
    val rest = g.stylus(g.alphaRest) - g.direction(g.alphaRest) * (h * 0.13f)
    drawCircle(Color.Black.copy(alpha = 0.12f), h * 0.02f, rest + Offset(0f, h * 0.006f))
    drawCircle(Chrome, h * 0.017f, rest)
    drawCircle(SmokedGlass.copy(alpha = 0.7f), h * 0.008f, rest)
}

private fun DrawScope.drawTonearm(g: DeckGeometry, alpha: Float) {
    val h = g.h
    val dir = g.direction(alpha)
    val perp = Offset(-dir.y, dir.x)
    val stylus = g.stylus(alpha)
    val angleDeg = Math.toDegrees(atan2(dir.y, dir.x).toDouble()).toFloat()

    // Counterweight behind the pivot: a ribbed chrome drum.
    val cwStart = g.pivot - dir * (h * 0.055f)
    val cwEnd = g.pivot - dir * (h * 0.15f)
    drawLine(Color(0xFF9EA2AA), g.pivot, cwEnd, strokeWidth = h * 0.012f)
    rotate(angleDeg, (cwStart + cwEnd) / 2f) {
        val c = (cwStart + cwEnd) / 2f
        val len = (cwStart - cwEnd).getDistance()
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF2F3F6), Color(0xFF8E929B), Color(0xFFDCDEE3)), startY = c.y - h * 0.04f, endY = c.y + h * 0.04f),
            topLeft = c - Offset(len / 2f, h * 0.04f), size = Size(len, h * 0.08f), cornerRadius = CornerRadius(h * 0.012f),
        )
        for (i in 1..5) {
            val x = c.x - len / 2f + len * i / 6f
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(x, c.y - h * 0.04f), Offset(x, c.y + h * 0.04f), strokeWidth = 1f)
        }
    }

    // The S-shaped arm tube: two gentle opposite bends, then the headshell.
    val headStart = stylus - dir * (h * 0.10f)
    val len = (headStart - g.pivot).getDistance()
    val path = Path().apply {
        moveTo(g.pivot.x, g.pivot.y)
        val c1 = g.pivot + dir * (len * 0.30f) + perp * (h * 0.045f)
        val c2 = g.pivot + dir * (len * 0.72f) - perp * (h * 0.050f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, headStart.x, headStart.y)
    }
    drawPath(path, Color.Black.copy(alpha = 0.12f), style = Stroke(h * 0.018f, cap = StrokeCap.Round))
    drawPath(path, Brush.linearGradient(listOf(Color(0xFFF7F8FA), Color(0xFFB2B6BE), Color(0xFFEFF1F4)), start = g.pivot, end = headStart), style = Stroke(h * 0.013f, cap = StrokeCap.Round))
    drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(h * 0.003f, cap = StrokeCap.Round))

    // Headshell, angled like a real offset head, with its finger lift.
    rotate(angleDeg + 22f, headStart) {
        drawRoundRect(SmokedGlass.copy(alpha = 0.92f), topLeft = headStart - Offset(h * 0.004f, h * 0.022f), size = Size(h * 0.12f, h * 0.044f), cornerRadius = CornerRadius(h * 0.008f))
        drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = headStart + Offset(h * 0.01f, -h * 0.015f), size = Size(h * 0.085f, h * 0.006f), cornerRadius = CornerRadius(h * 0.003f))
        drawLine(Chrome, headStart + Offset(h * 0.08f, h * 0.02f), headStart + Offset(h * 0.12f, h * 0.06f), strokeWidth = h * 0.007f, cap = StrokeCap.Round)
    }

    // Gimbal on top of the pivot.
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB8BBC2)), center = g.pivot, radius = h * 0.038f), h * 0.036f, g.pivot)
    drawCircle(Color.Black.copy(alpha = 0.25f), h * 0.036f, g.pivot, style = Stroke(1.2f))
    drawCircle(SmokedGlass, h * 0.012f, g.pivot)
}

private fun DrawScope.drawPitchFader(g: DeckGeometry, tm: TextMeasurer, pitch: Float, enabled: Boolean) {
    val h = g.h
    val t = g.pitchTrack
    val a = if (enabled) 1f else 0.4f
    // Slot plate with the scale.
    drawRoundRect(Color.White.copy(alpha = 0.35f * a), topLeft = t.topLeft, size = t.size, cornerRadius = CornerRadius(h * 0.01f))
    drawRoundRect(Color.Black.copy(alpha = 0.10f), topLeft = t.topLeft, size = t.size, cornerRadius = CornerRadius(h * 0.01f), style = Stroke(1f))
    val slotX = t.center.x
    drawLine(SmokedGlass.copy(alpha = 0.7f * a), Offset(slotX, t.top + h * 0.015f), Offset(slotX, t.bottom - h * 0.015f), strokeWidth = h * 0.008f, cap = StrokeCap.Round)
    for (i in -4..4) {
        val y = g.pitchY(i * 2f)
        val wide = i == 0 || abs(i) == 4
        drawLine(Room.Ink.copy(alpha = 0.35f * a), Offset(t.left + h * 0.006f, y), Offset(t.left + (if (wide) h * 0.016f else h * 0.010f), y), strokeWidth = 1f)
    }
    // The green light that shows the fader is at exactly zero.
    val zeroLit = abs(pitch) < 0.05f
    drawCircle(if (zeroLit) Color(0xFF39E07A) else Color(0xFF6E7570).copy(alpha = 0.35f), h * 0.007f, Offset(t.left - h * 0.015f, g.pitchY(0f)))
    if (zeroLit) drawCircle(Color(0xFF39E07A).copy(alpha = 0.25f), h * 0.015f, Offset(t.left - h * 0.015f, g.pitchY(0f)))
    // Fader cap.
    val y = g.pitchY(pitch)
    drawRoundRect(Color.Black.copy(alpha = 0.15f), topLeft = Offset(t.left - h * 0.006f, y - h * 0.018f + h * 0.006f), size = Size(t.width + h * 0.012f, h * 0.036f), cornerRadius = CornerRadius(h * 0.006f))
    drawRoundRect(Chrome, topLeft = Offset(t.left - h * 0.006f, y - h * 0.018f), size = Size(t.width + h * 0.012f, h * 0.036f), cornerRadius = CornerRadius(h * 0.006f))
    drawLine(Room.Ink.copy(alpha = 0.6f), Offset(t.left, y), Offset(t.right, y), strokeWidth = 1.2f)
    label(tm, "PITCH", Offset(t.center.x, t.bottom + h * 0.008f), h * 0.019f, centered = true)
    label(tm, if (pitch == 0f) "±8%" else (if (pitch > 0) "+" else "") + "%.1f".format(pitch), Offset(t.center.x, t.bottom + h * 0.032f), h * 0.019f, Room.Ink.copy(alpha = 0.8f), centered = true)
}

private fun DrawScope.flatKey(r: Rect, lit: Boolean) {
    val h = r.height
    drawRoundRect(Color.Black.copy(alpha = 0.12f), topLeft = r.topLeft + Offset(0f, h * 0.12f), size = r.size, cornerRadius = CornerRadius(h * 0.18f))
    drawRoundRect(Chrome, topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(h * 0.18f))
    drawRoundRect(Color.White.copy(alpha = 0.5f), topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(h * 0.18f), style = Stroke(1f))
    if (lit) drawRoundRect(Color(0xFFFF8A3D), topLeft = Offset(r.left + r.width * 0.12f, r.center.y - h * 0.08f), size = Size(r.width * 0.22f, h * 0.16f), cornerRadius = CornerRadius(h * 0.08f))
}

private fun DrawScope.drawButtons(g: DeckGeometry, tm: TextMeasurer, deck: DeckState) {
    val h = g.h
    // Start / stop: the big square key.
    val ss = g.startStop
    drawRoundRect(Color.Black.copy(alpha = 0.14f), topLeft = ss.topLeft + Offset(0f, h * 0.006f), size = ss.size, cornerRadius = CornerRadius(h * 0.012f))
    drawRoundRect(Chrome, topLeft = ss.topLeft, size = ss.size, cornerRadius = CornerRadius(h * 0.012f))
    drawRoundRect(Color.White.copy(alpha = 0.6f), topLeft = ss.topLeft + Offset(h * 0.008f, h * 0.008f), size = Size(ss.width - h * 0.016f, ss.height - h * 0.016f), cornerRadius = CornerRadius(h * 0.008f), style = Stroke(1f))
    label(tm, "START", Offset(ss.center.x, ss.center.y - h * 0.026f), h * 0.019f, Room.Ink.copy(alpha = 0.7f), centered = true)
    label(tm, "STOP", Offset(ss.center.x, ss.center.y + h * 0.002f), h * 0.019f, Room.Ink.copy(alpha = 0.7f), centered = true)
    val playing = deck.needleDown && !deck.held
    drawCircle(if (playing) Color(0xFF39E07A) else Color.Black.copy(alpha = 0.18f), h * 0.006f, Offset(ss.left + h * 0.018f, ss.top + h * 0.018f))

    // 33 / 45.
    flatKey(g.rpm33, deck.speed == Speed.RPM33)
    flatKey(g.rpm45, deck.speed == Speed.RPM45)
    label(tm, "33", Offset(g.rpm33.center.x + h * 0.012f, g.rpm33.top - h * 0.003f), h * 0.026f, Room.Ink.copy(alpha = 0.75f), centered = true)
    label(tm, "45", Offset(g.rpm45.center.x + h * 0.012f, g.rpm45.top - h * 0.003f), h * 0.026f, Room.Ink.copy(alpha = 0.75f), centered = true)

    // Previous / next, in the same style.
    flatKey(g.prev, false)
    flatKey(g.next, false)
    fun tri(c: Offset, s: Float, left: Boolean) {
        val p = Path().apply {
            if (left) { moveTo(c.x + s / 2, c.y - s / 2); lineTo(c.x - s / 2, c.y); lineTo(c.x + s / 2, c.y + s / 2) }
            else { moveTo(c.x - s / 2, c.y - s / 2); lineTo(c.x + s / 2, c.y); lineTo(c.x - s / 2, c.y + s / 2) }
            close()
        }
        drawPath(p, Room.Ink.copy(alpha = 0.75f))
    }
    val s = g.prev.height * 0.42f
    tri(g.prev.center - Offset(s * 0.5f, 0f), s, true); tri(g.prev.center + Offset(s * 0.5f, 0f), s, true)
    tri(g.next.center - Offset(s * 0.5f, 0f), s, false); tri(g.next.center + Offset(s * 0.5f, 0f), s, false)
}

private val LcdLit = Color(0xFF8DFFC4)
private val LcdOff = Color(0xFF8DFFC4).copy(alpha = 0.07f)

/** The little dot-matrix screen: two lines of pixel text; the top line scrolls when it's long. */
private fun DrawScope.drawPixelScreen(r: Rect, top: PixelLine, bottom: PixelLine, step: Long) {
    val h = r.height
    drawRoundRect(Color.Black.copy(alpha = 0.10f), topLeft = r.topLeft + Offset(0f, h * 0.06f), size = r.size, cornerRadius = CornerRadius(h * 0.14f))
    drawRoundRect(Chrome, topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(h * 0.14f))
    val inner = Rect(r.left + h * 0.08f, r.top + h * 0.08f, r.right - h * 0.08f, r.bottom - h * 0.08f)
    drawRoundRect(Color(0xFF0B1410).copy(alpha = 0.86f), topLeft = inner.topLeft, size = inner.size, cornerRadius = CornerRadius(h * 0.08f))

    val rows = PixelLine.ROWS * 2 + 2
    val pitch = (inner.height - h * 0.12f) / rows
    val cols = ((inner.width - h * 0.12f) / pitch).toInt()
    val x0 = inner.left + (inner.width - cols * pitch) / 2f
    val y0 = inner.top + (inner.height - rows * pitch) / 2f
    val dot = Size(pitch * 0.78f, pitch * 0.78f)
    fun line(p: PixelLine, rowOffset: Int, scroll: Boolean) {
        val gap = 12
        val period = p.width + gap
        val shift = if (scroll && p.width > cols) (step % period).toInt() else 0
        for (c in 0 until cols) {
            val src = c + shift
            val col = if (p.width > cols && scroll) src % period else src
            for (row in 0 until PixelLine.ROWS) {
                val lit = col < p.width && p.lit(col, row)
                drawRect(if (lit) LcdLit else LcdOff, topLeft = Offset(x0 + c * pitch, y0 + (row + rowOffset) * pitch), size = dot)
            }
        }
    }
    line(top, 0, scroll = true)
    line(bottom, PixelLine.ROWS + 2, scroll = false)
    // Glass sheen over the screen.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), startY = inner.top, endY = inner.center.y),
        topLeft = inner.topLeft, size = Size(inner.width, inner.height / 2f), cornerRadius = CornerRadius(h * 0.08f),
    )
}

/** A line of text rasterised into screen dots. */
class PixelLine(val width: Int, private val bits: BooleanArray) {
    fun lit(col: Int, row: Int): Boolean = bits[row * width + col]

    companion object {
        /** Glyphs are 5 dots tall, plus a row for descenders. */
        const val ROWS = 6
    }
}

/** Renders text with the Silkscreen pixel font (OFL) at its native size, one screen dot per pixel. */
object PixelFont {
    private var typeface: android.graphics.Typeface? = null
    private val cache = android.util.LruCache<String, PixelLine>(32)

    fun render(context: android.content.Context, text: String): PixelLine {
        val key = text
        cache.get(key)?.let { return it }
        val tf = typeface ?: runCatching { android.graphics.Typeface.createFromAsset(context.assets, "fonts/silkscreen.ttf") }.getOrNull()
            .also { typeface = it } ?: android.graphics.Typeface.MONOSPACE
        val paint = android.graphics.Paint().apply {
            isAntiAlias = false
            textSize = 8f
            this.typeface = tf
            color = android.graphics.Color.BLACK
        }
        val shown = text.uppercase()
        val w = (paint.measureText(shown).toInt() + 2).coerceIn(1, 4000)
        val bmp = android.graphics.Bitmap.createBitmap(w, 12, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawText(shown, 0f, 9f, paint)
        // Silkscreen caps sit on rows 4..8 at 8 px; row 9 holds anything that hangs below.
        val bits = BooleanArray(w * PixelLine.ROWS)
        for (row in 0 until PixelLine.ROWS) for (x in 0 until w) {
            bits[row * w + x] = (bmp.getPixel(x, row + 4) ushr 24) > 0x80
        }
        return PixelLine(w, bits).also { cache.put(key, it) }
    }
}

/** A small liquid-glass button. */
@Composable
fun GlassPill(
    text: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(30.dp)
            .widthIn(min = 48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50) },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(9.dp.toPx(), 14.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(if (active) Room.Accent.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.42f))
                },
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Room.Label.copy(color = if (active) Color.White else Room.Ink, fontWeight = FontWeight.Medium))
    }
}
