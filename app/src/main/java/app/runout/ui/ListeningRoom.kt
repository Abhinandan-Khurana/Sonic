package app.runout.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.runout.drops.DropItem
import app.runout.drops.DropState
import app.runout.drops.DropsRepository
import app.runout.library.ScanReport
import app.runout.library.ScanState
import app.runout.model.Album
import app.runout.model.Source
import app.runout.playback.DeckState
import app.runout.pressing.PressingDraft
import app.runout.social.Friend
import app.runout.social.Profile
import app.runout.sound.OutputStatus
import app.runout.sound.SoundSettings
import app.runout.soulseek.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class RoomState(
    val albums: List<Album>,
    val hasFolder: Boolean,
    val scan: ScanState?,
    val deck: DeckState,
    val crackleOn: Boolean,
    val clock: String,
    val report: ScanReport? = null,
    val sound: SoundSettings = SoundSettings(),
    val output: OutputStatus = OutputStatus(),
    val profile: Profile = Profile("me", "You", Profile.ACCENTS[0]),
    val friends: List<Friend> = emptyList(),
    val drops: DropState = DropState.Idle,
    val claimed: Set<String> = emptySet(),
    val claiming: Map<String, Float> = emptyMap(),
    val message: String? = null,
    val account: app.runout.account.Account? = null,
    val accountStatus: app.runout.account.AccountStatus? = null,
    val people: PeopleState = PeopleState(),
    val soulseekSession: SoulseekSession = SoulseekSession(),
    val soulseekResults: List<SoulseekSearchResult> = emptyList(),
    val soulseekSearching: Boolean = false,
    val soulseekTransfers: List<SoulseekTransfer> = emptyList(),
    val soulseekUploads: List<SoulseekUpload> = emptyList(),
    val soulseekContacts: List<SoulseekContact> = emptyList(),
    val soulseekConversations: List<SoulseekConversation> = emptyList(),
    val soulseekConversation: String? = null,
    val downloadFolder: String? = null,
    val sharedFolder: String? = null,
    val soulseekSharing: Boolean = false,
    val soulseekStayOnline: Boolean = false,
    val soulseekShareConsent: Boolean = false,
    val soulseekBackgroundConsent: Boolean = false,
    val soulseekSavedUsername: String? = null,
    val soulseekError: String? = null,
    val discovery: app.runout.discovery.DiscoveryState = app.runout.discovery.DiscoveryState(),
    val soulseekSearchStatus: SoulseekSearchStatus = SoulseekSearchStatus(),
    val folderPreview: SoulseekFolderPreview? = null,
    val onboardingComplete: Boolean = true,
    val onboardingStep: Int = 0,
    val coverLookupEnabled: Boolean = false,
    val coverLookupNoticePending: Boolean = false,

)

interface RoomActions : DeckActions {
    fun pickFolder()
    fun rescan()
    fun setCoverLookupEnabled(enabled: Boolean) {}
    fun dismissCoverLookupNotice() {}
    fun load(album: Album)
    fun playLocalTrack(album: Album, index: Int) { load(album); needleDrop(0f) }
    fun toggleCrackle()

    fun updateSound(transform: (SoundSettings) -> SoundSettings) {}
    fun applyPreset(name: String) {}
    fun importAutoEq() {}

    fun share(album: Album) {}
    fun removeRecord(album: Album) {}
    fun shareMyCard() {}
    fun rename(name: String) {}
    fun setAccent(accent: Int) {}
    fun removeFriend(id: String) {}

    fun refreshDrops() {}
    fun claim(item: DropItem) {}
    suspend fun dropCover(item: DropItem): String? = null

    fun pickImage(onPicked: (Uri?) -> Unit) {}
    fun press(draft: PressingDraft) {}
    /** Completion means the pressing was durably saved, not merely requested. */
    fun press(draft: PressingDraft, onResult: (Boolean) -> Unit) { press(draft) }

    fun dismissMessage() {}

