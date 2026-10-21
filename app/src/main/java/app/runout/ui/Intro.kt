package app.runout.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How long the intro runs, and how long it holds the finished wordmark before handing over. */
private const val INTRO_MS = 3400
private const val HOLD_MS = 450

/**
 * The first thing a new listener sees: the whole idea of Runout in three seconds. A sleeve slides
 * in, its disc slides out and settles on a platter, the tonearm swings over and drops, the grooves
 * light up in signal orange, and RUNOUT types itself out in dot-matrix. Tap anywhere to skip.
 * With system animations off it hands over straight away.
 */
@Composable
fun RunoutIntro(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val finish by rememberUpdatedState(onFinished)
    val t = remember { Animatable(0f) }
    val animationsOff = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
    LaunchedEffect(Unit) {
        if (animationsOff) { finish(); return@LaunchedEffect }
        t.animateTo(1f, tween(INTRO_MS, easing = LinearEasing))
        // Hold the finished wordmark on the frame clock (a delay would not be).
        Animatable(0f).animateTo(1f, tween(HOLD_MS))
        finish()
    }
    Canvas(
        modifier
            .fillMaxSize()
            .testTag("onboarding-intro")
            .semantics { contentDescription = "Runout. A record drops onto a turntable. Tap to skip." }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { finish() },
    ) { drawIntro(t.value) }
}

/** 0 → 1 over [a, b], eased; clamped outside it. */
private fun phase(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun lerp(a: Offset, b: Offset, f: Float) = Offset(lerp(a.x, b.x, f), lerp(a.y, b.y, f))

internal fun DrawScope.drawIntro(t: Float) {
    drawRect(Room.Background)
    val w = size.width
    val h = size.height
    val unit = minOf(w, h * 0.62f)
    // Off centre to the left, leaving room for the tonearm on the right.
    val center = Offset(w * 0.45f, h * 0.40f)
    val platterR = unit * 0.29f
    val recordR = platterR * 0.94f

    // A soft pool of light, so the scene isn't floating on flat white.
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFE6E2FF), Color.Transparent), center = center, radius = unit * 0.85f),
        radius = unit * 0.85f, center = center,
    )

    // The platter fades up first: a ring of strobe dots around a smoked mat.
    val platterIn = phase(t, 0.0f, 0.18f)
    drawCircle(Color.Black.copy(alpha = 0.08f * platterIn), platterR * 1.06f, center + Offset(0f, unit * 0.02f))
    drawCircle(Color(0xFFDADCE1).copy(alpha = platterIn), platterR * 1.04f, center)
    for (i in 0 until 120) {
        val a = i * (2 * PI / 120).toFloat()
        drawCircle(Color(0xFF6E717A).copy(alpha = 0.7f * platterIn), unit * 0.0045f, center + Offset(cos(a), sin(a)) * (platterR * 1.02f))
    }
    drawCircle(Color(0xFF26272C).copy(alpha = 0.55f * platterIn), platterR, center)

    // Sleeve: slides in from the left, holds while the disc comes out, then slides away.
    val sleeveSize = recordR * 2.1f
    val sleeveIn = phase(t, 0.06f, 0.24f)
    val sleeveOut = phase(t, 0.42f, 0.56f)
    val sleeveCenter = Offset(lerp(-sleeveSize, w * 0.5f, sleeveIn) - sleeveOut * w, h * 0.74f)

    // Disc: slides up out of the sleeve, then flies to the spindle and lands with a small squash.
    val out = phase(t, 0.22f, 0.36f)
    val fly = phase(t, 0.36f, 0.5f)
    val land = phase(t, 0.5f, 0.56f)
    val discStart = lerp(sleeveCenter, sleeveCenter - Offset(0f, sleeveSize * 0.55f), out)
    val discPos = lerp(discStart, center, fly)
    val squash = 1f - 0.06f * sin(land * PI.toFloat())
    val spin = phase(t, 0.62f, 1f) * 540f + out * 30f

    if (sleeveIn > 0f && out < 1f) {
        // Disc behind the sleeve while it is still being drawn out.
        drawDisc(discPos, recordR * squash, spin, glow = 0f)
    }
    if (sleeveIn > 0f && sleeveOut < 1f) {
        val tl = sleeveCenter - Offset(sleeveSize / 2f, sleeveSize / 2f)
        drawRect(Color.Black.copy(alpha = 0.10f), topLeft = tl + Offset(0f, unit * 0.02f), size = Size(sleeveSize, sleeveSize))
        drawRect(
            Brush.linearGradient(listOf(Color(0xFFE85D9C), Color(0xFF8A4FD8), Color(0xFF3B3FA8)), start = tl, end = tl + Offset(sleeveSize, sleeveSize)),
            topLeft = tl, size = Size(sleeveSize, sleeveSize),
        )
        drawCircle(Color(0xFFFFB37A), sleeveSize * 0.17f, tl + Offset(sleeveSize * 0.5f, sleeveSize * 0.62f))
        drawRect(Color(0xFF1B1D3A).copy(alpha = 0.8f), topLeft = tl + Offset(0f, sleeveSize * 0.7f), size = Size(sleeveSize, sleeveSize * 0.3f))
    }
    if (out >= 1f) {
        // Out of the sleeve: the grooves light up once the needle is down.
        val glow = phase(t, 0.66f, 0.82f)
        drawDisc(discPos, recordR * squash, spin, glow)
    }

    // Tonearm: swings in from its rest and drops onto the lead-in groove.
    val pivot = center + Offset(platterR * 1.22f, -platterR * 0.9f)
    // Solved for this pivot: at rest the needle sits just off the record; playing, on the lead-in groove.
    val restAngle = 95f
    val playAngle = 110f
    val swing = phase(t, 0.56f, 0.66f)
    val angle = lerp(restAngle, playAngle, swing)
    val armLen = platterR * 1.5f
    val rad = Math.toRadians(angle.toDouble()).toFloat()
    val tip = pivot + Offset(cos(rad), sin(rad)) * armLen
    val drop = phase(t, 0.64f, 0.68f)
    val armIn = phase(t, 0.1f, 0.2f)
    if (armIn > 0f) {
        drawCircle(Color(0xFFCBCDD3).copy(alpha = armIn), unit * 0.05f, pivot)
        drawLine(Color(0xFFB2B6BE).copy(alpha = armIn), pivot, tip, strokeWidth = unit * 0.016f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.7f * armIn), pivot, tip, strokeWidth = unit * 0.004f, cap = StrokeCap.Round)
        // Counterweight and headshell in the deck's soft metal orange.
        val back = pivot - Offset(cos(rad), sin(rad)) * unit * 0.09f
        drawLine(Room.Signal.copy(alpha = armIn), pivot, back, strokeWidth = unit * 0.05f, cap = StrokeCap.Butt)
        rotate(angle + 20f, tip) {
            drawRoundRect(Color(0xFF16171C).copy(alpha = armIn), topLeft = tip - Offset(unit * 0.01f, unit * 0.022f * (1f - drop * 0.25f)), size = Size(unit * 0.09f, unit * 0.04f), cornerRadius = CornerRadius(unit * 0.008f))
        }
        drawCircle(Color.White.copy(alpha = armIn), unit * 0.022f, pivot)
    }

    // RUNOUT, typed out letter by letter in dot-matrix under the deck.
    val typing = phase(t, 0.74f, 0.96f)
    drawDotWordmark("RUNOUT", Offset(w / 2f, h * 0.76f), dot = unit * 0.016f, shown = typing)
}

