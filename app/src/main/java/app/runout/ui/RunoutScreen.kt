package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.discovery.*
import app.runout.model.Album
import app.runout.soulseek.*

private enum class Destination(val label: String) { HOME("Home"), LIBRARY("Crate"), DISCOVER("Discover"), MESSAGES("Messages") }

@Composable
internal fun ConsoleTitle(kicker: String, title: String, detail: String? = null) {
    BasicText(kicker.uppercase(),style = Room.Label.copy(fontFamily = FontFamily.Monospace,letterSpacing = 1.5.sp,color = Room.Accent),modifier = Modifier.padding(bottom = 8.dp))
    BasicText(title,style = Room.Title.copy(fontSize = 26.sp,lineHeight = 30.sp,fontWeight = FontWeight.SemiBold))
    if(detail != null) BasicText(detail,style = Room.LabelQuiet.copy(fontSize = 13.sp,lineHeight = 19.sp),modifier = Modifier.padding(top = 8.dp,bottom = 12.dp))
}

@Composable
internal fun ConsoleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Color(0xFFFFFCF4),RoundedCornerShape(12.dp))
        .border(1.dp,Color(0xFFB7BAAA),RoundedCornerShape(12.dp)).padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp),content = content)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun RunoutScreen(state: RoomState, actions: RoomActions) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var overlay by rememberSaveable { mutableStateOf<Panel?>(null) }
    var setup by rememberSaveable { mutableStateOf(!state.onboardingComplete) }
    var setupStart by rememberSaveable { mutableIntStateOf(state.onboardingStep) }
    var drops by rememberSaveable { mutableStateOf(false) }
    var pressingChanged by remember { mutableStateOf(false) }
    var discardPressing by remember { mutableStateOf(false) }
    fun closeOverlay() {
        if (overlay == Panel.PRESSING && pressingChanged) discardPressing = true
        else { overlay = null; pressingChanged = false }
    }
    val openSetup = { setupStart = 1; setup = true }
    BackHandler(enabled = overlay != null || destination != Destination.HOME) {
        if(overlay != null) closeOverlay() else destination = Destination.HOME
    }
    Box(Modifier.fillMaxSize().background(Room.Background).semantics { testTagsAsResourceId = true }) {
        if (setup) {
            SetupScreen(state,actions,setupStart) { actions.finishOnboarding(); setup = false }
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp,vertical = 6.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        BasicText("RUNOUT",style = Room.Brand.copy(fontSize = 20.sp,letterSpacing = 1.2.sp))
                        BasicText("your room",style = Room.LabelQuiet.copy(fontSize = 12.sp))
                    }
                    Pill("↓ Downloads · ${state.soulseekTransfers.count { it.state in setOf(SoulseekTransferState.QUEUED,SoulseekTransferState.DOWNLOADING,SoulseekTransferState.PUBLISHING,SoulseekTransferState.VALIDATING) }}",{ overlay = Panel.TRANSFERS },Modifier.testTag("open-downloads"))
                    Pill("Settings",{ overlay = Panel.SOUND },Modifier.testTag("open-settings"))
                    BasicText(connectionLabel(state.soulseekSession),style = Room.LabelQuiet,modifier = Modifier.clickable { if(state.soulseekSession.hasAccount) overlay = Panel.ACCOUNT else openSetup() }.testTag("open-account"))
                }
                state.soulseekError?.let { error ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFFF2DACC)).padding(horizontal = 12.dp),verticalAlignment = Alignment.CenterVertically) {
                        BasicText(error,style = Room.Label.copy(color = Color(0xFF752B20)),modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        Pill("Dismiss",actions::clearSoulseekError)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when(destination) {
                        Destination.HOME -> RoomScreen(state,actions,modifier = Modifier.fillMaxSize().testTag("screen:home"),showChrome = false,idleRack = state.deck.album == null,inked = true)
                        Destination.LIBRARY -> LibraryScreen(state,actions) { destination = Destination.HOME }
                        Destination.DISCOVER -> Column(Modifier.fillMaxSize().testTag("screen:discover")) {
                            Chips(listOf(false,true),drops,{ if(it) "Friday Drops" else "Find music" },{ drops = it },Modifier.padding(horizontal = 16.dp,vertical = 4.dp))
                            if(drops) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                ConsoleTitle("Weekly selection","Friday Drops","Internet Archive · FLAC records to keep")
                                DropsPanel(state.drops,state.claimed,state.claiming,actions,state.soulseekSession.connected,onAccount = openSetup)
                            } else DiscoveryScreen(state,actions,onAccount = openSetup,onPlay = { destination = Destination.HOME })
                        }
                        Destination.MESSAGES -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen:messages")) {
                            ConsoleTitle("Private line","Messages")
                            ConnectionNotice(state,actions,openSetup)
                            SoulseekMessagesPanel(state,actions,onAccount = openSetup)
                        }
                    }
                    if(destination == Destination.LIBRARY) Pill("Press a record",{ overlay = Panel.PRESSING },Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("open-pressing"),active = true)
                }
                if(destination != Destination.HOME || state.deck.album != null) NowPlayingStrip(state,actions) { destination = Destination.HOME }
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Destination.entries.forEach { item ->
                        val unread = item == Destination.MESSAGES && state.soulseekConversations.any { it.unread > 0 }
                        Pill(item.label + if(unread) " •" else "",{ destination = item },Modifier.weight(1f).testTag("destination:${item.name}"),active = destination == item)
                    }
                }
            }
            if(overlay != null) RetroPage(overlay!!.title,{ closeOverlay() }) {
                when(overlay) {
                    Panel.ACCOUNT -> {
                        ConnectionNotice(state,actions,openSetup)
                        SoulseekAccountPanel(state,actions,onOffline = { overlay = null })
                        Pill("Review Soulseek setup",{ overlay = null; openSetup() },Modifier.padding(top = 12.dp))
                    }
                    Panel.TRANSFERS -> SoulseekTransfersPanel(state,actions)
                    Panel.SOUND -> SoundPanel(state.sound,state.output,actions)
                    Panel.PRESSING -> PressingPanel(state.albums,state.profile,actions) { pressingChanged = it }
                    else -> Unit
                }
            }
        }
        if (discardPressing) ConfirmationDialog(
            "Discard pressing draft?", "Your selected tracks, sleeve and record details will be lost.",
            "Discard draft", "Keep editing",
            onConfirm = { discardPressing = false; pressingChanged = false; overlay = null },
            onDismiss = { discardPressing = false },
        )
        state.folderPreview?.let { FolderPreviewScreen(it,state,actions) { peer ->
            actions.closeSoulseekFolder(); actions.openSoulseekConversation(peer); destination = Destination.MESSAGES; overlay = null
        } }
        if(state.coverLookupNoticePending && !setup) Box(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp,end = 16.dp,bottom = 90.dp).background(Room.Ink,RoundedCornerShape(8.dp)).padding(12.dp).testTag("cover-lookup-notice")) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Finding missing covers online is now opt-in. It is off unless you enable it in Collection & storage. Lookups send artist and album title to MusicBrainz, the Cover Art Archive and the Internet Archive.",style = Room.Label.copy(color = Color.White))
                Pill("OK",actions::dismissCoverLookupNotice)
            }
        } else if(state.message != null && !setup) Box(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp,end = 16.dp,bottom = 90.dp).background(Room.Ink,RoundedCornerShape(8.dp)).padding(12.dp)) {
            BasicText(state.message,style = Room.Label.copy(color = Color.White),modifier = Modifier.clickable { actions.dismissMessage() })
            LaunchedEffect(state.message) { kotlinx.coroutines.delay(7000); actions.dismissMessage() }
        }
    }
}

