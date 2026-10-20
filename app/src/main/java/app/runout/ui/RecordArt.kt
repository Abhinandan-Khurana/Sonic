package app.runout.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.model.Album
import app.runout.model.DiscStyle
import app.runout.model.Pressing
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * How a pressing looks: black for lossy files, solid colour for lossless, clear colour for hi-res.
 * Records from the Pressing Plant choose their own vinyl, including splatter, marble and picture discs.
 */
data class DiscLook(
    val base: Color,
    val alpha: Float,
    val grooveAlpha: Float,
    val style: DiscStyle? = null,
    val accent: Color = base,
    val seed: Int = 0,
)

fun discLookFor(album: Album?): DiscLook {
    val tint = album?.tint?.let { Color(it) } ?: Color(0xFF4A4A58)
    val chosen = album?.labelColor?.let { Color(it) } ?: tint
    val seed = album?.id?.hashCode() ?: 0
    return when (album?.disc) {
        DiscStyle.BLACK -> DiscLook(Color(0xFF111114), 1f, 1f, DiscStyle.BLACK)
        DiscStyle.COLOUR -> DiscLook(lerp(chosen, Color.Black, 0.15f), 1f, 0.85f, DiscStyle.COLOUR)
        DiscStyle.CLEAR -> DiscLook(lerp(chosen, Color.White, 0.1f), 0.58f, 0.75f, DiscStyle.CLEAR)
        DiscStyle.SPLATTER -> DiscLook(Color(0xFFF1F1EE), 1f, 0.55f, DiscStyle.SPLATTER, chosen, seed)
        DiscStyle.MARBLE -> DiscLook(lerp(chosen, Color.White, 0.6f), 1f, 0.6f, DiscStyle.MARBLE, chosen, seed)
        DiscStyle.PICTURE -> DiscLook(Color(0xFF1A1A1E), 1f, 0.45f, DiscStyle.PICTURE, chosen, seed)
        // A cover is the centre label of a black record, not a printed CD.
        null -> when {
            album?.coverPath != null -> DiscLook(Color(0xFF111114), 1f, 1f, DiscStyle.BLACK)
            album?.pressing == Pressing.CLEAR -> DiscLook(lerp(tint, Color.White, 0.1f), 0.58f, 0.75f)
            album?.pressing == Pressing.COLOUR -> DiscLook(lerp(tint, Color.Black, 0.3f), 1f, 0.85f)
            else -> DiscLook(Color(0xFF111114), 1f, 1f)
        }
    }
}

/** A colour for albums with no cover, picked from the title so it stays the same every time. */
fun fallbackColor(album: Album?): Color {
    val palette = listOf(0xFFE8E4FF, 0xFFDDEBFF, 0xFFFFE3EC, 0xFFE2F6EC, 0xFFFFF1D6, 0xFFE9E9EE)
    val i = Math.floorMod((album?.title ?: "").hashCode(), palette.size)
    return Color(palette[i])
}

/**
 * Draws a record: grooves, the smooth gaps between tracks, the label (the album cover, turning
 * with the platter), the label print and a light sheen that stays still while the record spins.
 */
