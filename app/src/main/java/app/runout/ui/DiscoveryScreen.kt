package app.runout.ui

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.discovery.*
import app.runout.soulseek.*
import java.util.Locale

@Composable
internal fun DiscoveryScreen(state: RoomState, actions: RoomActions, onAccount: () -> Unit, onCue: () -> Unit) {
    val discovery = state.discovery
    val options = discovery.options
    // Two places to dig: this device, or Soulseek. (An older "all sources" choice reads as Soulseek.)
    val source = if (options.source == SearchSource.LOCAL) SearchSource.LOCAL else SearchSource.SOULSEEK
    var filters by rememberSaveable { mutableStateOf(false) }
    val searching = discovery.online.phase == SearchPhase.SEARCHING
    val submit = { if(discovery.query.trim().isNotEmpty()) actions.searchSoulseek(discovery.query) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("screen:search"),contentPadding = PaddingValues(bottom = 96.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item("search-controls") {
                val online = source == SearchSource.SOULSEEK && state.soulseekSession.connected
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        Field(discovery.query,actions::setSearchQuery,"Search albums or artists",onSubmit = { if(online && !searching) submit() })
                    }
                    // One round key beside the field: search, and while searching, stop.
                    if(online) SearchKey(searching, enabled = discovery.query.trim().toByteArray().size >= 2, onSearch = submit, onStop = actions::stopSoulseekSearch)
                }
                Chips(listOf(SearchSource.LOCAL, SearchSource.SOULSEEK),source,{ it.label },{ actions.setSearchOptions(options.copy(source = it, kind = SearchKind.ALBUMS)) },Modifier.padding(vertical = 8.dp))
                if(source == SearchSource.SOULSEEK) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if(!state.soulseekSession.connected) Pill(if(state.soulseekSession.hasAccount) "Reconnect Soulseek" else "Connect Soulseek",{ if(state.soulseekSession.hasAccount) actions.reconnectSoulseek() else onAccount() },active = true,enabled = state.soulseekSession.state != SoulseekConnection.RECONNECTING)
                    }
                    if(!state.soulseekSession.connected && state.soulseekSession.hasAccount) BasicText(state.soulseekSession.error ?: "Your account is kept. Reconnect to search online.",style = Room.LabelQuiet,modifier = Modifier.padding(vertical = 8.dp))
                }
                if(discovery.query.isBlank()) BasicText("Albums only, always FLAC. Try an artist and an album title.",style = Room.LabelQuiet,modifier = Modifier.padding(vertical = 16.dp))
            }
            if(source == SearchSource.LOCAL && discovery.query.isNotBlank()) {
                val albums = discovery.local.filter { it.track == null }.ifEmpty { discovery.local.distinctBy { it.album.id }.map { it.copy(track = null) } }
                item("local-heading") { SectionTitle("On this device · ${plural(albums.size, "album")}") }
                items(albums,key = { "local:${it.id}" }) { hit -> LocalAlbumCard(hit, actions, onCue) }
                if(albums.isEmpty()) item("no-local") { BasicText("No albums here match. Try fewer words, or dig on Soulseek.",style = Room.LabelQuiet) }
            }
            if(source == SearchSource.SOULSEEK && discovery.online.query.isNotBlank()) {
                item("online-heading") {
                    SectionTitle("Soulseek · ${plural(discovery.remote.size, "album folder")}")
                    BasicText(searchStatus(discovery),style = Room.LabelQuiet,modifier = Modifier.testTag("search-status"))
                    if(discovery.online.limited) BasicText("Showing the first 5,000 FLAC matches. Refine your search for a closer shortlist.",style = Room.LabelQuiet)
                    if(discovery.online.phase in setOf(SearchPhase.FAILED,SearchPhase.INTERRUPTED)) {
                        Pill("Retry search",submit,enabled = state.soulseekSession.connected)
                    }
                    if(discovery.remote.isEmpty() && discovery.online.phase == SearchPhase.COMPLETE) Pill("Broaden search terms",actions::broadenSearch)
                }
                items(discovery.remote,key = { "folder:${it.id}" }) { group ->
                    RemoteAlbumCard(group, enabled = state.soulseekSession.connected) { actions.previewSoulseekFolder(group.tracks.first()) }
                }
            }
        }
        // Filters & sort: a small floating key, bottom right, out of the way of the results.
        if (source == SearchSource.SOULSEEK) FiltersKey(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { filters = true }
        if (filters) FiltersCard(options, actions) { filters = false }
    }
}

internal fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

