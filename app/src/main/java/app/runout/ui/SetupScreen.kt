package app.runout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.runout.soulseek.SoulseekConnection

@Composable
internal fun SetupScreen(state: RoomState, actions: RoomActions, initialStep: Int, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(initialStep.coerceIn(0,6)) }
    var share by rememberSaveable { mutableStateOf(state.soulseekShareConsent) }
    var background by rememberSaveable { mutableStateOf(state.soulseekBackgroundConsent) }
    fun move(next: Int) { step = next; actions.setOnboardingStep(next) }
    fun previous() = move(if (step == 6 || step == 2 && state.soulseekSession.connected) 0 else step-1)
    LaunchedEffect(state.soulseekSession.username) {
        share = state.soulseekShareConsent
        background = state.soulseekBackgroundConsent
    }
    LaunchedEffect(state.soulseekSession.connected,step) {
        if(step == 1 && state.soulseekSession.connected) move(2)
    }
    BackHandler(enabled = step != 0 || state.onboardingComplete) { if(step == 0) onFinish() else previous() }
    // Shown inside the room's glass sheet, so the turntable stays in view behind it.
    Column(Modifier.fillMaxSize().testTag("screen:setup")) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if(step == 0) "Welcome" else "Setup",Modifier.weight(1f))
            if(step != 0) Pill("Back",{ previous() },Modifier.testTag("setup-back"))
            if(state.onboardingComplete) Pill("Close",onFinish,Modifier.padding(start = 6.dp).testTag("setup-close"))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp,bottom = 20.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when(step) {
                0 -> {
                    ConsoleTitle("A room for your records","Drop the needle.","Your FLAC collection, a tactile vinyl deck, and a whole world of music when you want it.")
                    ConsoleCard {
                        BasicText("LISTEN OFFLINE",style = Room.Brand)
                        BasicText("No account. No connection. Just the music on your phone.",style = Room.LabelQuiet)
                        Pill("Listen offline",{ actions.disconnectSoulseek(); move(6) },Modifier.testTag("setup-offline"),active = true)
                    }
                    ConsoleCard {
                        BasicText("CONNECT SOULSEEK",style = Room.Brand)
                        BasicText("Find songs and albums, keep FLAC downloads and talk to other listeners. Internet Archive downloads are included.",style = Room.LabelQuiet)
                        Pill("Set up Soulseek",{ move(if(state.soulseekSession.connected) 2 else 1) },Modifier.testTag("setup-online"))
                    }
                }
                1 -> {
                    ConsoleTitle("01 / Your connection","Your Soulseek ID","Runout connects directly to Soulseek. There is no separate Runout account.")
                    SoulseekSignInForm(state,actions)
                    Pill("Keep listening offline",{ actions.disconnectSoulseek(); move(6) })
                }
                2 -> {
                    ConsoleTitle("02 / Keep your records","Choose a download folder","Completed FLAC downloads stay in this folder, work with other players and survive uninstalling Runout.")
                    ConsoleCard {
                        BasicText(state.downloadFolder ?: "No folder selected yet",style = Room.Title)
                        Pill(if(state.downloadFolder == null) "Choose download folder" else "Change download folder",actions::pickDownloadFolder,Modifier.testTag("setup-download-folder"),active = true)
                    }
                    Pill("Continue",{ move(3) },Modifier.testTag("setup-continue"),active = true)
                    if(state.downloadFolder == null) BasicText("You can choose later. Search and messages will still work; downloads will ask for a folder.",style = Room.LabelQuiet)
                }
                3 -> {
                    ConsoleTitle("03 / Give something back","Share your FLAC?","Only files in the folder you select are shared. Your entire phone library is never published automatically.")
                    ConsoleCard {
                        ToggleRow("Share a FLAC folder","Let other Soulseek listeners find and download selected music.",share,{ share = it })
                        if(share) {
                            BasicText(state.sharedFolder ?: "Choose the folder you want to share.",style = Room.LabelQuiet)
                            Pill(if(state.sharedFolder == null) "Choose sharing folder" else "Change sharing folder",actions::pickSharedFolder,Modifier.testTag("setup-sharing-folder"))
                        }
                    }
                    Pill(if(share) "Continue with sharing" else "Not now — continue",{ actions.setSoulseekSharing(share); move(4) },Modifier.testTag("setup-continue"),active = true,enabled = !share || state.sharedFolder != null)
                    BasicText("Your choice is remembered for this account. You can change it from Settings › Soulseek at any time.",style = Room.LabelQuiet)
                }
                4 -> {
                    ConsoleTitle("04 / Choose your rhythm","When to stay online","Both options reconnect after temporary network loss. Your local player always works offline.")
                    ConsoleCard {
                        BasicText("WHILE USING RUNOUT",style = Room.Brand)
                        BasicText("Uses less battery and data. Sharing and messages are available while connected; new messages arrive when you return. Active transfers can continue with a notification.",style = Room.LabelQuiet)
                        Pill(if(!background) "Selected" else "Use app-only mode",{ background = false },active = !background)
                    }
                    ConsoleCard {
                        BasicText("STAY ONLINE",style = Room.Brand)
                        BasicText("Receive messages and serve your shared FLAC while the screen is off. Uses more battery and data, needs notification permission, and shows an ongoing Disconnect notification. Android can still stop background activity.",style = Room.LabelQuiet)
                        Pill(if(background) "Selected" else "Use background mode",{ background = true },active = background)
                    }
                    Pill("Continue",{ actions.setSoulseekStayOnline(background); move(5) },Modifier.testTag("setup-continue"),active = true,enabled = state.soulseekSession.connected)
                    if(!state.soulseekSession.connected) ConnectionNotice(state,actions,{ move(1) })
                }
                5 -> {
                    ConsoleTitle("Ready to spin","Make yourself at home.","Search your own library and Soulseek in one place. Only FLAC music gets through.")
                    ConsoleCard {
                        BasicText("Downloads: ${state.downloadFolder ?: "choose when downloading"}",style = Room.Label)
                        BasicText("Sharing: ${if(share && state.sharedFolder != null) state.sharedFolder else "off"}",style = Room.Label)
                        BasicText("Connection: ${if(state.soulseekStayOnline) "background mode" else "while using Runout"}",style = Room.Label)
                    }
                    Pill("Enter your listening room",onFinish,Modifier.testTag("setup-finish"),active = true)
                }
                6 -> {
                    ConsoleTitle("Offline / No account needed","Bring your own records.","Choose a folder of FLAC albums. You can connect Soulseek later from Settings › Soulseek.")
                    Pill(if(state.hasFolder) "Change music folder" else "Choose music folder",actions::pickFolder,Modifier.testTag("setup-local-folder"),active = true)
                    if(state.hasFolder) BasicText("Music folder selected. Your records will appear in the crate.",style = Room.LabelQuiet)
                    Pill(if(state.hasFolder) "Start listening" else "Skip for now",onFinish,Modifier.testTag("setup-finish"))
                }
            }
            state.soulseekError?.let { BasicText(it,style = Room.Label.copy(color = androidx.compose.ui.graphics.Color(0xFF963A27))) }
        }
    }
}

