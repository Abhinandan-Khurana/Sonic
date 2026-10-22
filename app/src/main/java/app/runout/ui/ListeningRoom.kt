package app.runout.ui

import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import app.runout.ui.deck.RecordMaps
import app.runout.ui.deck.DiscRenderer
import app.runout.ui.deck.DeckRenderer
import app.runout.ui.deck.DeckModel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import app.runout.model.SideSplitter
import app.runout.model.Source
import app.runout.playback.DeckState
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
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
    /** The music folder just changed while signed in: ask whether the shared folder should follow. */
    val offerShareSwap: Boolean = false,
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
    val turtleMode: Boolean = false,

)

interface RoomActions : DeckActions {
    fun pickFolder()
    fun rescan()
    /** Settings › Library › Repair local albums: rebuild the shelf from the folder alone. */
    fun repairLibrary() { rescan() }
    fun setCoverLookupEnabled(enabled: Boolean) {}
    fun dismissCoverLookupNotice() {}
    fun load(album: Album)
    /** The disc has just been set down on the spindle: the soft thud of it seating. */
    fun discLanded() {}
    /** Puts the record on the platter cued at [trackId], needle up. Only the deck starts the music. */
    fun cueTrack(album: Album, trackId: String?) { load(album) }
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
    /** A drop's FLAC tracks, before it is claimed; empty when it has none, null when they couldn't be listed. */
    suspend fun dropTracks(item: DropItem): List<app.runout.drops.DropTrack>? = null
    /** Plays about thirty seconds of [url] to help choose a drop; null stops it. */
    fun listen(url: String?) {}

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
    fun pauseAllDownloads() {}
    fun resumeAllDownloads() {}
    fun setTurtleMode(enabled: Boolean) {}
    fun resumeSoulseekTransfer(id: String) {}
    fun cancelSoulseekTransfer(id: String) {}
    fun retrySoulseekTransfer(id: String) {}
    /** Off the downloads list (stopped first if running); music already saved stays. */
    fun removeSoulseekTransfer(id: String) {}
    fun removeFailedDownloads() {}
    /** Just these files of the open folder, joining [albumId] on the shelf when it's an album Runout downloaded. */
    fun downloadSoulseekTracks(files: List<SoulseekSearchResult>, albumId: String?) {}
    /** Looks again for a record's failed tracks, from anyone sharing the identical files. */
    fun fetchMissingTracks(albumId: String) {}
    fun pickDownloadFolder() {}
    fun pickSharedFolder() {}
    /** The answer to [RoomState.offerShareSwap]: true picks a new shared folder (its own permission). */
    fun answerShareSwap(change: Boolean) {}
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
 * The listening room, and the whole app. Landscape: glass turntable on the left, the crate on the
 * right. Portrait: the turntable on top, the crate below. Everything else (digging for records,
 * Friday Drops, friends, the pressing plant, settings, downloads, setup) slides in as a sheet of
 * glass. The music itself is only ever started and stopped on the turntable.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun RoomScreen(
    state: RoomState,
    actions: RoomActions,
    modifier: Modifier = Modifier,
    initialPulledId: String? = null,
    initialCrateIndex: Int = 0,
    initialPanel: Panel? = null,
    /** Which Settings page is open; null is the list. */
    initialSettings: SettingsSection? = null,
) {
    val density = LocalDensity.current
    val appContext = androidx.compose.ui.platform.LocalContext.current
    // The face is loaded before the first frame (MainActivity does it too; tests start here).
    remember(appContext) { TeType.init(appContext); TeType.family }
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
    var settings by remember { mutableStateOf(initialSettings) }
    // Each record's tracks that failed to download and weren't fixed: a red tab on its sleeve.
    val missingBy = remember(state.soulseekTransfers) {
        state.soulseekTransfers.filter { it.state == SoulseekTransferState.FAILED }.mapNotNull { it.albumId }.groupingBy { it }.eachCount()
    }
    // First launch opens onboarding over the room; Soulseek setup can be opened again later.
    var setupOpen by rememberSaveable { mutableStateOf(!state.onboardingComplete) }
    var setupStart by rememberSaveable { mutableIntStateOf(state.onboardingStep) }
    val openSetup = { setupStart = 1; setupOpen = true; panel = null }
    val openAccount = {
        if (state.soulseekSession.hasAccount) { settings = SettingsSection.ACCOUNT; panel = Panel.SETTINGS; pulledId = null } else openSetup()
    }
    // Pages reached from another page (Downloads from Settings, Dig from Downloads): back returns there.
    val pageHistory = remember { mutableStateListOf<Panel>() }
    val pageSteps = remember { PageSteps() }
    fun closePanel() {
        if (panel == Panel.DIG && state.folderPreview != null) actions.closeSoulseekFolder()
        pageHistory.clear()
        panel = null
    }
    fun goTo(next: Panel) {
        panel?.takeIf { it != next }?.let { pageHistory += it }
        panel = next
    }
    fun backPage() {
        if (panel == Panel.DIG && state.folderPreview != null) actions.closeSoulseekFolder()
        panel = pageHistory.removeAt(pageHistory.lastIndex)
    }
    // The head's "← back": out of the innermost step, else to the page before; null leaves "close ×".
    // Read by the head itself, so a step coming or going doesn't recompose the room.
    val headBack: () -> (() -> Unit)? = { pageSteps.steps.lastOrNull()?.second ?: if (pageHistory.isNotEmpty()) ({ backPage() }) else null }
    // A folder preview always opens inside the Dig sheet, wherever it was asked for.
    LaunchedEffect(state.folderPreview != null) {
        if (state.folderPreview != null && panel == null) panel = Panel.DIG
    }
    val disc = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    // The deck's camera and position, so a pulled record can be the 3D record in your hand.
    val deckLink = remember { DeckLink() }
    // The room's entrance after onboarding: 1 is fully in.
    val entry = remember { Animatable(1f) }
    // How high the disc is held (its scale), and how far it has turned since it left the sleeve.
    val lift = remember { Animatable(1f) }
    val spin = remember { Animatable(0f) }
    // Eject: the record flies from the platter back into its sleeve, dug to the front of the crate.
    var ejecting by remember { mutableStateOf<String?>(null) }
    var ejectSide by remember { mutableStateOf("A") }
    var ejectGaps by remember { mutableStateOf(emptyList<Float>()) }
    var crateTarget by remember { mutableStateOf<String?>(null) }
    // Whether the 3D record in hand is on screen yet.
    var heldReady by remember { mutableStateOf(false) }
    // A different record was dropped on a busy platter.
    var occupied by remember { mutableStateOf(false) }
    // The record on the platter was dragged as if to take it off by hand.
    var useEject by remember { mutableStateOf(false) }
    // A record held over a busy platter: the deck shakes and buzzes for a second (SWAP on the LED),
    // then the record on it is taken off and the one in hand lands.
    var holdingOverBusy by remember { mutableStateOf(false) }
    var swapping by remember { mutableStateOf(false) }
    var shakeX by remember { mutableFloatStateOf(0f) }
    val swapLift = remember { Animatable(0f) }
    val current by rememberUpdatedState(state)
    var menuFor by remember { mutableStateOf<String?>(null) }
    // The record in front of the crate, for the caption under it.
    var frontId by remember { mutableStateOf<String?>(null) }
    // First time in the room: the three "?" helps show until a record first lands on the platter.
    val prefs = remember { appContext.getSharedPreferences("room", android.content.Context.MODE_PRIVATE) }
    var roomHelps by remember { mutableStateOf(!prefs.getBoolean("helps_done", false) && state.deck.album == null) }
    LaunchedEffect(state.deck.album != null) {
        if (state.deck.album != null && roomHelps) { roomHelps = false; prefs.edit().putBoolean("helps_done", true).apply() }
    }
    var roomHelp by remember { mutableStateOf<TeHelp?>(null) }
    var detailsFor by remember { mutableStateOf<String?>(null) }
    val pickUp = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate)
    val landed = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
    val refused = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
    val buzz = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
    // The deck's Eject key, with the flight home when the 3D record can make it; otherwise at once.
    val deckActions = remember(actions) {
        object : DeckActions by actions {
            override fun eject() {
                val st = current
                val record = st.deck.album
                val fly = record != null && pulledId == null && ejecting == null && platterCenter != null &&
                    DeckRenderer.available && deckLink.camera != null && st.albums.any { it.id == record.id }
                if (!fly || record == null) { actions.eject(); return }
                ejectSide = st.deck.side?.letter?.toString() ?: "A"
                ejectGaps = st.deck.side?.trackBoundaries() ?: emptyList()
                ejecting = record.id
                crateTarget = record.id
                heldReady = false
                pulledId = record.id
            }
        }
    }
    val pulled = state.albums.firstOrNull { it.id == pulledId }

