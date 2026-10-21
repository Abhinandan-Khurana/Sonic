package app.runout.ui

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.runtime.DisposableEffect

import androidx.compose.ui.draw.shadow

import androidx.compose.foundation.layout.aspectRatio

import androidx.compose.foundation.layout.FlowRow

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.drops.DropItem
import app.runout.drops.DropState
import app.runout.drops.DropsRepository
import app.runout.model.Album
import app.runout.model.DiscStyle
import app.runout.model.Licences
import app.runout.model.Source
import app.runout.model.Track
import app.runout.social.Friend
import app.runout.social.Profile
import app.runout.social.ShelfItem
import app.runout.sound.BitPerfect
import app.runout.sound.OutputStatus
import app.runout.sound.Presets
import app.runout.sound.ReplayGainMode
import app.runout.sound.SoundSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The room's places. Those [inStrip] get a tab; Downloads opens from the ↓ count. [fullPage] ones
 * take the whole screen; the rest slide up as glass sheets.
 */
enum class Panel(val title: String, val nav: String, val inStrip: Boolean = true, val fullPage: Boolean = false) {
    DIG("Dig", "Dig", fullPage = true),
    DROPS("Friday Drops", "Drops"),
    FRIENDS("Friends", "Friends", fullPage = true),
    SETTINGS("Settings", "Settings"),
    DOWNLOADS("Downloads", "Downloads", inStrip = false),
}

/** The sections of the Settings sheet. */
enum class SettingsSection(val label: String) { SOUND("Sound"), ACCOUNT("Soulseek"), LIBRARY("Library"), ABOUT("About") }

// ---------------------------------------------------------------------------------------------
// Sound

