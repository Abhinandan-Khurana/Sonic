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