    BackHandler(enabled = panel != null || pulledId != null) {
        if (panel != null) { if (pageHistory.isNotEmpty()) backPage() else closePanel() } else pulledId = null
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Room.Background)
            .semantics { testTagsAsResourceId = true }
            .testTag("screen:room")
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        val portrait = maxHeight > maxWidth
        val screenW = maxWidth
        val screenH = maxHeight
        // In landscape the disc slides left out of its sleeve, towards the turntable; in portrait it rises.
        val pullOut: (SleeveSlot) -> Offset = { s ->
            if (portrait) Offset(0f, -s.rect.height * 0.65f) else Offset(-s.rect.width * 0.56f, 0f)
        }

        // Pulling a record: it slides up out of the sleeve, overshoots a touch and settles, turning a
        // little as it comes, the way a real disc does when you draw it out by the edge.
        LaunchedEffect(pulledId, slot != null, portrait) {
            val s = slot ?: return@LaunchedEffect
            if (pulledId == null) return@LaunchedEffect
            val home = platterCenter
            if (ejecting != null && ejecting == pulledId && home != null) {
                // Put back (TE storyboard, ~1.1 s): EJECT on the LED and the arm back to its rest;
                // the 3D record takes the deck's place exactly and lifts off (1.0 → 1.06); it flies on
                // a soft arc to the crate, shrinking to sleeve size, slides down into its sleeve, and
                // seats with a tick.
                if (current.deck.needleDown) { actions.liftNeedle(); delay(300) }
                disc.snapTo(home - rootOrigin)
                lift.snapTo(1f)
                spin.snapTo(deckLink.platterDegrees)
                withTimeoutOrNull(900) { snapshotFlow { heldReady }.first { it } }
                actions.eject()
                lift.animateTo(1.06f, tween(200, easing = FastOutSlowInEasing))
                val above = s.rect.center - rootOrigin + pullOut(s)
                launch { lift.animateTo(1f, tween(300)) }
                launch { spin.animateTo(spin.value - 90f, tween(500, easing = FastOutSlowInEasing)) }
                // The arc: a little higher than the straight line, at its middle.
                val from = disc.value
                val arc = Animatable(0f)
                arc.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) {
                    val t = value
                    val lineY = from.y + (above.y - from.y) * t
                    val p = Offset(from.x + (above.x - from.x) * t, lineY - kotlin.math.sin(t * Math.PI.toFloat()) * s.rect.height * 0.25f)
                    scope.launch { disc.snapTo(p) }
                }
                disc.animateTo(s.rect.center - rootOrigin, tween(200, easing = FastOutSlowInEasing))
                landed()
                pulledId = null
                ejecting = null
                crateTarget = null
                return@LaunchedEffect
            }
            val start = s.rect.center - rootOrigin
            disc.snapTo(start)
            lift.snapTo(1f)
            spin.snapTo(0f)
            launch { spin.animateTo(42f, tween(760, easing = FastOutSlowInEasing)) }
            disc.animateTo(start + pullOut(s), spring(dampingRatio = 0.58f, stiffness = 260f))
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val e = entry.value
                    val sc = 0.92f + 0.08f * e
                    scaleX = sc; scaleY = sc
                    alpha = e.coerceIn(0f, 1f)
                }
                .layerBackdrop(roomBackdrop)
                // Under a page the room is out of sight: keep TalkBack out of it too.
                .then(if (panel?.fullPage == true) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            RoomBackground(Modifier.fillMaxSize().layerBackdrop(backdrop), portrait)

            val nearPlatter = dragging && platterCenter?.let { (disc.value + rootOrigin - it).getDistance() < platterRadius * 0.95f } == true
            val turntable = @Composable { m: Modifier, alignment: Alignment ->
                Turntable(
                    deck = state.deck,
                    dropTargetActive = nearPlatter,
                    dropBlocked = state.deck.album != null && state.deck.album.id != pulledId,
                    onPlatterPlaced = { c, r -> platterCenter = c; platterRadius = r },
                    actions = deckActions,
                    modifier = m.graphicsLayer { translationX = shakeX },
                    contentAlignment = alignment,
                    link = deckLink,
                    onScreenLongPress = { panel = Panel.SIGNAL; pulledId = null },
                    ledOverride = when {
                        ejecting != null || swapLift.value > 0f -> "EJECT"
                        holdingOverBusy -> "SWAP"
                        else -> null
                    },
                    onRecordDragged = { useEject = true },
                    liftOff = { swapLift.value },
                )
            }
            val crate = @Composable { m: Modifier, geometry: CrateGeometry ->
                Crate(
                    albums = state.albums,
                    pulledId = pulledId,
                    onPull = { pulledId = it.id },
                    onPutBack = { pulledId = null },
                    onFrontSlot = { slot = it },
                    onMenu = { menuFor = it.id },
                    modifier = m,
                    geometry = geometry,
                    label = { if (it.id == state.deck.album?.id) "on the platter" else null },
                    onFront = { frontId = it?.id },
                    initialIndex = initialCrateIndex,
                    scrollTo = crateTarget,
                    missing = { missingBy[it.id] ?: 0 },
                    putBack = {
                        val s = slot
                        if (s != null && pulledId != null && ejecting == null) {
                            coroutineScope {
                                launch { lift.animateTo(1f, tween(160)) }
                                disc.animateTo(s.rect.center - rootOrigin, tween(260, easing = FastOutSlowInEasing))
                            }
                        }
                        pulledId = null
                    },
                )
            }
            val front = state.albums.firstOrNull { it.id == frontId } ?: state.albums.firstOrNull()
            val line = nowLine(state, pulled != null && ejecting == null, nearPlatter)
            val help = @Composable { h: TeHelp, m: Modifier -> if (roomHelps) HelpKey(h.title, m) { roomHelp = h } }

            if (portrait) {
                // TE · Home: top bar, the deck's band, the crate, its caption, the track wheel and the
                // "now spinning" line, with the nav bar (drawn by the room) underneath.
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = NAV_HEIGHT),
                ) {
                    TopBar(state, onDownloads = { panel = Panel.DOWNLOADS; pulledId = null }, onAccount = openAccount)
                    DeckBand(state, empty = state.albums.isEmpty(), help = { help(HELP_LED, it) }) { turntable(it, Alignment.Center) }
                    if (state.albums.isEmpty()) {
                        EmptyRoom(state, actions, Modifier.fillMaxWidth().weight(1f))
                    } else {
                        Box(Modifier.fillMaxWidth().weight(301f)) {
                            crate(Modifier.fillMaxSize(), CrateGeometry.Portrait)
                            if (roomHelps) Row(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText("how the crate works", style = Room.LabelQuiet)
                                HelpKey(HELP_CRATE.title, Modifier) { roomHelp = HELP_CRATE }
                            }
                        }
                        front?.let { AlbumCaption(it, portrait = true) }
                        Box(Modifier.fillMaxWidth().weight(105f).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                            TrackWheel(state, actions, rows = 4, help = { help(HELP_WHEEL, it) })
                        }
                        NowSpinning(line, hint = state.albums.size > 1)
                    }
                }
            } else {
                // Landscape: the deck's band on the left, ruled off; the room's column on the right
                // with the nav at its foot.
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
                    Column(
                        Modifier.weight(1f - LANDSCAPE_ROOM).fillMaxHeight().drawBehind {
                            drawLine(Room.Hairline, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), 1.dp.toPx())
                        },
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BasicText("rg-1 · white / teal", style = Room.LabelQuiet, modifier = Modifier.align(Alignment.End).padding(start = 22.dp, end = 22.dp, top = 22.dp))
                        turntable(Modifier.fillMaxWidth().weight(1f), Alignment.Center)
                        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 16.dp)) {
                            BasicText(bandCaption(state, landscape = true), style = Room.LabelQuiet, modifier = Modifier.weight(1f), maxLines = 1)
                            if (state.deck.album != null) BasicText("33⅓ rpm", style = Room.LabelQuiet)
                        }
                    }
                    Column(Modifier.weight(LANDSCAPE_ROOM).fillMaxHeight().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = NAV_HEIGHT)) {
                        TopBar(state, onDownloads = { panel = Panel.DOWNLOADS; pulledId = null }, onAccount = openAccount)
                        if (state.albums.isEmpty()) {
                            EmptyRoom(state, actions, Modifier.fillMaxWidth().weight(1f))
                        } else {
                            crate(Modifier.fillMaxWidth().weight(168f), CrateGeometry.Landscape)
                            front?.let { AlbumCaption(it, portrait = false) }
                            Box(Modifier.fillMaxWidth().weight(80f).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                                TrackWheel(state, actions, rows = 3)
                            }
                            NowSpinning(line, hint = state.albums.size > 1, landscape = true)
                        }
                    }
                }
            }
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
            // Side A's gaps between tracks, as the record will show them on the platter.
            val gaps = remember(pulled.id) { SideSplitter.split(pulled.tracks).firstOrNull()?.trackBoundaries() ?: emptyList() }

            val baseD = s.rect.width * 0.94f
            // The 3D record in hand, once its first frames are up; until then (and with no GPU) the
            // drawn disc below stands in, and it always takes the touches.
            val use3d = DeckRenderer.available && deckLink.camera != null
            var discRenderer by remember(pulled.id) { mutableStateOf<DiscRenderer?>(null) }
            var disc3dShown by remember(pulled.id) { mutableStateOf(false) }
            val use3dNow by rememberUpdatedState(use3d)
            val pc = platterCenter
            val dist = pc?.let { (disc.value + rootOrigin - it).getDistance() } ?: Float.MAX_VALUE
            val proximity = if (pc == null) 0f else (1f - dist / (platterRadius * 3.2f)).coerceIn(0f, 1f)
            val d = baseD + (platterRadius * 2f - baseD) * proximity
            val dDp = with(density) { d.toDp() }
            val restPos = s.rect.center - rootOrigin + pullOut(s)

            // Onto the platter: lined up with where it stopped, a thud and a short squash as it seats.
            suspend fun land(target: Offset) = coroutineScope {
                launch { lift.animateTo(1f, tween(180)) }
                // Turn to where the platter has stopped, so the 3D record lands lined up.
                val p = deckLink.platterDegrees
                launch { spin.animateTo(p + 360f * kotlin.math.round((spin.value - p) / 360f), tween(200)) }
                disc.animateTo(target - rootOrigin, tween(200, easing = FastOutSlowInEasing))
                landed()
                actions.discLanded()
                lift.animateTo(0.95f, tween(70))
                lift.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
                actions.load(pulled)
                // Hold the 3D record until the deck's own is drawn under it.
                if (use3dNow) delay(HANDOVER_MS)
                pulledId = null
            }

            // The swap: the record in hand steps back off the platter, the one on it rises out of
            // the deck (EJECT on the LED), and the one in hand lands in its place.
            suspend fun swapRecords() = coroutineScope {
                val target = platterCenter ?: return@coroutineScope
                swapping = true
                dragging = false
                occupied = false
                launch { disc.animateTo(target - rootOrigin + Offset(platterRadius * 0.45f, platterRadius * 0.6f), tween(320, easing = FastOutSlowInEasing)) }
                if (current.deck.needleDown) { actions.liftNeedle(); delay(300) }
                swapLift.snapTo(0f)
                swapLift.animateTo(1f, tween(420, easing = FastOutLinearInEasing))
                actions.eject()
                // The deck lets go of the old record before it drops back to the mat.
                withTimeoutOrNull(500) { snapshotFlow { current.deck.album }.first { it == null } }
                delay(50)
                swapLift.snapTo(0f)
                land(target)
                swapping = false
            }

            // Held over a busy platter for a second: the deck shakes, harder as it goes, with a tick
            // per shake, then the records swap. Letting go or moving off before then stops it.
            val overBusy = dragging && !swapping && pc != null && dist < platterRadius * 0.95f &&
                state.deck.album?.id?.let { it != pulled.id } == true
            LaunchedEffect(overBusy) {
                if (!overBusy) return@LaunchedEffect
                val motion = android.provider.Settings.Global.getFloat(appContext.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
                val px = with(density) { 1.dp.toPx() }
                holdingOverBusy = true
                try {
                    repeat(SWAP_SHAKES) { i ->
                        val a = px * (1.5f + 2f * i / (SWAP_SHAKES - 1))
                        if (motion) shakeX = if (i % 2 == 0) a else -a
                        buzz()
                        delay(SWAP_HOLD_MS / SWAP_SHAKES)
                    }
                } finally {
                    shakeX = 0f
                    holdingOverBusy = false
                }
                scope.launch { swapRecords() }
            }

            if (use3d) {
                // The 3D record counts as shown once its first frames are up and its face is on, so
                // a bare black disc never flashes.
                var framesUp by remember(pulled.id) { mutableStateOf(false) }
                var faceOn by remember(pulled.id) { mutableStateOf(false) }
                LaunchedEffect(framesUp, faceOn) { if (framesUp && faceOn) { disc3dShown = true; heldReady = true } }
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            runCatching { DiscRenderer(ctx, tv).also { it.resume() } }
                                .onSuccess { r -> r.onReady = { framesUp = true }; discRenderer = r }
                        }
                    },
                    onRelease = { discRenderer?.destroy(); discRenderer = null },
                    modifier = Modifier.fillMaxSize().zIndex(4.9f),
                )
                LaunchedEffect(discRenderer, cover) {
                    val r = discRenderer ?: return@LaunchedEffect
                    // Out of the sleeve it's side A; flying home from the deck, the side that was up.
                    val home = ejecting == pulled.id
                    val faceGaps = if (home) ejectGaps else gaps
                    val face = recordFace(pulled, cover, faceGaps, if (home) ejectSide else "A", measurer)
                    val maps = withContext(Dispatchers.Default) { RecordMaps.normal(faceGaps) to RecordMaps.roughness(faceGaps) }
                    r.setRecord(face.asAndroidBitmap(), maps.first, maps.second)
                    // A few frames for the new textures to reach the screen.
                    delay(64)
                    faceOn = true
                }
                LaunchedEffect(discRenderer) {
                    val r = discRenderer ?: return@LaunchedEffect
                    snapshotFlow {
                        val cam = deckLink.camera
                        val target = platterCenter
                        val near = if (target == null) 0f else (1f - (disc.value + rootOrigin - target).getDistance() / (platterRadius * 3.2f)).coerceIn(0f, 1f)
                        val diameter = baseD + (platterRadius * 2f - baseD) * near
                        if (cam == null) null else {
                            r.setDeckCamera(cam, deckLink.origin - rootOrigin)
                            DeckModel.heldDiscTransform(cam, disc.value + rootOrigin - deckLink.origin, diameter * lift.value / 2f, near, spin.value)
                        }
                    }.collect { m -> if (m != null) r.setTransform(m) }
                }
            }

            // A soft shadow under the 3D record while it's held, falling away as it nears the platter.
            if (disc3dShown) Canvas(Modifier.fillMaxSize().zIndex(4.8f)) {
                val held = (1f - proximity).coerceIn(0f, 1f) * (if (dragging) 1f else 0.7f)
                if (held > 0.02f) {
                    val r = d / 2f * lift.value
                    val c = disc.value + Offset(r * 0.10f, r * 0.42f)
                    scale(1f, 0.32f, pivot = c) {
                        drawCircle(
                            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.22f * held), Color.Transparent), center = c, radius = r * 1.05f),
                            radius = r * 1.05f, center = c,
                        )
                    }
                }
            }

            Canvas(
                Modifier
                    .zIndex(5f)
                    .offset { IntOffset((disc.value.x - d / 2f).roundToInt(), (disc.value.y - d / 2f).roundToInt()) }
                    .size(dDp)
                    .testTag("pulled-disc")
                    .graphicsLayer {
                        // Held up off the crate: a deeper shadow and a touch larger; squashed for a beat on landing.
                        shadowElevation = if (disc3dShown) 0f else if (dragging) 34f else 8f
                        scaleX = lift.value
                        scaleY = lift.value
                        shape = androidx.compose.foundation.shape.CircleShape
                        clip = false
                    }
                    .pointerInput(pulled.id) {
                        detectTapGestures(onLongPress = { menuFor = pulled.id })
                    }
                    .pointerInput(pulled.id) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                pickUp()
                                scope.launch { lift.animateTo(1.06f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (!swapping) scope.launch { disc.snapTo(disc.value + amount) }
                            },
                            onDragEnd = onDragEnd@{
                                // Mid-swap the record is already on its way to the platter.
                                if (swapping) return@onDragEnd
                                dragging = false
                                val target = platterCenter
                                val onPlatter = target != null && (disc.value + rootOrigin - target).getDistance() < platterRadius * 0.95f
                                val busy = current.deck.album?.id?.let { it != pulled.id } == true
                                scope.launch {
                                    if (onPlatter && busy) {
                                        // Platter occupied: the disc doesn't land. It bounces back along its
                                        // path (0.4 s, a small overshoot) with a double buzz and slides into its sleeve.
                                        occupied = true
                                        launch { refused(); delay(90); refused() }
                                        launch { lift.animateTo(1f, tween(400)) }
                                        disc.animateTo(restPos, spring(dampingRatio = 0.6f, stiffness = 380f))
                                        disc.animateTo(s.rect.center - rootOrigin, tween(200, easing = FastOutSlowInEasing))
                                        pulledId = null
                                    } else if (onPlatter && target != null) {
                                        land(target)
                                    } else {
                                        launch { lift.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
                                        disc.animateTo(restPos, spring(dampingRatio = 0.8f, stiffness = 300f))
                                    }
                                }
                            },
                            onDragCancel = onDragCancel@{
                                if (swapping) return@onDragCancel
                                dragging = false
                                scope.launch { lift.animateTo(1f) }
                                scope.launch { disc.animateTo(restPos, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                            },
                        )
                    },
            ) {
                if (!disc3dShown && ejecting != pulled.id) drawRecord(
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension / 2f,
                    rotation = spin.value,
                    look = look,
                    label = cover,
                    labelFallback = fallback,
                    trackBoundaries = gaps,
                    sideText = sideText,
                    qualityText = qualityText,
                )
            }

            // The sleeve sits in front of the disc while it slides out, and behind it once you pick it up.
            // Its label says what to do; once the record is over the deck the empty sleeve fades back.
            SleeveArt(
                pulled,
                label = if (proximity > 0.05f || ejecting == pulled.id) "sleeve · empty" else "pull up · drag",
                modifier = Modifier
                    .zIndex(if (dragging || proximity > 0.05f) 4f else 6f)
                    // A white wash rather than layer alpha: a translucent layer under the 3D overlay drops out.
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.White.copy(alpha = 0.55f * (proximity * 3f).coerceIn(0f, 1f)))
                    }
                    .offset { IntOffset((s.rect.left - rootOrigin.x).roundToInt(), (s.rect.top - rootOrigin.y).roundToInt()) }
                    .size(s.size)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onLongClick = { menuFor = pulled.id },
                        // Put back: the disc slides down into its sleeve before the sleeve goes back.
                        onClick = {
                            scope.launch {
                                launch { lift.animateTo(1f, tween(160)) }
                                disc.animateTo(s.rect.center - rootOrigin, tween(280, easing = FastOutSlowInEasing))
                                pulledId = null
                            }
                        },
                    ),
            )
        }

        // Long-press a sleeve or a disc: what you can do with that record.
        state.albums.firstOrNull { it.id == menuFor }?.let { record ->
            RecordMenu(
                record = record,
                pulled = record.id == pulledId,
                backdrop = roomBackdrop,
                onPutBack = { pulledId = null; menuFor = null },
                onSend = { actions.share(record); menuFor = null },
                onRemove = { removeId = record.id; menuFor = null },
                onDetails = { detailsFor = record.id; menuFor = null },
                onDismiss = { menuFor = null },
                missing = missingBy[record.id] ?: 0,
                // Off to Downloads, where its rows say what Runout is doing.
                onFetchMissing = { actions.fetchMissingTracks(record.id); menuFor = null; panel = Panel.DOWNLOADS },
            )
        }
        state.albums.firstOrNull { it.id == detailsFor }?.let { record ->
            RecordDetails(record, roomBackdrop, onDismiss = { detailsFor = null })
        }

        // What each place holds. Every numbered place is a page under the nav; Audio quality is a panel.
        val panelContent = @Composable { shown: Panel ->
            when (shown) {
                Panel.DIG -> {
                    NetworkNote(state, actions)
                    val preview = state.folderPreview
                    if (preview != null) FolderPreview(preview, state, actions) { peer ->
                        actions.closeSoulseekFolder(); actions.openSoulseekConversation(peer); goTo(Panel.FRIENDS)
                    } else DiscoveryScreen(state, actions, onAccount = openAccount, onCue = { panel = null })
                }
                Panel.DROPS -> DropsPanel(state.drops, state.claimed, state.claiming, actions, state.soulseekSession.signedIn, onAccount = openAccount)
                Panel.FRIENDS -> {
                    if (state.soulseekSession.hasAccount) ConnectionNotice(state, actions, openSetup)
                    SoulseekMessagesPanel(state, actions, onAccount = openAccount, onDig = { goTo(Panel.DIG) })
                }
                Panel.SETTINGS -> SettingsPanel(
                    state, actions, settings, { settings = it },
                    onDownloads = { goTo(Panel.DOWNLOADS) }, onSetup = openSetup, onOffline = { closePanel() },
                )
                Panel.DOWNLOADS -> SoulseekTransfersPanel(state, actions, onDig = { goTo(Panel.DIG) })
                Panel.SIGNAL -> SignalPanel(state, actions, onSoundSettings = { settings = SettingsSection.SOUND; goTo(Panel.SETTINGS) })
            }
        }

        // The Audio quality panel: up from the foot over the grey scrim, 83 % of the height.
        val open = panel?.takeIf { !it.fullPage }
        val sheetWidth = (screenW * 0.46f).coerceIn(340.dp, 520.dp)
        // Over the nav bar too, as on the board: the scrim and the panel cover the whole screen.
        Box(Modifier.fillMaxSize().zIndex(if (open != null) 11.8f else 10f)) {
            AnimatedVisibility(open != null, enter = fadeIn(), exit = fadeOut()) {
                // Only catches taps while a panel is open: during its fade-out the room must take the next tap.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Room.Scrim)
                        .then(
                            if (open != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { closePanel() }
                            else Modifier,
                        ),
                )
            }
            AnimatedVisibility(
                visible = open != null,
                enter = if (portrait) slideInVertically { it } + fadeIn() else slideInHorizontally { it } + fadeIn(),
                exit = if (portrait) slideOutVertically(tween(180)) { it } + fadeOut(tween(160)) else slideOutHorizontally(tween(180)) { it } + fadeOut(tween(160)),
                modifier = Modifier.align(if (portrait) Alignment.BottomCenter else Alignment.CenterEnd),
            ) {
                var shown by remember { mutableStateOf(open ?: Panel.SIGNAL) }
                if (open != null) shown = open
                PanelSheet(
                    title = shown.title,
                    backdrop = roomBackdrop,
                    portrait = portrait,
                    width = sheetWidth,
                    height = screenH * 0.83f,
                    onClose = { closePanel() },
                    onBack = headBack,
                    catchTaps = open != null,
                ) { CompositionLocalProvider(LocalPageSteps provides pageSteps) { panelContent(shown) } }
            }
        }

        // Full pages, above the room and under the nav bar.
        val page = panel?.takeIf { it.fullPage }
        val pageSheets = remember { PageSheetHost() }
        AnimatedVisibility(
            visible = page != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.zIndex(11f),
        ) {
            var shown by remember { mutableStateOf(page ?: Panel.DIG) }
            if (page != null) shown = page
            val title = if (shown == Panel.SETTINGS) settings?.label ?: shown.title else shown.title
            // Dig and Friends scroll their own lists; the rest scroll as one page.
            FullPage(shown.index, title, onClose = { closePanel() }, pageSheets, scroll = shown != Panel.DIG && shown != Panel.FRIENDS, onBack = headBack) {
                CompositionLocalProvider(LocalPageSteps provides pageSteps) { panelContent(shown) }
            }
        }

        // One nav bar for the room and every page, the open page's cell in black.
        // In landscape it sits at the foot of the room's column, beside the deck.
        // The same side insets as the room's column, so the bar lines up with it.
        if (!setupOpen) Box(Modifier.fillMaxSize().zIndex(11.5f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            TeNavBar(
                active = page,
                badges = navBadges(state),
                onOpen = {
                    if (it == Panel.SETTINGS) settings = null
                    // Friends always opens on its own page, not on the last chat.
                    if (it == Panel.FRIENDS) actions.closeSoulseekConversation()
                    // The nav bar starts afresh: nothing to go back to.
                    pageHistory.clear()
                    panel = if (panel == it) null else it
                    pulledId = null
                },
                onHome = if (page != null) ({ closePanel() }) else null,
                modifier = Modifier
                    .align(if (portrait) Alignment.BottomCenter else Alignment.BottomEnd)
                    .then(if (portrait) Modifier else Modifier.fillMaxWidth(LANDSCAPE_ROOM))
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
        // Sheets opened by a page's content cover the whole screen, nav bar included.
        if (page != null) Box(Modifier.fillMaxSize().zIndex(11.7f)) { PageSheetLayer(pageSheets) }

        // Onboarding: its own full screen, on first launch or when Soulseek setup is asked for again.
        // Finishing drops you into the room, which pops in behind the fading greeting.
        val firstRun = !state.onboardingComplete
        AnimatedVisibility(setupOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(320)), modifier = Modifier.zIndex(12f)) {
            SetupScreen(
                state, actions, setupStart,
                onFinish = {
                    actions.finishOnboarding()
                    setupOpen = false
                    scope.launch {
                        entry.snapTo(0f)
                        entry.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 220f))
                    }
                },
                intro = firstRun && setupStart == 0,
                onClose = if (firstRun) null else ({ setupOpen = false }),
            )
        }

        state.albums.firstOrNull { it.id == removeId }?.let { record ->
            GlassConfirm(
                "remove record?",
                if (record.source == Source.RECEIVED) "“${record.title.lowercase()}” and its received audio will be deleted from this device."
                else "“${record.title.lowercase()}” will be removed from your shelf. original music files are kept.",
                "remove record", "keep it", roomBackdrop,
                onConfirm = { actions.removeRecord(record); removeId = null; pulledId = null },
                onDismiss = { removeId = null },
            )
        }

        // Where toasts sit: above the "now spinning" line and the tabs, on the room's half in landscape.
        val toastAt = Modifier
            .align(if (portrait) Alignment.BottomCenter else Alignment.BottomEnd)
            .then(if (portrait) Modifier else Modifier.fillMaxWidth(LANDSCAPE_ROOM))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = NAV_HEIGHT + 49.dp, start = 22.dp, end = 22.dp)
            .widthIn(max = 520.dp)

        // Platter occupied: the error toast for 4 s, with "eject →" (the put-back).
        if (occupied && !setupOpen) {
            LaunchedEffect(Unit) { delay(4_000); occupied = false }
            TeToast("first, remove the current record from the platter, or hold the new one there to swap.", Room.Danger, toastAt.zIndex(21f).testTag("platter-occupied")) {
                TeLink("eject", { occupied = false; deckActions.eject() }, tag = "occupied-eject", color = Color.White)
            }
        }

        // Dragging the record off the platter: point to the key that does it.
        if (useEject && !setupOpen) {
            LaunchedEffect(Unit) { delay(3_500); useEject = false }
            TeToast("use the eject key to put the record back.", Room.Accent, toastAt.zIndex(21f).testTag("use-eject")) {
                TeLink("eject", { useEject = false; deckActions.eject() }, tag = "use-eject-key", color = Color.White)
            }
        }

        // Something joined the downloads queue (from Dig, a missing track, a retry): a pop that says
        // so, wherever you are, with the way to the list. What's already queued when the room opens
        // (the saved list arriving) doesn't count.
        val lastGroups = remember { HashMap<String, DownloadGroup>() }
        var watching by remember { mutableStateOf(false) }
        var added by remember { mutableIntStateOf(0) }
        var addedAt by remember { mutableLongStateOf(0L) }
        val addedTick = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
        LaunchedEffect(Unit) { frameDelay(2_000); watching = true }
        LaunchedEffect(state.soulseekTransfers) {
            val now = state.soulseekTransfers.associate { it.id to downloadGroup(it.state) }
            // A copy Runout found by itself to replace a failed one isn't news: you didn't add it.
            val replacements = state.soulseekTransfers.filter { it.replaced != null }.mapTo(HashSet()) { it.id }
            val fresh = now.count { (id, g) -> g == DownloadGroup.QUEUE && lastGroups[id] != DownloadGroup.QUEUE && id !in replacements }
            if (watching && fresh > 0) {
                val stamp = System.currentTimeMillis()
                added = if (stamp - addedAt < 3_000) added + fresh else fresh
                addedAt = stamp
                addedTick()
            }
            lastGroups.clear(); lastGroups.putAll(now)
        }
        if (added > 0 && !setupOpen) {
            LaunchedEffect(addedAt) { frameDelay(3_500); added = 0 }
            // On a page it sits on the nav bar, under the page's own keys (Dig's sort key);
            // in the room, where the room's toasts go.
            val popAt = if (panel?.fullPage == true) Modifier
                .align(if (portrait) Alignment.BottomCenter else Alignment.BottomEnd)
                .then(if (portrait) Modifier else Modifier.fillMaxWidth(LANDSCAPE_ROOM))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = NAV_HEIGHT, start = 22.dp, end = 22.dp)
                .widthIn(max = 520.dp)
            else toastAt
            DownloadsAddedPop(added, addedAt, popAt.zIndex(21f).testTag("downloads-added")) { added = 0; panel = Panel.DOWNLOADS; pulledId = null }
        }

        // The room's first-time helps.
        Box(Modifier.fillMaxSize().zIndex(if (roomHelp != null) 31f else -1f)) { TeHelpSheet(roomHelp) { roomHelp = null } }

        // Notes: one toast at a time. The cover notice waits for OK; a Soulseek problem stays until
        // it's tapped away; anything else fades after a few seconds.
        val notice = state.coverLookupNoticePending && !setupOpen
        val msg = if (setupOpen) null else state.message
        val problem = state.soulseekError?.takeIf { panel == null && !setupOpen }
        if (notice || msg != null || problem != null) {
            if (!notice && msg != null) LaunchedEffect(msg) {
                delay(4_500)
                actions.dismissMessage()
            }
            val at = toastAt.zIndex(20f).then(
                if (notice) Modifier.testTag("cover-lookup-notice")
                else Modifier.testTag("message").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (msg != null) actions.dismissMessage() else actions.clearSoulseekError()
                },
            )
            if (notice) TeToast(
                "finding missing covers online is now opt-in. it is off unless you enable it in settings › soulseek › collection & storage. " +
                    "lookups send artist and album title to musicbrainz, the cover art archive and the internet archive.",
                Room.Accent, at,
            ) { TeLink("ok", actions::dismissCoverLookupNotice, tag = "action:OK", arrow = "", color = Color.White) }
            else TeToast(msg ?: problem.orEmpty(), if (msg == null) Room.Danger else Room.Accent, at)
        }
    }
}