/** An album on this device: its sleeve, title and artist; it goes on the platter cued at side A. */
@Composable
private fun LocalAlbumCard(hit: LocalMatch, actions: RoomActions, onCue: () -> Unit) {
    val cue = { actions.cueTrack(hit.album, hit.track?.id); onCue() }
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineTop(Room.HairlineSoft)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SleeveArt(hit.album, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(hit.album.title, style = Room.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(hit.album.artist, style = Room.LabelQuiet, maxLines = 1)
            SpecTag(hit.album.quality.label)
        }
        // The record goes on the platter cued; the deck plays it.
        Pill("Put on the platter", cue, Modifier.testTag("cue-local:${hit.id}"), active = true)
    }
}

/**
 * An album folder on Soulseek. The whole card opens the folder preview; the peer's state is three
 * small marks: a slot dot (green when free), the queue, and the advertised speed.
 */
@Composable
private fun RemoteAlbumCard(group: RemoteAlbum, enabled: Boolean, onPreview: () -> Unit) {
    val tick = rememberTick()
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineTop(Room.HairlineSoft)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) { tick(); onPreview() }
            .testTag("preview-folder:${group.id}")
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Two lines, no art: the album and its folder, then the spec and the peer's state.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(group.album, style = Room.Title.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                group.parent?.let { BasicText(it, style = Room.LabelQuiet, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(listOfNotNull("flac", qualityShort(group.bits, group.rate), plural(group.tracks.size, "track")).joinToString(" · "), style = Room.Label.copy(color = Room.Body))
                PeerMarks(group)
            }
        }
        BasicText("→", style = Room.Title)
    }
}

/** Slot, queue and speed as tiny marks, with the words kept for TalkBack. */
@Composable
private fun PeerMarks(group: RemoteAlbum) {
    val words = listOfNotNull(
        if (group.available) "upload slot free" else "peer busy",
        group.queue?.let { "$it queued" },
        if (group.speed > 0) "${sizeLabel(group.speed)} per second" else null,
    ).joinToString(", ")
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = words },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(7.dp)) { drawRect(if (group.available) Room.Online else Room.Faint) }
        group.queue?.takeIf { it > 0 }?.let { BasicText("≡$it", style = Room.LabelQuiet.copy(fontSize = 10.sp)) }
        if (group.speed > 0) BasicText("↯" + sizeLabel(group.speed), style = Room.LabelQuiet.copy(fontSize = 10.sp, color = if (group.speed >= 1_048_576) Room.Online else Room.Quiet))
    }
}

/** A stand-in sleeve for a folder we can't see the cover of yet: colours from its name, set like a label. */
@Composable
internal fun GeneratedSleeve(title: String, artist: String?, modifier: Modifier = Modifier) {
    val seed = (title + artist.orEmpty()).hashCode()
    val hue = ((seed and 0x7fffffff) % 360).toFloat()
    val a = Color.hsl(hue, 0.55f, 0.62f)
    val b = Color.hsl((hue + 40f) % 360f, 0.5f, 0.38f)
    Box(modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(a, b)), RectangleShape)) {
        BasicText(
            title.take(18).uppercase(),
            style = Room.Label.copy(fontSize = 6.5.sp, letterSpacing = 0.6.sp, color = Color.White.copy(alpha = 0.92f), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            maxLines = 3,
            modifier = Modifier.padding(5.dp),
        )
    }
}

private fun qualityShort(bits: Int?, rate: Int?): String? =
    if (bits == null || rate == null) null else "$bits/${if (rate % 1000 == 0) (rate / 1000).toString() else String.format(Locale.ROOT, "%.1f", rate / 1000.0)}"

