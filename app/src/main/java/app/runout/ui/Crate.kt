package app.runout.ui

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
 * The crate: sleeves stand in a row in perspective; the one in the middle turns to face you and
 * lifts. Flick to dig, tap the front sleeve to pull its record out.
 */
@Composable
fun Crate(
    albums: List<Album>,
    hasFolder: Boolean,
    pulledId: String?,
    loadedId: String?,
    onPickFolder: () -> Unit,
    onPull: (Album) -> Unit,
    onPutBack: () -> Unit,
    onFrontSlot: (SleeveSlot) -> Unit,
    modifier: Modifier = Modifier,
    /** Long-press on a sleeve: the record's menu. */
    onMenu: (Album) -> Unit = {},
    initialIndex: Int = 0,
    emptyTitle: String = if (hasFolder) "No albums found" else "Choose your\nmusic folder",
    emptyDetail: String = if (hasFolder) "Tap to pick another folder" else "Each album folder becomes\na record on your shelf",
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(initialIndex.toFloat()) }
    val decay = rememberSplineBasedDecay<Float>()

    LaunchedEffect(albums.size) {
        if (albums.isNotEmpty() && pos.value > albums.lastIndex) pos.snapTo(albums.lastIndex.toFloat())
    }

    BoxWithConstraints(modifier) {
        val boxHeight = maxHeight
        val sleeve = min(maxHeight.value * 0.58f, maxWidth.value * 0.47f).dp
        val sleevePx = with(density) { sleeve.toPx() }
        val stepPx = sleevePx * 0.55f
        var originInRoot = Offset.Zero

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { c ->
                    originInRoot = c.positionInRoot()
                    val cx = originInRoot.x + c.size.width / 2f
                    val cy = originInRoot.y + c.size.height * 0.44f - with(density) { 10.dp.toPx() }
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
            if (albums.isEmpty()) {
                EmptySleeve(
                    title = emptyTitle,
                    detail = emptyDetail,
                    size = sleeve,
                    onClick = onPickFolder,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = boxHeight * 0.12f),
                )
                return@Box
            }
            val center = pos.value
            // One soft shadow on the "floor" under the front slot. It never turns with a sleeve (a
            // shadow cast by a half-turned sleeve smears into a grey wedge mid-flick), and it only
            // darkens as a sleeve settles square to you.
            val settled = 1f - (abs(center - center.roundToInt()) * 2f).coerceIn(0f, 1f)
            Canvas(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = boxHeight * 0.12f)
                    .size(sleeve)
                    .zIndex(-10f),
            ) {
                val floor = Offset(size.width / 2f, size.height * 1.02f)
                val r = size.width * 0.62f
                // A round soft shadow squashed flat, so it fades out evenly on every side.
                scale(1f, 0.16f, pivot = floor) {
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.24f * settled), Color.Transparent),
                            center = floor, radius = r,
                        ),
                        radius = r, center = floor,
                    )
                }
            }
            val first = (center - 6).toInt().coerceAtLeast(0)
            val last = (center + 6).toInt().coerceAtMost(albums.lastIndex)
            for (i in first..last) {
                val album = albums[i]
                key(album.id) {
                    val dx = i - center
                    val adx = abs(dx)
                    val near = min(adx, 1f)
                    val x = if (adx <= 1f) dx * sleevePx * 0.74f else sign(dx) * (sleevePx * 0.74f + (adx - 1f) * sleevePx * 0.17f)
                    val isFront = adx < 0.5f
                    val hidden = isFront && album.id == pulledId
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .padding(bottom = boxHeight * 0.12f)
                            .size(sleeve)
                            .zIndex(-adx)
                            .testTag("sleeve:${album.title}")
                            .graphicsLayer {
                                translationX = x
                                translationY = -12.dp.toPx() * (1f - near)
                                // A slightly tighter turn than before, so the side sleeves read as standing in a crate.
                                rotationY = -dx.coerceIn(-1f, 1f) * 52f
                                val s = 1f - 0.16f * near
                                scaleX = s
                                scaleY = s
                                cameraDistance = 16f * density.density
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
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
                                    scope.launch { pos.animateTo(i.toFloat(), spring(dampingRatio = 0.86f, stiffness = 160f)) }
                                }
                            },
                    ) {
                        SleeveArt(album, Modifier.fillMaxSize())
                        if (album.id == loadedId) {
                            OnPlatterTag(Modifier.align(Alignment.BottomStart).padding(7.dp))
                        }
                    }
                }
            }

            // Caption under the front sleeve, set small like the references.
            val front = albums.getOrNull(center.roundToInt().coerceIn(0, albums.lastIndex))
            if (front != null) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = boxHeight * 0.44f + sleeve / 2 + 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Short: title and artist, the format as a tiny printed tag. The licence and the
                    // rest live in the record's details (long-press).
                    BasicText(front.title, style = Room.Title.copy(textAlign = TextAlign.Center), maxLines = 1)
                    BasicText(front.artist, style = Room.LabelQuiet.copy(textAlign = TextAlign.Center), maxLines = 1)
                    SpecTag(front.quality.label, Modifier.padding(top = 4.dp))
                    front.unavailableReason?.let {
                        BasicText(it, style = Room.LabelQuiet.copy(textAlign = TextAlign.Center, color = Room.Danger), maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnPlatterTag(modifier: Modifier) {
    Box(
        modifier
            .alpha(0.94f)
            .background(Room.Ink.copy(alpha = 0.72f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        BasicText("On the platter", style = Room.Label.copy(color = Color.White, fontSize = 9.sp))
    }
}

@Composable
private fun EmptySleeve(title: String, detail: String, size: Dp, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Room.Faint,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(title, style = Room.Title.copy(textAlign = TextAlign.Center), modifier = Modifier.testTag("empty-title"))
            BasicText(
                detail,
                style = Room.LabelQuiet.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 10.dp).testTag("empty-detail"),
            )
        }
    }
}