/** TE · Remove record?: a TE card with the 22/700 question, a 10/400 line and two keys side by side, the red one to act. */
@Composable
private fun GlassConfirm(
    title: String,
    detail: String,
    confirm: String,
    dismiss: String,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TeCard("confirm", onDismiss) {
        BasicText(title.uppercase(), style = Room.Title)
        BasicText(detail, style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            TeButton(dismiss, onDismiss, Modifier.weight(1f), kind = KeyKind.OUTLINE)
            val tick = rememberTick()
            Box(
                Modifier
                    .weight(1f)
                    .background(Room.Danger)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { tick(); onConfirm() }
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(confirm, style = Room.Text.copy(color = Color.White), maxLines = 1) }
        }
    }
}

/**
 * TE · Toast: a black slip 22 dp from the sides, above the line and the tabs, 11 × 16 inside: an
 * 8 dp lamp (blue for news, red for a problem) and 10/400 white text, with an optional key.
 */
@Composable
private fun TeToast(text: String, lamp: Color, modifier: Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.background(Room.Ink).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(8.dp).background(lamp))
        BasicText(text, style = Room.Label.copy(color = Color.White, lineHeight = 15.sp), modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Waits [ms] on the frame clock (which tests can step), not the wall clock. */
private suspend fun frameDelay(ms: Long) {
    val start = withFrameMillis { it }
    while (withFrameMillis { it } - start < ms) Unit
}

/**
 * "2 tracks added to downloads": the toast's black bar with the orange download glyph, popping up
 * (a short overshoot) each time more join; "view →" opens the list.
 */
@Composable
private fun DownloadsAddedPop(count: Int, stamp: Long, modifier: Modifier, onView: () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(stamp) {
        pop.snapTo(0f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 520f))
    }
    Row(
        modifier
            .graphicsLayer {
                val k = 0.82f + 0.18f * pop.value
                scaleX = k; scaleY = k
                alpha = pop.value.coerceIn(0f, 1f)
                translationY = (1f - pop.value) * 18.dp.toPx()
            }
            .background(Room.Ink)
            // 48 dp, the link's own touch height: low enough to sit under a page's corner key.
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Canvas(Modifier.size(16.dp)) { drawDownloadGlyph(Room.Signal) }
        BasicText(
            "${if (count == 1) "1 track" else "$count tracks"} added to downloads",
            style = Room.Label.copy(color = Color.White, lineHeight = 15.sp),
            modifier = Modifier.weight(1f),
        )
        TeLink("view", onView, tag = "downloads-added-view", color = Color.White)
    }
}