fun DrawScope.drawRecord(
    center: Offset,
    radius: Float,
    rotation: Float,
    look: DiscLook,
    label: ImageBitmap?,
    labelFallback: Color,
    trackBoundaries: List<Float>,
    sideText: TextLayoutResult?,
    qualityText: TextLayoutResult?,
) {
    // Body
    drawCircle(look.base.copy(alpha = look.alpha), radius, center)
    val discClip = Path().apply { addOval(Rect(center, radius)) }
    when (look.style) {
        DiscStyle.SPLATTER -> rotate(rotation, center) {
            clipPath(discClip) {
                val rnd = Random(look.seed)
                repeat(46) {
                    val a = rnd.nextFloat() * 6.2832f
                    val d = radius * (0.12f + rnd.nextFloat() * 0.9f)
                    val p = center + Offset(cos(a) * d, sin(a) * d)
                    val c = if (it % 3 == 0) Color(0xFF111114) else look.accent
                    drawCircle(c.copy(alpha = 0.85f), radius * (0.012f + rnd.nextFloat() * 0.05f), p)
                }
            }
        }
        DiscStyle.MARBLE -> rotate(rotation, center) {
            clipPath(discClip) {
                val rnd = Random(look.seed)
                repeat(5) { i ->
                    rotate(rnd.nextFloat() * 360f, center) {
                        drawCircle(
                            Brush.sweepGradient(
                                0f to Color.Transparent,
                                0.08f + i * 0.02f to look.accent.copy(alpha = 0.55f),
                                0.2f + i * 0.03f to Color.Transparent,
                                0.55f to Color.White.copy(alpha = 0.35f),
                                0.7f to Color.Transparent,
                                1f to Color.Transparent,
                                center = center,
                            ),
                            radius = radius * (1f - i * 0.12f),
                            center = center,
                        )
                    }
                }
            }
        }
        DiscStyle.PICTURE -> if (label != null) {
            rotate(rotation, center) {
                clipPath(discClip) {
                    drawImage(
                        image = label,
                        dstOffset = IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
                        dstSize = IntSize((radius * 2).toInt(), (radius * 2).toInt()),
                    )
                }
            }
            // The clear vinyl rim around the printed picture.
            drawCircle(Color.White.copy(alpha = 0.30f), radius * 0.982f, center, style = Stroke(radius * 0.036f))
            drawCircle(Color.Black.copy(alpha = 0.12f), radius * 0.964f, center, style = Stroke(radius * 0.004f))
        }
        else -> Unit
    }

    // Grooves, from the lead-in near the edge to the run-out near the label.
    val grooveOuter = radius * 0.975f
    val grooveInner = radius * 0.42f
    val rings = 110
    val hair = (radius / 300f).coerceAtLeast(0.5f)
    // Clear vinyl shows its grooves as darker lines; solid vinyl as faint light lines.
    val grooveColor = if (look.alpha < 1f) Color.Black else Color.White
    for (i in 0..rings) {
        val r = grooveInner + (grooveOuter - grooveInner) * i / rings
        val jitter = ((i * 37) % 11) / 10f
        val a = (0.022f + 0.022f * jitter) * look.grooveAlpha
        drawCircle(grooveColor.copy(alpha = a), r, center, style = Stroke(hair))
    }

    // Gaps between tracks show as smooth bands, like on a real record.
    val playOuter = radius * 0.95f
    val playInner = radius * 0.43f
    trackBoundaries.forEach { f ->
        val r = playOuter - (playOuter - playInner) * f
        val pictured = look.style == DiscStyle.PICTURE && label != null
        drawCircle(look.base.copy(alpha = if (look.alpha < 1f || pictured) 0.35f else 1f), r, center, style = Stroke(radius * 0.011f))
        drawCircle(Color.White.copy(alpha = 0.08f), r, center, style = Stroke(hair))
    }

    // Light catching the grooves: drawn without rotation, so it stays put as the record turns.
    rotate(-38f, center) {
        drawCircle(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                0.07f to Color.White.copy(alpha = 0.16f),
                0.15f to Color.Transparent,
                0.5f to Color.Transparent,
                0.57f to Color.White.copy(alpha = 0.10f),
                0.65f to Color.Transparent,
                1f to Color.Transparent,
                center = center,
            ),
            radius = grooveOuter,
            center = center,
        )
    }
    drawCircle(Color.White.copy(alpha = 0.16f), radius, center, style = Stroke(radius * 0.006f))

    // Label
    val lr = radius * 0.34f
    rotate(rotation, center) {
        val clip = Path().apply { addOval(Rect(center, lr)) }
        val pictureDisc = look.style == DiscStyle.PICTURE && label != null
        if (!pictureDisc) clipPath(clip) {
            if (label != null) {
                drawImage(
                    image = label,
                    dstOffset = IntOffset((center.x - lr).toInt(), (center.y - lr).toInt()),
                    dstSize = IntSize((lr * 2).toInt(), (lr * 2).toInt()),
                )
            } else {
                drawCircle(labelFallback, lr, center)
            }
        }
        if (!pictureDisc) drawCircle(Color.Black.copy(alpha = 0.22f), lr, center, style = Stroke(radius * 0.008f))
        else drawCircle(Color.White.copy(alpha = 0.18f), lr, center, style = Stroke(radius * 0.004f))
        sideText?.let { t ->
            val top = center + Offset(-t.size.width / 2f, -lr * 0.78f)
            drawRoundRect(
                Color.Black.copy(alpha = 0.42f),
                topLeft = top - Offset(radius * 0.02f, radius * 0.006f),
                size = androidx.compose.ui.geometry.Size(t.size.width + radius * 0.04f, t.size.height + radius * 0.012f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.03f),
            )
            drawText(t, topLeft = top)
        }
        qualityText?.let { t ->
            val top = center + Offset(-t.size.width / 2f, lr * 0.40f)
            drawRoundRect(
                Color.Black.copy(alpha = 0.42f),
                topLeft = top - Offset(radius * 0.02f, radius * 0.006f),
                size = androidx.compose.ui.geometry.Size(t.size.width + radius * 0.04f, t.size.height + radius * 0.012f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.03f),
            )
            drawText(t, topLeft = top)
        }
    }

    // Spindle hole
    drawCircle(Room.Chrome, radius * 0.024f, center)
    drawCircle(Color.Black.copy(alpha = 0.35f), radius * 0.024f, center, style = Stroke(radius * 0.005f))
}

/** An album sleeve: the cover (or a typographic sleeve if none was found), shrink-wrap sheen, and a Hi-Res sticker. */
@Composable
fun SleeveArt(album: Album?, modifier: Modifier = Modifier, showSticker: Boolean = true) {
    val cover by rememberCover(album)
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(fallbackColor(album)),
    ) {
        val bitmap = cover
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = album?.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (album != null) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(album.title, style = Room.Title.copy(fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 4)
                BasicText(album.artist, style = Room.LabelQuiet.copy(color = Room.Ink.copy(alpha = 0.6f)), maxLines = 2)
            }
        }
        // Shrink-wrap
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.0f),
                    0.42f to Color.White.copy(alpha = 0.0f),
                    0.5f to Color.White.copy(alpha = 0.18f),
                    0.58f to Color.White.copy(alpha = 0.0f),
                    1f to Color.White.copy(alpha = 0.06f),
                ),
            ),
        )
        if (showSticker && album?.quality?.isHiRes == true) {
            HiResSticker(album.quality.shortLabel, Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
    }
}

@Composable
fun HiResSticker(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(34.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Room.Accent),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "HI-RES\n$text",
            style = Room.Label.copy(color = Color.White, fontSize = 6.5.sp, lineHeight = 7.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
    }
}
