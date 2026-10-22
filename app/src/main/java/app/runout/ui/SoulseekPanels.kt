package app.runout.ui

import androidx.compose.ui.graphics.RectangleShape
import app.runout.soulseek.SoulseekMessage
import app.runout.soulseek.SoulseekMessageState

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.Role



import androidx.compose.foundation.clickable

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.horizontalScroll

import androidx.compose.ui.geometry.Offset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekPolicy
import app.runout.soulseek.SoulseekSession
import app.runout.soulseek.SoulseekTransferState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun connectionLabel(session: SoulseekSession) = when(session.state) {
    SoulseekConnection.CONNECTED -> "Online"
    SoulseekConnection.CONNECTING -> "Connecting"
    SoulseekConnection.RECONNECTING -> "Reconnecting"
    SoulseekConnection.PAUSED -> "Paused"
    SoulseekConnection.ERROR -> "Account !"
    else -> if (session.hasAccount) "Offline" else "Connect"
}

@Composable
internal fun ConnectionNotice(state: RoomState, actions: RoomActions, onAccount: () -> Unit) {
    val session = state.soulseekSession
    if(session.connected) return
    ConsoleCard(Modifier.padding(vertical = 8.dp)) {
        BasicText(if(session.hasAccount) connectionLabel(session) else "Your local music is always available",style = Room.Title)
        BasicText(session.error ?: "Connect Soulseek to discover FLAC, download records and send messages.",style = Room.LabelQuiet)
        if(session.hasAccount) Pill("Reconnect",actions::reconnectSoulseek,enabled = session.state != SoulseekConnection.RECONNECTING)
        else Pill("Connect Soulseek",onAccount,active = true)
    }
}

@Composable
internal fun NetworkNote(state: RoomState, actions: RoomActions) {
    val note = state.soulseekError ?: state.soulseekSession.error ?: return
    BasicText(note, style = Room.LabelQuiet.copy(color = Color(0xFFAD3A22)), modifier = Modifier.padding(vertical = 8.dp).testTag("soulseek-error"))
    Pill("Dismiss", actions::clearSoulseekError)
}

@Composable
private fun SignInNotice(onAccount: () -> Unit) {
    BasicText("Sign in with Soulseek to search, download FLAC, and send private messages.", style = Room.LabelQuiet)
    Pill("Sign in with Soulseek", onAccount, modifier = Modifier.padding(vertical = 8.dp), active = true)
}

