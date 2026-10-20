package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
internal fun DiscoveryScreen(state: RoomState, actions: RoomActions, onAccount: () -> Unit, onPlay: () -> Unit) {
    val discovery = state.discovery
    val options = discovery.options
    var filters by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val searching = discovery.online.phase == SearchPhase.SEARCHING
    val submit = { if(discovery.query.trim().isNotEmpty()) actions.searchSoulseek(discovery.query) }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("screen:search"),contentPadding = PaddingValues(16.dp,12.dp,16.dp,24.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("search-controls") {
            ConsoleTitle("Dig for a record","Find your next favorite.","Songs, albums and artists · always FLAC")
            Field(discovery.query,actions::setSearchQuery,"Search songs, albums or artists",onSubmit = { if(state.soulseekSession.connected && !searching) submit() })
            Chips(SearchSource.entries,options.source,{ it.label },{ actions.setSearchOptions(options.copy(source = it)) },Modifier.padding(vertical = 8.dp))
            Chips(SearchKind.entries,options.kind,{ it.label },{ actions.setSearchOptions(options.copy(kind = it)) })
            if(options.source != SearchSource.LOCAL) {
                FlowRow(Modifier.padding(vertical = 8.dp),horizontalArrangement = Arrangement.spacedBy(8.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if(state.soulseekSession.connected) {
                        Pill(if(searching) "Searching Soulseek…" else "Search Soulseek",submit,Modifier.testTag("submit-search"),active = true,enabled = discovery.query.trim().toByteArray().size >= 2 && !searching)
                        if(searching) Pill("Stop",actions::stopSoulseekSearch)
                    } else Pill(if(state.soulseekSession.hasAccount) "Reconnect Soulseek" else "Connect Soulseek",{ if(state.soulseekSession.hasAccount) actions.reconnectSoulseek() else onAccount() },active = true,enabled = state.soulseekSession.state != SoulseekConnection.RECONNECTING)
                    Pill(if(filters) "Hide filters" else "Filters & sort",{ filters = !filters },Modifier.testTag("search-filters"))
                }
                if(filters) ConsoleCard {
                    BasicText("Sort online results",style = Room.LabelQuiet)
                    Chips(SearchSort.entries,options.sort,{ it.label },{ actions.setSearchOptions(options.copy(sort = it)) })
                    BasicText("Reported FLAC quality",style = Room.LabelQuiet)
                    Chips(SearchQuality.entries,options.quality,{ it.label },{ actions.setSearchOptions(options.copy(quality = it)) })
                    ToggleRow("Available upload slots only","Peer availability is advertised and can change.",options.availableOnly,{ actions.setSearchOptions(options.copy(availableOnly = it)) })
                    BasicText("Soulseek shows FLAC only. Every search word must match the artist, album, folder, or track title.", style = Room.LabelQuiet)
                }
                if(!state.soulseekSession.connected && state.soulseekSession.hasAccount) BasicText(state.soulseekSession.error ?: "Your account is kept. Reconnect to search online.",style = Room.LabelQuiet,modifier = Modifier.padding(vertical = 8.dp))
            }
            if(discovery.query.isBlank()) BasicText("Try an artist with a song or album title. Local matches appear as you type; online search starts when you press Search.",style = Room.LabelQuiet,modifier = Modifier.padding(vertical = 16.dp))
        }
        if(options.source != SearchSource.SOULSEEK && discovery.query.isNotBlank()) {
            item("local-heading") { SectionTitle("On this device · ${discovery.local.size} matches") }
            items(discovery.local,key = { "local:${it.id}" }) { hit ->
                ConsoleCard {
                    BasicText(hit.track?.title ?: hit.album.title,style = Room.Title)
                    BasicText("${hit.album.artist} · ${hit.album.title} · ${hit.album.quality.label}",style = Room.LabelQuiet)
                    Pill(if(hit.track == null) "Play album" else "Play song",{
                        val index = hit.track?.let { track -> hit.album.tracks.indexOfFirst { it.id == track.id } } ?: 0
                        actions.playLocalTrack(hit.album,index)
                        onPlay()
                    },Modifier.testTag("play-local:${hit.id}"),active = true)
                }
            }
            if(discovery.local.isEmpty()) item("no-local") { BasicText("No local matches. Try fewer words or search Soulseek.",style = Room.LabelQuiet) }
        }
        if(options.source != SearchSource.LOCAL && discovery.online.query.isNotBlank()) {
            item("online-heading") {
                SectionTitle("Soulseek · ${discovery.remote.sumOf { it.tracks.size }} matching FLAC files")
                BasicText(searchStatus(discovery),style = Room.LabelQuiet,modifier = Modifier.testTag("search-status"))
                if(discovery.online.limited) BasicText("Showing the first 5,000 FLAC matches. Refine your search for a closer shortlist.",style = Room.LabelQuiet)
                if(discovery.online.phase in setOf(SearchPhase.FAILED,SearchPhase.INTERRUPTED)) {
                    Pill("Retry search",submit,enabled = state.soulseekSession.connected)
                }
                if(discovery.remote.isEmpty() && discovery.online.phase == SearchPhase.COMPLETE) Pill("Broaden search terms",actions::broadenSearch)
            }
            discovery.remote.forEach { group ->
                val open = group.id in expanded || options.kind == SearchKind.SONGS
                item("folder:${group.id}") {
                    ConsoleCard(Modifier.testTag("search-album:${group.id}")) {
                        BasicText(group.title,style = Room.Title.copy(fontSize = 16.sp))
                        BasicText("${group.username} · ${group.tracks.size} matching tracks",style = Room.LabelQuiet)
                        BasicText(availability(group.tracks.firstOrNull { it.freeSlots > 0 } ?: group.tracks.first()),style = Room.LabelQuiet)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("Preview album folder",{ actions.previewSoulseekFolder(group.tracks.first()) },Modifier.testTag("preview-folder:${group.id}"),active = true,enabled = state.soulseekSession.connected)
                            if(options.kind != SearchKind.SONGS) Pill(if(open) "Hide tracks" else "Show tracks",{ expanded = if(open) expanded - group.id else expanded + group.id })
                        }
                    }
                }
                if(open) items(group.tracks,key = { "remote:${it.id}" }) { track ->
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp,bottom = 6.dp).testTag("search-track:${track.id}"),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            BasicText(track.fileName,style = Room.Title.copy(fontSize = 13.sp),maxLines = 2,overflow = TextOverflow.Ellipsis)
                            BasicText(trackDetails(track),style = Room.LabelQuiet)
                        }
                        Pill("Download",{ actions.downloadSoulseek(track) },enabled = state.soulseekSession.connected,modifier = Modifier.testTag("download-track:${track.id}"))
                    }
                }
            }
        }
    }
}