private fun emptyTitle(state: RoomState): String = when {
    !state.hasFolder -> "CHOOSE YOUR MUSIC FOLDER"
    state.scan is ScanState.Scanning -> "READING YOUR FOLDER…"
    else -> "NO ALBUMS FOUND"
}

private fun emptyDetail(state: RoomState): String = when {
    !state.hasFolder -> "each album folder becomes a record on your shelf. flac only. nothing leaves your phone."
    state.scan is ScanState.Scanning -> (state.scan as ScanState.Scanning).progress.let { "${it.folders} folders · ${it.audioFiles} music files so far" }
    state.report != null -> state.report.summary.lowercase() + " pick another folder to try again."
    else -> "pick another folder to try again."
}

/**
 * The TE panel: white, a 2 dp ink rule along the top, 22 dp in (33 at the foot), the 22/700 title
 * and "close ×" on one line, 16 dp between parts.
 */
@Composable
private fun PanelSheet(
    title: String,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop,
    portrait: Boolean,
    width: Dp,
    height: Dp,
    /** Null hides Close: first-run setup has to be answered. */
    onClose: (() -> Unit)?,
    /** Set inside a step or a page reached from another: "← back" in close's place. */
    onBack: () -> (() -> Unit)? = { null },
    /** Off for content that scrolls itself, like a lazy list of search results. */
    scroll: Boolean = true,
    /** Off while the sheet slides away, so a tap meant for the room isn't swallowed. */
    catchTaps: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheet = if (portrait) Modifier.fillMaxWidth().height(height) else Modifier.width(width).fillMaxHeight()
    Column(
        sheet
            .background(Room.Background)
            .hairlineTop(Room.Ink, 2.dp)
            // Taps inside the panel stay inside it (without turning it into one button for TalkBack).
            .then(if (catchTaps) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title.uppercase(), style = Room.Title, modifier = Modifier.weight(1f).testTag("panel-title"))
            HeadKey(onClose, onBack)
        }
        // The panel keeps its size; its contents shrink above the keyboard.
        val body = Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(WindowInsets.ime)
        Column(if (scroll) body.verticalScroll(rememberScrollState()).padding(bottom = 33.dp) else body, content = content)
    }
}

