package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.BuildConfig

/** "0.6", as the boards write it: the version without a trailing ".0". */
private val VERSION = BuildConfig.VERSION_NAME.removeSuffix(".0")

/**
 * Settings, as the TE · Settings boards. [section] null is the list: four numbered rows standing at
 * the foot of the page, above the faint RUNOUT wordmark and the build line. Each row opens its own
 * page (the head shows its name), which starts with "← settings".
 */
@Composable
fun SettingsPanel(
    state: RoomState,
    actions: RoomActions,
    section: SettingsSection?,
    onSection: (SettingsSection?) -> Unit,
    onDownloads: () -> Unit,
    onSetup: () -> Unit,
    onOffline: () -> Unit,
) {
    if (section == null) {
        Column(
            Modifier.fillMaxWidth().fillPageHeight().testTag("panel:settings"),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.Bottom),
        ) {
            Column(Modifier.fillMaxWidth()) {
                SettingsSection.entries.forEachIndexed { i, s ->
                    TeRow("%02d".format(i + 1), s.label.lowercase(), summary(s, state), { onSection(s) }, Modifier.testTag("settings:${s.name}"))
                }
            }
            // The wordmark set huge and faint underneath, like a manual's back page.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                BasicText("RUNOUT", style = Room.Display.copy(fontSize = 66.sp, color = Room.HairlineSoft), maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                BasicText("$VERSION · white / teal · flac only", style = Room.LabelQuiet)
            }
        }
        return
    }
    BackHandler { onSection(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (section == SettingsSection.ABOUT) Modifier.fillPageHeight() else Modifier)
            .testTag("panel:settings"),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        PageBackLink("← settings", "settings-back") { onSection(null) }
        when (section) {
            SettingsSection.SOUND -> SoundPanel(state.sound, state.output, state.crackleOn, actions)
            SettingsSection.ACCOUNT -> {
                ConnectionNotice(state, actions, onSetup)
                SoulseekAccountPanel(state, actions, onOffline, onSignIn = onSetup)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    TeButton("downloads", onDownloads, Modifier.testTag("settings-downloads"), kind = KeyKind.OUTLINE)
                    TeButton("review soulseek setup", onSetup, Modifier.testTag("settings-setup"), kind = KeyKind.OUTLINE)
                }
            }
            SettingsSection.LIBRARY -> LibraryPage(state, actions)
            SettingsSection.ABOUT -> AboutPage()
        }
    }
}

private fun summary(s: SettingsSection, state: RoomState): String = when (s) {
    SettingsSection.SOUND -> {
        val eq = when {
            !state.sound.eqEnabled -> "eq off"
            state.sound.useParametric -> "autoeq"
            else -> "eq ${state.sound.presetName.lowercase()}"
        }
        "$eq · pure ${if (state.sound.pure) "on" else "off"}"
    }
    SettingsSection.ACCOUNT ->
        if (state.soulseekSession.hasAccount) "${state.soulseekSession.username} · ${connectionLabel(state.soulseekSession)}".lowercase() else "not signed in"
    SettingsSection.LIBRARY -> "${recordCount(state.albums.size)} · ${if (state.hasFolder) "1 folder" else "no folder"}"
    SettingsSection.ABOUT -> "runout $VERSION · rg-1"
}

/**
 * Settings › Library: 01 music folder (what a record is, the scan in an ink-ruled stats strip, change
 * folder and rescan) and 02 repair local albums (the black repair key and, after a repair, the files
 * that couldn't be read in a red-ruled box, each with its reason).
 */
@Composable
private fun LibraryPage(state: RoomState, actions: RoomActions) {
    PageSection("01 music folder") {
        BasicText(
            if (state.hasFolder) "each album folder is a record in the crate." else "no music folder yet. each album folder in it becomes a record in the crate.",
            style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp),
        )
        state.report?.let { r ->
            if (r.error != null) BasicText(r.summary.lowercase(), style = Room.Label.copy(color = Room.Danger, lineHeight = 15.sp))
            else LibraryStats(r.albums, r.audioFiles, r.folders, r.unreadable)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            // Change folder lives here now, not on the home screen.
            if (state.hasFolder) {
                TeButton("change folder", actions::pickFolder, Modifier.weight(1f).testTag("settings-change-folder"), kind = KeyKind.OUTLINE)
                TeButton("rescan", actions::rescan, Modifier.weight(1f), kind = KeyKind.OUTLINE)
            } else {
                TeButton("choose folder", actions::pickFolder, Modifier.testTag("settings-change-folder"))
            }
        }
    }
    if (state.hasFolder) RepairLibrary(state, actions)
}

