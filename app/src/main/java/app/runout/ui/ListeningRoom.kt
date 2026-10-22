package app.runout.ui

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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
    remember { TeType.init(appContext) }
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
    // First launch opens onboarding over the room; Soulseek setup can be opened again later.
    var setupOpen by rememberSaveable { mutableStateOf(!state.onboardingComplete) }
    var setupStart by rememberSaveable { mutableIntStateOf(state.onboardingStep) }
    val openSetup = { setupStart = 1; setupOpen = true; panel = null }
    val openAccount = {
        if (state.soulseekSession.hasAccount) { settings = SettingsSection.ACCOUNT; panel = Panel.SETTINGS; pulledId = null } else openSetup()
    }
    fun closePanel() {
        if (panel == Panel.DIG && state.folderPreview != null) actions.closeSoulseekFolder()
        panel = null
    }
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
    val current by rememberUpdatedState(state)
    var menuFor by remember { mutableStateOf<String?>(null) }
    var detailsFor by remember { mutableStateOf<String?>(null) }
    val pickUp = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate)
    val landed = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
    val refused = rememberTick(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
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
        if (panel != null) closePanel() else pulledId = null
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
            if (portrait) Offset(0f, -s.rect.height * 0.58f) else Offset(-s.rect.width * 0.56f, 0f)
        }

        // Pulling a record: it slides up out of the sleeve, overshoots a touch and settles, turning a
        // little as it comes, the way a real disc does when you draw it out by the edge.
        LaunchedEffect(pulledId, slot != null, portrait) {
            val s = slot ?: return@LaunchedEffect
            if (pulledId == null) return@LaunchedEffect
            val home = platterCenter
            if (ejecting != null && ejecting == pulledId && home != null) {
                // Eject: the 3D record takes the deck's place exactly, then the deck lets go of its
                // own and the record arcs back into its sleeve.
                disc.snapTo(home - rootOrigin)
                lift.snapTo(1f)
                spin.snapTo(deckLink.platterDegrees)
                withTimeoutOrNull(900) { snapshotFlow { heldReady }.first { it } }
                actions.eject()
                delay(120)
                launch { lift.animateTo(1.1f, tween(240, easing = FastOutSlowInEasing)); lift.animateTo(1f, tween(300)) }
                launch { spin.animateTo(spin.value - 140f, tween(540, easing = FastOutSlowInEasing)) }
                disc.animateTo(s.rect.center - rootOrigin, tween(540, easing = FastOutSlowInEasing))
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
                .layerBackdrop(roomBackdrop),
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
                    modifier = m,
                    contentAlignment = alignment,
                    link = deckLink,
                    onScreenLongPress = { panel = Panel.SIGNAL; pulledId = null },
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
                    onMenu = { menuFor = it.id },
                    modifier = m,
                    initialIndex = initialCrateIndex,
                    scrollTo = crateTarget,
                    emptyTitle = emptyTitle(state),
                    emptyDetail = emptyDetail(state),
                )
            }

            if (portrait) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 104.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The deck runs almost edge to edge, past the room's side margins.
                    turntable(Modifier.requiredWidth(screenW - 8.dp).weight(0.40f), Alignment.Center)
                    TrackWheel(state, actions, Modifier.fillMaxWidth().padding(top = 6.dp))
                    Spacer(Modifier.height(8.dp))
                    crate(Modifier.fillMaxWidth().weight(0.60f))
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 28.dp, end = 20.dp, top = 46.dp, bottom = 42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(0.53f).fillMaxHeight()) {
                        turntable(Modifier.fillMaxWidth().weight(1f), Alignment.CenterStart)
                        TrackWheel(state, actions, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    Spacer(Modifier.width(24.dp))
                    // Room underneath for the tab bar.
                    crate(Modifier.weight(0.47f).fillMaxHeight().padding(bottom = LANDSCAPE_TAB_ROOM))
                }
            }

            CornerLabels(
                state, pulled != null, portrait, screenW, backdrop, actions,
                onOpen = { if (it == Panel.SETTINGS) settings = null; panel = it; pulledId = null },
                onDownloads = { panel = Panel.DOWNLOADS; pulledId = null },
                onAccount = openAccount,
                onSignal = { panel = Panel.SIGNAL; pulledId = null },
            )
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

            if (use3d) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            runCatching { DiscRenderer(ctx, tv).also { it.resume() } }
                                .onSuccess { r -> r.onReady = { disc3dShown = true; heldReady = true }; discRenderer = r }
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
                                scope.launch { disc.snapTo(disc.value + amount) }
                            },
                            onDragEnd = {
                                dragging = false
                                val target = platterCenter
                                val onPlatter = target != null && (disc.value + rootOrigin - target).getDistance() < platterRadius * 0.95f
                                val busy = current.deck.album?.id?.let { it != pulled.id } == true
                                scope.launch {
                                    if (onPlatter && busy) {
                                        // Platter occupied: the record bounces off and goes back to hand.
                                        refused()
                                        occupied = true
                                        launch { lift.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
                                        disc.animateTo(restPos, spring(dampingRatio = 0.42f, stiffness = 240f))
                                    } else if (onPlatter && target != null) {
                                        launch { lift.animateTo(1f, tween(180)) }
                                        // Turn to where the platter has stopped, so the 3D record lands lined up.
                                        val p = deckLink.platterDegrees
                                        launch { spin.animateTo(p + 360f * kotlin.math.round((spin.value - p) / 360f), tween(200)) }
                                        disc.animateTo(target - rootOrigin, tween(200, easing = FastOutSlowInEasing))
                                        // The thud: a short squash as it seats on the spindle.
                                        landed()
                                        actions.discLanded()
                                        lift.animateTo(0.95f, tween(70))
                                        lift.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
                                        actions.load(pulled)
                                        // Hold the 3D record until the deck's own is drawn under it.
                                        if (use3dNow) delay(HANDOVER_MS)
                                        pulledId = null
                                    } else {
                                        launch { lift.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
                                        disc.animateTo(restPos, spring(dampingRatio = 0.8f, stiffness = 300f))
                                    }
                                }
                            },
                            onDragCancel = {
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
            SleeveArt(
                pulled,
                Modifier
                    .zIndex(if (dragging || proximity > 0.05f) 4f else 6f)
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
            )
        }
        state.albums.firstOrNull { it.id == detailsFor }?.let { record ->
            RecordDetails(record, roomBackdrop, onDismiss = { detailsFor = null })
        }

        // What each place holds. Dig and Friends get their own full page; the rest slide up as glass.
        val panelContent = @Composable { shown: Panel ->
            when (shown) {
                Panel.DIG -> {
                    NetworkNote(state, actions)
                    val preview = state.folderPreview
                    if (preview != null) FolderPreview(preview, state, actions) { peer ->
                        actions.closeSoulseekFolder(); actions.openSoulseekConversation(peer); panel = Panel.FRIENDS
                    } else DiscoveryScreen(state, actions, onAccount = openAccount, onCue = { panel = null })
                }
                Panel.DROPS -> DropsPanel(state.drops, state.claimed, state.claiming, actions, state.soulseekSession.signedIn, onAccount = openAccount)
                Panel.FRIENDS -> {
                    if (state.soulseekSession.hasAccount) ConnectionNotice(state, actions, openSetup)
                    SoulseekMessagesPanel(state, actions, onAccount = openAccount, onDig = { panel = Panel.DIG })
                }
                Panel.SETTINGS -> SettingsPanel(
                    state, actions, settings, { settings = it },
                    onDownloads = { panel = Panel.DOWNLOADS }, onSetup = openSetup, onOffline = { panel = null },
                )
                Panel.DOWNLOADS -> SoulseekTransfersPanel(state, actions)
                Panel.SIGNAL -> SignalPanel(state, actions, onSoundSettings = { settings = SettingsSection.SOUND; panel = Panel.SETTINGS })
            }
        }

        // Sheets
        val open = panel?.takeIf { !it.fullPage }
        val sheetWidth = (screenW * 0.46f).coerceIn(340.dp, 520.dp)
        Box(Modifier.fillMaxSize().zIndex(10f)) {
            AnimatedVisibility(open != null, enter = fadeIn(), exit = fadeOut()) {
                // Only catches taps while a sheet is open: during its fade-out the room's tabs must
                // take the next tap straight away.
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
                // A quick exit, so the room's tabs are clear almost at once after Close.
                exit = if (portrait) slideOutVertically(tween(180)) { it } + fadeOut(tween(160)) else slideOutHorizontally(tween(180)) { it } + fadeOut(tween(160)),
                modifier = Modifier.align(if (portrait) Alignment.BottomCenter else Alignment.CenterEnd),
            ) {
                var shown by remember { mutableStateOf(open ?: Panel.SETTINGS) }
                if (open != null) shown = open
                PanelSheet(
                    title = shown.title,
                    backdrop = roomBackdrop,
                    portrait = portrait,
                    width = sheetWidth,
                    height = screenH * 0.74f,
                    onClose = { closePanel() },
                    catchTaps = open != null,
                ) { panelContent(shown) }
            }
        }

        // Full pages
        val page = panel?.takeIf { it.fullPage }
        AnimatedVisibility(
            visible = page != null,
            enter = slideInVertically { it / 10 } + fadeIn(),
            exit = slideOutVertically { it / 10 } + fadeOut(),
            modifier = Modifier.zIndex(11f),
        ) {
            var shown by remember { mutableStateOf(page ?: Panel.DIG) }
            if (page != null) shown = page
            // Both pages scroll their own lists.
            FullPage(shown.title, onClose = { closePanel() }) { panelContent(shown) }
        }

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
                "Remove record?",
                if (record.source == Source.RECEIVED) "“${record.title}” and its received audio will be deleted from this device."
                else "“${record.title}” will be removed from your shelf. Original music files are kept.",
                "Remove record", "Keep it", roomBackdrop,
                onConfirm = { actions.removeRecord(record); removeId = null; pulledId = null },
                onDismiss = { removeId = null },
            )
        }

        // Platter occupied: a red note with the way out.
        if (occupied && !setupOpen) {
            LaunchedEffect(Unit) { delay(5_000); occupied = false }
            Row(
                Modifier
                    .zIndex(21f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (portrait) 120.dp else 56.dp, start = 22.dp, end = 22.dp)
                    .widthIn(max = 520.dp)
                    .background(Room.Background)
                    .border(1.dp, Room.Danger)
                    .testTag("platter-occupied")
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText("first, remove the current record from the platter.", style = Room.Label.copy(color = Room.Danger), modifier = Modifier.weight(1f, fill = false))
                TextAction("eject", { occupied = false; deckActions.eject() }, tag = "occupied-eject", color = Room.Ink)
            }
        }

        // Notes: one small glass note at a time. The cover notice waits for OK; a Soulseek problem
        // stays until it's tapped away; anything else fades after a few seconds.
        val notice = state.coverLookupNoticePending && !setupOpen
        val msg = if (setupOpen) null else state.message
        val problem = state.soulseekError?.takeIf { panel == null && !setupOpen }
        if (notice || msg != null || problem != null) {
            if (!notice && msg != null) LaunchedEffect(msg) {
                delay(4_500)
                actions.dismissMessage()
            }
            Box(
                Modifier
                    .zIndex(20f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (portrait) 120.dp else 56.dp, start = 24.dp, end = 24.dp)
                    .widthIn(max = 520.dp)
                    // A printed slip: white, ink rule, red rule for a Soulseek problem.
                    .background(Room.Background)
                    .border(1.dp, if (!notice && msg == null) Room.Danger else Room.Ink)
                    .then(
                        if (notice) Modifier.testTag("cover-lookup-notice")
                        else Modifier.testTag("message").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (msg != null) actions.dismissMessage() else actions.clearSoulseekError()
                        },
                    )
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                if (notice) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        "Finding missing covers online is now opt-in. It is off unless you enable it in Settings › Soulseek › Collection & storage. " +
                            "Lookups send artist and album title to MusicBrainz, the Cover Art Archive and the Internet Archive.",
                        style = Room.Label.copy(fontWeight = FontWeight.Medium),
                    )
                    Pill("OK", actions::dismissCoverLookupNotice)
                } else BasicText(msg ?: problem.orEmpty(), style = Room.Label.copy(color = if (msg == null) Room.Danger else Room.Ink))
            }
        }
    }
}