@Composable
fun SoulseekAccountPanel(state: RoomState, actions: RoomActions, onOffline: () -> Unit, onSignIn: () -> Unit = {}) {
    val session = state.soulseekSession
    Column(Modifier.fillMaxWidth().testTag("panel:account")) {
        BasicText("Your local FLAC library is yours to play without an account.", style = Room.Title.copy(fontSize = 14.sp))
        BasicText("Sign in to search Soulseek, download from Soulseek or Internet Archive, and message people.", style = Room.LabelQuiet, modifier = Modifier.padding(vertical = 6.dp))
        if (session.hasAccount) {
            SectionTitle("Account · ${session.username}")
            BasicText(if(session.connected) "Connected to Soulseek" else session.error ?: when(session.state) {
                SoulseekConnection.CONNECTING -> "Connecting to Soulseek"
                SoulseekConnection.RECONNECTING -> "Reconnecting to Soulseek"
                else -> "Connection paused"
            },style = Room.LabelQuiet,modifier = Modifier.testTag("account-connection"))
            if(!session.connected) Pill("Reconnect",actions::reconnectSoulseek,enabled = session.state != SoulseekConnection.RECONNECTING)
            Pill("Sign out", actions::signOutSoulseek)
        } else {
            // One sign-in for the whole app: the same screen as onboarding, always remembered.
            Pill("Sign in with Soulseek", onSignIn, modifier = Modifier.padding(top = 8.dp).testTag("account-sign-in"), active = true)
            Pill("Continue offline", onOffline, modifier = Modifier.padding(top = 8.dp))
        }
        NetworkNote(state, actions)
        SectionTitle("Keep your downloads")
        BasicText(state.downloadFolder ?: "Choose a music folder before downloading. FLAC files in this folder stay accessible to other players and remain after uninstalling Runout.", style = Room.LabelQuiet)
        Pill(if (state.downloadFolder == null) "Choose download folder" else "Change download folder", actions::pickDownloadFolder, modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Collection & storage")
        ToggleRow("Find missing covers online", "Send artist and album title to MusicBrainz, the Cover Art Archive and the Internet Archive to find missing covers. Off unless you opt in.", state.coverLookupEnabled, actions::setCoverLookupEnabled)
        SectionTitle("Share FLAC on Soulseek")
        BasicText(state.sharedFolder ?: "Choose a folder to share. Only validated FLAC files in that folder will be visible to other users.", style = Room.LabelQuiet)
        Pill(if (state.sharedFolder == null) "Choose sharing folder" else "Change sharing folder", actions::pickSharedFolder, modifier = Modifier.padding(vertical = 8.dp))
        ToggleRow("Share selected FLAC folder", "Other Soulseek users can search and download files you explicitly share.", state.soulseekShareConsent, actions::setSoulseekSharing, enabled = session.hasAccount && (state.soulseekShareConsent || (session.connected && state.sharedFolder != null)))
        ToggleRow("Stay online in the background", "Keep private messages and sharing available with an ongoing notification. You can disconnect from that notification.", state.soulseekBackgroundConsent, actions::setSoulseekStayOnline, enabled = session.hasAccount && (state.soulseekBackgroundConsent || session.connected))
        if(state.soulseekShareConsent && !state.soulseekSharing) BasicText("Sharing is paused. It resumes after connection and folder checks succeed.",style = Room.LabelQuiet)
        if(state.soulseekBackgroundConsent && !state.soulseekStayOnline) BasicText("Background mode is paused. Check notification permission and reconnect to resume.",style = Room.LabelQuiet)
        BasicText("Soulseek's protocol does not encrypt sign-in credentials or private messages.", style = Room.LabelQuiet, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
    }
}

private fun bytesLabel(bytes: Long): String = if (bytes >= 1_073_741_824L) String.format(Locale.ROOT, "%.1f GB", bytes / 1_073_741_824.0) else String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)

@Composable
fun SoulseekTransfersPanel(state: RoomState, actions: RoomActions) {
    Column(Modifier.fillMaxWidth().testTag("panel:transfers")) {
        BackgroundUseNote()
        // The controls as three small hardware keys: pause all, resume all, turtle (go gently).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            TeKey(null, actions::pauseAllDownloads, Modifier.testTag("downloads-pause-all"), enabled = state.soulseekSession.signedIn, description = "Pause all") { c ->
                val w = size.width
                drawRect(c, topLeft = Offset(w * 0.22f, w * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.18f, w * 0.7f))
                drawRect(c, topLeft = Offset(w * 0.6f, w * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.18f, w * 0.7f))
            }
            TeKey(null, actions::resumeAllDownloads, Modifier.testTag("downloads-resume-all"), enabled = state.soulseekSession.signedIn, description = "Resume all") { c ->
                val w = size.width
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.25f, w * 0.12f); lineTo(w * 0.85f, w * 0.5f); lineTo(w * 0.25f, w * 0.88f); close() }, c)
            }
            TeKey(null, { actions.setTurtleMode(!state.turtleMode) }, Modifier.testTag("downloads-turtle"), selected = state.turtleMode, description = if (state.turtleMode) "Turtle mode on" else "Turtle mode") { c ->
                // A small turtle: domed shell, head, two feet.
                val w = size.width
                drawArc(c, 180f, 180f, true, topLeft = Offset(w * 0.12f, w * 0.28f), size = androidx.compose.ui.geometry.Size(w * 0.62f, w * 0.56f))
                drawCircle(c, w * 0.11f, Offset(w * 0.84f, w * 0.52f))
                drawRect(c, topLeft = Offset(w * 0.18f, w * 0.56f), size = androidx.compose.ui.geometry.Size(w * 0.12f, w * 0.16f))
                drawRect(c, topLeft = Offset(w * 0.54f, w * 0.56f), size = androidx.compose.ui.geometry.Size(w * 0.12f, w * 0.16f))
            }
        }
        NetworkNote(state, actions)
        if (state.soulseekTransfers.isEmpty()) BasicText("Nothing downloading.", style = Room.LabelQuiet, modifier = Modifier.padding(vertical = 18.dp).testTag("downloads-empty"))
        state.soulseekTransfers.forEach { transfer ->
            SectionTitle(transfer.fileName)
            BasicText("${transfer.username} · ${transfer.state.name.lowercase().replaceFirstChar { it.uppercase() }}", style = Room.LabelQuiet)
            transfer.queuePosition?.let { BasicText("Peer queue position $it", style = Room.LabelQuiet) }
            BasicText(if (transfer.provider == "Internet Archive") "${(transfer.progress * 100).toInt()}% · Internet Archive" else "${bytesLabel(transfer.bytes)} of ${bytesLabel(transfer.size)} · ${(transfer.progress * 100).toInt()}%", style = Room.LabelQuiet)
            ProgressLine(transfer.progress, Modifier.padding(vertical = 6.dp))
            transfer.error?.let { BasicText(it, style = Room.LabelQuiet.copy(color = Color(0xFFAD3A22))) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (transfer.state) {
                    SoulseekTransferState.QUEUED, SoulseekTransferState.DOWNLOADING -> {
                        Pill("Pause", { actions.pauseSoulseekTransfer(transfer.id) })
                        Pill("Cancel", { actions.cancelSoulseekTransfer(transfer.id) })
                    }
                    SoulseekTransferState.PAUSED -> {
                        Pill("Resume", { actions.resumeSoulseekTransfer(transfer.id) }, enabled = state.soulseekSession.signedIn)
                        Pill("Cancel", { actions.cancelSoulseekTransfer(transfer.id) })
                    }
                    SoulseekTransferState.FAILED, SoulseekTransferState.CANCELLED -> Pill("Retry", { actions.retrySoulseekTransfer(transfer.id) }, enabled = state.soulseekSession.signedIn)
                    else -> Unit
                }
            }
        }
        if (state.soulseekUploads.isNotEmpty()) {
            SectionTitle("Shared FLAC uploads")
            Pill("Stop sharing", { actions.setSoulseekSharing(false) })
            state.soulseekUploads.forEach { upload ->
                BasicText(upload.fileName, style = Room.Title.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 10.dp))
                BasicText("${upload.username} · ${upload.state} · ${(upload.progress * 100).toInt()}%", style = Room.LabelQuiet)
                ProgressLine(upload.progress)
            }
        }
    }
}