/** A record seen from above: grooves, a label, and (when lit) orange light running round the grooves. */
private fun DrawScope.drawDisc(c: Offset, r: Float, spin: Float, glow: Float) {
    drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.02f, c + Offset(0f, r * 0.04f))
    drawCircle(Color(0xFF17181D), r, c)
    for (i in 1..14) {
        val gr = r * (0.42f + i * 0.04f)
        drawCircle(Color.White.copy(alpha = 0.05f), gr, c, style = Stroke(r * 0.004f))
    }
    if (glow > 0f) {
        rotate(spin, c) {
            drawCircle(
                Brush.sweepGradient(listOf(Color.Transparent, Room.Signal.copy(alpha = 0.55f * glow), Color.Transparent, Room.Signal.copy(alpha = 0.35f * glow), Color.Transparent), center = c),
                radius = r * 0.97f, center = c, style = Stroke(r * 0.5f),
            )
        }
    }
    rotate(spin, c) {
        drawCircle(Brush.linearGradient(listOf(Room.SignalLight, Room.Signal), start = c - Offset(r, r) * 0.3f, end = c + Offset(r, r) * 0.3f), r * 0.32f, c)
        drawLine(Color.White.copy(alpha = 0.8f), c + Offset(r * 0.08f, 0f), c + Offset(r * 0.26f, 0f), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
    }
    drawCircle(Color.White, r * 0.025f, c)
    // A fixed sheen across the vinyl, the way a lamp catches it.
    drawCircle(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent), start = c - Offset(r, r), end = c + Offset(r, r)),
        r, c,
    )
}

/** 5×7 dot glyphs for the wordmark. */
private val GLYPHS = mapOf(
    'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
)

/** Draws [text] in dot-matrix centred on [center]; [shown] 0–1 types it out left to right. */
internal fun DrawScope.drawDotWordmark(text: String, center: Offset, dot: Float, shown: Float, color: Color = Room.Ink) {
    val letters = text.length
    val letterW = dot * 6f
    val totalW = letters * letterW - dot
    val x0 = center.x - totalW / 2f
    val y0 = center.y - dot * 3.5f
    val visible = shown * letters
    text.forEachIndexed { i, ch ->
        val f = (visible - i).coerceIn(0f, 1f)
        if (f <= 0f) return@forEachIndexed
        val rows = GLYPHS[ch] ?: return@forEachIndexed
        // Each letter pops in with a tiny overshoot.
        val s = 0.6f + 0.4f * f + 0.08f * sin(f * PI.toFloat())
        val lc = Offset(x0 + i * letterW + dot * 2.5f, center.y)
        scale(s, s, pivot = lc) {
            rows.forEachIndexed { row, bits ->
                bits.forEachIndexed { col, b ->
                    val p = Offset(x0 + i * letterW + col * dot + dot / 2f, y0 + row * dot + dot / 2f)
                    if (b == '1') drawCircle(color.copy(alpha = f), dot * 0.42f, p)
                    else drawCircle(color.copy(alpha = 0.06f * f), dot * 0.42f, p)
                }
            }
        }
    }
}
