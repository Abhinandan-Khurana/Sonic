package app.runout.ui

import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The drawn pieces of onboarding: the RUNOUT wordmark, the sign-in's record collage and the
 * caution icon on the share key, and the loader for the pictures the owner adds to
 * assets/onboarding (the step pictures and the collage covers).
 */

/**
 * The SONIC. wordmark in Array Bold, its dot in signal orange, drawn rather than set as text: the
 * room never shows the app's name as a text node, so onboarding's wordmark is a graphic that says
 * "Sonic" to TalkBack.
 * [letters] types it out (0–6); [cursor] shows the orange block cursor after the last letter, and
 * [reserveCursor] keeps its room so typing never shifts the layout.
 */
@Composable
internal fun Wordmark(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    letters: Int = WORDMARK.length,
    cursor: Boolean = false,
    reserveCursor: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val style = Room.Display.copy(fontSize = fontSize)
    val full = remember(fontSize) { measurer.measure(brandMark(), style) }
    val density = LocalDensity.current
    val fontPx = with(density) { fontSize.toPx() }
    // The board's cursor: 10 × 30 beside 33 sp type, a hair after the last letter.
    val cursorW = fontPx * 10f / 33f
    val cursorGap = fontPx * 4f / 33f
    val width = full.size.width + if (reserveCursor) cursorGap + cursorW else 0f
    Canvas(
        modifier
            .size(with(density) { width.toDp() }, with(density) { full.size.height.toDp() })
            .semantics { contentDescription = "Sonic" },
    ) {
        val shown = letters.coerceIn(0, WORDMARK.length)
        val typed = if (shown == WORDMARK.length) full else measurer.measure(brandMark(letters = shown), style)
        if (shown > 0) drawText(typed)
        if (cursor) {
            val x = (if (shown > 0) typed.size.width.toFloat() + cursorGap else 0f)
            // Sits on the capital height: 4/33 down from the line top, 30/33 tall.
            drawRect(Room.Signal, Offset(x, fontPx * 4f / 33f), Size(cursorW, fontPx * 30f / 33f))
        }
    }
}

private const val WORDMARK = "SONIC."

/**
 * The name as the brand sets it: SONIC in [ink] (the style's colour when unspecified) and its full
 * stop in signal orange. [letters] cuts it short for typing it out.
 */
internal fun brandMark(ink: Color = Color.Unspecified, letters: Int = WORDMARK.length): AnnotatedString = buildAnnotatedString {
    val shown = WORDMARK.take(letters.coerceIn(0, WORDMARK.length))
    withStyle(SpanStyle(color = ink)) { append(shown.removeSuffix(".")) }
    if (shown.endsWith(".")) withStyle(SpanStyle(color = Room.Signal)) { append(".") }
}

/** One sleeve in the collage: a flat ground colour (or the night-sky gradient), a sun and a horizon. */
private class SleeveArt(val sky: List<Pair<Float, Color>>, val sun: Color, val ground: Color)

/** The six sleeves of the Sign in board, in order. */
private val SLEEVES = listOf(
    SleeveArt(listOf(0f to Color(0xFF2E2A5C), 0.55f to Color(0xFF8A3F8C), 0.72f to Color(0xFFB05A7A)), Color(0xFFF07A4F), Color(0xFF1B1A2E)),
    SleeveArt(listOf(0f to Color(0xFF0E7C6B)), Room.Signal, Color(0xFF0A0A0A)),
    SleeveArt(listOf(0f to Color(0xFFE9E4D8)), Room.Ink, Color(0xFFC9C2B0)),
    SleeveArt(listOf(0f to Room.Accent), Room.Warning, Color(0xFF0B2A44)),
    SleeveArt(listOf(0f to Color(0xFF272727)), Room.Faint, Color(0xFF0F0E12)),
    SleeveArt(listOf(0f to Color(0xFFF0CCCC)), Room.Danger, Color(0xFF272727)),
)

/** The covers that stand in for the drawn sleeves, `assets/onboarding/cover-1` … `cover-6`. */
private val COVERS = List(6) { "cover-${it + 1}" }

/** A sleeve is never bigger than on the 412-wide board, so the collage stays 274 dp tall. */
private val MAX_CELL = (412.dp - 2.dp) / 3

/**
 * The record collage at the top of the sign-in: two rows of three square sleeves on black, 1 dp
 * apart (TE · Onboarding · Sign in). Each is the owner's cover from the assets when it's there,
 * otherwise a drawn sun over a horizon. On a wide screen the sleeves keep their board size and
 * the grid sits in the middle of the black band.
 */