/**
 * Friends, on its own page: your saved friends with their online dots, then your chats, newest
 * first; a floating + starts a chat by Soulseek username. Inside a chat you can send a record as a
 * card, which the other person can find on Soulseek with one tap.
 */
@Composable
fun SoulseekMessagesPanel(state: RoomState, actions: RoomActions, onAccount: () -> Unit, onDig: () -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.soulseekConversation) { state.soulseekConversation?.let { selected = it } }
    val peer = selected
    Box(Modifier.fillMaxSize().testTag("panel:messages")) {
        if (peer == null) {
            var newChat by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
                if (!state.soulseekSession.signedIn) SignInNotice(onAccount)
                NetworkNote(state, actions)
                val friends = state.soulseekContacts
                if (friends.isNotEmpty()) {
                    SectionTitle("Friends")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        friends.sortedByDescending { it.online }.forEach { f ->
                            Column(
                                Modifier
                                    .testTag("friend:${f.username}")
                                    .clickable(role = Role.Button) { selected = f.username; actions.openSoulseekConversation(f.username) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Avatar(f.username, online = f.online, size = 46.dp)
                                BasicText(f.username, style = Room.Label.copy(fontSize = 10.sp), maxLines = 1)
                            }
                        }
                    }
                }
                SectionTitle("Chats")
                val chats = state.soulseekConversations.sortedByDescending { c -> c.messages.maxOfOrNull { it.timestamp } ?: 0L }
                if (chats.isEmpty()) BasicText("No chats yet. Tap + to message someone.", style = Room.LabelQuiet, modifier = Modifier.padding(vertical = 10.dp))
                chats.forEach { c ->
                    val last = c.messages.maxByOrNull { it.timestamp }
                    val online = state.soulseekContacts.firstOrNull { it.username == c.username }?.online == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag("chat:${c.username}")
                            .clickable(role = Role.Button) { selected = c.username; actions.openSoulseekConversation(c.username) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(c.username, online = online, size = 42.dp)
                        Column(Modifier.weight(1f)) {
                            BasicText(c.username, style = Room.Title.copy(fontSize = 14.sp))
                            BasicText(last?.let { preview(it) } ?: "", style = Room.LabelQuiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            last?.let { BasicText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.timestamp)), style = Room.LabelQuiet.copy(fontSize = 10.sp)) }
                            if (c.unread > 0) Box(Modifier.size(9.dp).background(Room.Signal, RectangleShape).semantics { contentDescription = "${c.unread} unread" })
                        }
                    }
                }
            }
            // Start a chat: a floating +, bottom right.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(56.dp)
                    
                    .background(Room.Ink, RectangleShape)
                    .clickable(enabled = state.soulseekSession.signedIn, role = Role.Button) { newChat = true }
                    .semantics { contentDescription = "New chat" }
                    .testTag("new-chat"),
                contentAlignment = Alignment.Center,
            ) { BasicText("+", style = Room.Title.copy(fontSize = 26.sp, color = Color.White)) }
            if (newChat) NewChat(onDismiss = { newChat = false }) { name, friend ->
                if (friend) actions.addSoulseekContact(name)
                actions.openSoulseekConversation(name)
                selected = name
                newChat = false
            }
        } else {
            Chat(peer, state, actions, onBack = { selected = null; actions.closeSoulseekConversation() }, onDig = onDig)
        }
    }
}