@Composable
fun SoundPanel(s: SoundSettings, output: OutputStatus, actions: RoomActions) {
    Column(Modifier.fillMaxWidth().testTag("panel:sound")) {
        BasicText(output.label, style = Room.Label.copy(fontWeight = FontWeight.Medium))
        if (s.pure && output.bitPerfect != BitPerfect.OFF) {
            BasicText(output.bitPerfect.label, style = Room.LabelQuiet)
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow(
            "Pure",
            "The music, untouched.",
            s.pure,
            { on -> actions.updateSound { it.copy(pure = on) } },
            more = "Nothing touches the signal: no EQ, ReplayGain, crackle or platter wind-down. Bit-perfect to a USB DAC on Android 14 and later.",
        )
        ToggleRow(
            "Hi-res output",
            "Each file at its own sample rate.",
            s.hiResOutput,
            { on -> actions.updateSound { it.copy(hiResOutput = on) } },
            more = "Plays each file at its own sample rate in 32-bit float, up to 24-bit/192 kHz, instead of folding it down to 16-bit.",
        )

        val dsp = !s.pure
        SectionTitle("Equaliser")
        ToggleRow(
            "EQ",
            if (s.useParametric) "AutoEq headphone profile · ${s.parametric.size} filters" else "10-band graphic EQ · ${s.presetName}",
            s.eqEnabled,
            { on -> actions.updateSound { it.copy(eqEnabled = on) } },
            enabled = dsp,
        )
        // Presets as a row of small hardware keys; the one in use is lit orange.
        FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Presets.all.map { it.first }.forEach { name ->
                TeKey(name, { actions.applyPreset(name) }, Modifier.testTag("preset:$name"), selected = name == s.presetName && !s.useParametric, enabled = dsp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SoundSettings.GRAPHIC_LABELS.forEachIndexed { i, label ->
                EqFader(
                    label = label,
                    gainDb = s.graphic.getOrElse(i) { 0f },
                    maxDb = SoundSettings.MAX_GAIN_DB,
                    enabled = dsp && !s.useParametric,
                    onChange = { g ->
                        actions.updateSound { cur ->
                            val next = cur.graphic.toMutableList().also { it[i] = g }
                            val named = Presets.all.firstOrNull { it.second == next }?.first ?: "Custom"
                            cur.copy(graphic = next, presetName = named, eqEnabled = true, useParametric = false)
                        }
                    },
                )
            }
        }
        val pre = if (s.eqEnabled) s.preampDb() else 0f
        BasicText(
            if (pre < -0.05f) "Automatic headroom ${"%.1f".format(pre)} dB, so boosted bands never clip" else "No boost, no headroom needed",
            style = Room.LabelQuiet,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Import AutoEq…", actions::importAutoEq, enabled = dsp)
            if (s.useParametric) Pill("Use graphic EQ", { actions.updateSound { it.copy(useParametric = false, presetName = "Custom") } }, enabled = dsp)
        }

        SectionTitle("Loudness")
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("ReplayGain", style = Room.Title.copy(fontSize = 13.sp))
            var why by remember { mutableStateOf(false) }
            InfoDot("ReplayGain") { why = !why }
            if (why) BasicText("Evens out loudness between records using the gain tags in your files.", style = Room.LabelQuiet, modifier = Modifier.padding(start = 4.dp).weight(1f))
        }
        Chips(
            ReplayGainMode.entries.toList(), s.replayGain, { it.label },
            { m -> actions.updateSound { it.copy(replayGain = m) } },
            enabled = dsp, modifier = Modifier.padding(vertical = 6.dp),
        )
        ToggleRow("Limiter", "Catches peaks.", s.limiter, { on -> actions.updateSound { it.copy(limiter = on) } }, enabled = dsp, more = "A look-ahead limiter catches peaks above −0.3 dBFS.")
        ToggleRow("Warmth", "Like a warm cartridge.", s.warmth, { on -> actions.updateSound { it.copy(warmth = on) } }, enabled = dsp, more = "A gentle low-shelf lift and a softer top end.")
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Friends: see People.kt

// ---------------------------------------------------------------------------------------------
// Friday Drops

@Composable
fun DropsPanel(
    drops: DropState,
    claimed: Set<String>,
    claiming: Map<String, Float>,
    actions: RoomActions,
    signedIn: Boolean = false,
    onAccount: () -> Unit = {},
) {
    LaunchedEffect(drops is DropState.Idle) { if (drops is DropState.Idle) actions.refreshDrops() }
    val left = (DropsRepository.CLAIMS_PER_WEEK - claimed.size).coerceAtLeast(0)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().testTag("panel:drops")) {
        if (!signedIn) {
            BasicText("Sign in with Soulseek to download from Internet Archive. Local FLAC playback is always available without an account.", style = Room.LabelQuiet)
            Pill("Sign in with Soulseek", onAccount, modifier = Modifier.padding(vertical = 8.dp))
        }
        when (drops) {
            DropState.Idle, DropState.Loading -> BasicText("Digging through the archive…", style = Room.Label)
            is DropState.Failed -> {
                BasicText(drops.reason, style = Room.Label)
                Spacer(Modifier.height(8.dp))
                Pill("Try again", actions::refreshDrops)
            }
            is DropState.Ready -> {
                val w = drops.week
                val item = w.items.firstOrNull { it.identifier == open }
                if (item != null) {
                    DropPreview(item, isClaimed = item.identifier in claimed, progress = claiming[item.identifier], canClaim = left > 0 && signedIn, actions = actions) { open = null }
                    return@Column
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("Week of ${w.opens.dayOfMonth} ${w.opens.month.name.lowercase().replaceFirstChar { it.uppercase() }}", style = Room.Label.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
                    ClaimLights(left)
                }
                if (left == 0) BasicText("All claimed this week · next drop Friday ${w.closes.dayOfMonth}", style = Room.LabelQuiet)
                Spacer(Modifier.height(10.dp))
                // The shelf: the ten drops as sleeves, two to a row, like a record-shop rack.
                w.items.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { d ->
                            DropSleeve(d, d.identifier in claimed, claiming[d.identifier], actions, Modifier.weight(1f)) { open = d.identifier }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        BasicText(
            "Ten Creative Commons albums from the Internet Archive every Friday, the same for everyone. Claim three a week.",
            style = Room.LabelQuiet,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
    }
}

/** Claims left this week as three small lights: lit orange while unused, dark once spent. */
@Composable
private fun ClaimLights(left: Int) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$left of ${DropsRepository.CLAIMS_PER_WEEK} claims left" }.testTag("claim-lights"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(DropsRepository.CLAIMS_PER_WEEK) { i ->
            val lit = i < left
            Canvas(Modifier.size(12.dp)) {
                if (lit) drawCircle(Brush.radialGradient(listOf(Room.Signal.copy(alpha = 0.45f), Color.Transparent)), size.minDimension / 2f)
                drawCircle(if (lit) Room.Signal else Room.Ink.copy(alpha = 0.18f), size.minDimension * 0.3f)
                drawCircle(Color.White.copy(alpha = if (lit) 0.6f else 0.2f), size.minDimension * 0.1f, center - Offset(size.minDimension * 0.08f, size.minDimension * 0.08f))
            }
        }
        BasicText("$left left", style = Room.LabelQuiet.copy(fontSize = 10.sp))
    }
}

@Composable
private fun DropSleeve(item: DropItem, isClaimed: Boolean, progress: Float?, actions: RoomActions, modifier: Modifier, onOpen: () -> Unit) {
    val cover by produceState<ImageBitmap?>(null, item.identifier) {
        value = actions.dropCover(item)?.let { CoverCache.load(it) }
    }
    val tick = rememberTick()
    Column(
        modifier.testTag("drop:${item.identifier}").clickable(role = androidx.compose.ui.semantics.Role.Button) { tick(); onOpen() },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).shadow(6.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(fallbackColorFor(item.title))) {
            cover?.let { Image(it, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (isClaimed) BasicText("In your crate", style = Room.Label.copy(color = Color.White, fontSize = 9.sp), modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Room.Ink.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp))
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(6.dp))
        }
        BasicText(item.title, style = Room.Title.copy(fontSize = 13.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        BasicText(listOfNotNull(item.creator, item.year?.toString()).joinToString(" · "), style = Room.LabelQuiet, maxLines = 1)
    }
}

/** A drop up close: the sleeve, its tracks with a short listen each, and Claim. */
@Composable
private fun DropPreview(item: DropItem, isClaimed: Boolean, progress: Float?, canClaim: Boolean, actions: RoomActions, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { actions.listen(null); onBack() }
    DisposableEffect(item.identifier) { onDispose { actions.listen(null) } }
    val cover by produceState<ImageBitmap?>(null, item.identifier) { value = actions.dropCover(item)?.let { CoverCache.load(it) } }
    // null while loading; then the list, or null again if the archive couldn't be reached.
    var loaded by remember(item.identifier) { mutableStateOf(false) }
    val tracks by produceState<List<app.runout.drops.DropTrack>?>(null, item.identifier) { value = actions.dropTracks(item); loaded = true }
    val noFlac = loaded && tracks?.isEmpty() == true
    var playing by remember { mutableStateOf<String?>(null) }
    // The listen stops by itself after thirty seconds; mirror that here.
    LaunchedEffect(playing) { if (playing != null) { kotlinx.coroutines.delay(30_000); playing = null } }
    Column(Modifier.fillMaxWidth().testTag("drop-preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextAction("‹ All drops", { actions.listen(null); onBack() }, tag = "drops-back", color = Room.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp).shadow(6.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(fallbackColorFor(item.title))) {
                cover?.let { Image(it, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(item.title, style = Room.Title.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 3)
                BasicText(listOfNotNull(item.creator, item.year?.toString()).joinToString(" · "), style = Room.LabelQuiet)
                Licences.label(item.license)?.let { SpecTag(it) }
            }
        }
        when {
            isClaimed -> BasicText("In your crate", style = Room.Label.copy(fontWeight = FontWeight.Medium))
            progress != null -> Column { BasicText("Claiming · ${(progress * 100).roundToInt()}%", style = Room.LabelQuiet); ProgressLine(progress) }
            // An album with no FLAC can't be kept, so there's nothing to claim.
            else -> Pill(if (noFlac) "No FLAC to claim" else "Claim", { actions.listen(null); actions.claim(item) }, Modifier.fillMaxWidth().testTag("claim:${item.identifier}"), active = canClaim && !noFlac, enabled = canClaim && !noFlac)
        }
        SectionTitle("Tracks")
        val list = tracks
        when {
            list == null && !loaded -> BasicText("Listing the tracks…", style = Room.LabelQuiet)
            list == null -> BasicText("Couldn't reach the archive for the track list. You can still claim it.", style = Room.LabelQuiet)
            list.isEmpty() -> BasicText("This album has no FLAC files, so it can't be claimed. It uses no allowance.", style = Room.LabelQuiet)
            else -> list.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText((i + 1).toString().padStart(2, '0'), style = Room.LabelQuiet, modifier = Modifier.width(22.dp))
                    BasicText(t.title, style = Room.Title.copy(fontSize = 13.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val on = playing == t.url
                    TeKey(null, {
                        if (on) { actions.listen(null); playing = null } else { actions.listen(t.url); playing = t.url }
                    }, Modifier.testTag("listen:$i"), selected = on, description = if (on) "Stop listening" else "Listen to ${t.title}") { c ->
                        val w = size.width
                        if (on) drawRect(c, topLeft = Offset(w * 0.25f, w * 0.25f), size = androidx.compose.ui.geometry.Size(w * 0.5f, w * 0.5f))
                        else drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.3f, w * 0.18f); lineTo(w * 0.82f, w * 0.5f); lineTo(w * 0.3f, w * 0.82f); close() }, c)
                    }
                }
            }
        }
        BasicText("Listening plays thirty seconds from the archive. It doesn't use a claim.", style = Room.LabelQuiet.copy(fontSize = 10.sp), modifier = Modifier.padding(top = 6.dp))
    }
}

private fun fallbackColorFor(title: String): Color {
    val palette = listOf(0xFFE8E4FF, 0xFFDDEBFF, 0xFFFFE3EC, 0xFFE2F6EC, 0xFFFFF1D6, 0xFFE9E9EE)
    return Color(palette[abs(title.hashCode()) % palette.size])
}



fun formatLength(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Where a record came from, for the crate caption. */
fun provenance(album: Album): String? = when (album.source) {
    Source.RECEIVED -> "Sent by ${album.from ?: "a friend"}"
    Source.DROP -> listOfNotNull("Friday Drop", Licences.label(album.license)).joinToString(" · ")
    Source.SOULSEEK -> "Soulseek · ${album.from ?: "FLAC download"}"
    Source.PRESSING -> "Your pressing" + (album.disc?.let { " · ${it.label.lowercase()} vinyl" } ?: "")
    Source.FOLDER -> Licences.label(album.license)
}
