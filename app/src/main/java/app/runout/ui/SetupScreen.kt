package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.soulseek.SoulseekConnection
import app.runout.soulseek.SoulseekPolicy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Onboarding steps. The numbers are what is saved as the resume point, unchanged from the earlier
 * setup so an interrupted onboarding picks up in the right place.
 */
internal enum class Step(val saved: Int) { INTRO(0), LOGIN(1), DOWNLOADS(2), SHARE(3), RHYTHM(4), WELCOME(5), OFFLINE(6) }

private fun stepFor(saved: Int, intro: Boolean): Step = when (saved) {
    0 -> if (intro) Step.INTRO else Step.LOGIN
    else -> Step.entries.firstOrNull { it.saved == saved } ?: Step.LOGIN
}

/**
 * Onboarding, on its own full screen: the intro, a Soulseek sign-in shaped like a music app's
 * welcome, then one decision per screen with everything near the bottom for a thumb. Choices move
 * on by themselves; there are no Continue buttons. It ends by greeting you and dropping you into
 * the room.
 */
@Composable
internal fun SetupScreen(
    state: RoomState,
    actions: RoomActions,
    initialStep: Int,
    /** Show the intro animation before the sign-in (first launch only). */
    intro: Boolean = false,
    /** Leave without finishing, when setup was opened again from the room. */
    onClose: (() -> Unit)? = null,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(stepFor(initialStep.coerceIn(0, 6), intro)) }
    fun move(next: Step) { step = next; if (next != Step.INTRO) actions.setOnboardingStep(next.saved) }
    fun back() {
        when (step) {
            Step.OFFLINE, Step.DOWNLOADS -> move(Step.LOGIN)
            Step.SHARE -> move(Step.DOWNLOADS)
            Step.RHYTHM -> move(Step.SHARE)
            else -> onClose?.invoke()
        }
    }
    // On the first run, Back on the sign-in leaves the app without finishing onboarding.
    BackHandler(enabled = step !in setOf(Step.INTRO, Step.LOGIN, Step.WELCOME) || onClose != null) { back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Room.Background)
            .testTag("screen:setup"),
    ) {
        OnboardingBackground()
        AnimatedContent(
            targetState = step,
            transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 12 }) togetherWith fadeOut(tween(180)) },
            label = "onboarding",
        ) { shown ->
            // Each step is its own subtree, so TalkBack (and test tools) never see the previous step's text.
            key(shown) {
                when (shown) {
                    Step.INTRO -> RunoutIntro(onFinished = { move(Step.LOGIN) })
                    Step.LOGIN -> LoginStep(state, actions, onConnected = { move(Step.DOWNLOADS) }, onOffline = { actions.disconnectSoulseek(); move(Step.OFFLINE) })
                    Step.OFFLINE -> OfflineStep(state, actions, onDone = { move(Step.WELCOME) }, onBack = { back() })
                    Step.DOWNLOADS -> DownloadsStep(state, actions, onDone = { move(Step.SHARE) }, onBack = { back() })
                    Step.SHARE -> ShareStep(state, actions, onDone = { move(Step.RHYTHM) }, onBack = { back() })
                    Step.RHYTHM -> RhythmStep(state, actions, onDone = { move(Step.WELCOME) }, onBack = { back() })
                    Step.WELCOME -> WelcomeStep(state.soulseekSession.username?.takeIf { state.soulseekSession.hasAccount }, onFinish)
                }
            }
        }
        if (onClose != null && step == Step.LOGIN) {
            Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextAction("Close", onClose, tag = "setup-close", color = Room.Ink)
            }
        }
    }
}

/** The same soft light as the room, so onboarding feels like the room before the furniture arrives. */
@Composable
private fun OnboardingBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val a = Offset(size.width * 0.3f, size.height * 0.18f)
        val b = Offset(size.width * 0.8f, size.height * 0.5f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE6E2FF), Color.Transparent), center = a, radius = size.width * 0.9f), size.width * 0.9f, a)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDDEEFF), Color.Transparent), center = b, radius = size.width * 0.7f), size.width * 0.7f, b)
    }
}