/** The scan as a TE stats strip, ruled in ink above and below; unreadable files count in red. */
@Composable
private fun LibraryStats(albums: Int, files: Int, folders: Int, unreadable: Int) {
    Row(Modifier.fillMaxWidth().hairlineTop(Room.Ink).hairlineBottom(Room.Ink)) {
        listOf(
            Triple("$albums", "albums", Room.Ink),
            Triple("$files", "music files", Room.Ink),
            Triple("$folders", "folders", Room.Ink),
            Triple("$unreadable", "unreadable", if (unreadable > 0) Room.Danger else Room.Ink),
        ).forEach { (value, label, colour) ->
            Column(Modifier.weight(1f).padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(value, style = Room.Title.copy(color = colour, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified))
                BasicText(label, style = Room.LabelQuiet)
            }
        }
    }
}

/**
 * Repair local albums: rebuilds the shelf from the folder alone and lists each .flac file that
 * couldn't be read, with the reason. The folder is opened read-only, so the files themselves
 * aren't rewritten; the reasons say what to do with them.
 */
@Composable
private fun RepairLibrary(state: RoomState, actions: RoomActions) {
    PageSection("02 repair local albums") {
        BasicText(
            "albums missing or vanishing? rebuild the shelf from your folder. runout forgets its saved list, reads every album again, " +
                "and shows each file it couldn't play and why. your music files are never changed.",
            style = Room.Label.copy(color = Room.Body, lineHeight = 15.sp),
        )
        val scanning = state.scan != null
        TeButton(
            if (scanning) "reading your folder…" else "repair local albums",
            { if (!scanning) actions.repairLibrary() },
            Modifier.testTag("settings-repair"),
            kind = if (scanning) KeyKind.OUTLINE else KeyKind.FILLED,
        )
        val broken = state.report?.unreadableFiles.orEmpty()
        if (broken.isEmpty()) return@PageSection
        val total = state.report?.unreadable ?: broken.size
        Column(Modifier.fillMaxWidth().border(1.dp, Room.Danger).testTag("repair-report")) {
            BasicText(
                "$total ${if (total == 1) "file" else "files"} couldn't be read",
                style = Room.Label.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth().background(Room.Danger).padding(11.dp),
            )
            broken.take(SHOWN_BROKEN).forEach { f ->
                Column(Modifier.fillMaxWidth().hairlineTop().padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(f.path.lowercase(), style = Room.Label.copy(lineHeight = 15.sp), maxLines = 2)
                    BasicText(f.reason.lowercase(), style = Room.LabelQuiet.copy(lineHeight = 15.sp))
                }
            }
            if (total > SHOWN_BROKEN) BasicText("and ${total - SHOWN_BROKEN} more", style = Room.LabelQuiet, modifier = Modifier.fillMaxWidth().hairlineTop().padding(11.dp))
        }
        BasicText(
            "to fix one: re-download or re-rip it as real flac, or check it with flac -t on a computer. then tap repair again.",
            style = Room.LabelQuiet.copy(lineHeight = 15.sp),
        )
    }
}

private const val SHOWN_BROKEN = 50

/**
 * Settings › About: the facts at the foot of the page, one per hairline row (an 88 dp grey key and
 * the 10/400 text), and under an ink rule the version in 33/700 and the build line.
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AboutPage() {
    Spacer(Modifier.weight(1f))
    val fonts = (if (TeType.isArray) "array by indian type foundry, via fontshare: itf free font license. " else "") +
        "inter and silkscreen: sil open font license."
    Column(Modifier.fillMaxWidth()) {
        listOf(
            "records" to "your flac records play offline, with no account.",
            "soulseek" to "optional. finds and downloads flac and carries private messages. its protocol does not encrypt sign-in or messages.",
            "drops" to "friday drops come from the internet archive under their own licences.",
            "fonts" to fonts,
            "libraries" to "backdrop, filament, media3, androidx: apache 2.0. soulseek core: mit.",
        ).forEach { (key, value) ->
            Row(Modifier.fillMaxWidth().hairlineTop().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BasicText(key, style = Room.LabelQuiet, modifier = Modifier.width(88.dp))
                BasicText(value, style = Room.Label.copy(lineHeight = 15.sp), modifier = Modifier.weight(1f))
            }
        }
    }
    Column(Modifier.fillMaxWidth().hairlineTop(Room.Ink).padding(top = 11.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        BasicText("RUNOUT $VERSION", style = Room.Display.copy(fontSize = 33.sp), maxLines = 1)
        BasicText(if (BuildConfig.DEBUG) "development build · rg-1 v3" else "rg-1 v3", style = Room.LabelQuiet)
    }
}