/**
 * A place with its own page: the TE screen head ("02 / dig", "close ×", the 44/700 title), then the
 * body 11 dp below it and 22 dp in, above the keyboard and the nav bar.
 */
@Composable
private fun FullPage(index: String, title: String, onClose: () -> Unit, sheets: PageSheetHost, scroll: Boolean = false, navRoom: Boolean = true, onBack: () -> (() -> Unit)? = { null }, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Room.Background).pointerInput(Unit) { detectTapGestures { } }) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(bottom = if (navRoom) NAV_HEIGHT else 0.dp),
        ) {
            TeScreenHead(index, title, onClose, onBack = onBack)
            val body = Modifier.fillMaxWidth().weight(1f)
            CompositionLocalProvider(LocalPageSheetHost provides sheets) {
                Column(
                    (if (scroll) body.verticalScroll(rememberScrollState()) else body).padding(start = 22.dp, end = 22.dp, top = 11.dp, bottom = 22.dp),
                    content = content,
                )
            }
        }
    }
}

/** The room's column in landscape: 412.5 of 892 on the board, beside the deck's band. */
internal const val LANDSCAPE_ROOM = 0.4625f

/** The nav bar's height: 11 + 20 icon + 8 + a 10 sp line + 11. */
internal val NAV_HEIGHT = 62.dp