private fun connectionLabel(session: SoulseekSession) = when(session.state) {
    SoulseekConnection.CONNECTED -> "● Online"
    SoulseekConnection.CONNECTING -> "Connecting"
    SoulseekConnection.RECONNECTING -> "Reconnecting"
    SoulseekConnection.PAUSED -> "Paused"
    SoulseekConnection.ERROR -> "Account !"
    else -> "Connect"
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
internal fun NowPlayingStrip(state: RoomState, actions: RoomActions, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF253D30)).padding(horizontal = 12.dp,vertical = 6.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).clickable(onClick = onHome).padding(4.dp)) {
            BasicText(state.deck.currentTrackTitle ?: state.deck.album?.title ?: "Nothing on the platter",style = Room.Title.copy(color = Color(0xFFCEE8A5),fontSize = 13.sp),maxLines = 1,overflow = TextOverflow.Ellipsis)
            BasicText(state.deck.album?.let { "${it.artist} · ${it.title}" } ?: "Choose a record from your library",style = Room.Label.copy(color = Color(0xFFCDD2C4)),maxLines = 1,overflow = TextOverflow.Ellipsis)
        }
        Pill(if(state.deck.isPlaying) "Pause" else "Play",actions::startStop,Modifier.testTag("mini-play"),enabled = state.deck.album != null)
    }
}

@Composable
internal fun ConfirmationDialog(
    title: String, detail: String, confirm: String, dismiss: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ConsoleCard {
            BasicText(title, style = Room.Title)
            BasicText(detail, style = Room.LabelQuiet)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(dismiss, onDismiss)
                Pill(confirm, onConfirm)
            }
        }
    }
}

@Composable
internal fun RetroPage(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Room.Background).safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),verticalAlignment = Alignment.CenterVertically) {
            BasicText(title,style = Room.Title.copy(fontSize = 22.sp),modifier = Modifier.weight(1f))
            Pill("Close",onClose,Modifier.testTag("close-page"))
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 20.dp),content = content)
    }
}

@Composable
private fun LibraryScreen(state: RoomState, actions: RoomActions, onPlay: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var matches by remember { mutableStateOf(state.albums) }
    LaunchedEffect(query,state.albums) {
        kotlinx.coroutines.delay(150)
        matches = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val needle = normalize(query)
            state.albums.filter { normalize("${it.artist} ${it.title}").contains(needle) }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("screen:library"),contentPadding = PaddingValues(16.dp,8.dp,16.dp,80.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("library-controls") {
            ConsoleTitle("Your record collection","Library","Local FLAC, downloads and records you pressed.")
            Field(query,{ query = it },"Find an album in your library")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if(state.hasFolder) "Change music folder" else "Choose music folder",actions::pickFolder)
                if(state.hasFolder) Pill("Rescan",actions::rescan)
            }
            if(state.albums.isEmpty()) BasicText("Your first record is waiting. Choose a FLAC folder or discover something online.",style = Room.LabelQuiet,modifier = Modifier.padding(top = 16.dp))
        }
        items(matches,key = { it.id }) { album ->
            ConsoleCard {
                BasicText(album.title,style = Room.Title)
                BasicText("${album.artist} · ${album.tracks.size} tracks · ${album.quality.label}",style = Room.LabelQuiet)
                album.unavailableReason?.let { BasicText(it,style = Room.LabelQuiet.copy(color = Color(0xFF9A3429))) }
                Pill("Put on the platter",{ actions.load(album); onPlay() },active = true)
            }
        }
    }
}