/** A step's frame: a small top row (Back, progress), and the content gathered at the bottom. */
@Composable
private fun StepFrame(
    progress: Int?,
    onBack: (() -> Unit)?,
    top: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).windowInsetsPadding(WindowInsets.ime)) {
        top()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextAction("Back", onBack, tag = "setup-back", color = Room.Ink)
            Spacer(Modifier.weight(1f))
            if (progress != null) ProgressDots(progress, 3)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp, top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ProgressDots(current: Int, of: Int) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Step ${current + 1} of $of" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(of) { i ->
            Box(Modifier.size(width = if (i == current) 18.dp else 6.dp, height = 6.dp).background(if (i <= current) Room.Ink else Room.Faint, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun Headline(kicker: String?, title: String, body: String?) {
    if (kicker != null) BasicText(kicker.uppercase(), style = Room.LabelQuiet.copy(fontSize = 10.sp, letterSpacing = 1.2.sp))
    BasicText(title, style = Room.Title.copy(fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.semantics { heading() })
    if (body != null) BasicText(body, style = Room.LabelQuiet.copy(fontSize = 13.sp, lineHeight = 18.sp))
}

/** The full-width primary button of a step. */
@Composable
private fun BigButton(text: String, onClick: () -> Unit, tag: String, enabled: Boolean = true, filled: Boolean = true, leading: (DrawScope.() -> Unit)? = null) {
    val tick = rememberTick()
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(if (filled) Room.Accent else Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
            .border(1.dp, if (filled) Color.Transparent else Room.Ink.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { tick(); onClick() }
            .testTag(tag)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Canvas(Modifier.size(18.dp)) { leading() }
        if (leading != null) Spacer(Modifier.width(10.dp))
        BasicText(text, style = Room.Title.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else Room.Ink))
    }
}

@Composable
private fun QuietLink(text: String, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = Room.LabelQuiet.copy(fontSize = 13.sp),
        modifier = modifier
            .testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .touchHeight()
            .padding(horizontal = 4.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Sign-in

@Composable
private fun LoginStep(state: RoomState, actions: RoomActions, onConnected: () -> Unit, onOffline: () -> Unit) {
    val session = state.soulseekSession
    var id by rememberSaveable(state.soulseekSavedUsername) { mutableStateOf(state.soulseekSavedUsername.orEmpty()) }
    // Passwords never go into saved instance state.
    var password by remember { mutableStateOf("") }
    var info by remember { mutableStateOf(false) }
    val busy = session.state == SoulseekConnection.CONNECTING || session.state == SoulseekConnection.RECONNECTING
    // Move on only when a sign-in made here succeeds, never just because an account is already
    // connected (Back from the next step must be able to land here).
    var wasConnected by remember { mutableStateOf(session.connected) }
    LaunchedEffect(session.connected) {
        if (session.connected && !wasConnected) onConnected()
        wasConnected = session.connected
    }
    val connect = {
        actions.clearSoulseekError()
        // Always remembered: this is an app on your phone, you stay signed in (Android Keystore).
        actions.signInSoulseek(id.trim(), password, true)
        password = ""
    }
    // With the keyboard up there is no room for the collage and the headline: they'd slide up
    // under each other. The records fade away and the title shrinks to the wordmark.
    @OptIn(ExperimentalLayoutApi::class)
    val typing = WindowInsets.isImeVisible
    val collage by androidx.compose.animation.core.animateFloatAsState(if (typing) 0f else 1f, label = "collage")
    StepFrame(progress = null, onBack = null, top = {
        if (collage > 0f) RecordCollage(Modifier.fillMaxWidth().fillMaxHeight(0.42f).alpha(collage))
    }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!typing) AppMark()
            Canvas(Modifier.size(width = 150.dp, height = 22.dp).semantics { contentDescription = "Runout" }) {
                drawDotWordmark("RUNOUT", Offset(size.width / 2f, size.height / 2f), dot = size.height / 7.2f, shown = 1f)
            }
            if (!typing) BasicText(
                "Every record you own.\nOne room to play them.",
                style = Room.Title.copy(fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(4.dp))
        if (session.connected) {
            BasicText("Signed in to Soulseek as ${session.username}.", style = Room.Label)
            BigButton("Continue", onConnected, "setup-continue")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(id, { id = it.take(30) }, "Soulseek ID") }
                InfoButton { info = true }
            }
            Field(password, { password = it.take(128) }, "Password", secret = true, onSubmit = { if (SoulseekPolicy.validUsername(id.trim()) && password.isNotEmpty() && !busy) connect() })
            if (id.isNotEmpty() && !SoulseekPolicy.validUsername(id.trim())) {
                BasicText("Use 1–30 printable characters.", style = Room.LabelQuiet.copy(color = Room.Danger))
            }
            (session.error ?: state.soulseekError)?.let { BasicText(it, style = Room.Label.copy(color = Room.Danger), modifier = Modifier.testTag("soulseek-error")) }
            BigButton(if (busy) "Connecting…" else "Connect", connect, "submit-soulseek", enabled = !busy && SoulseekPolicy.validUsername(id.trim()) && password.isNotEmpty())
            if (state.soulseekSavedUsername != null && !busy) {
                QuietLink("Sign in again as ${state.soulseekSavedUsername}", actions::signInSavedSoulseek, "setup-saved", Modifier.align(Alignment.CenterHorizontally))
            }
        }
        QuietLink("Skip, listen offline", onOffline, "setup-offline", Modifier.align(Alignment.CenterHorizontally))
    }
    if (info) SoulseekIdInfo { info = false }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    Box(
        Modifier
            .testTag("soulseek-id-info")
            .semantics { contentDescription = "How Soulseek IDs work" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(20.dp).border(1.2.dp, Room.Quiet, CircleShape), contentAlignment = Alignment.Center) {
            BasicText("i", style = Room.Label.copy(fontWeight = FontWeight.SemiBold, color = Room.Quiet))
        }
    }
}

/** What the ⓘ says: Soulseek has no sign-up page; a new ID is made by signing in with it. */
@Composable
private fun SoulseekIdInfo(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.16f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .padding(12.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(22.dp)
                .testTag("soulseek-id-help"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText("How a Soulseek ID works", style = Room.Title.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Soulseek has no sign-up page. Your ID is created the first time you sign in with it.", style = Room.Label)
            BasicText("New here? Type the ID you'd like and a password, then Connect. If Soulseek says the password is wrong, that ID already belongs to someone: try another one.", style = Room.Label)
            BasicText("Keep your password somewhere safe: Soulseek can't reset it. Signing in here signs out any other app using the same ID.", style = Room.LabelQuiet)
            BigButton("Got it", onDismiss, "soulseek-id-help-close", filled = false)
        }
    }
}

/** The app mark: a small record with a soft-orange label. */
@Composable
private fun AppMark() {
    Canvas(Modifier.size(58.dp)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.12f), r, c + Offset(0f, r * 0.08f))
        drawCircle(Room.Ink, r, c)
        for (i in 1..5) drawCircle(Color.White.copy(alpha = 0.08f), r * (0.5f + i * 0.09f), c, style = Stroke(1f))
        drawCircle(Brush.linearGradient(listOf(Room.SignalLight, Room.Signal), start = c - Offset(r, r) * 0.3f, end = c + Offset(r, r) * 0.3f), r * 0.36f, c)
        drawCircle(Color.White, r * 0.05f, c)
    }
}

/**
 * The hero at the top of the sign-in: records and sleeves scattered like a crate tipped over,
 * fading into the page (the music-app welcome, in the room's colours).
 */
@Composable
private fun RecordCollage(modifier: Modifier) {
    val palette = listOf(
        listOf(Color(0xFFE85D9C), Color(0xFF6A49D8)), listOf(Color(0xFF7FD3C1), Color(0xFF2F6E8E)),
        listOf(Color(0xFFFFC27A), Color(0xFFE0603C)), listOf(Color(0xFFB9C7FF), Color(0xFF4256C9)),
        listOf(Color(0xFFF7E3A1), Color(0xFF9C7B2E)), listOf(Color(0xFFE6B8FF), Color(0xFF8B3FB0)),
    )
    Canvas(modifier) {
        val rnd = Random(17)
        val cols = 4
        val cell = size.width / cols
        for (row in 0 until 4) for (col in 0 until cols) {
            val i = row * cols + col
            val c = Offset(col * cell + cell * (0.5f + (rnd.nextFloat() - 0.5f) * 0.35f) + (if (row % 2 == 1) cell * 0.5f else 0f) - cell * 0.25f, row * cell * 0.82f + cell * 0.35f)
            val colors = palette[i % palette.size]
            val r = cell * (0.36f + rnd.nextFloat() * 0.08f)
            if (i % 3 == 1) {
                // A sleeve.
                drawRect(Color.Black.copy(alpha = 0.08f), topLeft = c - Offset(r, r) + Offset(0f, r * 0.08f), size = Size(r * 2, r * 2))
                drawRect(Brush.linearGradient(colors, start = c - Offset(r, r), end = c + Offset(r, r)), topLeft = c - Offset(r, r), size = Size(r * 2, r * 2))
            } else {
                // A record, label in the sleeve's colours.
                drawCircle(Color.Black.copy(alpha = 0.10f), r, c + Offset(0f, r * 0.06f))
                drawCircle(Color(0xFF17181D), r, c)
                for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.06f), r * (0.5f + k * 0.1f), c, style = Stroke(1f))
                drawCircle(Brush.linearGradient(colors, start = c - Offset(r, r) * 0.3f, end = c + Offset(r, r) * 0.3f), r * 0.34f, c)
                drawCircle(Color.White, r * 0.04f, c)
            }
        }
        // Fade into the page.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Room.Background.copy(alpha = 0.6f), Room.Background), startY = size.height * 0.35f, endY = size.height))
    }
}

// ---------------------------------------------------------------------------------------------
// Steps

@Composable
private fun OfflineStep(state: RoomState, actions: RoomActions, onDone: () -> Unit, onBack: () -> Unit) {
    StepFrame(progress = null, onBack = onBack) {
        Headline("Offline / No account needed", "Bring your own records.", "Choose a folder of FLAC albums. You can connect Soulseek later from Settings › Soulseek.")
        BigButton(if (state.hasFolder) "Change music folder" else "Choose music folder", actions::pickFolder, "setup-local-folder")
        if (state.hasFolder) BasicText("Music folder selected. Your records will appear in the crate.", style = Room.LabelQuiet)
        BigButton(if (state.hasFolder) "Start listening" else "Skip for now", onDone, "setup-finish", filled = false)
    }
}

@Composable
private fun DownloadsStep(state: RoomState, actions: RoomActions, onDone: () -> Unit, onBack: () -> Unit) {
    // Choosing the folder is the answer: as soon as a new one comes back, move on.
    val atEntry = remember { state.downloadFolder }
    LaunchedEffect(state.downloadFolder) {
        if (state.downloadFolder != null && state.downloadFolder != atEntry) onDone()
    }
    StepFrame(progress = 0, onBack = onBack) {
        Headline("Keep your records", "Where should downloads go?", "Finished FLAC downloads land in this folder, play in other apps too, and stay if you uninstall Runout.")
        BigButton(if (state.downloadFolder == null) "Choose download folder" else "Change download folder", actions::pickDownloadFolder, "setup-download-folder")
        QuietLink(if (state.downloadFolder != null) "Keep “${state.downloadFolder}”" else "Choose later", onDone, "setup-later", Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ShareStep(state: RoomState, actions: RoomActions, onDone: () -> Unit, onBack: () -> Unit) {
    val atEntry = remember { state.sharedFolder }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(state.sharedFolder, asked) {
        if (asked && state.sharedFolder != null && state.sharedFolder != atEntry) {
            actions.setSoulseekSharing(true)
            onDone()
        }
    }
    StepFrame(progress = 1, onBack = onBack) {
        Headline("Give something back", "Share a folder?", null)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Canvas(Modifier.size(22.dp).padding(top = 2.dp)) { drawCaution(Room.Signal) }
            BasicText(
                "The folder you choose becomes public: anyone on Soulseek can browse it and download its FLAC files. Nothing else on your phone is shared.",
                style = Room.Label.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
        BigButton("Share a folder", { asked = true; actions.pickSharedFolder() }, "setup-share", leading = { drawCaution(Color.White) })
        QuietLink("Skip", { actions.setSoulseekSharing(false); onDone() }, "setup-share-skip", Modifier.align(Alignment.CenterHorizontally))
    }
}

/** A warning triangle with an exclamation mark. */
private fun DrawScope.drawCaution(color: Color) {
    val w = size.width
    val p = Path().apply { moveTo(w / 2f, w * 0.06f); lineTo(w * 0.96f, w * 0.9f); lineTo(w * 0.04f, w * 0.9f); close() }
    drawPath(p, color, style = Stroke(w * 0.09f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(color, Offset(w / 2f, w * 0.38f), Offset(w / 2f, w * 0.62f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
    drawCircle(color, w * 0.055f, Offset(w / 2f, w * 0.76f))
}

@Composable
private fun RhythmStep(state: RoomState, actions: RoomActions, onDone: () -> Unit, onBack: () -> Unit) {
    val connected = state.soulseekSession.connected
    StepFrame(progress = 2, onBack = onBack) {
        Headline("Choose your rhythm", "When should you stay online?", "Both reconnect after a network blip. Your own records always play offline.")
        RhythmCard(
            "While using Runout", "Less battery and data. Messages arrive when you come back.",
            selected = !state.soulseekBackgroundConsent, enabled = connected, tag = "rhythm:app",
        ) { actions.setSoulseekStayOnline(false); onDone() }
        RhythmCard(
            "Stay online", "Messages and sharing keep going with the screen off, with a Disconnect notification.",
            selected = state.soulseekBackgroundConsent, enabled = connected, tag = "rhythm:background",
        ) { actions.setSoulseekStayOnline(true); onDone() }
        if (!connected) ConnectionNotice(state, actions) { }
    }
}

/** A choice you tap as a whole; the selected one is tinted, there is no button inside it. */
@Composable
private fun RhythmCard(title: String, detail: String, selected: Boolean, enabled: Boolean, tag: String, onPick: () -> Unit) {
    val tick = rememberTick()
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (selected) Room.Accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Room.Accent else Room.Ink.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { tick(); onPick() }
            .semantics { if (selected) contentDescription = "$title, selected" }
            .testTag(tag)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(title, style = Room.Title.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Room.Accent else Room.Ink))
        BasicText(detail, style = Room.LabelQuiet.copy(fontSize = 12.5.sp, lineHeight = 17.sp))
    }
}

/** The last screen: a greeting that pops in, then the room. */
@Composable
private fun WelcomeStep(name: String?, onFinish: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val hold = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinish)
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
        // Held on screen by an animation rather than a delay, so it runs on the frame clock.
        hold.animateTo(1f, tween(650))
        finish()
    }
    Box(Modifier.fillMaxSize().testTag("setup-welcome"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer {
                val s = 0.82f + 0.18f * pop.value
                scaleX = s; scaleY = s
                alpha = pop.value.coerceIn(0f, 1f)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppMark()
            BasicText(if (name != null) "Welcome in, $name." else "Welcome in.", style = Room.Title.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold))
            BasicText("Make yourself at home.", style = Room.LabelQuiet.copy(fontSize = 14.sp))
        }
    }
}