/** A new Friday Drop, or an unread message: an orange square on that place's cell. */
internal fun navBadges(state: RoomState): Set<Panel> = buildSet {
    if (state.drops is DropState.Ready && state.claimed.size < DropsRepository.CLAIMS_PER_WEEK) add(Panel.DROPS)
    if (state.soulseekConversations.any { it.unread > 0 }) add(Panel.FRIENDS)
}

/** Plain white paper: the deck and the sleeves carry all the colour. */
@Composable
private fun RoomBackground(modifier: Modifier, @Suppress("UNUSED_PARAMETER") portrait: Boolean) {
    Box(modifier.background(Room.Background))
}

/** TE · Home's top bar: the side that's up (or the empty platter) on the left; the line out, downloads and the shelf on the right. */
@Composable
private fun TopBar(state: RoomState, onDownloads: () -> Unit, onAccount: () -> Unit) {
    val deck = state.deck
    val side = deck.side
    // Downloads still in the queue or waiting for attention; finished and cancelled ones don't count.
    val transfers = state.soulseekTransfers.filter { it.state != SoulseekTransferState.COMPLETED && it.state != SoulseekTransferState.CANCELLED }
    val scanText = when (val s = state.scan) {
        is ScanState.Scanning -> "scanning · ${s.progress.albums}"
        is ScanState.FindingCovers -> "covers · ${s.done + 1}/${s.total}"
        null -> null
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).testTag("side-indicator").semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (deck.isPlaying) PlayLamp() else Lamp(if (deck.album != null) Room.Signal else null)
            BasicText(
                when {
                    side != null -> "side ${side.letter.uppercaseChar()}"
                    state.albums.isEmpty() -> "no record"
                    else -> "platter empty"
                },
                style = Room.Strong,
                maxLines = 1,
            )
            if (side != null) BasicText("${deck.trackCode.uppercase()} · ${deck.trackIndex + 1} / ${side.tracks.size}", style = Room.LabelQuiet, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (scanText != null) BasicText(scanText, style = Room.LabelQuiet, maxLines = 1)
            val c = connectionColor(state.soulseekSession)
            Box(
                Modifier
                    .testTag("open-account")
                    .semantics { contentDescription = connectionLabel(state.soulseekSession) }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onAccount)
                    .touchHeight()
                    .padding(horizontal = 4.dp),
            ) { Lamp(if (c == Room.Quiet) null else c) }
            // Downloads: a 16 dp download glyph in the colour of the Downloads segment that most needs
            // you: red if any failed, else orange while any are in the queue, green when all are done.
            val status = listOf(DownloadGroup.FAILED, DownloadGroup.QUEUE, DownloadGroup.COMPLETED)
                .firstOrNull { g -> state.soulseekTransfers.any { downloadGroup(it.state) == g } }
            Box(
                Modifier
                    .testTag("open-downloads")
                    .semantics { contentDescription = "Downloads" + (status?.let { ", ${it.label}" } ?: "") }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDownloads)
                    .touchHeight()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) { drawDownloadGlyph(status?.colour ?: Room.Ink) }
            }
            BasicText("${state.albums.size} rec", style = Room.Label, maxLines = 1, modifier = Modifier.semantics { contentDescription = recordCount(state.albums.size) })
        }
    }
}