/** A question asked on glass over the room. Back or a tap outside answers with [dismiss]. */
@Composable
private fun GlassConfirm(
    title: String,
    detail: String,
    confirm: String,
    dismiss: String,
    backdrop: Backdrop,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().zIndex(30f), contentAlignment = Alignment.Center) {
        // A sibling scrim, so the question and its answers stay separate nodes for TalkBack.
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
                .background(Room.Background)
                .border(1.dp, Room.Ink)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
                .testTag("confirm"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(title, style = Room.Title.copy(fontSize = 16.sp))
            BasicText(detail, style = Room.Label.copy(color = Room.Body))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(dismiss, onDismiss)
                Pill(confirm, onConfirm, active = true)
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
    /** Null hides Close: first-run setup has to be answered. */
    onClose: (() -> Unit)?,
    /** Off for content that scrolls itself, like a lazy list of search results. */
    scroll: Boolean = true,
    /** Off while the sheet slides away, so a tap meant for the room isn't swallowed. */
    catchTaps: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheet = if (portrait) Modifier.fillMaxWidth().height(height)
    else Modifier.width(width).fillMaxHeight()
    Column(
        sheet
            // A flat white panel ruled in ink along its open edge, after the TE catalogue.
            .background(Room.Background)
            .then(if (portrait) Modifier.hairlineTop(Room.Ink) else Modifier.border(1.dp, Room.Ink))
            // Taps inside the sheet stay inside it (without turning the whole sheet into one button for TalkBack).
            .then(if (catchTaps) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)
            .padding(start = 22.dp, end = 22.dp, top = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Room.Title.copy(fontSize = 18.sp), modifier = Modifier.weight(1f).testTag("panel-title"))
            if (onClose != null) TextAction("Close", onClose, color = Room.Ink)
        }
        // The glass keeps its size; its contents shrink above the keyboard.
        val body = Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(WindowInsets.ime)
        Column(if (scroll) body.verticalScroll(rememberScrollState()) else body, content = content)
    }
}

