package app.runout.ui

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
import app.runout.pressing.PressingDraft
import app.runout.pressing.PressingRepository
import app.runout.pressing.SleeveArtist
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

/** The room's glass sheets. Those [inStrip] get a pill in the room; Downloads opens from the ↓ count. */
enum class Panel(val title: String, val nav: String, val inStrip: Boolean = true) {
    DIG("Dig", "Dig"),
    DROPS("Friday Drops", "Drops"),
    FRIENDS("Friends", "Friends"),
    PRESSING("Pressing Plant", "Pressing Plant"),
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
            "Nothing touches the signal: no EQ, ReplayGain, crackle or platter wind-down. Bit-perfect to a USB DAC on Android 14 and later.",
            s.pure,
            { on -> actions.updateSound { it.copy(pure = on) } },
        )
        ToggleRow(
            "Hi-res output",
            "Plays each file at its own sample rate in 32-bit float, up to 24-bit/192 kHz, instead of folding it down to 16-bit.",
            s.hiResOutput,
            { on -> actions.updateSound { it.copy(hiResOutput = on) } },
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
        Chips(
            options = Presets.all.map { it.first },
            selected = s.presetName.takeIf { !s.useParametric },
            label = { it },
            onSelect = actions::applyPreset,
            enabled = dsp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
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
        BasicText("ReplayGain", style = Room.Title.copy(fontSize = 13.sp))
        BasicText("Evens out loudness between records using the gain tags in your files.", style = Room.LabelQuiet)
        Chips(
            ReplayGainMode.entries.toList(), s.replayGain, { it.label },
            { m -> actions.updateSound { it.copy(replayGain = m) } },
            enabled = dsp, modifier = Modifier.padding(vertical = 6.dp),
        )
        ToggleRow("Limiter", "A look-ahead limiter catches peaks above −0.3 dBFS.", s.limiter, { on -> actions.updateSound { it.copy(limiter = on) } }, enabled = dsp)
        ToggleRow("Warmth", "A gentle low-shelf lift and a softer top end, like a warm cartridge.", s.warmth, { on -> actions.updateSound { it.copy(warmth = on) } }, enabled = dsp)
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
                BasicText("Week of ${w.opens.dayOfMonth} ${w.opens.month.name.lowercase().replaceFirstChar { it.uppercase() }}", style = Room.Label.copy(fontWeight = FontWeight.Medium))
                BasicText(
                    if (left > 0) "$left of ${DropsRepository.CLAIMS_PER_WEEK} claims left this week" else "You've claimed all ${DropsRepository.CLAIMS_PER_WEEK} this week · next drop Friday ${w.closes.dayOfMonth}",
                    style = Room.LabelQuiet,
                )
                Spacer(Modifier.height(8.dp))
                w.items.forEach { item ->
                    DropRow(item, isClaimed = item.identifier in claimed, progress = claiming[item.identifier], canClaim = left > 0 && signedIn, actions = actions)
                }
            }
        }
        BasicText(
            "Every Friday, ten Creative Commons albums from Internet Archive's netlabel collection, the same ten for everyone. Claim up to three FLAC albums into your chosen music folder. Albums without FLAC cannot be claimed and use no allowance.",
            style = Room.LabelQuiet,
            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun DropRow(item: DropItem, isClaimed: Boolean, progress: Float?, canClaim: Boolean, actions: RoomActions) {
    val cover by produceState<ImageBitmap?>(null, item.identifier) {
        value = actions.dropCover(item)?.let { CoverCache.load(it) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("drop:${item.identifier}"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(2.dp)).background(fallbackColorFor(item.title))) {
            cover?.let { Image(it, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(item.title, style = Room.Title.copy(fontSize = 13.sp), maxLines = 1)
            BasicText(listOfNotNull(item.creator, item.year?.toString()).joinToString(" · "), style = Room.LabelQuiet, maxLines = 1)
            Licences.label(item.license)?.let { BasicText(it, style = Room.LabelQuiet.copy(fontSize = 9.sp)) }
        }
        Spacer(Modifier.width(8.dp))
        when {
            isClaimed -> BasicText("In your crate", style = Room.LabelQuiet)
            progress != null -> Column(Modifier.width(70.dp), horizontalAlignment = Alignment.End) {
                BasicText("${(progress * 100).roundToInt()}%", style = Room.LabelQuiet)
                ProgressLine(progress)
            }
            else -> Pill("Claim", { actions.claim(item) }, active = canClaim, enabled = canClaim)
        }
    }
}

private fun fallbackColorFor(title: String): Color {
    val palette = listOf(0xFFE8E4FF, 0xFFDDEBFF, 0xFFFFE3EC, 0xFFE2F6EC, 0xFFFFF1D6, 0xFFE9E9EE)
    return Color(palette[abs(title.hashCode()) % palette.size])
}

// ---------------------------------------------------------------------------------------------
// Pressing Plant

private val LABEL_COLOURS = Profile.ACCENTS + listOf(0xFFFFD166.toInt(), 0xFF9AD7FF.toInt())

@Composable
fun PressingPanel(shelf: List<Album>, profile: Profile, actions: RoomActions, onDraftChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf(0) }
    var disc by rememberSaveable { mutableStateOf(DiscStyle.COLOUR) }
    var labelColour by rememberSaveable { mutableStateOf(profile.accent) }
    var owned by rememberSaveable { mutableStateOf(false) }
    var picture by remember { mutableStateOf<Uri?>(null) }
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<Pair<Album, Track>>() }
    var pressing by remember { mutableStateOf(false) }
    val changed = title.isNotEmpty() || artist.isNotEmpty() || notes.isNotEmpty() || picked.isNotEmpty() ||
        picture != null || pattern != 0 || disc != DiscStyle.COLOUR || labelColour != profile.accent || owned
    LaunchedEffect(changed) { onDraftChanged(changed) }

    val pictureBitmap by produceState<ImageBitmap?>(null, picture) {
        value = picture?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val generated = remember(pattern, title, artist, labelColour) {
        SleeveArtist.render(pattern, title.ifBlank { "Untitled" }, artist.ifBlank { profile.name }, labelColour, size = 320).asImageBitmap()
    }
    val sleeve = pictureBitmap ?: generated
    val totalMs = picked.sumOf { it.second.durationMs }

    Column(Modifier.fillMaxWidth().testTag("panel:pressing")) {
        // Preview: the sleeve and the record it'll press.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(sleeve, "Sleeve preview", Modifier.size(104.dp).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            val preview = Album(
                id = "preview-$title", title = title, artist = artist, year = null, folderUri = "", tracks = emptyList(),
                quality = PressingRepository.qualityFor(picked.map { it.first }.distinctBy { it.id }),
                disc = disc, labelColor = labelColour,
            )
            val look = discLookFor(preview)
            val bounds = remember(picked.size, totalMs) {
                var acc = 0L
                picked.dropLast(1).map { acc += it.second.durationMs; acc.toFloat() / totalMs.coerceAtLeast(1) }
            }
            Canvas(Modifier.size(104.dp)) {
                drawRecord(
                    center = Offset(size.width / 2, size.height / 2), radius = size.minDimension / 2, rotation = 0f,
                    look = look, label = sleeve, labelFallback = Color(labelColour), trackBoundaries = bounds,
                    sideText = null, qualityText = null,
                )
            }
        }

        SectionTitle("Record")
        Field(title, { title = it }, "Title")
        Field(artist, { artist = it }, "Artist (you, a band, a mixtape name)")
        Field(notes, { notes = it }, "Liner notes (optional)", singleLine = false)

        SectionTitle("Sleeve")
        Chips(SleeveArtist.PATTERN_NAMES.indices.toList(), if (picture == null) pattern else null, { SleeveArtist.PATTERN_NAMES[it] }, { pattern = it; picture = null })
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(if (picture == null) "Use a picture…" else "Change picture", { actions.pickImage { uri -> if (uri != null) picture = uri } })
            if (picture != null) Pill("Remove picture", { picture = null })
        }

        SectionTitle("Vinyl")
        Chips(DiscStyle.entries.toList(), disc, { it.label }, { disc = it })
        ColourDots(LABEL_COLOURS, labelColour, { labelColour = it }, Modifier.padding(top = 8.dp))

        SectionTitle("Tracks · ${picked.size}" + if (totalMs > 0) " · ${formatLength(totalMs)}" else "")
        picked.forEachIndexed { i, (album, t) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("${i + 1}", style = Room.LabelQuiet, modifier = Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(t.title, style = Room.Label, maxLines = 1)
                    BasicText(album.artist, style = Room.LabelQuiet.copy(fontSize = 9.sp), maxLines = 1)
                }
                BasicText("Remove", style = Room.LabelQuiet, modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { picked.removeAt(i) })
            }
        }
        Field(search, { search = it }, "Find tracks on your shelf")
        val playable = remember(shelf) { shelf.filter { a -> a.tracks.any { it.uri.isNotBlank() } } }
        val matches = playable.filter { a ->
            search.isBlank() || a.title.contains(search, true) || a.artist.contains(search, true) || a.tracks.any { it.title.contains(search, true) }
        }.take(24)
        if (playable.isEmpty()) BasicText("Your crate is empty. Choose a music folder first, then pick tracks here.", style = Room.LabelQuiet)
        matches.forEach { a ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = if (expanded == a.id) null else a.id }
                        .padding(vertical = 5.dp)
                        .testTag("pick-album:${a.title}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SleeveArt(a, Modifier.size(30.dp), showSticker = false)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(a.title, style = Room.Label, maxLines = 1)
                        BasicText("${a.artist} · ${a.tracks.size} tracks", style = Room.LabelQuiet.copy(fontSize = 9.sp), maxLines = 1)
                    }
                    BasicText(if (expanded == a.id) "–" else "+", style = Room.Title)
                }
                if (expanded == a.id || (search.isNotBlank() && a.tracks.any { it.title.contains(search, true) })) {
                    a.tracks.filter { it.uri.isNotBlank() }.forEach { t ->
                        val on = picked.any { it.second.id == t.id }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    val at = picked.indexOfFirst { it.second.id == t.id }
                                    if (at >= 0) picked.removeAt(at) else if (picked.size < MAX_PRESSING_TRACKS) picked.add(a to t)
                                }
                                .padding(start = 38.dp, top = 4.dp, bottom = 4.dp)
                                .testTag("pick-track:${t.title}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).border(1.dp, if (on) Room.Accent else Room.Faint, CircleShape).padding(3.dp).background(if (on) Room.Accent else Color.Transparent, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            BasicText(t.title, style = Room.Label, maxLines = 1, modifier = Modifier.weight(1f))
                            BasicText(formatLength(t.durationMs), style = Room.LabelQuiet)
                        }
                    }
                }
            }
        }

        SectionTitle("Sharing")
        ToggleRow(
            "I made this music",
            "Then its audio travels when you send the record. Leave it off for mixtapes of other people's music: friends get the sleeve, the vinyl and the track list.",
            owned, { owned = it },
        )
        Spacer(Modifier.height(10.dp))
        val ready = title.isNotBlank() && picked.isNotEmpty() && !pressing
        Pill(
            if (ready) "Press it" else if (title.isBlank()) "Give it a title" else "Add some tracks",
            {
                val submitted = PressingDraft(
                        title = title, artist = artist.ifBlank { profile.name }, tracks = picked.map { it.second },
                        quality = PressingRepository.qualityFor(picked.map { it.first }.distinctBy { it.id }),
                        disc = disc, labelColor = labelColour, coverImage = picture, pattern = pattern, owned = owned, notes = notes,
                    )
                val originalArtist = artist
                pressing = true
                actions.press(submitted) { saved ->
                    pressing = false
                    // A late result must not erase edits made while this press was in flight.
                    if (saved && title == submitted.title && artist == originalArtist && notes == submitted.notes &&
                        picked.map { it.second } == submitted.tracks && picture == submitted.coverImage &&
                        pattern == submitted.pattern && disc == submitted.disc && labelColour == submitted.labelColor && owned == submitted.owned) {
                        title = ""; artist = ""; notes = ""; picked.clear(); picture = null
                        pattern = 0; disc = DiscStyle.COLOUR; labelColour = profile.accent; owned = false
                    }
                }
            },
            active = ready, enabled = ready,
        )
        Spacer(Modifier.height(14.dp))
    }
}

private const val MAX_PRESSING_TRACKS = 40

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
