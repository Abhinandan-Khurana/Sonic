package app.runout.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.library.FolderUsage
import app.runout.library.StorageKind
import app.runout.library.StorageReport
import app.runout.library.StorageState
import app.runout.ui.*

/**
 * Settings › Storage, the page two fingers find on Settings: the phone's space with the music and
 * downloads in it, each folder by kind (hi-res and cd-quality flac, other audio, pictures,
 * unfinished downloads, the rest) with its largest records, and Sonic's own files. It measures
 * when it opens; nothing is deleted here but Sonic's cache.
 */
@Composable
internal fun StoragePage(state: RoomState, actions: RoomActions) {
    LaunchedEffect(Unit) { if (state.storage !is StorageState.Measuring) actions.analyzeStorage() }
    Column(Modifier.fillMaxWidth().testTag("panel:storage"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        BasicText(
            "what your music and downloads take up, read from the folders you picked. only sizes and names are looked at.",
            style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp),
        )
        when (val s = state.storage) {
            is StorageState.Done -> Report(s.report, actions)
            is StorageState.Measuring -> Column(Modifier.fillMaxWidth().padding(vertical = 33.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DotVinylSpinner(Modifier.size(96.dp), description = "Measuring your folders")
                BasicText(if (s.files == 0) "measuring your folders…" else "measuring… ${"%,d".format(s.files)} files", style = Room.LabelQuiet, modifier = Modifier.testTag("storage-measuring"))
            }
            StorageState.Idle -> Unit
        }
    }
}

@Composable
private fun Report(r: StorageReport, actions: RoomActions) {
    val music = r.music?.bytes ?: 0L
    val downloads = r.downloads?.bytes ?: 0L
    r.device?.let { d ->
        PageSection("01 phone") {
            val used = (d.total - d.free).coerceAtLeast(0L)
            StatsStrip(listOf(bytes(d.total) to "total", bytes(used) to "used", bytes(d.free) to "free"))
            UsageBar(listOf(music to Room.Signal, downloads to Room.Accent, (used - music - downloads).coerceAtLeast(0L) to Room.Quiet, d.free to Room.HairlineSoft))
            Legend(Room.Signal, "your music", music, d.total)
            if (r.downloads != null) Legend(Room.Accent, "downloads", downloads, d.total)
            Legend(Room.Quiet, "everything else", (used - music - downloads).coerceAtLeast(0L), d.total)
            Legend(Room.HairlineSoft, "free", d.free, d.total)
        }
    }
    PageSection("02 music folder" + (r.music?.label?.let { " · ${it.lowercase()}" } ?: "")) {
        r.music?.let { Folder(it, "storage-music") } ?: Quiet("no music folder yet. choose one in library.")
    }
    PageSection("03 download folder" + (r.downloads?.label?.let { " · ${it.lowercase()}" } ?: "")) {
        when {
            r.downloadsInMusic -> Quiet("downloads land inside your music folder, so they're counted above.")
            r.downloads != null -> Folder(r.downloads, "storage-downloads")
            else -> Quiet("no download folder yet. choose one in library.")
        }
    }
    PageSection("04 sonic") {
        StatsStrip(listOf(bytes(r.app.covers) to "covers", bytes(r.app.cache) to "cache", bytes(r.app.data) to "data"))
        Quiet("covers are the sleeves sonic has kept; data is its library list, chats and settings. the cache is safe to clear.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            TeButton("clear cache", actions::clearCache, Modifier.weight(1f).testTag("storage-clear-cache"), kind = KeyKind.OUTLINE, enabled = r.app.cache > 0)
            TeButton("measure again", actions::analyzeStorage, Modifier.weight(1f).testTag("storage-again"), kind = KeyKind.OUTLINE)
        }
    }
}

/** A folder: its strip, its bar by kind with a legend line each, and its largest records. */
@Composable
private fun Folder(f: FolderUsage, tag: String) {
    if (f.error != null) { BasicText(f.error, style = Room.Label.copy(color = Room.Danger, lineHeight = 15.sp)); return }
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        StatsStrip(listOf(bytes(f.bytes) to "size", "%,d".format(f.files) to "files", "${f.albums}" to "records"))
        val kinds = StorageKind.entries.mapNotNull { k -> f.byKind[k]?.takeIf { it > 0 }?.let { k to it } }
        if (kinds.isNotEmpty()) {
            UsageBar(kinds.map { (k, b) -> b to colourOf(k) })
            kinds.forEach { (k, b) -> Legend(colourOf(k), k.label, b, f.bytes) }
        }
        if (f.largest.isNotEmpty()) {
            BasicText("largest records", style = Room.LabelQuiet, modifier = Modifier.padding(top = 11.dp))
            Column(Modifier.fillMaxWidth()) {
                f.largest.forEachIndexed { i, rec ->
                    Row(
                        Modifier.fillMaxWidth().hairlineTop().padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        BasicText("%02d".format(i + 1), style = Room.LabelQuiet)
                        Column(Modifier.weight(1f)) {
                            BasicText(rec.title.lowercase(), style = Room.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            rec.artist?.let { BasicText(it.lowercase(), style = Room.LabelQuiet, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        BasicText(bytes(rec.bytes), style = Room.Label)
                    }
                }
            }
        }
    }
}

/** The kind's colour: orange for hi-res, ink for cd-quality, then quieter for what isn't music. */
@Composable
private fun colourOf(k: StorageKind): Color = when (k) {
    StorageKind.FLAC_HI_RES -> Room.Signal
    StorageKind.FLAC_CD -> Room.Ink
    StorageKind.OTHER_AUDIO -> Room.Accent
    StorageKind.PICTURES -> Room.Quiet
    StorageKind.PARTIAL -> Room.Warning
    StorageKind.OTHER -> Room.Hairline
}

/** A 10 dp bar in a 1 dp ink outline, its parts side by side in proportion. */
@Composable
private fun UsageBar(parts: List<Pair<Long, Color>>) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(12.dp).hairlineTop(Room.Ink).hairlineBottom(Room.Ink)) {
        parts.filter { it.first > 0 }.forEach { (b, c) ->
            Box(Modifier.weight((b.toFloat() / total).coerceAtLeast(0.004f)).fillMaxHeight().background(c))
        }
    }
}

/** A swatch, the name, its size and its share. */
@Composable
private fun Legend(colour: Color, label: String, b: Long, of: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(colour))
        BasicText(label, style = Room.Label, modifier = Modifier.weight(1f))
        BasicText(bytes(b), style = Room.Label)
        BasicText(percent(b, of), style = Room.LabelQuiet, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Numbers over labels, ruled in ink above and below, as the library's scan strip. */
@Composable
private fun StatsStrip(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().hairlineTop(Room.Ink).hairlineBottom(Room.Ink)) {
        items.forEach { (value, label) ->
            Column(Modifier.weight(1f).padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(value, style = Room.Title.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), maxLines = 1)
                BasicText(label, style = Room.LabelQuiet)
            }
        }
    }
}

@Composable
private fun Quiet(text: String) = BasicText(text, style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp))

/** "41.2 gb", "512 mb", "980 kb": decimal units, as the phone's own settings count. */
internal fun bytes(b: Long): String = when {
    b >= 1_000_000_000L -> "%.1f gb".format(b / 1e9)
    b >= 1_000_000L -> "%.0f mb".format(b / 1e6)
    b >= 1_000L -> "%.0f kb".format(b / 1e3)
    else -> "$b b"
}

private fun percent(b: Long, of: Long): String = if (of <= 0) "" else {
    val p = b * 100.0 / of
    if (p in 0.01..0.99) "<1%" else "%.0f%%".format(p)
}
