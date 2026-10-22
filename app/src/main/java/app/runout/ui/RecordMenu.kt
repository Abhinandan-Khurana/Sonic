package app.runout.ui

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
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

/**
 * The TE dialog card, centred over the grey scrim: white, a 2 dp ink outline, 22 dp in all round,
 * 16 dp between parts, 22 dp from the screen's edges. Back or a tap outside dismisses it.
 */
@Composable
internal fun TeCard(tag: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().zIndex(25f), contentAlignment = Alignment.Center) {
        // The scrim is a sibling behind the card, not its parent: a clickable parent would merge the
        // whole card into one "dismiss" button for TalkBack.
        Box(
            Modifier
                .fillMaxSize()
                .background(Room.Scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .padding(22.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(Room.Background)
                .border(2.dp, Room.Ink)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(22.dp)
                .testTag(tag),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** The record at the top of a card: its 66 dp sleeve, the title in 16/700 and the artist in 10/400. */
@Composable
private fun CardRecord(record: Album, artist: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SleeveArt(record, Modifier.size(66.dp), showSticker = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(record.title.uppercase(), style = Room.Album.copy(fontSize = 16.sp), maxLines = 2)
            BasicText(artist.lowercase(), style = Room.Label.copy(color = Room.Body), maxLines = 1)
        }
    }
}

/** What you can do with a record: long-press its sleeve or its disc. */
@Composable
internal fun RecordMenu(
    record: Album,
    pulled: Boolean,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop,
    onPutBack: () -> Unit,
    onSend: () -> Unit,
    onRemove: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    TeCard("record-menu", onDismiss) {
        CardRecord(record, record.artist)
        Column(Modifier.fillMaxWidth()) {
            val rows = buildList {
                if (pulled) add(Triple("Put back", onPutBack, Room.Ink))
                add(Triple(if (record.audioShareable) "Send with audio" else "Send as a card", onSend, Room.Ink))
                add(Triple("Details", onDetails, Room.Ink))
                if (record.source != Source.FOLDER) add(Triple("Remove", onRemove, Room.Danger))
            }
            rows.forEachIndexed { n, (text, action, color) -> MenuRow("%02d".format(n + 1), text, action, color) }
        }
    }
}

/** A menu row: a hairline above, 16 dp above and below, the index, the 12/400 action and an arrow. */
@Composable
private fun MenuRow(index: String, text: String, onClick: () -> Unit, color: Color = Room.Ink) {
    val tick = rememberTick()
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineTop()
            .testTag("menu:$text")
            .clickable(role = Role.Button) { tick(); onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        BasicText(index, style = Room.LabelQuiet)
        BasicText(text.lowercase(), style = Room.Text.copy(color = color), modifier = Modifier.weight(1f))
        BasicText("→", style = Room.Text.copy(color = color))
    }
}

/** The record's back cover: format, licence, where it came from, and the running order by side. */
@Composable
internal fun RecordDetails(record: Album, @Suppress("UNUSED_PARAMETER") backdrop: Backdrop, onDismiss: () -> Unit) {
    TeCard("record-details", onDismiss) {
        CardRecord(record, listOfNotNull(record.artist, record.year?.toString()).joinToString(" · "))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SpecTag(record.quality.label)
            app.runout.model.Licences.label(record.license)?.let { TeTag(it.lowercase(), color = Room.Accent) }
        }
        record.unavailableReason?.let { BasicText(it.lowercase(), style = Room.Label.copy(color = Room.Danger, lineHeight = 15.sp)) }
        provenance(record)?.let { BasicText(it.lowercase(), style = Room.Label.copy(color = Room.Accent, lineHeight = 15.sp)) }
        val sides = remember(record.id) { app.runout.model.SideSplitter.split(record.tracks) }
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            sides.forEach { side ->
                side.tracks.forEachIndexed { i, track ->
                    // An ink rule where a new side begins, hairlines between songs.
                    Row(
                        Modifier.fillMaxWidth().hairlineTop(if (i == 0 && side.index > 0) Room.Ink else Room.HairlineSoft).padding(top = 11.dp, bottom = 11.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        BasicText("${side.letter.lowercaseChar()}${i + 1}", style = Room.LabelQuiet)
                        BasicText(track.title.lowercase(), style = Room.Label, maxLines = 1, modifier = Modifier.weight(1f))
                        BasicText(clock(track.durationMs), style = Room.LabelQuiet.copy(textAlign = TextAlign.End), modifier = Modifier.width(44.dp))
                    }
                }
            }
        }
        TeButton("close", onDismiss, Modifier.fillMaxWidth(), kind = KeyKind.OUTLINE)
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** The spec as a TE Tag: 1 dp ink outline, 3 × 6 inside, 10/400, lowercase. */
@Composable
internal fun SpecTag(text: String, modifier: Modifier = Modifier) = TeTag(text.lowercase(), modifier)
