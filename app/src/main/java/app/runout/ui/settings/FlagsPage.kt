package app.runout.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.drops.DropItem
import app.runout.drops.DropState
import app.runout.drops.DropsRepository
import app.runout.model.Album
import app.runout.model.Licences
import app.runout.model.Source
import app.runout.sound.Presets
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundSettings
import app.runout.ui.*
import kotlin.math.abs
import kotlin.math.roundToInt

private val HelpAutoFlip = TeHelp("help · flags", "auto-flip", listOf(
    "when a side runs out, the record turns over and the next side plays from its first song.",
    "after the last side it rests, the way a real deck would.",
))

private val HelpAutoPlay = TeHelp("help · flags", "auto-play", listOf(
    "putting a record on the platter lowers the needle by itself.",
    "turn it off to put records on first and start them from the deck.",
))

private val HelpSleep = TeHelp("help · flags", "sleep mode", listOf(
    "puts a moon in the room's top bar. tap it to stop the music in 15 to 50 minutes.",
    "when the time is up the music fades out over a few seconds and the needle lifts.",
))

/**
 * Settings › Flags: 01 the deck's habits, each a switch; 02 the theme (the phone's, or light or
 * dark pinned); 03 which RG-1 stands in the room (the theme's, or one of the three pinned).
 */
@Composable
internal fun FlagsPage(state: RoomState, actions: RoomActions) {
    var help by remember { mutableStateOf<TeHelp?>(null) }
    val f = state.deckFlags
    Column(Modifier.fillMaxWidth().testTag("panel:flags"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageSection("01 habits") {
            Column(Modifier.fillMaxWidth()) {
                HelpToggle("Auto-flip", "Side A over, side B plays.", f.autoFlip, actions::setAutoFlip) { help = HelpAutoFlip }
                HelpToggle("Auto-play", "A record on the platter starts playing.", f.autoPlay, actions::setAutoPlay) { help = HelpAutoPlay }
                HelpToggle("Sleep mode", "A moon in the room for the sleep timer.", f.sleepMode, actions::setSleepMode) { help = HelpSleep }
            }
        }
        PageSection("02 theme") {
            BasicText("phone follows your phone's dark mode. dark is true black, easy on an oled screen.", style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp))
            Segments(ThemeMode.entries, state.look.theme, { it.label }, tag = { "theme:${it.name}" }, onSelect = actions::setTheme)
        }
        PageSection("03 deck") {
            BasicText("theme puts the dark deck in a dark room and white / teal in a light one.", style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp))
            Segments(DeckSkin.entries, state.look.deck, { it.label }, tag = { "deck:${it.name}" }, onSelect = actions::setDeckSkin)
        }
    }
    TeHelpSheet(help) { help = null }
}