/** Playing: a green play triangle in the lamp's 8 dp box. */
@Composable
private fun PlayLamp() {
    Canvas(Modifier.size(8.dp).semantics { contentDescription = "Playing" }) {
        drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }, Room.Online)
    }
}

/** The download glyph in the nav icons' line: an arrow down into an open tray. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDownloadGlyph(ink: Color) {
    val w = size.width
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    val arrow = Path().apply {
        moveTo(w * 0.5f, w * 0.1f); lineTo(w * 0.5f, w * 0.62f)
        moveTo(w * 0.27f, w * 0.4f); lineTo(w * 0.5f, w * 0.63f); lineTo(w * 0.73f, w * 0.4f)
    }
    val tray = Path().apply {
        moveTo(w * 0.1f, w * 0.62f); lineTo(w * 0.1f, w * 0.88f); lineTo(w * 0.9f, w * 0.88f); lineTo(w * 0.9f, w * 0.62f)
    }
    drawPath(arrow, ink, style = stroke)
    drawPath(tray, ink, style = stroke)
}

/** An 8 dp square lamp: filled with [color], or hollow ink when there's nothing to say. */
@Composable
private fun Lamp(color: Color?) {
    Box(Modifier.size(8.dp).then(if (color != null) Modifier.background(color) else Modifier.border(1.dp, Room.Ink)))
}

/** The deck's band: the RG-1 at 412 × 240, and under it "33⅓ rpm" (the LED already shows the side and time). */
@Composable
private fun DeckBand(state: RoomState, empty: Boolean, help: @Composable (Modifier) -> Unit, deck: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (empty) 0.dp else 16.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(412f / 240f).alpha(if (empty) 0.5f else 1f)) { deck(Modifier.fillMaxSize()) }
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            help(Modifier)
            Spacer(Modifier.weight(1f))
            if (!empty) BasicText("33⅓ rpm", style = Room.LabelQuiet)
        }
    }
}

/** What the deck's caption says: the side and the time, or why the platter is empty. */
private fun bandCaption(state: RoomState, landscape: Boolean): String {
    val deck = state.deck
    val side = deck.side
    return when {
        state.albums.isEmpty() -> "rg-1 · nothing on the platter"
        side == null -> "rg-1 · platter empty"
        landscape -> "${deck.trackCode.lowercase()} · ${deck.currentTrackTitle?.lowercase().orEmpty()} · ${clock(deck.trackPositionMs, pad = false)}"
        else -> "rg-1 · side ${side.letter.lowercaseChar()} · ${clock(deck.trackPositionMs, pad = true)}"
    }
}

/** The record in front: its title in 18/700 caps, the artist in 9/400 and the spec as a TE Tag. */
@Composable
private fun AlbumCaption(album: Album, portrait: Boolean) {
    Column(
        // Pulled up towards the crate's floor, over the end of the reflection.
        Modifier.fillMaxWidth().offset(y = if (portrait) (-12).dp else 0.dp).padding(start = 22.dp, end = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (portrait) 6.dp else 4.dp),
    ) {
        BasicText(album.title.uppercase(), style = if (portrait) Room.Album else Room.Album.copy(fontSize = 16.sp), maxLines = 1)
        if (portrait) {
            BasicText(album.artist.lowercase(), style = Room.Label.copy(fontSize = 9.sp, color = Room.Body), maxLines = 1, modifier = Modifier.testTag("caption-artist"))
            SpecTag(album.quality.label)
        } else {
            BasicText("${album.artist.lowercase()} · ${album.quality.shortLabel.lowercase()}", style = Room.Label.copy(color = Room.Body), maxLines = 1, modifier = Modifier.testTag("caption-artist"))
        }
        album.unavailableReason?.let { BasicText(it.lowercase(), style = Room.Label.copy(color = Room.Danger, textAlign = TextAlign.Center), maxLines = 2) }
    }
}

