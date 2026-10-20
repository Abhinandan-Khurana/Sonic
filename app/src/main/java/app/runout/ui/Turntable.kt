package app.runout.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.PI
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
    fun flip()
    fun startStop() {}
    fun nextTrack() {}
    fun previousTrack() {}
    /** Lift the record off the platter and clear the deck. */
    fun eject() {}
}

/**
 * Where everything sits on the plinth, in the canvas's own pixels. The layout follows the classic
 * direct-drive deck: platter left, S-shaped tonearm on its base at the top right, start/stop at the
 * bottom left, the transport keys and pixel screen at the bottom right. The tonearm is solved with
 * the law of cosines so the stylus follows a true arc across the grooves.
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

    val screen = Rect(w - h * 0.47f, h * 0.855f, w - h * 0.04f, h * 0.965f)
    val startStop = Rect(h * 0.03f, h * 0.86f, h * 0.165f, h * 0.965f)
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

/**
 * How fast the platter turns on screen, in degrees a second: one turn every 3.6 s. A true 33⅓
 * (200°/s) reads as racing on a phone-sized platter, so the picture turns at half that; the music
 * always plays at the speed it was cut.
 */
const val RPM_33_DEG = 100f

/** 180 dots to a row, lit by a 50 Hz flash: the pattern freezes exactly at platter speed. */
private const val STROBE_DOTS = 180
private const val STROBE_SPACING = 360f / STROBE_DOTS
private const val FLASH_PERIOD_S = 0.02f

/**
 * How fast the strobe dots appear to move at platter speed [omega], in degrees a second. Between
 * flashes a dot travels omega × T; you only see where it lands relative to the dot ahead, so at
 * exactly 33⅓ the pattern stands still, and a little off speed it crawls forwards or backwards.
 */
internal fun strobeDrift(omega: Float): Float {
    val perFlash = omega * FLASH_PERIOD_S
    var wrapped = perFlash % STROBE_SPACING
    if (wrapped > STROBE_SPACING / 2f) wrapped -= STROBE_SPACING
    return wrapped / FLASH_PERIOD_S
}

