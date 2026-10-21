package app.runout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.runout.BuildConfig

/**
 * Everything that configures the room, in one glass sheet: the sound chain, the Soulseek account
 * (with its folders, sharing and background choices), the music folder, and what the app is.
 */
@Composable
fun SettingsPanel(
    state: RoomState,
    actions: RoomActions,
    section: SettingsSection,
    onSection: (SettingsSection) -> Unit,
    onDownloads: () -> Unit,
    onSetup: () -> Unit,
    onOffline: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("panel:settings")) {
        Chips(SettingsSection.entries, section, { it.label }, onSection, Modifier.padding(bottom = 6.dp))
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
                BasicText(if (state.hasFolder) "Each album folder in your music folder is a record in the crate." else "No music folder yet. Choose a folder of FLAC albums.", style = Room.LabelQuiet)
                state.report?.let { BasicText(it.summary, style = Room.LabelQuiet, modifier = Modifier.padding(top = 6.dp)) }
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (state.hasFolder) "Change music folder" else "Choose music folder", actions::pickFolder, active = !state.hasFolder)
                    if (state.hasFolder) Pill("Rescan", actions::rescan)
                }
                SectionTitle("The deck")
                ToggleRow("Crackle", "Surface noise under the music. Always off in Pure.", state.crackleOn, { actions.toggleCrackle() })
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
