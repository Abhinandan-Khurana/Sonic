package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.runout.model.Album
import app.runout.model.Source
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** A small glass card over the room; Back or a tap outside dismisses it. */
@Composable
private fun GlassCard(backdrop: Backdrop, tag: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().zIndex(25f), contentAlignment = Alignment.Center) {
        // The scrim is a sibling behind the card, not its parent: a clickable parent would merge the
        // whole card into one "dismiss" button for TalkBack.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.10f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(24.dp) },
                    effects = { vibrancy(); blur(20.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.9f)) },
                )
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 14.dp)
                .testTag(tag),
        ) { content() }
    }
}

/** What you can do with a record: long-press its sleeve or its disc. */
@Composable
internal fun RecordMenu(
    record: Album,
    pulled: Boolean,
    backdrop: Backdrop,
    onPutBack: () -> Unit,
    onSend: () -> Unit,
    onRemove: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(backdrop, "record-menu", onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(record.title, style = Room.Title.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            BasicText(record.artist, style = Room.LabelQuiet, maxLines = 1)
        }
        if (pulled) MenuRow("Put back", onPutBack)
        MenuRow(if (record.audioShareable) "Send with audio" else "Send as a card", onSend)
        MenuRow("Details", onDetails)
        if (record.source != Source.FOLDER) MenuRow("Remove", onRemove, Room.Danger)
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit, color: Color = Room.Ink) {
    val tick = rememberTick()
    BasicText(
        text,
        style = Room.Title.copy(color = color),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu:$text")
            .clickable(role = Role.Button) { tick(); onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
    )
}

/** The record's back cover: format, where it came from, its licence and the running order. */
@Composable
internal fun RecordDetails(record: Album, backdrop: Backdrop, onDismiss: () -> Unit) {
    GlassCard(backdrop, "record-details", onDismiss) {
        Column(
            Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(record.title, style = Room.Title.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            BasicText(listOfNotNull(record.artist, record.year?.toString()).joinToString(" · "), style = Room.LabelQuiet)
            SpecTag(record.quality.label)
            record.unavailableReason?.let { BasicText(it, style = Room.LabelQuiet.copy(color = Room.Danger)) }
            provenance(record)?.let { BasicText(it, style = Room.LabelQuiet.copy(color = Room.Accent)) }
            record.tracks.forEach { track ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText("${'A' + (track.discNumber - 1).coerceAtLeast(0)}${track.trackNumber}", style = Room.LabelQuiet)
                    BasicText(track.title, style = Room.Label, maxLines = 1)
                }
            }
            Pill("Close", onDismiss, Modifier.padding(top = 8.dp))
        }
    }
}

/** A tiny printed spec label, Teenage Engineering style: hairline box, caps, wide tracking. */
@Composable
internal fun SpecTag(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text.uppercase(),
        style = Room.Label.copy(fontSize = 8.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium, color = Room.Ink.copy(alpha = 0.72f)),
        maxLines = 1,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .border(0.8.dp, Room.Ink.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