/** A round initial, with a green dot when the person is online. */
@Composable
private fun Avatar(name: String, online: Boolean, size: androidx.compose.ui.unit.Dp) {
    val hue = ((name.hashCode() and 0x7fffffff) % 360).toFloat()
    Box(Modifier.size(size)) {
        Box(Modifier.size(size).background(Color.hsl(hue, 0.45f, 0.82f), RectangleShape), contentAlignment = Alignment.Center) {
            BasicText(name.take(1).uppercase(), style = Room.Title.copy(fontSize = (size.value * 0.4f).sp, color = Color.hsl(hue, 0.5f, 0.3f)))
        }
        if (online) Box(Modifier.align(Alignment.BottomEnd).size(size * 0.28f).background(Color.White, RectangleShape).padding(2.dp).background(Room.Online, RectangleShape))
    }
}

private fun preview(m: SoulseekMessage): String {
    val card = RecordCard.parse(m.text)
    val text = if (card != null) "♪ ${card.album} — ${card.artist}" else m.text
    return (if (m.outgoing) "You: " else "") + text.lineSequence().first()
}

@Composable
private fun NewChat(onDismiss: () -> Unit, onStart: (String, Boolean) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var friend by rememberSaveable { mutableStateOf(true) }
    PageSheet("new-chat-card", onDismiss) {
        BasicText("New chat", style = Room.Title.copy(fontSize = 17.sp))
        Field(username, { username = it.take(30) }, "Soulseek username")
        ToggleRow("Add as a friend", "See when they're online.", friend, { friend = it })
        Pill("Start chat", { onStart(username.trim(), friend) }, Modifier.testTag("start-chat"), active = true, enabled = SoulseekPolicy.validUsername(username.trim()))
    }
}

@Composable
private fun Chat(peer: String, state: RoomState, actions: RoomActions, onBack: () -> Unit, onDig: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val conversation = state.soulseekConversations.firstOrNull { it.username == peer }
    LaunchedEffect(peer, conversation?.unread) { actions.markSoulseekRead(peer) }
    val contact = state.soulseekContacts.firstOrNull { it.username == peer }
    var sharing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TextAction("‹ Chats", onBack, tag = "chats-back", color = Room.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Avatar(peer, online = contact?.online == true, size = 36.dp)
            Column(Modifier.weight(1f)) {
                BasicText(peer, style = Room.Title.copy(fontSize = 16.sp))
                BasicText(if (contact?.online == true) "Online" else if (contact != null) "Offline" else "Not a friend yet", style = Room.LabelQuiet)
            }
            if (contact == null) Pill("Add friend", { actions.addSoulseekContact(peer) }, Modifier.testTag("add-friend"))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            conversation?.messages.orEmpty().forEach { message ->
                val card = RecordCard.parse(message.text)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .padding(start = if (message.outgoing) 40.dp else 0.dp, end = if (message.outgoing) 0.dp else 40.dp)
                        .background(if (message.outgoing) Room.Surface else Room.Background, RectangleShape)
                        .padding(10.dp),
                ) {
                    if (card != null) RecordCardView(card, state, mine = message.outgoing) {
                        actions.setSearchOptions(state.discovery.options.copy(source = app.runout.discovery.SearchSource.SOULSEEK))
                        actions.setSearchQuery("${card.artist} ${card.album}")
                        actions.searchSoulseek("${card.artist} ${card.album}")
                        onDig()
                    } else BasicText(message.text, style = Room.Title.copy(fontSize = 13.sp))
                    BasicText(
                        "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp))}" + if (message.outgoing && message.state != SoulseekMessageState.SENT) " · ${message.state.name.lowercase()}" else "",
                        style = Room.LabelQuiet.copy(fontSize = 10.sp),
                    )
                }
            }
        }
        var text by rememberSaveable(peer) { mutableStateOf("") }
        val bytes = text.trim().toByteArray(Charsets.UTF_8).size
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TeKey(null, { sharing = true }, Modifier.testTag("share-record"), enabled = state.soulseekSession.signedIn && state.albums.isNotEmpty(), description = "Send a record") { c ->
                // A small record.
                val w = size.width
                drawCircle(c, w * 0.42f, center)
                drawCircle(Color.White, w * 0.14f, center)
            }
            Box(Modifier.weight(1f)) { Field(text, { text = it }, "Private message", singleLine = false) }
            Pill("Send message", {
                val submitted = text
                actions.sendSoulseekMessage(peer, submitted) { if (text == submitted) text = "" }
            }, active = true, enabled = state.soulseekSession.signedIn && text.isNotBlank() && bytes <= 4096)
        }
        if (bytes > 4096) BasicText("Messages must contain 1–4,096 bytes of text.", style = Room.LabelQuiet)
    }
    if (sharing) RecordPicker(state.albums, onDismiss = { sharing = false }) { album ->
        actions.sendSoulseekMessage(peer, RecordCard.of(album).encode()) {}
        sharing = false
    }
}