/** The two lines on the deck's little pixel screen. */
fun screenLines(deck: DeckState): Pair<String, String> {
    val album = deck.album ?: return "RUNOUT · DROP A RECORD ON THE PLATTER" to "NO RECORD"
    val track = deck.currentTrack
    val title = "${deck.trackCode} ${track?.title ?: album.title} · ${album.artist}"
    val s = deck.trackPositionMs / 1000
    val time = "%d:%02d".format(s / 60, s % 60)
    val status = when {
        deck.sideFinished -> "SIDE ${deck.side?.letter} END"
        !deck.needleDown -> "CUE ${deck.trackCode}"
        deck.held -> "STOP $time"
        else -> time
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

    // Platter: spins up when the needle goes down, winds down when stopped or lifted. A heavy
    // platter takes its time: about a second and a half to reach speed, longer to coast to a stop.
    var rotation by remember { mutableFloatStateOf(0f) }
    var omega by remember { mutableFloatStateOf(0f) }
    // The strobe dots as you'd see them under mains light: frozen at exactly 33⅓, drifting while
    // the platter is off speed. Drawing them at the platter's true angle instead makes them shimmer
    // at screen refresh rates and the whole deck looks like it's racing.
    var strobe by remember { mutableFloatStateOf(0f) }
    val target = if (deck.album != null && deck.needleDown && !deck.held) RPM_33_DEG else 0f
    val held = deck.held
    LaunchedEffect(target) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
            last = now
            val tau = when {
                target > omega -> 0.5f
                held -> 0.16f
                else -> 1.3f
            }
            omega += (target - omega) * (1f - exp(-dt / tau))
            rotation = (rotation + omega * dt) % 360f
            strobe = (strobe + strobeDrift(omega) * dt) % 360f
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
    // The keys travel down while held, like real switches.
    val ssSource = remember { MutableInteractionSource() }
    val prevSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val ssDown by ssSource.collectIsPressedAsState()
    val prevDown by prevSource.collectIsPressedAsState()
    val nextDown by nextSource.collectIsPressedAsState()
    val currentDeck by rememberUpdatedState(deck)
    val currentArm by rememberUpdatedState(armAlpha)

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
                // The base layer: plinth, platter body, keys. It only redraws when a key moves or
                // the deck's lights change, never per frame.
                val gx = DeckGeometry(size)
                drawPlinth(gx, textMeasurer)
                drawPlatter(gx, hasRecord = deck.album != null)
                drawButtons(gx, textMeasurer, deck, ssDown, prevDown, nextDown)
                drawArmBase(gx, textMeasurer)
            }
            // The strobe ring: redrawn only while the platter is off speed and the dots drift.
            Canvas(Modifier.fillMaxSize()) { drawStrobe(DeckGeometry(size), strobe) }
            // The record (or the bare mat): the only thing that turns every frame.
            Canvas(Modifier.fillMaxSize()) {
                val gx = DeckGeometry(size)
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
                } else drawMat(gx, rotation)
                if (dropTargetActive) {
                    drawCircle(
                        Room.Signal.copy(alpha = 0.85f),
                        gx.recordR * 1.03f,
                        gx.center,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                    )
                }
            }
            Canvas(Modifier.fillMaxSize()) { drawPixelScreen(DeckGeometry(size).screen, pixels1, pixels2, scrollStep) }
            Canvas(Modifier.fillMaxSize()) { drawTonearm(DeckGeometry(size), armAlpha) }

            // Touch targets for the controls, with names for TalkBack.
            val (l1, l2) = screenLines(deck)
            ControlArea(gm.startStop, "Start or stop", "deck:startstop", ssSource, stateText = if (deck.needleDown && !deck.held) "Playing" else "Stopped") { actions.startStop() }
            ControlArea(gm.prev, "Previous track", "deck:prev", prevSource) { actions.previousTrack() }
            ControlArea(gm.next, "Next track", "deck:next", nextSource) { actions.nextTrack() }
            Box(
                Modifier
                    .rectOffset(gm.screen)
                    .testTag("deck:screen")
                    .semantics { contentDescription = "$l1. $l2" },
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
private fun BoxScope.ControlArea(r: Rect, label: String, tag: String, source: MutableInteractionSource, stateText: String? = null, onClick: () -> Unit) {
    val tick = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
    Box(
        Modifier
            .rectOffset(r)
            .testTag(tag)
            .semantics { contentDescription = label; if (stateText != null) stateDescription = stateText }
            .clickable(interactionSource = source, indication = null, role = Role.Button) { tick(); onClick() },
    )
}

// ---------------------------------------------------------------------------------------------
// Drawing

private val Chrome = Brush.linearGradient(listOf(Color(0xFFF7F8FA), Color(0xFFB9BCC4), Color(0xFFEDEFF3), Color(0xFFA9ADB6)).map { it.copy(alpha = 0.88f) })
private val SmokedGlass = Color(0xFF16171C)

/** Draws small printed text on the plinth; returns its width. */
private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, size: Float, color: Color = Room.Ink.copy(alpha = 0.55f), weight: FontWeight = FontWeight.Medium, centered: Boolean = false, alignEnd: Boolean = false): Float {
    val r = tm.measure(text, TextStyle(fontFamily = Room.Inter, fontSize = (size / density).sp, fontWeight = weight, color = color, letterSpacing = (size * 0.08f / density).sp))
    val x = when {
        centered -> at.x - r.size.width / 2f
        alignEnd -> at.x - r.size.width
        else -> at.x
    }
    drawText(r, topLeft = Offset(x, at.y))
    return r.size.width.toFloat()
}