/**
 * A place with its own whole screen (Dig, Friends): the room's light, a title and Close at the top,
 * and the content filling the rest above the keyboard.
 */
@Composable
private fun FullPage(title: String, onClose: () -> Unit, scroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val sheets = remember { PageSheetHost() }
    Box(Modifier.fillMaxSize().background(Room.Background).pointerInput(Unit) { detectTapGestures { } }) {
        RoomBackground(Modifier.fillMaxSize(), portrait = true)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, style = Room.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f).testTag("panel-title"))
                TextAction("Close", onClose, color = Room.Ink)
            }
            val body = Modifier.fillMaxWidth().weight(1f)
            CompositionLocalProvider(LocalPageSheetHost provides sheets) {
                Column(if (scroll) body.verticalScroll(rememberScrollState()) else body, content = content)
            }
        }
        // Sheets opened by the page's content cover the whole page, edge to edge.
        PageSheetLayer(sheets)
    }
}

/** Plain white paper: the deck and the sleeves carry all the colour. */
@Composable
private fun RoomBackground(modifier: Modifier, @Suppress("UNUSED_PARAMETER") portrait: Boolean) {
    Box(modifier.background(Room.Background))
}

@Composable
private fun CornerLabels(
    state: RoomState,
    pulled: Boolean,
    portrait: Boolean,
    screenW: Dp,
    backdrop: Backdrop,
    actions: RoomActions,
    onOpen: (Panel) -> Unit,
    onDownloads: () -> Unit,
    onAccount: () -> Unit,
    onSignal: () -> Unit,
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
    val newDrop = state.drops is DropState.Ready && state.claimed.size < DropsRepository.CLAIMS_PER_WEEK
    // Downloads still in the queue or waiting for attention; finished and cancelled ones don't count.
    val transfers = state.soulseekTransfers.filter { it.state != SoulseekTransferState.COMPLETED && it.state != SoulseekTransferState.CANCELLED }
    val badges = buildSet {
        if (newDrop) add(Panel.DROPS)
        if (state.soulseekConversations.any { it.unread > 0 }) add(Panel.FRIENDS)
    }

    // Which side is up and where the needle is: "side a · a2 · 2/4".
    val sideLine = deck.side?.let { side -> "side ${side.letter.lowercaseChar()} · ${deck.trackCode.lowercase()} · ${deck.trackIndex + 1}/${side.tracks.size}" }
        ?: "platter empty"

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(start = if (portrait) 16.dp else 28.dp, end = if (portrait) 16.dp else 28.dp, top = 4.dp, bottom = 12.dp)) {
        // Top bar, ruled underneath: the side that's up on the left; the shelf and the line out on the right.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().drawBehind {
                drawLine(Room.Ink, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(sideLine, style = Room.Label.copy(color = if (deck.album != null) Room.Ink else Room.Quiet), modifier = Modifier.testTag("side-indicator"), maxLines = 1)
            if (state.hasFolder) RefreshKey(actions::rescan)
            Spacer(Modifier.weight(1f))
            if (scanText != null) BasicText(scanText.lowercase(), style = Room.LabelQuiet, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            BasicText(recordCount(state.albums.size), style = Room.Label, maxLines = 1)
            StatusDot("↓ ${transfers.size}", downloadsColor(transfers), "open-downloads", onDownloads)
            // The connection: one square lamp, its words left to TalkBack.
            StatusDot("", connectionColor(state.soulseekSession), "open-account", onAccount, description = connectionLabel(state.soulseekSession))
        }

        val nav = @Composable { m: Modifier -> TabBar(backdrop, badges, onOpen, m) }
        if (portrait) {
            Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(nowSpinning, style = Room.LabelQuiet, maxLines = 1)
                nav(Modifier)
            }
        } else {
            // Landscape mirrors portrait: the same top bar, the line and the tabs at the foot.
            nav(Modifier.align(Alignment.BottomEnd).widthIn(max = screenW * 0.42f))
            BasicText(nowSpinning, style = Room.LabelQuiet, modifier = Modifier.align(Alignment.BottomStart).widthIn(max = screenW * 0.48f), maxLines = 1)
        }
    }
}