    fun createAccount(username: String, name: String, accent: Int) {}
    fun restoreAccount(key: String, username: String?) {}
    fun copyAccountKey() {}
    fun setShareShelf(on: Boolean) {}
    fun searchPeople(query: String) {}
    fun openProfile(pubkey: String) {}
    fun closeProfile() {}
    fun addFriend(profile: app.runout.account.RemoteProfile) {}
    fun refreshFriends() {}
    suspend fun coverFor(item: app.runout.social.ShelfItem): String? = null
    fun signInSoulseek(username: String, password: String, remember: Boolean) {}
    fun signInSavedSoulseek() {}
    fun signOutSoulseek() {}
    fun searchSoulseek(query: String) {}
    fun stopSoulseekSearch() {}
    fun downloadSoulseek(result: SoulseekSearchResult) {}
    fun downloadSoulseekFolder(result: SoulseekSearchResult) {}
    fun pauseSoulseekTransfer(id: String) {}
    fun resumeSoulseekTransfer(id: String) {}
    fun cancelSoulseekTransfer(id: String) {}
    fun retrySoulseekTransfer(id: String) {}
    fun pickDownloadFolder() {}
    fun pickSharedFolder() {}
    fun setSoulseekSharing(enabled: Boolean) {}
    fun setSoulseekStayOnline(enabled: Boolean) {}
    fun addSoulseekContact(username: String) {}
    fun openSoulseekConversation(username: String) {}
    fun closeSoulseekConversation() {}
    fun sendSoulseekMessage(username: String, text: String) {}
    /** Only an actual durable insert may acknowledge the draft. Legacy actions do not. */
    fun sendSoulseekMessage(username: String, text: String, onPersisted: () -> Unit) { sendSoulseekMessage(username, text) }
    fun markSoulseekRead(username: String) {}
    fun clearSoulseekError() {}
    fun reconnectSoulseek() {}
    fun previewSoulseekFolder(result: SoulseekSearchResult) { downloadSoulseekFolder(result) }
    fun confirmSoulseekFolder() {}
    fun closeSoulseekFolder() {}
    fun setSearchQuery(value: String) {}
    fun setSearchOptions(value: app.runout.discovery.DiscoveryOptions) {}
    fun broadenSearch() {}
    fun finishOnboarding() {}
    fun setOnboardingStep(value: Int) {}
    fun disconnectSoulseek() {}

}

/**
 * The listening room. Landscape: glass turntable on the left, the crate on the right.
 * Portrait: the turntable on top, the crate below. Panels slide in as sheets of glass.
 */
