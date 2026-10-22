package app.runout.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * Where the room's deck is, so onboarding can put its own deck in exactly the same place: the
 * intro draws its renders there, and the welcome opens a window there onto the room's live deck.
 * The deck you are greeted beside is then the one the room plays on, and nothing moves or flashes
 * when the room comes in around it.
 *
 * [rect] is in the coordinates of the room's screen, which onboarding covers exactly.
 */
@Stable
internal class DeckSpot(private val find: () -> Rect?) {
    /** The deck's view (the turntable's box), or null until the room has laid it out. */
    val rect: Rect? get() = find()

    /** How far onboarding's window onto the room's deck is open, 0 to 1 (the welcome opens it). */
    var window by mutableFloatStateOf(0f)
}

/** The room's deck, for onboarding drawn over it; null when onboarding stands alone (tests). */
internal val LocalDeckSpot = staticCompositionLocalOf<DeckSpot?> { null }

/**
 * Onboarding's white paper. Over the room, while the welcome has its window open, the paper is cut
 * away where the room's deck is, so the deck shows through in its own place, live.
 */
internal fun DrawScope.drawPaper(spot: DeckSpot?) {
    val open = spot?.window ?: 0f
    val hole = if (open > 0f) spot?.rect else null
    if (hole == null) {
        drawRect(Room.Background)
        return
    }
    clipRect(hole.left, hole.top, hole.right, hole.bottom, ClipOp.Difference) { drawRect(Room.Background) }
    // Opening: the paper over the deck thins away.
    if (open < 1f) drawRect(Room.Background, hole.topLeft, hole.size, alpha = 1f - open)
}