/** A record shared in a chat: its sleeve, title and artist; a tap looks for it on Soulseek. */
@Composable
private fun RecordCardView(card: RecordCard, state: RoomState, mine: Boolean, onFind: () -> Unit) {
    val local = state.albums.firstOrNull { it.title.equals(card.album, true) && it.artist.equals(card.artist, true) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("record-card")) {
        if (local != null) SleeveArt(local, Modifier.size(56.dp)) else GeneratedSleeve(card.album, card.artist, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(card.album, style = Room.Title.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(card.artist, style = Room.LabelQuiet, maxLines = 1)
            card.quality?.let { SpecTag(it) }
            if (!mine) Pill(if (local != null) "In your crate" else "Find on Soulseek", onFind, Modifier.testTag("find-record"), enabled = local == null)
        }
    }
}

@Composable
private fun RecordPicker(albums: List<app.runout.model.Album>, onDismiss: () -> Unit, onPick: (app.runout.model.Album) -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).background(Color.White, RectangleShape).padding(20.dp).testTag("record-picker")) {
            BasicText("Send a record", style = Room.Title.copy(fontSize = 17.sp), modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                albums.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(a) }.testTag("pick-record:${a.id}").padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SleeveArt(a, Modifier.size(44.dp))
                        Column { BasicText(a.title, style = Room.Title.copy(fontSize = 14.sp)); BasicText(a.artist, style = Room.LabelQuiet) }
                    }
                }
            }
        }
    }
}

/**
 * A record sent in a Soulseek private message. It travels as ordinary text that any Soulseek
 * client shows sensibly ("♪ Album — Artist · FLAC 24/96"), plus a last line Runout recognises:
 * `runout:record?artist=…&album=…&q=…`, URL-encoded.
 */
internal data class RecordCard(val artist: String, val album: String, val quality: String?) {
    fun encode(): String {
        fun e(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
        val q = quality?.let { "&q=${e(it)}" } ?: ""
        return "♪ $album — $artist" + (quality?.let { " · $it" } ?: "") + "\nrunout:record?artist=${e(artist)}&album=${e(album)}$q"
    }

    companion object {
        private val line = Regex("runout:record\\?artist=([^&\\s]*)&album=([^&\\s]*)(?:&q=([^&\\s]*))?")
        fun of(album: app.runout.model.Album) = RecordCard(album.artist, album.title, album.quality.label)
        fun parse(text: String): RecordCard? {
            val m = line.find(text) ?: return null
            fun d(v: String) = runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
            val artist = d(m.groupValues[1]); val album = d(m.groupValues[2])
            if (artist.isBlank() || album.isBlank()) return null
            return RecordCard(artist, album, m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.let(::d))
        }
    }
}

/**
 * When Android's "Background usage" is off for Runout, downloads stop the moment you leave the app
 * (they come back as "Interrupted system call"). Say so where the downloads are, with a way to fix it.
 */
@Composable
private fun BackgroundUseNote() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val restricted = remember {
        android.os.Build.VERSION.SDK_INT >= 28 &&
            context.getSystemService(android.app.ActivityManager::class.java)?.isBackgroundRestricted == true
    }
    if (!restricted) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Room.Signal.copy(alpha = 0.1f), RectangleShape)
            .padding(12.dp)
            .testTag("background-restricted"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText("Background usage is off for Runout", style = Room.Title.copy(fontSize = 13.sp))
        BasicText(
            "Android stops downloads as soon as you leave the app. Keep Runout open, or allow background usage: App info › Battery › Allow background usage. Interrupted downloads continue when you come back.",
            style = Room.LabelQuiet,
        )
        Pill("Open App info", {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }, Modifier.testTag("open-app-info"), active = true)
    }
}