@Composable
fun RoomScreen(
    state: RoomState,
    actions: RoomActions,
    modifier: Modifier = Modifier,
    initialPulledId: String? = null,
    initialCrateIndex: Int = 0,
    initialPanel: Panel? = null,
    showChrome: Boolean = true,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val roomBackdrop = rememberLayerBackdrop()

    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var platterCenter by remember { mutableStateOf<Offset?>(null) }
    var platterRadius by remember { mutableStateOf(0f) }
    var slot by remember { mutableStateOf<SleeveSlot?>(null) }
    var pulledId by remember { mutableStateOf(initialPulledId) }
    var removeId by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(initialPanel) }
    var pressingChanged by remember { mutableStateOf(false) }
    var discardPressing by remember { mutableStateOf(false) }
    fun closePanel() {
        if (panel == Panel.PRESSING && pressingChanged) discardPressing = true
        else { panel = null; pressingChanged = false }
    }
    val disc = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val pulled = state.albums.firstOrNull { it.id == pulledId }

    BackHandler(enabled = panel != null || pulledId != null) {
        if (panel != null) closePanel() else pulledId = null
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Room.Background)
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        val portrait = maxHeight > maxWidth
        val screenW = maxWidth
        val screenH = maxHeight
        // In landscape the disc slides left out of its sleeve, towards the turntable; in portrait it rises.
        val pullOut: (SleeveSlot) -> Offset = { s ->
            if (portrait) Offset(0f, -s.rect.height * 0.58f) else Offset(-s.rect.width * 0.56f, 0f)
        }

        LaunchedEffect(pulledId, slot != null, portrait) {
            val s = slot ?: return@LaunchedEffect
            if (pulledId == null) return@LaunchedEffect
            val start = s.rect.center - rootOrigin
            disc.snapTo(start)
            disc.animateTo(start + pullOut(s), tween(620, easing = FastOutSlowInEasing))
        }

        Box(Modifier.fillMaxSize().layerBackdrop(roomBackdrop)) {
            RoomBackground(Modifier.fillMaxSize().layerBackdrop(backdrop), portrait)

            val nearPlatter = dragging && platterCenter?.let { (disc.value + rootOrigin - it).getDistance() < platterRadius * 0.95f } == true
            val turntable = @Composable { m: Modifier, alignment: Alignment ->
                Turntable(
                    deck = state.deck,
                    backdrop = backdrop,
                    dropTargetActive = nearPlatter,
                    onPlatterPlaced = { c, r -> platterCenter = c; platterRadius = r },
                    actions = actions,
                    modifier = m,
                    contentAlignment = alignment,
                    pitchEnabled = !state.sound.pure,
                )
            }
            val crate = @Composable { m: Modifier ->
                Crate(
                    albums = state.albums,
                    hasFolder = state.hasFolder,
                    pulledId = pulledId,
                    loadedId = state.deck.album?.id,
                    onPickFolder = actions::pickFolder,
                    onPull = { pulledId = it.id },
                    onPutBack = { pulledId = null },
                    onFrontSlot = { slot = it },
                    modifier = m,
                    initialIndex = initialCrateIndex,
                    emptyTitle = emptyTitle(state),
                    emptyDetail = emptyDetail(state),
                )
            }

            if (portrait) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 16.dp, end = 16.dp, top = if (showChrome) 70.dp else 8.dp, bottom = if (showChrome) 86.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    turntable(Modifier.fillMaxWidth().weight(0.42f), Alignment.Center)
                    Spacer(Modifier.height(10.dp))
                    crate(Modifier.fillMaxWidth().weight(0.58f))
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 28.dp, end = 20.dp, top = if (showChrome) 46.dp else 8.dp, bottom = if (showChrome) 42.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    turntable(Modifier.weight(0.53f).fillMaxHeight(), Alignment.CenterStart)
                    Spacer(Modifier.width(24.dp))
                    crate(Modifier.weight(0.47f).fillMaxHeight())
                }
            }

            if (showChrome) CornerLabels(state, pulled != null, portrait, backdrop, actions, onOpen = { panel = it; pulledId = null })
        }

        // The pulled record, drawn above everything so it can travel across the room.
        val s = slot
        if (pulled != null && s != null) {
            val measurer = rememberTextMeasurer()
            val labelStyle = Room.Label.copy(color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
            val sideText = remember(pulled.id) { measurer.measure("SIDE A", labelStyle) }
            val qualityText = remember(pulled.id) { measurer.measure(pulled.quality.shortLabel.uppercase(), labelStyle) }
            val cover by rememberCover(pulled)
            val look = remember(pulled.id) { discLookFor(pulled) }
            val fallback = remember(pulled.id) { fallbackColor(pulled) }

            val baseD = s.rect.width * 0.94f
            val pc = platterCenter
            val dist = pc?.let { (disc.value + rootOrigin - it).getDistance() } ?: Float.MAX_VALUE
            val proximity = if (pc == null) 0f else (1f - dist / (platterRadius * 3.2f)).coerceIn(0f, 1f)
            val d = baseD + (platterRadius * 2f - baseD) * proximity
            val dDp = with(density) { d.toDp() }
            val restPos = s.rect.center - rootOrigin + pullOut(s)

            Canvas(
                Modifier
                    .zIndex(5f)
                    .offset { IntOffset((disc.value.x - d / 2f).roundToInt(), (disc.value.y - d / 2f).roundToInt()) }
                    .size(dDp)
                    .testTag("pulled-disc")
                    .graphicsLayer { shadowElevation = if (dragging) 24f else 8f; shape = androidx.compose.foundation.shape.CircleShape; clip = false }
                    .pointerInput(pulled.id) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDrag = { change, amount ->
                                change.consume()
                                scope.launch { disc.snapTo(disc.value + amount) }
                            },
                            onDragEnd = {
                                dragging = false
                                val target = platterCenter
                                val onPlatter = target != null && (disc.value + rootOrigin - target).getDistance() < platterRadius * 0.95f
                                scope.launch {
                                    if (onPlatter && target != null) {
                                        disc.animateTo(target - rootOrigin, tween(240, easing = FastOutSlowInEasing))
                                        actions.load(pulled)
                                        pulledId = null
                                    } else {
                                        disc.animateTo(restPos, spring(dampingRatio = 0.8f, stiffness = 300f))
                                    }
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { disc.animateTo(restPos, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                            },
                        )
                    },
            ) {
                drawRecord(
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension / 2f,
                    rotation = 0f,
                    look = look,
                    label = cover,
                    labelFallback = fallback,
                    trackBoundaries = emptyList(),
                    sideText = sideText,
                    qualityText = qualityText,
                )
            }

            // The sleeve sits in front of the disc while it slides out, and behind it once you pick it up.
            SleeveArt(
                pulled,
                Modifier
                    .zIndex(if (dragging || proximity > 0.05f) 4f else 6f)
                    .offset { IntOffset((s.rect.left - rootOrigin.x).roundToInt(), (s.rect.top - rootOrigin.y).roundToInt()) }
                    .size(s.size)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pulledId = null },
            )

            // Send it to a friend, or take a received record off the shelf. Sits beside the sleeve,
            // pulled back inside the screen when the sleeve is near the edge.
            Layout(
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                        GlassPill(
                            if (pulled.audioShareable) "Send with audio" else "Send as a card",
                            backdrop, active = true, onClick = { actions.share(pulled) }, modifier = Modifier.testTag("send"),
                        )
                        if (pulled.source != Source.FOLDER) {
                            GlassPill("Remove", backdrop, onClick = { removeId = pulled.id })
                        }
                    }
                },
                modifier = Modifier.zIndex(7f).fillMaxSize(),
            ) { measurables, constraints ->
                val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val margin = 12.dp.roundToPx()
                    val want = (s.rect.right - rootOrigin.x).roundToInt() + 10.dp.roundToPx()
                    val x = want.coerceAtMost(constraints.maxWidth - p.width - margin).coerceAtLeast(0)
                    p.place(x, (s.rect.top - rootOrigin.y).roundToInt())
                }
            }
        }

        state.albums.firstOrNull { it.id == removeId }?.let { record ->
            ConfirmationDialog(
                "Remove record?",
                if (record.source == Source.RECEIVED) "“${record.title}” and its received audio will be deleted from this device."
                else "“${record.title}” will be removed from your shelf. Original music files are kept.",
                "Remove record", "Keep it",
                onConfirm = { actions.removeRecord(record); removeId = null; pulledId = null },
                onDismiss = { removeId = null },
            )
        }

        if (discardPressing) ConfirmationDialog(
            "Discard pressing draft?", "Your selected tracks, sleeve and record details will be lost.",
            "Discard draft", "Keep editing",
            onConfirm = { discardPressing = false; pressingChanged = false; panel = null },
            onDismiss = { discardPressing = false },
        )

        // Panels
        val open = panel
        Box(Modifier.fillMaxSize().zIndex(10f)) {
            AnimatedVisibility(open != null, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.06f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { closePanel() },
                )
            }
            AnimatedVisibility(
                visible = open != null,
                enter = if (portrait) slideInVertically { it } + fadeIn() else slideInHorizontally { it } + fadeIn(),
                exit = if (portrait) slideOutVertically { it } + fadeOut() else slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(if (portrait) Alignment.BottomCenter else Alignment.CenterEnd),
            ) {
                var shown by remember { mutableStateOf(open ?: Panel.SOUND) }
                if (open != null) shown = open
                PanelSheet(
                    title = shown.title,
                    backdrop = roomBackdrop,
                    portrait = portrait,
                    width = (screenW * 0.46f).coerceIn(340.dp, 520.dp),
                    height = screenH * 0.74f,
                    onClose = { closePanel() },
                ) {
                    when (shown) {
                        Panel.SOUND -> SoundPanel(state.sound, state.output, actions)
                        Panel.SEARCH -> SoulseekSearchPanel(state, actions, onAccount = { panel = Panel.ACCOUNT }, onTransfers = { panel = Panel.TRANSFERS }, onMessage = { peer -> actions.openSoulseekConversation(peer); panel = Panel.FRIENDS })
                        Panel.TRANSFERS -> SoulseekTransfersPanel(state, actions)
                        Panel.ACCOUNT -> SoulseekAccountPanel(state, actions, onOffline = { panel = null })
                        Panel.FRIENDS -> SoulseekMessagesPanel(state, actions, onAccount = { panel = Panel.ACCOUNT })
                        Panel.DROPS -> DropsPanel(state.drops, state.claimed, state.claiming, actions, state.soulseekSession.signedIn, onAccount = { panel = Panel.ACCOUNT })
                        Panel.PRESSING -> PressingPanel(state.albums, state.profile, actions) { pressingChanged = it }
                    }
                }
            }
        }

        // Messages: a small glass note that fades after a few seconds.
        val msg = state.message
        if (msg != null) {
            LaunchedEffect(msg) {
                delay(4_500)
                actions.dismissMessage()
            }
            Box(
                Modifier
                    .zIndex(20f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (portrait) 120.dp else 56.dp, start = 24.dp, end = 24.dp)
                    .widthIn(max = 520.dp)
                    .drawBackdrop(
                        backdrop = roomBackdrop,
                        shape = { RoundedCornerShape(18.dp) },
                        effects = { vibrancy(); blur(10.dp.toPx()); lens(10.dp.toPx(), 18.dp.toPx()) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.78f)) },
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.dismissMessage() }
                    .padding(horizontal = 18.dp, vertical = 11.dp)
                    .testTag("message"),
            ) {
                BasicText(msg, style = Room.Label.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

private fun emptyTitle(state: RoomState): String = when {
    !state.hasFolder -> "Choose your\nmusic folder"
    state.scan is ScanState.Scanning -> "Reading your\nfolder…"
    else -> "No albums found"
}

private fun emptyDetail(state: RoomState): String = when {
    !state.hasFolder -> "Each album folder becomes\na record on your shelf"
    state.scan is ScanState.Scanning -> (state.scan as ScanState.Scanning).progress.let { "${it.folders} folders · ${it.audioFiles} music files so far" }
    state.report != null -> state.report.summary + "\n\nTap to pick another folder"
    else -> "Tap to pick another folder"
}

@Composable
private fun PanelSheet(
    title: String,
    backdrop: Backdrop,
    portrait: Boolean,
    width: Dp,
    height: Dp,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheet = if (portrait) Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp).padding(bottom = 8.dp)
    else Modifier.width(width).fillMaxHeight().padding(10.dp)
    Column(
        sheet
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = {
                    vibrancy()
                    blur(24.dp.toPx())
                    lens(16.dp.toPx(), 30.dp.toPx(), chromaticAberration = true)
                },
                // Mostly white so text stays readable over bright sleeves; the edges still bend the room.
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.86f)) },
            )
            // Taps inside the sheet stay inside it (without turning the whole sheet into one button for TalkBack).
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 22.dp, end = 22.dp, top = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Room.Title.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f).testTag("panel-title"))
            TextAction("Close", onClose)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), content = content)
    }
}