private fun DrawScope.drawPlinth(g: DeckGeometry, tm: TextMeasurer) {
    val h = g.h
    // A hairline inset, like the edge of a cast top plate.
    drawRoundRect(Color.White.copy(alpha = 0.55f), topLeft = Offset(h * 0.012f, h * 0.012f), size = Size(size.width - h * 0.024f, size.height - h * 0.024f), cornerRadius = CornerRadius(h * 0.06f), style = Stroke(1.2f))
    // Four tiny screws in the corners of the top plate.
    listOf(Offset(h * 0.045f, h * 0.045f), Offset(size.width - h * 0.045f, h * 0.045f), Offset(h * 0.045f, size.height - h * 0.045f), Offset(size.width - h * 0.045f, size.height - h * 0.045f))
        .forEach { c ->
            drawCircle(Color.Black.copy(alpha = 0.10f), h * 0.009f, c)
            drawLine(Color.Black.copy(alpha = 0.22f), c - Offset(h * 0.006f, 0f), c + Offset(h * 0.006f, 0f), strokeWidth = 1f)
        }

    // 45 adapter well, top left, with its printed speed beside it.
    drawCircle(SmokedGlass.copy(alpha = 0.10f), h * 0.052f, g.adapterWell)
    drawCircle(Color.White.copy(alpha = 0.7f), h * 0.052f, g.adapterWell, style = Stroke(1.2f))
    drawLine(Color.Black.copy(alpha = 0.12f), g.adapterWell - Offset(h * 0.03f, 0f), g.adapterWell + Offset(h * 0.03f, 0f), strokeWidth = h * 0.012f, cap = StrokeCap.Round)

    // Model plate above the screen: the name, the model, and a fine engraved rule with ticks.
    val plateY = g.screen.top - h * 0.06f
    val nameW = label(tm, "RUNOUT", Offset(g.screen.left, plateY), h * 0.03f, Room.Ink.copy(alpha = 0.85f), FontWeight.SemiBold)
    label(tm, "RG-1", Offset(g.screen.left + nameW + h * 0.018f, plateY + h * 0.004f), h * 0.022f, Room.Signal, FontWeight.SemiBold)
    label(tm, "GLASS DRIVE · 33⅓ RPM", Offset(g.screen.right, plateY + h * 0.009f), h * 0.014f, Room.Ink.copy(alpha = 0.5f), centered = false, alignEnd = true)
    val ruleY = g.screen.top - h * 0.016f
    drawLine(Room.Ink.copy(alpha = 0.22f), Offset(g.screen.left, ruleY), Offset(g.screen.right, ruleY), strokeWidth = 1f)
    val ticks = 24
    for (i in 0..ticks) {
        val x = g.screen.left + (g.screen.right - g.screen.left) * i / ticks
        val long = i % 6 == 0
        drawLine(Room.Ink.copy(alpha = if (long) 0.35f else 0.2f), Offset(x, ruleY), Offset(x, ruleY - h * (if (long) 0.014f else 0.007f)), strokeWidth = 1f)
    }
}

private fun DrawScope.drawPlatter(g: DeckGeometry, hasRecord: Boolean) {
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
    drawCircle(Color.Black.copy(alpha = 0.10f), g.platterR, g.center, style = Stroke(1f))
    if (hasRecord) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), center = g.center + Offset(0f, h * 0.008f), radius = g.recordR * 1.04f),
            radius = g.recordR * 1.04f, center = g.center + Offset(0f, h * 0.008f),
        )
    }
}

/** Four rows of strobe dots on the rim, drawn where the eye sees them (see [strobeDrift]). */
private fun DrawScope.drawStrobe(g: DeckGeometry, angle: Float) {
    val h = g.h
    rotate(angle, g.center) {
        val band = g.ringR - g.platterR
        for (row in 0 until 4) {
            val r = g.platterR + band * (0.2f + row * 0.2f)
            val pts = List(STROBE_DOTS) { i ->
                val a = i * (2 * PI / STROBE_DOTS).toFloat() + row * 0.008f
                g.center + Offset(cos(a), sin(a)) * r
            }
            drawPoints(pts, PointMode.Points, Color(0xFF5E6168).copy(alpha = 0.8f), strokeWidth = h * 0.0062f, cap = StrokeCap.Round)
        }
    }
}

/** The bare platter: a smoked, see-through rubber mat with its concentric ribs. */
private fun DrawScope.drawMat(g: DeckGeometry, rotation: Float) {
    val h = g.h
    drawCircle(SmokedGlass.copy(alpha = 0.50f), g.platterR * 0.985f, g.center)
    for (i in 1..9) {
        drawCircle(Color.White.copy(alpha = 0.06f), g.platterR * (0.16f + i * 0.09f), g.center, style = Stroke(h * 0.004f))
    }
    rotate(rotation, g.center) {
        drawLine(Color.White.copy(alpha = 0.12f), g.center, g.center + Offset(g.platterR * 0.9f, 0f), strokeWidth = 1.5f)
    }
    drawCircle(Color.White.copy(alpha = 0.9f), g.platterR * 0.022f, g.center)
}

