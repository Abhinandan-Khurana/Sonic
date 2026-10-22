package app.runout.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import app.runout.model.Album
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.foundation.Canvas

/** Where the crate's front sleeve is, so the room can slide its disc out. */
data class SleeveSlot(val rect: Rect, val size: Dp)

/**
 * Where the front sleeve sits in the crate, as fractions of the crate's box, from the boards: in
 * portrait a 151 dp sleeve in a 412 × 301 crate, its top at 97; in landscape 130 in 412 × 168, at 10.
 */
data class CrateGeometry(val sleeveOfWidth: Float, val sleeveOfHeight: Float, val topOfHeight: Float) {
    companion object {
        // The owner asked for bigger sleeves sitting a little lower than the board's 151 at 97.
        val Portrait = CrateGeometry(0.42f, 0.56f, 0.30f)
        val Landscape = CrateGeometry(130f / 412.5f, 130f / 168f, 10f / 168f)
    }
}

/**
 * The crate, after TE · Home: the front sleeve square to you with its faint reflection on the floor
 * line; its neighbours turned hard away, edge to edge, and the ones beyond seen almost side-on. Flick
 * to dig, tap the front sleeve to pull its record out.
 */
@Composable
fun Crate(
    albums: List<Album>,
    pulledId: String?,
    onPull: (Album) -> Unit,
    onPutBack: () -> Unit,
    onFrontSlot: (SleeveSlot) -> Unit,
    modifier: Modifier = Modifier,
    geometry: CrateGeometry = CrateGeometry.Portrait,
    /** The label tab on a sleeve (ON THE PLATTER), or null. */
    label: (Album) -> String? = { null },
    /** The record in front, for the caption under the crate. */
    onFront: (Album?) -> Unit = {},
    /** Long-press on a sleeve: the record's menu. */
    onMenu: (Album) -> Unit = {},
    initialIndex: Int = 0,
    /** Dig to this record (a record flying back from the deck needs its sleeve in front). */
    scrollTo: String? = null,
    /** Put the pulled record back in its sleeve before digging to another one. */
    putBack: suspend () -> Unit = {},
    /** How many of a record's tracks failed to download and are still missing. */
    missing: (Album) -> Int = { 0 },
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(initialIndex.toFloat()) }
    val decay = rememberSplineBasedDecay<Float>()

    LaunchedEffect(scrollTo) {
        val i = albums.indexOfFirst { it.id == scrollTo }
        if (i >= 0) pos.animateTo(i.toFloat(), spring(dampingRatio = 0.86f, stiffness = 160f))
    }
    LaunchedEffect(albums.size) {
        if (albums.isNotEmpty() && pos.value > albums.lastIndex) pos.snapTo(albums.lastIndex.toFloat())
    }
    val frontIndex = pos.value.roundToInt().coerceIn(0, (albums.size - 1).coerceAtLeast(0))
    val front = albums.getOrNull(frontIndex)
    LaunchedEffect(front?.id) { onFront(front) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val sleeve = min(maxWidth.value * geometry.sleeveOfWidth, maxHeight.value * geometry.sleeveOfHeight).dp
        val sleevePx = with(density) { sleeve.toPx() }
        val top = maxHeight * geometry.topOfHeight
        val crateHeight = maxHeight
        val stepPx = sleevePx * 0.55f

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { c ->
                    val origin = c.positionInRoot()
                    val cx = origin.x + c.size.width / 2f
                    val cy = origin.y + with(density) { top.toPx() } + sleevePx / 2f
                    onFrontSlot(SleeveSlot(Rect(Offset(cx, cy), sleevePx / 2f), sleeve))
                }
                .pointerInput(albums.size, pulledId) {
                    if (albums.isEmpty() || pulledId != null) return@pointerInput
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, dx ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            scope.launch {
                                pos.snapTo((pos.value - dx / stepPx).coerceIn(-0.45f, albums.lastIndex + 0.45f))
                            }
                        },
                        onDragEnd = {
                            val v = -tracker.calculateVelocity().x / stepPx
                            scope.launch {
                                val projected = decay.calculateTargetValue(pos.value, v)
                                val snapped = projected.roundToInt().coerceIn(0, albums.lastIndex).toFloat()
                                pos.animateTo(snapped, spring(dampingRatio = 0.86f, stiffness = 140f), initialVelocity = v)
                            }
                        },
                        onDragCancel = {
                            scope.launch { pos.animateTo(pos.value.roundToInt().coerceIn(0, albums.lastIndex).toFloat()) }
                        },
                    )
                },
        ) {
            if (albums.isEmpty()) return@Box
            val center = pos.value
            // The floor: a #E5E5E5 line under the sleeves, 7.5 % in from each side.
            Canvas(Modifier.fillMaxSize().zIndex(-20f)) {
                val y = top.toPx() + sleevePx
                drawLine(Room.HairlineSoft, Offset(size.width * 0.075f, y), Offset(size.width * 0.925f, y), 0.85.dp.toPx())
            }
            val first = (center - 4).toInt().coerceAtLeast(0)
            val last = (center + 4).toInt().coerceAtMost(albums.lastIndex)
            for (i in first..last) {
                val album = albums[i]
                key(album.id) {
                    val dx = i - center
                    val adx = abs(dx)
                    val near = min(adx, 1f)
                    val far = (adx - 1f).coerceIn(0f, 1f)
                    // Neighbours stand edge to edge with the front sleeve, a half sleeve wide; the
                    // next ones a seventh of a sleeve wide, almost side-on.
                    val x = if (adx <= 1f) dx * sleevePx * 0.757f else sign(dx) * (sleevePx * 0.757f + (adx - 1f) * sleevePx * 0.33f)
                    val isFront = adx < 0.5f
                    val hidden = album.id == pulledId && isFront
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = top)
                            .size(sleeve)
                            .zIndex(-adx)
                            .testTag("sleeve:${album.title}")
                            .graphicsLayer {
                                translationX = x
                                rotationY = -sign(dx) * (near * 58f + far * 22f)
                                val s = 1f - 0.045f * near - 0.08f * far
                                scaleX = s
                                scaleY = s
                                cameraDistance = 9f * density.density
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                alpha = if (hidden) 0f else (1f - (adx - 2.4f).coerceAtLeast(0f)).coerceIn(0f, 1f)
                            }
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClick = { onMenu(album) },
                            ) {
                                if (isFront) {
                                    if (pulledId == album.id) onPutBack() else onPull(album)
                                } else {
                                    // A record still out of its sleeve goes back first, then the crate slides.
                                    scope.launch {
                                        if (pulledId != null) putBack()
                                        pos.animateTo(i.toFloat(), spring(dampingRatio = 0.86f, stiffness = 160f))
                                    }
                                }
                            },
                    ) {
                        // Stickers and the label tab only on the sleeve facing you, as on the board.
                        SleeveArt(album, Modifier.fillMaxSize(), showSticker = adx < 0.5f, label = if (adx < 0.5f) label(album) else null, missing = missing(album))
                    }
                }
            }
            // The front sleeve's reflection: 30 % opacity, a third of a sleeve deep, washing out to white.
            val shown = front != null && front.id != pulledId
            if (front != null && shown) Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = top + sleeve)
                    // A fifth of a sleeve deep, or what's left of the crate under the floor line.
                    .size(sleeve, minOf(sleeve * 0.2f, (crateHeight - top - sleeve).coerceAtLeast(0.dp)))
                    .clipToBounds()
                    .graphicsLayer { alpha = 0.3f * (1f - (abs(center - frontIndex) * 2f).coerceIn(0f, 1f)) },
            ) {
                // Flipped about its middle, so its bottom edge meets the floor line at the top of the box.
                SleeveArt(
                    front,
                    Modifier.wrapContentHeight(Alignment.Top, unbounded = true).requiredSize(sleeve).graphicsLayer { scaleY = -1f },
                    showSticker = false,
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.2f), 0.9f to Color.White)))
            }
            // The sleeves' contact shadow: soft and widest under the front sleeve, falling on the floor.
            Canvas(Modifier.fillMaxSize().zIndex(0.5f)) {
                val y = top.toPx() + sleevePx
                val rx = sleevePx * 0.95f
                val ry = 9.dp.toPx()
                val c = Offset(size.width / 2f, y)
                clipRect(top = y) {
                    scale(1f, ry / rx, pivot = c) {
                        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent), center = c, radius = rx), radius = rx, center = c)
                    }
                }
            }
        }
    }
}