@Composable
private fun RoomBackground(modifier: Modifier, portrait: Boolean) {
    Canvas(modifier) {
        drawRect(Room.Background)
        // Soft pastel light behind the turntable, so the glass has something to bend.
        val a = if (portrait) Offset(size.width * 0.35f, size.height * 0.22f) else Offset(size.width * 0.18f, size.height * 0.30f)
        val b = if (portrait) Offset(size.width * 0.75f, size.height * 0.45f) else Offset(size.width * 0.40f, size.height * 0.85f)
        val ra = minOf(size.width, size.height) * 0.75f
        val rb = minOf(size.width, size.height) * 0.6f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE6E2FF), Color.Transparent), center = a, radius = ra), radius = ra, center = a)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDDEEFF), Color.Transparent), center = b, radius = rb), radius = rb, center = b)
        // A fine dot grid, Y2K-catalogue style, under the turntable.
        val step = 22.dp.toPx()
        val dot = Color(0xFF000000).copy(alpha = 0.07f)
        val maxX = if (portrait) size.width else size.width * 0.62f
        val maxY = if (portrait) size.height * 0.52f else size.height
        var y = step / 2
        while (y < maxY) {
            var x = step / 2
            while (x < maxX) {
                drawCircle(dot, 1.1f, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
private fun CornerLabels(
    state: RoomState,
    pulled: Boolean,
    portrait: Boolean,
    backdrop: Backdrop,
    actions: RoomActions,
    onOpen: (Panel) -> Unit,
) {
    val deck = state.deck
    val nowSpinning = when {
        deck.album == null -> "Nothing on the platter"
        deck.sideFinished -> "Side ${deck.side?.letter} finished — tap the record to flip it"
        deck.needleDown -> "Now spinning — Side ${deck.side?.letter} · ${deck.currentTrackTitle ?: deck.album.title}"
        else -> "${deck.album.title} · Side ${deck.side?.letter} cued — lower the tonearm"
    }
    val scanText = when (val s = state.scan) {
        is ScanState.Scanning -> "Scanning · ${s.progress.albums} albums found"
        is ScanState.FindingCovers -> "Finding covers · ${s.done + 1} of ${s.total}"
        null -> null
    }
    val status = listOfNotNull(state.clock, "${state.albums.size} records", scanText).joinToString("   ")
    val newDrop = state.drops is DropState.Ready && state.claimed.size < DropsRepository.CLAIMS_PER_WEEK

    val nav = @Composable {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Panel.entries.forEach { p ->
                GlassPill(
                    text = p.nav + when {
                        p == Panel.DROPS && newDrop -> " •"
                        p == Panel.FRIENDS && state.soulseekConversations.any { it.unread > 0 } -> " •"
                        else -> ""
                    },
                    backdrop = backdrop,
                    onClick = { onOpen(p) },
                    modifier = Modifier.testTag("nav:${p.name}"),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = if (portrait) 18.dp else 28.dp, vertical = 16.dp)) {
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("RUNOUT", style = Room.Brand)
                BasicText("your room", style = Room.LabelQuiet)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextAction(if (state.hasFolder) "Change folder" else "Choose folder", actions::pickFolder)
                if (state.hasFolder) TextAction("Rescan", actions::rescan)
                TextAction(if (state.crackleOn) "Crackle on" else "Crackle off", actions::toggleCrackle)
            }
        }

        if (portrait) {
            BasicText(status, style = Room.Label, modifier = Modifier.align(Alignment.TopEnd), maxLines = 2)
            Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(nowSpinning, style = Room.Label, maxLines = 1)
                nav()
            }
        } else {
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                nav()
                BasicText(status, style = Room.Label)
            }
            BasicText(nowSpinning, style = Room.Label, modifier = Modifier.align(Alignment.BottomStart), maxLines = 1)
            val hint = when {
                state.albums.isEmpty() -> "Choose a folder to begin"
                pulled -> "Drag the disc onto the platter"
                else -> "Flick to dig  ⟷"
            }
            BasicText(hint, style = Room.Label, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
internal fun TextAction(text: String, onClick: () -> Unit) {
    BasicText(
        text,
        style = Room.LabelQuiet.copy(fontSize = 10.sp),
        modifier = Modifier
            .testTag("action:$text")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}