private fun DrawScope.drawArmBase(g: DeckGeometry, tm: TextMeasurer) {
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
    // Anti-skate dial with its engraved 0–3 scale.
    val scaleR = h * 0.036f
    for (i in 0..12) {
        val a = (PI * (0.85 + i * 0.1)).toFloat()
        val long = i % 4 == 0
        drawLine(
            Room.Ink.copy(alpha = if (long) 0.45f else 0.25f),
            g.antiSkate + Offset(cos(a), sin(a)) * scaleR,
            g.antiSkate + Offset(cos(a), sin(a)) * (scaleR + h * (if (long) 0.012f else 0.006f)),
            strokeWidth = 1f,
        )
    }
    drawCircle(Chrome, h * 0.024f, g.antiSkate)
    drawCircle(SmokedGlass.copy(alpha = 0.8f), h * 0.016f, g.antiSkate)
    drawLine(Room.Signal, g.antiSkate, g.antiSkate + Offset(h * 0.010f, -h * 0.010f), strokeWidth = h * 0.004f)
    label(tm, "ANTI-SKATE", Offset(g.antiSkate.x, g.antiSkate.y + h * 0.05f), h * 0.012f, Room.Ink.copy(alpha = 0.45f), centered = true)
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

    // Counterweight behind the pivot: a ribbed drum in signal orange.
    val cwStart = g.pivot - dir * (h * 0.055f)
    val cwEnd = g.pivot - dir * (h * 0.15f)
    drawLine(Color(0xFF9EA2AA), g.pivot, cwEnd, strokeWidth = h * 0.012f)
    rotate(angleDeg, (cwStart + cwEnd) / 2f) {
        val c = (cwStart + cwEnd) / 2f
        val len = (cwStart - cwEnd).getDistance()
        drawRoundRect(Color.Black.copy(alpha = 0.14f), topLeft = c - Offset(len / 2f, h * 0.036f), size = Size(len, h * 0.08f), cornerRadius = CornerRadius(h * 0.012f))
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFFF8A5C), Room.Signal, Color(0xFFC9461A)), startY = c.y - h * 0.04f, endY = c.y + h * 0.04f),
            topLeft = c - Offset(len / 2f, h * 0.04f), size = Size(len, h * 0.08f), cornerRadius = CornerRadius(h * 0.012f),
        )
        for (i in 1..5) {
            val x = c.x - len / 2f + len * i / 6f
            drawLine(Color.Black.copy(alpha = 0.16f), Offset(x, c.y - h * 0.04f), Offset(x, c.y + h * 0.04f), strokeWidth = 1f)
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

    // Headshell, angled like a real offset head, with its orange finger lift.
    rotate(angleDeg + 22f, headStart) {
        drawRoundRect(SmokedGlass.copy(alpha = 0.92f), topLeft = headStart - Offset(h * 0.004f, h * 0.022f), size = Size(h * 0.12f, h * 0.044f), cornerRadius = CornerRadius(h * 0.008f))
        drawRoundRect(Color.White.copy(alpha = 0.18f), topLeft = headStart + Offset(h * 0.01f, -h * 0.015f), size = Size(h * 0.085f, h * 0.006f), cornerRadius = CornerRadius(h * 0.003f))
        drawLine(Room.Signal, headStart + Offset(h * 0.08f, h * 0.02f), headStart + Offset(h * 0.12f, h * 0.06f), strokeWidth = h * 0.009f, cap = StrokeCap.Round)
    }

    // Gimbal on top of the pivot.
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB8BBC2)), center = g.pivot, radius = h * 0.038f), h * 0.036f, g.pivot)
    drawCircle(Color.Black.copy(alpha = 0.25f), h * 0.036f, g.pivot, style = Stroke(1.2f))
    drawCircle(SmokedGlass, h * 0.012f, g.pivot)
}

/**
 * A hardware key, Teenage Engineering style: a cap sitting on a darker skirt, with real travel.
 * Pressed, the cap sinks onto the skirt and its shadow tightens.
 */