/**
 * The record's songs under the deck, coded by side (A1, A2, B1…): about four rows at a time, the one
 * under the needle sharp and the rest soft. Tap one to drop the needle there.
 */
@Composable
private fun TrackWheel(state: RoomState, actions: RoomActions, modifier: Modifier = Modifier) {
    val deck = state.deck
    val album = deck.album
    if (album == null) {
        // Keeps its room, so the crate doesn't jump as records come and go.
        Column(modifier) {
            BasicText("rg-1 · no record", style = Room.LabelQuiet.copy(fontSize = 10.sp), modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.height(WHEEL_ROW * 4))
        }
        return
    }
    val rows = remember(album.id, deck.sides) {
        deck.sides.flatMap { side -> side.tracks.mapIndexed { i, t -> Triple(side.index, i, t) } }
    }
    val current = rows.indexOfFirst { (side, i, _) -> side == deck.sideIndex && i == deck.trackIndex }.coerceAtLeast(0)
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { list.animateScrollToItem((current - 1).coerceAtLeast(0)) }
    val tick = rememberTick()
    Column(modifier.testTag("track-wheel")) {
        val side = deck.side
        BasicText(
            "rg-1 · side ${side?.letter?.lowercaseChar() ?: "a"} · ${formatClock(deck.trackPositionMs)}",
            style = Room.LabelQuiet.copy(fontSize = 10.sp),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        androidx.compose.foundation.lazy.LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(WHEEL_ROW * 4)) {
            items(rows.size) { n ->
                val (sideIndex, i, track) = rows[n]
                val focused = n == current
                val distance = kotlin.math.abs(n - current)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ROW)
                        .then(if (focused) Modifier else Modifier.blur((distance.coerceAtMost(2) * 0.8f).dp).alpha(1f - 0.25f * distance.coerceAtMost(3)))
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText("${'a' + sideIndex}${i + 1}", style = Room.Label.copy(color = if (focused) Room.Signal else Room.Quiet), modifier = Modifier.width(26.dp))
                    BasicText(track.title.lowercase(), style = Room.Label.copy(color = if (focused) Room.Ink else Room.Body), maxLines = 1, modifier = Modifier.weight(1f))
                    BasicText(formatClock(track.durationMs), style = Room.LabelQuiet)
                }
            }
        }
    }
}

private val WHEEL_ROW = 18.dp

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Space kept under the crate in landscape for the tab bar (58 dp) and a small gap. */
private val LANDSCAPE_TAB_ROOM = 50.dp

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