@Composable
internal fun SoulseekSignInForm(state: RoomState, actions: RoomActions) {
    var username by rememberSaveable(state.soulseekSavedUsername) { mutableStateOf(state.soulseekSavedUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(false) }
    var create by rememberSaveable { mutableStateOf(false) }
    val busy = state.soulseekSession.state in setOf(SoulseekConnection.CONNECTING,SoulseekConnection.RECONNECTING)
    ConsoleCard {
        Chips(listOf(false,true),create,{ if(it) "Create an ID" else "Sign in" },{ create = it })
        Field(username,{ username = it.take(30) },"Soulseek username")
        Field(password,{ password = it.take(128) },"Soulseek password",secret = true)
        ToggleRow("Remember my sign-in","Encrypt credentials with Android Keystore and reconnect on future launches.",remember,{ remember = it })
        BasicText(if(create) "An unused username creates your Soulseek ID. There is no password recovery; keep your credentials somewhere safe." else "Check your username carefully: Soulseek creates an account for an unused name, even if it was a typo.",style = Room.LabelQuiet)
        BasicText("Connecting here disconnects another client using this ID. Soulseek does not encrypt credentials or messages in transit.",style = Room.LabelQuiet)
        state.soulseekSession.error?.let { BasicText(it,style = Room.Label.copy(color = androidx.compose.ui.graphics.Color(0xFF963A27))) }
        Pill(if(busy) "Connecting…" else if(create) "Create Soulseek ID" else "Connect Soulseek",{ actions.clearSoulseekError(); actions.signInSoulseek(username,password,remember); password = "" },Modifier.testTag("submit-soulseek"),active = true,enabled = !busy && app.runout.soulseek.SoulseekPolicy.validUsername(username) && password.isNotEmpty())
        if(state.soulseekSavedUsername != null && !busy) Pill("Use saved sign-in",actions::signInSavedSoulseek)
    }
}
