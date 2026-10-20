package app.runout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.playback.DeckState
import app.runout.playback.Speed
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Plate = Color(0xFFEFE6D2)
private val DeckInk = Color(0xFF2B2925)
private val Window = Color(0xFF0F1713)
private val Lcd = Color(0xFF8DFFC4)
private val LcdQuiet = Color(0xFF7EC8FF)

/** The printed transcription deck from the pen.dev home. Playback still goes through [DeckActions]. */
@Composable
fun InkedDeck(
    deck: DeckState,
    dropTargetActive: Boolean,
    onPlatterPlaced: (center: Offset, radius: Float) -> Unit,
    actions: DeckActions,
    modifier: Modifier = Modifier,
) {
    val cover by rememberCover(deck.album)
    val look = remember(deck.album) { discLookFor(deck.album) }
    val fallback = remember(deck.album) { fallbackColor(deck.album) }
    val speed = if (deck.speed == Speed.RPM45) "45" else "33⅓"
    val lcd = if (deck.album == null) "$speed  ·  NO RECORD" else "$speed  ·  ${deck.trackCode.ifBlank { "A1" }}"
    val status = when {
        deck.album == null -> "PLACE A RECORD"
        deck.sideFinished -> "TURN THE RECORD OVER"
        deck.needleDown && deck.isPlaying -> "SIDE ${deck.side?.letter ?: "A"}  ·  PLAYING"
        deck.needleDown -> "SIDE ${deck.side?.letter ?: "A"}  ·  PAUSED"
        else -> "NEEDLE UP"
    }
    Box(modifier.fillMaxSize().testTag("turntable")) {
        Canvas(
            Modifier.fillMaxSize().padding(8.dp).onGloballyPositioned { coords ->
                val side = min(coords.size.width, coords.size.height).toFloat()
                val center = coords.positionInRoot() + Offset(coords.size.width * 0.38f, coords.size.height * 0.40f)
                onPlatterPlaced(center, side * 0.30f)
            },
        ) {
            val plate = Size(size.width, size.height)
            drawRoundRect(Plate, cornerRadius = CornerRadius(28f, 28f))
            val step = 7f
            var y = 0f
            while (y < plate.height + plate.width) {
                drawLine(DeckInk.copy(alpha = 0.08f), Offset(0f, y), Offset(y, 0f), strokeWidth = 1f)
                y += step
            }
            drawRoundRect(DeckInk.copy(alpha = 0.35f), style = Stroke(1.5f), cornerRadius = CornerRadius(28f, 28f))
            val c = Offset(size.width * 0.38f, size.height * 0.40f)
            val r = min(size.width, size.height) * 0.30f
            drawCircle(Color(0xFFD9D3C6), r * 1.08f, c)
            repeat(72) { i ->
                val a = i * 6.2832f / 72f
                drawLine(DeckInk, c + Offset(cos(a), sin(a)) * r * 1.02f, c + Offset(cos(a), sin(a)) * r * 1.08f, 1.2f)
            }
            if (deck.album == null) {
                drawCircle(Color(0xFFC9C3B6), r, c)
                drawCircle(Color.White.copy(alpha = 0.55f), r * 0.16f, c)
            } else {
                drawRecord(c, r, 0f, look, cover, fallback, deck.side?.trackBoundaries().orEmpty(), null, null)
            }
            if (dropTargetActive) drawCircle(Color(0xFFD9481F), r * 1.04f, c, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            val pivot = Offset(size.width * 0.78f, size.height * 0.16f)
            drawCircle(Color(0xFFD7D8DC), r * 0.16f, pivot)
            drawLine(Color(0xFFE7E7EA), pivot, c + Offset(r * 0.55f, -r * 0.15f), r * 0.045f)
            drawRoundRect(Window, topLeft = Offset(size.width * 0.34f, size.height * 0.78f), size = Size(size.width * 0.58f, size.height * 0.16f), cornerRadius = CornerRadius(8f, 8f))
        }
        BasicText("RUNOUT  TRANSCRIPTION DECK · RT-1", Modifier.align(Alignment.TopStart).padding(18.dp), style = Room.Label.copy(color = DeckInk, fontSize = 9.sp, letterSpacing = 0.6.sp))
        BasicText(lcd, Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 28.dp), style = Room.Label.copy(color = Lcd, fontFamily = FontFamily.Monospace, fontSize = 11.sp))
        BasicText(status, Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 12.dp).testTag("deck:screen"), style = Room.Label.copy(color = LcdQuiet, fontFamily = FontFamily.Monospace, fontSize = 10.sp))
        DeckChip("MOTOR", if (deck.needleDown) "ON" else "OFF", Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 54.dp).testTag("deck:motor").semantics { contentDescription = "Motor switch" }) { actions.startStop() }
        DeckChip("33", if (deck.speed == Speed.RPM33) "33" else "33", Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp).testTag("deck:33")) { if (deck.speed != Speed.RPM33) actions.toggleSpeed() }
        DeckChip("CUE", if (deck.needleDown) "UP" else "DOWN", Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).testTag("deck:cue").semantics { contentDescription = "Cue lever" }) {
            if (deck.needleDown) actions.liftNeedle() else actions.needleDrop(0f)
        }
    }
}

@Composable
private fun DeckChip(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    BasicText(
        "$label $value",
        modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp),
        style = Room.Label.copy(color = DeckInk, fontWeight = FontWeight.Medium, fontSize = 10.sp),
    )
}