/** The line above the nav: an orange lamp and what's happening, and "flick ← →" for the crate. */
@Composable
private fun NowSpinning(line: Pair<String, Boolean>, hint: Boolean, landscape: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().hairlineTop(Room.HairlineSoft).padding(horizontal = 22.dp, vertical = if (landscape) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Lamp(if (line.second) Room.Signal else null)
        BasicText(line.first, style = Room.Label, maxLines = 1, modifier = Modifier.weight(1f).testTag("now-line"))
        if (hint) BasicText("flick ← →", style = Room.LabelQuiet)
    }
}

/** What the line above the nav says, and whether its lamp is lit. */
private fun nowLine(state: RoomState, pulled: Boolean, overPlatter: Boolean): Pair<String, Boolean> {
    val deck = state.deck
    val side = deck.side?.letter?.lowercaseChar()
    return when {
        overPlatter && deck.album == null -> "let go to drop · side a" to true
        pulled && deck.album == null -> "drag the record onto the platter" to true
        deck.album == null -> "nothing on the platter" to false
        deck.sideFinished -> "side $side finished — tap the record to flip it" to true
        // Stopped with the needle down: the start key, a call or unplugged headphones paused it.
        deck.needleDown && !deck.isPlaying -> "stopped — side $side · ${(deck.currentTrackTitle ?: deck.album.title).lowercase()} · press start" to false
        deck.needleDown -> "now spinning — side $side · ${(deck.currentTrackTitle ?: deck.album.title).lowercase()}" to true
        else -> "${deck.album.title.lowercase()} · side $side cued — lower the tonearm" to true
    }
}

/** No records yet: TE · Home · empty, the 33/700 title, a line and the key, low for the thumb. */
@Composable
private fun EmptyRoom(state: RoomState, actions: RoomActions, modifier: Modifier) {
    Column(modifier.padding(horizontal = 22.dp, vertical = 33.dp), verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.Bottom)) {
        BasicText(emptyTitle(state), style = Room.Display.copy(fontSize = 33.sp, lineHeight = 36.sp), modifier = Modifier.testTag("empty-title"))
        BasicText(emptyDetail(state), style = Room.Text.copy(color = Room.Body, lineHeight = 18.sp), modifier = Modifier.testTag("empty-detail"))
        if (state.scan !is ScanState.Scanning) TeButton(if (state.hasFolder) "choose another folder" else "choose folder", actions::pickFolder, Modifier.testTag("choose-folder"))
    }
}

/**
 * The record's songs, coded by side (a1, a2, b1…): the one playing sharp between ink rules with
 * "▲▼", the others soft and fading. Scroll through them; tap one to drop the needle there.
 */
@Composable
private fun TrackWheel(state: RoomState, actions: RoomActions, rows: Int, help: @Composable (Modifier) -> Unit = {}) {
    val deck = state.deck
    val album = deck.album
    val height = WHEEL_FOCUS + WHEEL_ROW * (rows - 1)
    if (album == null) {
        // Keeps its room, so the crate doesn't jump as records come and go.
        Spacer(Modifier.fillMaxWidth().height(height))
        return
    }
    val list = remember(album.id, deck.sides) { deck.sides.flatMap { side -> side.tracks.mapIndexed { i, t -> Triple(side.index, i, t) } } }
    val current = list.indexOfFirst { (side, i, _) -> side == deck.sideIndex && i == deck.trackIndex }.coerceAtLeast(0)
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { scroll.animateScrollToItem((current - 1).coerceAtLeast(0)) }
    val tick = rememberTick()
    androidx.compose.foundation.lazy.LazyColumn(state = scroll, modifier = Modifier.fillMaxWidth().height(height).testTag("track-wheel")) {
        items(list.size) { n ->
            val (sideIndex, i, track) = list[n]
            val focused = n == current
            val distance = kotlin.math.abs(n - current)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (focused) Modifier.height(WHEEL_FOCUS).hairlineTop(Room.Ink).hairlineBottom(Room.Ink)
                        else Modifier.height(WHEEL_ROW).blur(if (distance == 1) 0.6.dp else 1.dp).alpha(if (distance == 1) 0.45f else 0.25f),
                    )
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        tick()
                        if (sideIndex == deck.sideIndex) {
                            val s = deck.side ?: return@clickable
                            val at = s.positionOf(i, 0) + 1
                            actions.needleDrop((at.toFloat() / s.durationMs.coerceAtLeast(1)).coerceIn(0f, 0.999f))
                        } else actions.cueTrack(album, track.id)
                    }
                    .testTag("track:${'A' + sideIndex}${i + 1}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                BasicText("${'a' + sideIndex}${i + 1}", style = Room.Label.copy(color = if (focused) Room.Signal else Room.Quiet), modifier = Modifier.width(22.dp))
                BasicText(track.title.lowercase(), style = if (focused) Room.Text else Room.Label, maxLines = 1, modifier = Modifier.weight(1f))
                if (focused) {
                    help(Modifier)
                    BasicText("▲▼", style = Room.LabelQuiet)
                }
            }
        }
    }
}

/** The wheel's rows: 10 sp with 6 dp above and below; the playing one 24 + 12 between its rules. */
private val WHEEL_ROW = 25.dp
private val WHEEL_FOCUS = 36.dp

private fun clock(ms: Long, pad: Boolean): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return if (pad) "%02d:%02d".format(total / 60, total % 60) else "%d:%02d".format(total / 60, total % 60)
}

/** A TE Help "?" that opens its help panel. */
@Composable
private fun HelpKey(about: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) { InfoDot(about, onClick) }
}

/** The room's first-time helps, from TE · Help panels. */
private val HELP_CRATE = TeHelp(
    "help · room · first time", "the crate",
    listOf(
        "flick left or right to browse. tap a sleeve or pull it up to take the record out.",
        "drag the record onto the platter to play it. tap it to flip the side, hold it to slow it down.",
        "long-press a sleeve for the record menu: details, put back, remove.",
    ),
)
private val HELP_WHEEL = TeHelp(
    "help · room · first time", "the track wheel",
    listOf(
        "the songs of the record on the platter, numbered by side: a1, a2, b1…",
        "scroll to move through them; the sharp one is playing. tap another to drop the needle there.",
    ),
)
private val HELP_LED = TeHelp(
    "help · room · first time", "the led window",
    listOf(
        "shows the track and time while a record plays.",
        "hold it to open the signal chain: what the file is, what runout does, and what your speaker gets.",
    ),
)

internal fun recordCount(n: Int) = if (n == 1) "1 record" else "$n records"

@Composable
internal fun TextAction(text: String, onClick: () -> Unit, tag: String = "action:$text", color: Color = Room.Quiet) {
    BasicText(
        text,
        style = Room.LabelQuiet.copy(fontSize = 10.sp, color = color),
        modifier = Modifier
            .testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .touchHeight()
            .padding(horizontal = 2.dp),
    )
}

/** How long the 3D record stays over the platter after landing, until the deck draws its own. */
private const val HANDOVER_MS = 350L

/** How long a record is held over a busy platter before the swap, and how many shakes that is. */
private const val SWAP_HOLD_MS = 1_000L
private const val SWAP_SHAKES = 24
