package app.runout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.playback.DeckState
import app.runout.playback.Speed
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Plate = Color(0xFFEFE6D2)
private val DeckInk = Color(0xFF2B2925)
private val Window = Color(0xFF0F1713)
private val Lcd = Color(0xFF8DFFC4)
private val LcdQuiet = Color(0xFF7EC8FF)

/** Nearest band start. Boundaries are the later tracks' starts; 0 is the first band. */
internal fun snapToBand(fraction: Float, boundaries: List<Float>): Float {
    val marks = listOf(0f) + boundaries.filter { it in 0f..1f }
    return marks.minBy { abs(it - fraction) }
}

/** Printed transcription deck. The record, arm, cue, and motor are the controls. */
@Composable
fun InkedDeck(
    deck: DeckState,
    dropTargetActive: Boolean,
    onPlatterPlaced: (center: Offset, radius: Float) -> Unit,
    actions: DeckActions,
    modifier: Modifier = Modifier,
    showControls: Boolean = false,
) {
    val cover by rememberCover(deck.album)
    val look = remember(deck.album) { discLookFor(deck.album) }
    val fallback = remember(deck.album) { fallbackColor(deck.album) }
    var arm by remember(deck.album?.id, deck.fraction) { mutableFloatStateOf(deck.fraction) }
    val speed = if (deck.speed == Speed.RPM45) "45" else "33⅓"
    val lcd = if (deck.album == null) "$speed  ·  NO RECORD" else "$speed  ·  ${deck.trackCode.ifBlank { "A1" }}"
    val status = when {
        deck.album == null -> "PLACE A RECORD"
        deck.sideFinished -> "TURN THE RECORD OVER"
        deck.held -> "BRAKING"
        deck.needleDown && deck.isPlaying -> "SIDE ${deck.side?.letter ?: "A"}  ·  PLAYING"
        deck.needleDown -> "SIDE ${deck.side?.letter ?: "A"}  ·  CUE SETTLING"
        else -> "NEEDLE UP"
    }
    Box(
        modifier.fillMaxSize().testTag("turntable").semantics {
            contentDescription = "Transcription deck. $status"
            customActions = listOf(
                CustomAccessibilityAction("Lower needle") { actions.needleDrop(0f); true },
                CustomAccessibilityAction("Lift needle") { actions.liftNeedle(); true },
                CustomAccessibilityAction("Turn record over") { actions.flip(); true },
                CustomAccessibilityAction("Put record away") { actions.eject(); true },
            )
        },
    ) {
        Canvas(
            Modifier.fillMaxSize().padding(8.dp).onGloballyPositioned { coords ->
                val side = min(coords.size.width, coords.size.height).toFloat()
                onPlatterPlaced(coords.positionInRoot() + Offset(coords.size.width * 0.38f, coords.size.height * 0.40f), side * 0.30f)
            },
        ) {
            drawRoundRect(Plate, cornerRadius = CornerRadius(28f, 28f))
            var y = 0f
            while (y < size.height + size.width) {
                drawLine(DeckInk.copy(alpha = 0.08f), Offset(0f, y), Offset(y, 0f), 1f)
                y += 7f
            }
            drawRoundRect(DeckInk.copy(alpha = 0.35f), style = Stroke(1.5f), cornerRadius = CornerRadius(28f, 28f))
            val c = Offset(size.width * 0.38f, size.height * 0.40f)
            val r = min(size.width, size.height) * 0.30f
            drawCircle(Color(0xFFD9D3C6), r * 1.08f, c)
            repeat(72) { i ->
                val a = i * 6.2832f / 72f
                drawLine(DeckInk, c + Offset(cos(a), sin(a)) * r * 1.02f, c + Offset(cos(a), sin(a)) * r * 1.08f, 1.2f)
            }
            if (deck.album == null) drawCircle(Color(0xFFC9C3B6), r, c) else drawRecord(c, r, 0f, look, cover, fallback, deck.side?.trackBoundaries().orEmpty(), null, null)
            drawCircle(Color.White.copy(alpha = 0.7f), r * 0.08f, c)
            if (dropTargetActive) drawCircle(Color(0xFFD9481F), r * 1.04f, c, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            val pivot = Offset(size.width * 0.78f, size.height * 0.18f)
            val stylus = if (deck.needleDown) c + Offset(r * (0.15f + arm * 0.7f), -r * 0.25f) else Offset(size.width * 0.84f, size.height * 0.34f)
            drawCircle(Color(0xFFD7D8DC), r * 0.12f, pivot)
            drawLine(Color(0xFFE7E7EA), pivot, stylus, r * 0.04f)
            drawCircle(DeckInk, r * 0.035f, stylus)
            repeat(4) { i ->
                val sx = if (i % 2 == 0) 22f else size.width - 22f
                val sy = if (i < 2) 22f else size.height - 22f
                drawCircle(DeckInk.copy(alpha = 0.45f), 4f, Offset(sx, sy))
            }
            drawRoundRect(Window, topLeft = Offset(size.width * 0.34f, size.height * 0.78f), size = Size(size.width * 0.58f, size.height * 0.16f), cornerRadius = CornerRadius(8f, 8f))
        }
        if (deck.album != null) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 36.dp).size(180.dp).testTag("deck:record").pointerInput(deck.sideFinished, deck.needleDown) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var total = Offset.Zero
                        var holding = false
                        val started = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            total += change.positionChange()
                            if (!holding && deck.needleDown && total.getDistance() < 16f && change.uptimeMillis - started > 280) {
                                holding = true
                                actions.holdPlatter()
                            }
                            change.consume()
                        }
                        if (holding) actions.releasePlatter()
                        else when {
                            total.y < -48f && deck.sideFinished -> actions.flip()
                            total.y > 64f -> actions.eject()
                        }
                    }
                },
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 28.dp).size(96.dp).testTag("deck:arm").pointerInput(deck.album?.id, deck.needleDown) {
                    detectDragGestures(
                        onDragStart = { if (deck.needleDown) actions.liftNeedle() },
                        onDrag = { _, amount -> arm = (arm + amount.y / size.height).coerceIn(0f, 1f) },
                        onDragEnd = { actions.needleDrop(snapToBand(arm, deck.side?.trackBoundaries().orEmpty())) },
                    )
                },
            )
        }
        BasicText("RUNOUT  TRANSCRIPTION DECK · RT-1", Modifier.align(Alignment.TopStart).padding(18.dp), style = Room.Label.copy(color = DeckInk, fontSize = 9.sp, letterSpacing = 0.6.sp))
        BasicText(lcd, Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 28.dp), style = Room.Label.copy(color = Lcd, fontFamily = FontFamily.Monospace, fontSize = 11.sp))
        BasicText(status, Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 12.dp).testTag("deck:screen"), style = Room.Label.copy(color = LcdQuiet, fontFamily = FontFamily.Monospace, fontSize = 10.sp))
        DeckChip("MOTOR", if (deck.isPlaying || deck.needleDown) "ON" else "OFF", Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 58.dp).testTag("deck:motor")) { actions.startStop() }
        DeckChip("33", if (deck.speed == Speed.RPM33) "ON" else "—", Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp).testTag("deck:33")) { if (deck.speed != Speed.RPM33) actions.toggleSpeed() }
        DeckChip("45", if (deck.speed == Speed.RPM45) "ON" else "—", Modifier.align(Alignment.BottomEnd).padding(end = 108.dp, bottom = 58.dp).testTag("deck:45")) { if (deck.speed != Speed.RPM45) actions.toggleSpeed() }
        DeckChip("CUE", if (deck.needleDown) "DOWN" else "UP", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).testTag("deck:cue")) {
            if (deck.needleDown) actions.liftNeedle() else actions.needleDrop(0f)
        }
        if (showControls) Row(Modifier.align(Alignment.TopCenter).padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DeckChip("LOWER", "", Modifier.testTag("deck-strip:lower")) { actions.needleDrop(0f) }
            DeckChip("LIFT", "", Modifier.testTag("deck-strip:lift")) { actions.liftNeedle() }
            DeckChip("FLIP", "", Modifier.testTag("deck-strip:flip")) { actions.flip() }
            DeckChip("AWAY", "", Modifier.testTag("deck-strip:put-away")) { actions.eject() }
        }
    }
}

@Composable
private fun DeckChip(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    BasicText(
        if (value.isBlank()) label else "$label $value",
        modifier.size(width = 72.dp, height = 48.dp).clickable(onClick = onClick).padding(8.dp),
        style = Room.Label.copy(color = DeckInk, fontWeight = FontWeight.Medium, fontSize = 10.sp),
    )
}
