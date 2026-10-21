package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.runout.BuildConfig

/**
 * Settings as a plain list: Sound, Soulseek, Library, About. Each row opens its own page, with a
 * way back to the list. [section] null is the list itself.
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
    Column(Modifier.fillMaxWidth().testTag("panel:settings")) {
        if (section == null) {
            SettingsSection.entries.forEach { s ->
                SettingsRow(s.label, summary(s, state), Modifier.testTag("settings:${s.name}")) { onSection(s) }
            }
            return@Column
        }
        BackHandler { onSection(null) }
        TextAction("‹ Settings", { onSection(null) }, tag = "settings-back", color = Room.Ink)
        BasicText(section.label, style = Room.Title.copy(fontSize = 17.sp), modifier = Modifier.padding(vertical = 6.dp))
        when (section) {
            SettingsSection.SOUND -> SoundPanel(state.sound, state.output, actions)
            SettingsSection.ACCOUNT -> {
                ConnectionNotice(state, actions, onSetup)
                SoulseekAccountPanel(state, actions, onOffline, onSignIn = onSetup)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Downloads", onDownloads, Modifier.testTag("settings-downloads"))
                    Pill("Review Soulseek setup", onSetup, Modifier.testTag("settings-setup"))
                }
            }
            SettingsSection.LIBRARY -> {
                SectionTitle("Music folder")
                BasicText(if (state.hasFolder) "Each album folder is a record in the crate." else "No music folder yet.", style = Room.LabelQuiet)
                state.report?.let { BasicText(it.summary, style = Room.LabelQuiet, modifier = Modifier.padding(top = 6.dp)) }
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Change folder lives here now, not on the home screen.
                    Pill(if (state.hasFolder) "Change folder" else "Choose folder", actions::pickFolder, Modifier.testTag("settings-change-folder"), active = !state.hasFolder)
                    if (state.hasFolder) Pill("Rescan", actions::rescan)
                }
                SectionTitle("The deck")
                ToggleRow("Crackle", "Surface noise under the music.", state.crackleOn, { actions.toggleCrackle() }, more = "Always off in Pure.")
            }
            SettingsSection.ABOUT -> {
                BasicText("Runout ${BuildConfig.VERSION_NAME}", style = Room.Title)
                BasicText(
                    "Your FLAC records play offline, with no account. Soulseek is optional: it finds and downloads FLAC and carries private messages, " +
                        "and its protocol does not encrypt sign-in or messages. Friday Drops come from the Internet Archive under their own licences.",
                    style = Room.LabelQuiet,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                BasicText("Inter and Silkscreen: SIL Open Font License. Backdrop, Media3 and AndroidX: Apache 2.0. Soulseek core: MIT.", style = Room.LabelQuiet)
            }
        }
    }
}

private fun summary(s: SettingsSection, state: RoomState): String = when (s) {
    SettingsSection.SOUND -> if (state.sound.pure) "Pure" else if (state.sound.eqEnabled) "EQ · ${state.sound.presetName}" else "EQ off"
    SettingsSection.ACCOUNT -> if (state.soulseekSession.hasAccount) "${state.soulseekSession.username} · ${connectionLabel(state.soulseekSession)}" else "Not signed in"
    SettingsSection.LIBRARY -> recordCount(state.albums.size)
    SettingsSection.ABOUT -> "Runout ${BuildConfig.VERSION_NAME}"
}

/** One row of the Settings list: a name, its current state in grey, and a chevron. */
@Composable
private fun SettingsRow(title: String, summary: String, modifier: Modifier, onClick: () -> Unit) {
    val tick = rememberTick()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { tick(); onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Room.Title.copy(fontSize = 15.sp))
            BasicText(summary, style = Room.LabelQuiet, maxLines = 1)
        }
        BasicText("›", style = Room.Title.copy(fontSize = 20.sp, color = Room.Quiet))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Room.Ink.copy(alpha = 0.08f)))
}
