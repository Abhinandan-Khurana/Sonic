package app.runout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import app.runout.model.Album
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Idle shelf: picture discs in a receding row. A vertical flick moves the row; tapping the
 * facing disc unboxes it onto the listening room, where it can be dragged onto the platter.
 * The arrangement follows the A24 disc library's selected-disc perspective, in vinyl form.
 */
@Composable
fun VinylRack(
    albums: List<Album>,
    onUnbox: (Album) -> Unit,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(initialIndex.coerceIn(0, albums.lastIndex.coerceAtLeast(0)).toFloat()) }
    val decay = rememberSplineBasedDecay<Float>()
    LaunchedEffect(albums.size) {
        if (albums.isNotEmpty() && pos.value > albums.lastIndex) pos.snapTo(albums.lastIndex.toFloat())
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Room.Background)
            .testTag("vinyl-rack"),
    ) {
        if (albums.isEmpty()) return@BoxWithConstraints
        val disc = min(maxWidth.value * 0.62f, maxHeight.value * 0.46f).dp
        val discPx = with(density) { disc.toPx() }
        val stepPx = discPx * 0.72f
        val center = pos.value
        val focus = albums[center.roundToInt().coerceIn(0, albums.lastIndex)]
        val wide = maxWidth > 640.dp

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(albums.size) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onVerticalDrag = { change, dy ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            scope.launch {
                                pos.snapTo((pos.value - dy / stepPx).coerceIn(-0.4f, albums.lastIndex + 0.4f))
                            }
                        },
                        onDragEnd = {
                            val v = -tracker.calculateVelocity().y / stepPx
                            scope.launch {
                                val projected = decay.calculateTargetValue(pos.value, v)
                                pos.animateTo(
                                    projected.roundToInt().coerceIn(0, albums.lastIndex).toFloat(),
                                    spring(dampingRatio = 0.86f, stiffness = 140f),
                                    initialVelocity = v,
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch { pos.animateTo(pos.value.roundToInt().coerceIn(0, albums.lastIndex).toFloat()) }
                        },
                    )
                },
        ) {
            val first = (center - 4).toInt().coerceAtLeast(0)
            val last = (center + 4).toInt().coerceAtMost(albums.lastIndex)
            for (i in first..last) {
                val album = albums[i]
                key(album.id) {
                    val dx = i - center
                    val adx = abs(dx)
                    val near = min(adx, 1f)
                    val x = if (adx <= 1f) dx * discPx * 0.62f else sign(dx) * (discPx * 0.62f + (adx - 1f) * discPx * 0.28f)
                    val facing = adx < 0.5f
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .padding(top = if (wide) 0.dp else 72.dp)
                            .size(disc)
                            .zIndex(-adx)
                            .testTag("rack-disc:${album.title}")
                            .graphicsLayer {
                                translationX = x
                                translationY = adx * 18.dp.toPx()
                                rotationY = -dx.coerceIn(-1.4f, 1.4f) * 42f
                                rotationX = 28f - 16f * (1f - near)
                                val s = 1.08f - 0.24f * near - 0.06f * (adx - 1f).coerceAtLeast(0f)
                                scaleX = s
                                scaleY = s
                                cameraDistance = 18f * density.density
                                transformOrigin = TransformOrigin(0.5f, 0.55f)
                                alpha = (1f - (adx - 3.1f).coerceAtLeast(0f)).coerceIn(0.2f, 1f)
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (facing) onUnbox(album)
                                else scope.launch { pos.animateTo(i.toFloat(), spring(dampingRatio = 0.86f, stiffness = 160f)) }
                            },
                    ) {
                        RackDisc(album, Modifier.fillMaxSize())
                    }
                }
            }
        }

        Column(
            Modifier
                .align(if (wide) Alignment.CenterStart else Alignment.TopStart)
                .padding(start = 28.dp, top = 28.dp, end = 24.dp)
                .zIndex(2f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText("RECORD", style = Room.LabelQuiet.copy(letterSpacing = 1.4.sp, fontSize = 10.sp))
            BasicText(
                focus.title,
                style = Room.Title.copy(fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("rack-focus"),
            )
            BasicText(focus.artist, style = Room.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            focus.year?.let { BasicText(it.toString(), style = Room.LabelQuiet) }
            BasicText(
                "Scroll to move the records. Tap the facing disc to unbox it, then drag it onto the platter.",
                style = Room.LabelQuiet.copy(fontSize = 11.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RackDisc(album: Album, modifier: Modifier) {
    val cover by rememberCover(album)
    val look = remember(album.id) { discLookFor(album) }
    val fallback = remember(album.id) { fallbackColor(album) }
    Canvas(modifier.clip(CircleShape)) {
        drawRecord(
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.minDimension / 2f,
            rotation = 0f,
            look = look,
            label = cover,
            labelFallback = fallback,
            trackBoundaries = emptyList(),
            sideText = null,
            qualityText = null,
        )
    }
}
