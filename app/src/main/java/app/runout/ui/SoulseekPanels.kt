package app.runout.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
        BasicText("Completed FLAC downloads are added to your crate and kept in your selected music folder.", style = Room.LabelQuiet)
        NetworkNote(state, actions)
        if (state.soulseekTransfers.isEmpty()) BasicText("No downloads yet. Search Soulseek or claim a Friday Drop.", style = Room.LabelQuiet, modifier = Modifier.padding(vertical = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Pill("Pause all", actions::pauseAllDownloads, enabled = state.soulseekSession.signedIn)
            Pill("Resume all", actions::resumeAllDownloads, enabled = state.soulseekSession.signedIn)
            Pill(if (state.turtleMode) "Turtle on" else "Turtle mode", { actions.setTurtleMode(!state.turtleMode) }, active = state.turtleMode)
        }
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

@Composable
fun SoulseekMessagesPanel(state: RoomState, actions: RoomActions, onAccount: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.soulseekConversation) { state.soulseekConversation?.let { selected = it } }
    val peer = selected
    Column(Modifier.fillMaxWidth().testTag("panel:messages")) {
        if (!state.soulseekSession.signedIn) SignInNotice(onAccount)
        NetworkNote(state, actions)
        if (peer == null) {
            var username by rememberSaveable { mutableStateOf("") }
            Field(username, { username = it.take(30) }, "Soulseek contact username")
            Pill("Open conversation", { actions.addSoulseekContact(username); actions.openSoulseekConversation(username); selected = username }, enabled = state.soulseekSession.signedIn && SoulseekPolicy.validUsername(username))
            val peers = (state.soulseekContacts.map { it.username } + state.soulseekConversations.map { it.username }).distinct()
            if (peers.isEmpty()) BasicText("Add a Soulseek username or message a peer from search results.", style = Room.LabelQuiet, modifier = Modifier.padding(vertical = 10.dp))
            peers.forEach { name ->
                val unread = state.soulseekConversations.firstOrNull { it.username == name }?.unread ?: 0
                val online = state.soulseekContacts.firstOrNull { it.username == name }?.online == true
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(name, style = Room.Title.copy(fontSize = 14.sp))
                        BasicText((if (online) "Online" else "Offline") + if (unread > 0) " · $unread unread" else "", style = Room.LabelQuiet)
                    }
                    Pill("Message", { selected = name; actions.openSoulseekConversation(name) })
                }
            }
        } else {
            Pill("All conversations", { selected = null; actions.closeSoulseekConversation() })
            SectionTitle(peer)
            val conversation = state.soulseekConversations.firstOrNull { it.username == peer }
            LaunchedEffect(peer, conversation?.unread) { actions.markSoulseekRead(peer) }
            conversation?.messages.orEmpty().forEach { message ->
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(if (message.outgoing) Room.Accent.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(10.dp)) {
                    Column {
                        BasicText(message.text, style = Room.Title.copy(fontSize = 13.sp))
                        BasicText("${if (message.outgoing) "You" else peer} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp))} · ${message.state.name.lowercase()}", style = Room.LabelQuiet.copy(fontSize = 10.sp))
                    }
                }
            }
            var text by rememberSaveable(peer) { mutableStateOf("") }
            val bytes = text.trim().toByteArray(Charsets.UTF_8).size
            Field(text, { text = it }, "Private message", singleLine = false)
            if (bytes > 4096) BasicText("Messages must contain 1–4,096 bytes of text.", style = Room.LabelQuiet)
            Pill("Send message", {
                val submitted = text
                actions.sendSoulseekMessage(peer, submitted) { if (text == submitted) text = "" }
            }, active = true, enabled = state.soulseekSession.signedIn && text.isNotBlank() && bytes <= 4096)
            BasicText("Soulseek can deliver stored messages when a contact reconnects. Messages are not end-to-end encrypted.", style = Room.LabelQuiet, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}