private fun DrawScope.teKey(r: Rect, cap: Color, skirt: Color, pressed: Boolean): Rect {
    val h = r.height
    val travel = h * 0.09f
    val radius = CornerRadius(h * 0.16f)
    drawRoundRect(Color.Black.copy(alpha = if (pressed) 0.10f else 0.16f), topLeft = r.topLeft + Offset(0f, travel * (if (pressed) 1.2f else 1.8f)), size = r.size, cornerRadius = radius)
    drawRoundRect(skirt, topLeft = r.topLeft + Offset(0f, travel), size = r.size, cornerRadius = radius)
    val face = Rect(r.topLeft + Offset(0f, if (pressed) travel * 0.8f else 0f), r.size)
    drawRoundRect(cap, topLeft = face.topLeft, size = face.size, cornerRadius = radius)
    // A clear glass coat over the cap: its colour shows through untouched, with a glossy top,
    // a soft diagonal reflection and a bright rim where the light catches the edge.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f), Color.Transparent), startY = face.top, endY = face.top + face.height * 0.62f),
        topLeft = face.topLeft, size = face.size, cornerRadius = radius,
    )
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.Transparent, 0.42f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.22f), 0.6f to Color.Transparent, 1f to Color.Transparent,
            start = face.topLeft, end = face.bottomRight,
        ),
        topLeft = face.topLeft, size = face.size, cornerRadius = radius,
    )
    drawRoundRect(Color.Black.copy(alpha = 0.10f), topLeft = face.topLeft + Offset(0f, face.height * 0.55f), size = Size(face.width, face.height * 0.45f), cornerRadius = radius)
    drawRoundRect(Color.White.copy(alpha = 0.75f), topLeft = face.topLeft, size = face.size, cornerRadius = radius, style = Stroke(1.2f))
    return face
}

private val KeyGrey = Color(0xFFEDEEF1)
private val KeyGreySkirt = Color(0xFFB4B7BF)
private val SignalSkirt = Color(0xFFB83E15)

private fun DrawScope.drawButtons(g: DeckGeometry, tm: TextMeasurer, deck: DeckState, ssDown: Boolean, prevDown: Boolean, nextDown: Boolean) {
    val h = g.h
    val playing = deck.needleDown && !deck.held
    // Start / stop: the big orange key, with its engraved label and a status light above it.
    val ss = g.startStop
    val led = Offset(ss.left + h * 0.012f, ss.top - h * 0.028f)
    if (playing) drawCircle(Brush.radialGradient(listOf(Room.Signal.copy(alpha = 0.55f), Color.Transparent), center = led, radius = h * 0.03f), h * 0.03f, led)
    drawCircle(if (playing) Room.Signal else Color.Black.copy(alpha = 0.16f), h * 0.0075f, led)
    label(tm, "START · STOP", Offset(led.x + h * 0.018f, led.y - h * 0.01f), h * 0.0145f, Room.Ink.copy(alpha = 0.55f))
    val face = teKey(ss, Room.Signal, SignalSkirt, ssDown)
    // Printed on the cap: play and stop symbols.
    val c = face.center
    val s = face.height * 0.18f
    drawPath(Path().apply { moveTo(c.x - s * 1.5f, c.y - s); lineTo(c.x - s * 0.1f, c.y); lineTo(c.x - s * 1.5f, c.y + s); close() }, Color.White)
    drawRect(Color.White, topLeft = Offset(c.x + s * 0.4f, c.y - s * 0.85f), size = Size(s * 1.7f, s * 1.7f))

    // Previous / next: grey keys under one engraved label.
    label(tm, "TRACK", Offset((g.prev.left + g.next.right) / 2f, g.prev.top - h * 0.032f), h * 0.0145f, Room.Ink.copy(alpha = 0.55f), centered = true)
    val pf = teKey(g.prev, KeyGrey, KeyGreySkirt, prevDown)
    val nf = teKey(g.next, KeyGrey, KeyGreySkirt, nextDown)
    fun tri(c: Offset, s: Float, left: Boolean) {
        val p = Path().apply {
            if (left) { moveTo(c.x + s / 2, c.y - s / 2); lineTo(c.x - s / 2, c.y); lineTo(c.x + s / 2, c.y + s / 2) }
            else { moveTo(c.x - s / 2, c.y - s / 2); lineTo(c.x + s / 2, c.y); lineTo(c.x - s / 2, c.y + s / 2) }
            close()
        }
        drawPath(p, Room.Ink.copy(alpha = 0.78f))
    }
    val t = g.prev.height * 0.36f
    tri(pf.center - Offset(t * 0.5f, 0f), t, true); tri(pf.center + Offset(t * 0.5f, 0f), t, true)
    tri(nf.center - Offset(t * 0.5f, 0f), t, false); tri(nf.center + Offset(t * 0.5f, 0f), t, false)
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

    // A finer matrix than the glyphs need: smaller dots, with a dark margin round the two lines.
    val rows = PixelLine.ROWS * 2 + 6
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
    line(top, 2, scroll = true)
    line(bottom, PixelLine.ROWS + 4, scroll = false)
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .touchHeight()
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
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Room.Label.copy(color = if (active) Color.White else Room.Ink, fontWeight = FontWeight.Medium))
    }
}