private fun searchStatus(state: DiscoveryState): String {
    if(normalize(state.query) != normalize(state.online.query)) return "Press Search Soulseek to look for these new terms."
    return when(state.online.phase) {
        SearchPhase.SEARCHING -> "Listening for peers… Results appear as they arrive."
        SearchPhase.COMPLETE -> if(state.remote.isEmpty()) "No matching FLAC with these filters. Try fewer words or change filters." else "Search complete. Preview a folder to check its full track list."
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

@Composable
internal fun FolderPreviewScreen(preview: SoulseekFolderPreview, state: RoomState, actions: RoomActions, onMessage: (String) -> Unit = {}) {
    BackHandler(onBack = actions::closeSoulseekFolder)
    Column(Modifier.fillMaxSize().background(Room.Background).safeDrawingPadding().padding(16.dp).testTag("screen:folder-preview")) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            BasicText("ALBUM / FOLDER PREVIEW",style = Room.Brand,modifier = Modifier.weight(1f))
            Pill("Close",actions::closeSoulseekFolder)
        }
        ConsoleTitle(preview.username,preview.directory.substringAfterLast('/').ifBlank { "Shared FLAC" },if(preview.loading) "Getting the complete folder listing…" else "${preview.files.size} FLAC tracks · ${sizeLabel(preview.files.sumOf { it.size })}")
        Pill("Message this peer",{ onMessage(preview.username) },enabled = state.soulseekSession.connected)
        preview.error?.let { BasicText(it,style = Room.LabelQuiet) }
        BasicText("This is a peer's folder listing. Search matches alone do not establish a complete release.",style = Room.LabelQuiet)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding = PaddingValues(vertical = 12.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(preview.files,key = { it.id }) { file ->
                Column { BasicText(file.fileName,style = Room.Title);BasicText(trackDetails(file),style = Room.LabelQuiet) }
            }
        }
        if(state.downloadFolder == null) Pill("Choose download folder",actions::pickDownloadFolder,Modifier.fillMaxWidth())
        else BasicText("Keep in ${state.downloadFolder}",style = Room.LabelQuiet,modifier = Modifier.padding(vertical = 8.dp))
        Pill("Download ${preview.files.size} FLAC tracks",actions::confirmSoulseekFolder,Modifier.fillMaxWidth().testTag("confirm-folder"),active = true,enabled = !preview.loading && preview.error == null && preview.files.isNotEmpty() && state.downloadFolder != null && state.soulseekSession.connected)
    }
}