@Composable
internal fun RecordCollage(modifier: Modifier = Modifier) {
    val covers = rememberOnboardingPictures(COVERS, MAX_CELL)
    BoxWithConstraints(modifier.fillMaxWidth().background(Room.Ink)) {
        val gap = 1.dp
        val cell = minOf((maxWidth - gap * 2) / 3, MAX_CELL)
        Canvas(
            Modifier
                .align(Alignment.Center)
                .size(cell * 3 + gap * 2, cell * 2 + gap)
                .clearAndSetSemantics { },
        ) {
            val g = gap.toPx()
            val c = (size.width - g * 2) / 3f
            SLEEVES.forEachIndexed { i, art ->
                val o = Offset((i % 3) * (c + g), (i / 3) * (c + g))
                val cover = covers.getOrNull(i)
                if (cover != null) drawCover(cover, o, c) else drawSleeveArt(art, o, c)
            }
        }
    }
}

/** A cover filling its square, cropped to the middle if it isn't square itself. */
private fun DrawScope.drawCover(cover: ImageBitmap, o: Offset, c: Float) {
    val side = minOf(cover.width, cover.height)
    drawImage(
        cover,
        srcOffset = IntOffset((cover.width - side) / 2, (cover.height - side) / 2),
        srcSize = IntSize(side, side),
        dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
        dstSize = IntSize((o.x + c).roundToInt() - o.x.roundToInt(), (o.y + c).roundToInt() - o.y.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/**
 * Pictures the owner drops into `assets/onboarding/` (`<name>.webp`, `.png` or `.jpg`), decoded off
 * the main thread at about [maxSide] across and kept while the screen is up. Each is null while it
 * loads, and stays null when its file isn't there, so the drawn layout stands in.
 */
@Composable
internal fun rememberOnboardingPictures(names: List<String>, maxSide: Dp): List<ImageBitmap?> {
    val assets = LocalContext.current.assets
    val px = with(LocalDensity.current) { maxSide.roundToPx() }
    val pictures by produceState(List<ImageBitmap?>(names.size) { null }, names, px) {
        value = withContext(Dispatchers.IO) { names.map { decodeOnboardingPicture(assets, it, px) } }
    }
    return pictures
}

/** One picture from `assets/onboarding/`, as [rememberOnboardingPictures] loads them. */
@Composable
internal fun rememberOnboardingPicture(name: String, maxSide: Dp): ImageBitmap? =
    rememberOnboardingPictures(remember(name) { listOf(name) }, maxSide).firstOrNull()

private val PICTURE_TYPES = listOf("webp", "png", "jpg")

/** Decodes `onboarding/<name>.*`, halving it while it's still twice [maxSide] across; null if absent. */
private fun decodeOnboardingPicture(assets: AssetManager, name: String, maxSide: Int): ImageBitmap? {
    for (type in PICTURE_TYPES) {
        val path = "onboarding/$name.$type"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val there = try {
            assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            true
        } catch (_: IOException) {
            false
        }
        if (!there) continue
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = try {
            assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        } catch (_: IOException) {
            null
        }
        if (bitmap != null) return bitmap.asImageBitmap()
    }
    return null
}

private fun DrawScope.drawSleeveArt(art: SleeveArt, o: Offset, c: Float) {
    if (art.sky.size == 1) {
        drawRect(art.sky[0].second, o, Size(c, c))
    } else {
        drawRect(Brush.verticalGradient(*art.sky.toTypedArray(), startY = o.y, endY = o.y + c), o, Size(c, c))
    }
    // Sun: 60.1 on a 136.6 sleeve, 38.2 in and 49.2 down; ground from 98.4 to the foot.
    drawCircle(art.sun, c * 0.22f, o + Offset(c * 0.28f + c * 0.22f, c * 0.36f + c * 0.22f))
    drawRect(art.ground, o + Offset(0f, c * 0.72f), Size(c, c * 0.28f))
}

/** Lucide's triangle-alert, drawn on its 24-unit grid. */
private val CAUTION = listOf(
    "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
    "M12 9v4",
    "M12 17h.01",
).map { PathParser().parsePathString(it).toPath() }

/** The caution sign on the share key, in warning yellow. */
internal fun DrawScope.drawCaution(color: Color = Room.Warning) {
    val k = size.width / 24f
    scale(k, k, pivot = Offset.Zero) {
        CAUTION.forEach { drawPath(it, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

/** Reads a 12 sp line at the board's 17 sp leading, for body copy under titles. */
internal val OnboardingBody get() = Room.Text.copy(color = Room.Body, lineHeight = 17.sp)