/** The round search key beside the field; a stop square while Soulseek is being searched. */
@Composable
private fun SearchKey(searching: Boolean, enabled: Boolean, onSearch: () -> Unit, onStop: () -> Unit) {
    val tick = rememberTick()
    val on = searching || enabled
    Box(
        Modifier
            .size(48.dp)
            .background(if (on) Room.Ink else Room.HairlineSoft, RectangleShape)
            .clickable(enabled = on, role = androidx.compose.ui.semantics.Role.Button) { tick(); if (searching) onStop() else onSearch() }
            .semantics { contentDescription = if (searching) "Stop searching" else "Search Soulseek" }
            .testTag(if (searching) "stop-search" else "submit-search"),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (on) Color.White else Room.Quiet
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            if (searching) {
                drawRoundRect(ink, androidx.compose.ui.geometry.Offset(w * 0.25f, w * 0.25f), androidx.compose.ui.geometry.Size(w * 0.5f, w * 0.5f), androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
            } else {
                val stroke = w * 0.11f
                drawCircle(ink, w * 0.3f, androidx.compose.ui.geometry.Offset(w * 0.42f, w * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawLine(ink, androidx.compose.ui.geometry.Offset(w * 0.64f, w * 0.64f), androidx.compose.ui.geometry.Offset(w * 0.9f, w * 0.9f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

/** The floating Filters & sort key. */
@Composable
private fun FiltersKey(modifier: Modifier, onClick: () -> Unit) {
    val tick = rememberTick()
    Box(
        modifier
            .size(54.dp)
            .background(Room.Background)
            .border(1.dp, Room.Ink)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { tick(); onClick() }
            .semantics { contentDescription = "Filters and sort" }
            .testTag("search-filters"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            listOf(0.25f to 0.62f, 0.5f to 0.32f, 0.75f to 0.55f).forEach { (y, knob) ->
                drawLine(Room.Ink, androidx.compose.ui.geometry.Offset(w * 0.1f, w * y), androidx.compose.ui.geometry.Offset(w * 0.9f, w * y), strokeWidth = w * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(Color.White, w * 0.1f, androidx.compose.ui.geometry.Offset(w * knob, w * y))
                drawCircle(Room.Signal, w * 0.1f, androidx.compose.ui.geometry.Offset(w * knob, w * y), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.075f))
            }
        }
    }
}

@Composable
private fun FiltersCard(options: DiscoveryOptions, actions: RoomActions, onDismiss: () -> Unit) {
    PageSheet("search-filter-card", onDismiss) {
        BasicText("Filters & sort", style = Room.Title.copy(fontSize = 17.sp))
        BasicText("Sort", style = Room.LabelQuiet)
        Chips(SearchSort.entries,options.sort,{ it.label },{ actions.setSearchOptions(options.copy(sort = it)) })
        BasicText("Reported FLAC quality", style = Room.LabelQuiet)
        Chips(SearchQuality.entries,options.quality,{ it.label },{ actions.setSearchOptions(options.copy(quality = it)) })
        ToggleRow("Free upload slots only","Peer availability is advertised and can change.",options.availableOnly,{ actions.setSearchOptions(options.copy(availableOnly = it)) })
        Pill("Done", onDismiss, Modifier.testTag("search-filters-done"), active = true)
    }
}

private fun searchStatus(state: DiscoveryState): String {
    if(normalize(state.query) != normalize(state.online.query)) return "Press Search Soulseek to look for these new terms."
    return when(state.online.phase) {
        SearchPhase.SEARCHING -> "Listening for peers… Results appear as they arrive."
        SearchPhase.COMPLETE -> if(state.remote.isEmpty()) "No matching FLAC albums with these filters. Try fewer words or change filters." else "Search complete. Tap an album to see its full track list."
        SearchPhase.CANCELLED -> "Search stopped. Matches already received are kept."
        SearchPhase.FAILED, SearchPhase.INTERRUPTED -> state.online.error ?: "Search interrupted. Your results are kept."
        else -> "Ready to search."
    }
}
private fun availability(track: SoulseekSearchResult) = buildString {
    append(if(track.freeSlots > 0) "Upload slot available" else "Peer may have a queue")
    track.queueLength?.let { append(" · $it queued") }
    if(track.uploadSpeed > 0) append(" · advertised ${sizeLabel(track.uploadSpeed)}/s")
}
private fun sizeLabel(bytes: Long) = when {
    bytes >= 1_073_741_824 -> String.format(Locale.ROOT,"%.1f GB",bytes/1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.ROOT,"%.1f MB",bytes/1_048_576.0)
    else -> String.format(Locale.ROOT,"%.0f KB",bytes/1024.0)
}
private fun trackDetails(track: SoulseekSearchResult) = "FLAC · " + listOf(
    track.bitDepth?.let { "$it-bit" } ?: "bit depth unknown",
    track.sampleRate?.let { String.format(Locale.ROOT,"%.1f kHz",it/1000.0) } ?: "sample rate unknown",
    sizeLabel(track.size),
).joinToString(" · ")

/**
 * A peer's complete album folder, shown before anything downloads: laid out like the back of a
 * sleeve, with the running order in plain track names.
 */
@Composable
internal fun FolderPreview(preview: SoulseekFolderPreview, state: RoomState, actions: RoomActions, onMessage: (String) -> Unit = {}) {
    BackHandler(onBack = actions::closeSoulseekFolder)
    val (artist, album) = folderNames(preview.directory)
    val folder = preview.directory.substringAfterLast('/')
    val bits = preview.files.mapNotNull { it.bitDepth }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val rate = preview.files.mapNotNull { it.sampleRate }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    Column(Modifier.fillMaxSize().testTag("screen:folder-preview")) {
        TextAction("‹ Back to results", actions::closeSoulseekFolder, tag = "preview-back", color = Room.Ink)
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GeneratedSleeve(album, artist, Modifier.size(104.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(album, style = Room.Title.copy(fontSize = 19.sp, lineHeight = 23.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 3, overflow = TextOverflow.Ellipsis)
                artist?.let { BasicText(it, style = Room.LabelQuiet.copy(fontSize = 13.sp), maxLines = 1) }
                if (!preview.loading) SpecTag(listOfNotNull("FLAC", qualityShort(bits, rate), sizeLabel(preview.files.sumOf { it.size })).joinToString(" · "))
            }
        }
        // The peer, small: whose folder it is, and a way to message them.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText("from ${preview.username}", style = Room.LabelQuiet)
            MessageKey(enabled = state.soulseekSession.connected) { onMessage(preview.username) }
        }
        if (preview.loading) BasicText("Getting the full track list…", style = Room.LabelQuiet, modifier = Modifier.padding(vertical = 10.dp))
        preview.error?.let { BasicText(it,style = Room.LabelQuiet.copy(color = Room.Danger)) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding = PaddingValues(vertical = 12.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(preview.files,key = { _, f -> f.id }) { i, file ->
                val (number, title) = cleanTrackName(file.fileName, folder, artist, i + 1)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(number, style = Room.LabelQuiet.copy(fontSize = 12.sp), modifier = Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(title, style = Room.Title.copy(fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        // Only a track that differs from the album says what it is.
                        if ((file.bitDepth != null && file.bitDepth != bits) || (file.sampleRate != null && file.sampleRate != rate)) {
                            BasicText(trackDetails(file), style = Room.LabelQuiet)
                        }
                    }
                }
            }
        }
        if(state.downloadFolder == null) Pill("Choose download folder",actions::pickDownloadFolder,Modifier.fillMaxWidth())
        else BasicText("Keeps in ${state.downloadFolder}",style = Room.LabelQuiet,modifier = Modifier.padding(vertical = 8.dp))
        Pill("Download ${plural(preview.files.size, "FLAC track")}",actions::confirmSoulseekFolder,Modifier.fillMaxWidth().testTag("confirm-folder"),active = true,enabled = !preview.loading && preview.error == null && preview.files.isNotEmpty() && state.downloadFolder != null && state.soulseekSession.connected)
    }
}

/** A small round message key. */
@Composable
private fun MessageKey(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = "Message this peer" }
            .testTag("message-peer"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val p = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, w * 0.1f, w, w * 0.72f, w * 0.18f, w * 0.18f))
                moveTo(w * 0.25f, w * 0.72f); lineTo(w * 0.2f, w * 0.95f); lineTo(w * 0.45f, w * 0.72f)
            }
            drawPath(p, if (enabled) Room.Ink else Room.Faint, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.09f))
        }
    }
}

private val innerNumber = Regex("\\s[-–]\\s+(\\d{1,3})(?:\\s*[-_.)]\\s*|\\s+)")
private val leadingNumber = Regex("^\\s*(?:cd\\s*\\d+\\s*[-_. ]\\s*)?(\\d{1,3})(?:\\s*[-_.)]\\s*|\\s+)")

/**
 * "Air - Moon Safari - 01 La femme d'argent.flac" → ("01", "La femme d'argent"). Drops the
 * extension, any artist/album prefix the file repeats, and the track number (shown separately).
 */
internal fun cleanTrackName(fileName: String, album: String, artist: String?, fallbackNumber: Int): Pair<String, String> {
    var name = fileName.substringAfterLast('/').substringBeforeLast('.')
    // Strip a repeated "Artist - Album - " (or either alone) from the front.
    val prefixes = listOfNotNull(album, artist, artist?.let { "$it - $album" }).map { it.trim() }.filter { it.length >= 2 }.sortedByDescending { it.length }
    for (p in prefixes) {
        if (name.startsWith(p, ignoreCase = true)) name = name.substring(p.length).trimStart(' ', '-', '_', '.', '–')
    }
    var m = leadingNumber.find(name)
    // Still "Album - 01 Title" (e.g. the folder had a year the file doesn't)? Take it from the
    // last " - NN " in the name.
    if (m == null) innerNumber.findAll(name).lastOrNull()?.let { inner ->
        name = name.substring(inner.range.first + inner.value.indexOf(inner.groupValues[1]))
        m = leadingNumber.find(name)
    }
    val number = m?.groupValues?.get(1)?.padStart(2, '0') ?: fallbackNumber.toString().padStart(2, '0')
    if (m != null) name = name.substring(m.range.last + 1)
    // "Artist - Title" left over after the number: keep the title.
    if (artist != null && name.startsWith("$artist - ", ignoreCase = true)) name = name.substring(artist.length + 3)
    return number to name.trim().ifBlank { fileName }
}
