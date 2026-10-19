package app.runout

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.runout.account.Account
import app.runout.account.RemoteProfile
import app.runout.drops.DropItem
import app.runout.social.ShelfItem
import app.runout.ui.PeopleState
import app.runout.model.Album
import app.runout.model.Source
import app.runout.pressing.PressingDraft
import app.runout.sharing.ImportResult
import app.runout.sharing.VinylPackage
import app.runout.sound.SoundSettings
import app.runout.ui.RoomActions
import app.runout.ui.RoomScreen
import app.runout.ui.RoomState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val graph get() = (application as RunoutApp).graph
    private val message = MutableStateFlow<String?>(null)
    private val claiming = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val people = MutableStateFlow(PeopleState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (savedInstanceState == null) handleIncoming(intent)

        val graph = graph
        setContent {
            val albums by graph.shelf.collectAsStateWithLifecycle()
            val folder by graph.library.folder.collectAsStateWithLifecycle()
            val scan by graph.library.scan.collectAsStateWithLifecycle()
            val report by graph.library.report.collectAsStateWithLifecycle()
            val deck by graph.deck.state.collectAsStateWithLifecycle()
            val crackle by graph.deck.crackleOn.collectAsStateWithLifecycle()
            val sound by graph.sound.settings.collectAsStateWithLifecycle()
            val output by graph.sound.status.collectAsStateWithLifecycle()
            val profile by graph.social.profile.collectAsStateWithLifecycle()
            val friends by graph.social.friends.collectAsStateWithLifecycle()
            val drops by graph.drops.state.collectAsStateWithLifecycle()
            val claimed by graph.drops.claimed.collectAsStateWithLifecycle()
            val claimingNow by claiming.collectAsStateWithLifecycle()
            val note by message.collectAsStateWithLifecycle()
            val account by graph.accounts.account.collectAsStateWithLifecycle()
            val accountStatus by graph.accounts.status.collectAsStateWithLifecycle()
            val peopleNow by people.collectAsStateWithLifecycle()

            var clock by remember { mutableStateOf(now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    clock = now()
                    delay(15_000)
                }
            }

            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) graph.library.setFolder(uri)
            }
            val autoEqPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) importAutoEq(uri)
            }
            var onImage by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
            val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                onImage?.invoke(uri)
                onImage = null
            }

            val actions = remember {
                object : RoomActions {
                    override fun pickFolder() = folderPicker.launch(null)
                    override fun rescan() { graph.library.rescan() }
                    override fun load(album: Album) = graph.deck.load(album)
                    override fun toggleCrackle() = graph.deck.toggleCrackle()
                    override fun needleDrop(fraction: Float) = graph.deck.needleDrop(fraction)
                    override fun liftNeedle() = graph.deck.liftNeedle()
                    override fun holdPlatter() = graph.deck.holdPlatter()
                    override fun releasePlatter() = graph.deck.releasePlatter()
                    override fun toggleSpeed() = graph.deck.toggleSpeed()
                    override fun flip() = graph.deck.flip()
                    override fun startStop() = graph.deck.startStop()
                    override fun nextTrack() = graph.deck.nextTrack()
                    override fun previousTrack() = graph.deck.previousTrack()
                    override fun setPitch(percent: Float) = graph.deck.setPitch(percent)

                    override fun updateSound(transform: (SoundSettings) -> SoundSettings) = graph.sound.update(transform)
                    override fun applyPreset(name: String) = graph.sound.applyPreset(name)
                    override fun importAutoEq() = autoEqPicker.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))

                    override fun share(album: Album) = shareAlbum(album)
                    override fun removeRecord(album: Album) = remove(album)
                    override fun shareMyCard() = shareCard()
                    override fun rename(name: String) = if (graph.accounts.account.value != null) graph.accounts.rename(name) else graph.social.rename(name)
                    override fun setAccent(accent: Int) = if (graph.accounts.account.value != null) graph.accounts.setAccent(accent) else graph.social.setAccent(accent)
                    override fun removeFriend(id: String) = graph.social.removeFriend(id)

                    override fun refreshDrops() { graph.scope.launch { graph.drops.refresh() } }
                    override fun claim(item: DropItem) = claimDrop(item)
                    override suspend fun dropCover(item: DropItem): String? = graph.drops.thumbnail(item)?.absolutePath

                    override fun pickImage(onPicked: (Uri?) -> Unit) {
                        onImage = onPicked
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    override fun press(draft: PressingDraft) = pressRecord(draft)
                    override fun dismissMessage() { message.value = null }

                    override fun createAccount(username: String, name: String, accent: Int) = accountAction("Creating your account…") {
                        graph.accounts.create(username, name, accent, graph.shelf.value)
                    }
                    override fun restoreAccount(key: String, username: String?) = accountAction("Restoring your account…") {
                        graph.accounts.restore(key, graph.shelf.value, username)
                    }
                    override fun copyAccountKey() = copyKey()
                    override fun setShareShelf(on: Boolean) {
                        graph.accounts.setShareShelf(on)
                        graph.scope.launch { graph.accounts.publish(graph.shelf.value) }
                    }
                    override fun searchPeople(query: String) = search(query)
                    override fun openProfile(pubkey: String) = openPerson(pubkey)
                    override fun closeProfile() = people.update { it.copy(viewing = null) }
                    override fun addFriend(profile: RemoteProfile) {
                        graph.accounts.addFriend(profile)
                        message.value = "${profile.name} is now your friend"
                    }
                    override fun refreshFriends() { graph.scope.launch { runCatching { graph.accounts.refreshFriends() } } }
                    override suspend fun coverFor(item: ShelfItem): String? {
                        val id = "shelf-" + app.runout.library.LibraryScanner.stableId(item.artist.lowercase() + "|" + item.title.lowercase())
                        return (graph.artwork.cachedFile(id) ?: graph.artwork.lookupOnline(id, item.artist, item.title))?.absolutePath
                    }
                }
            }

            RoomScreen(
                state = RoomState(
                    albums = albums,
                    hasFolder = folder != null,
                    scan = scan,
                    deck = deck,
                    crackleOn = crackle,
                    clock = clock,
                    report = report,
                    sound = sound,
                    output = output,
                    profile = profile,
                    friends = friends,
                    drops = drops,
                    claimed = claimed,
                    claiming = claimingNow,
                    message = note,
                    account = account,
                    accountStatus = accountStatus,
                    people = peopleNow,
                ),
                actions = actions,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** A .vinyl file opened from a chat app, Files or Quick Share. */
    private fun handleIncoming(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        message.value = "Opening record…"
        graph.scope.launch {
            message.value = when (val r = graph.share.import(uri)) {
                is ImportResult.AlbumAdded -> "“${r.album.title}” from ${r.album.from} is in your crate"
                is ImportResult.CardAdded -> "${r.fromName} sent you “${r.album.title}” as a card · see Friends"
                is ImportResult.FriendAdded -> if (r.isNew) "${r.name} is now your friend · ${r.records} records" else "Updated ${r.name}'s shelf"
                is ImportResult.Failed -> "Couldn't open that file: ${r.reason}"
            }
        }
    }

    private fun shareAlbum(album: Album) {
        message.value = "Packing “${album.title}”…"
        graph.scope.launch {
            val file = runCatching { graph.share.exportAlbum(album) }.getOrElse {
                message.value = "Couldn't pack the record: ${it.message}"
                return@launch
            }
            launchShare(file, "Send “${album.title}”")
            message.value = if (album.audioShareable) "Sending “${album.title}” with its audio"
            else "Sending “${album.title}” as a card: sleeve and track list. Audio only travels for your own music and Creative Commons records."
        }
    }

    private fun shareCard() {
        graph.scope.launch {
            val file = runCatching { graph.share.exportFriendCard(graph.shelf.value) }.getOrElse {
                message.value = "Couldn't make your card: ${it.message}"
                return@launch
            }
            launchShare(file, "Send your friend card")
        }
    }

    private fun launchShare(file: File, title: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = VinylPackage.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, title))
    }

    private fun remove(album: Album) {
        if (graph.deck.state.value.album?.id == album.id) graph.deck.eject()
        when (album.source) {
            Source.RECEIVED -> graph.share.delete(album.id)
            Source.DROP -> graph.drops.remove(album.id)
            Source.PRESSING -> graph.pressing.delete(album.id)
            Source.FOLDER -> return
        }
        message.value = "Took “${album.title}” off the shelf"
    }

    private fun claimDrop(item: DropItem) {
        if (item.identifier in claiming.value) return
        claiming.update { it + (item.identifier to 0f) }
        graph.scope.launch {
            val result = graph.drops.claim(item) { p -> claiming.update { m -> if (item.identifier in m) m + (item.identifier to p) else m } }
            claiming.update { it - item.identifier }
            message.value = result.fold({ "“${it.title}” is in your crate" }, { "Couldn't claim it: ${it.message}" })
        }
    }

    private fun pressRecord(draft: PressingDraft) {
        message.value = "Pressing “${draft.title}”…"
        graph.scope.launch {
            message.value = runCatching { graph.pressing.press(draft) }.fold(
                { "Pressed “${it.title}”. It's in your crate." },
                { "Couldn't press it: ${it.message}" },
            )
        }
    }

    private fun importAutoEq(uri: Uri) {
        graph.scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().take(256_000).toByteArray().toString(Charsets.UTF_8) } }.getOrNull()
            }
            message.value = if (text != null && graph.sound.importAutoEq(text)) "AutoEq profile loaded" else "That file isn't an AutoEq ParametricEQ.txt"
        }
    }

    private fun accountAction(working: String, block: suspend () -> Result<Account>) {
        message.value = working
        graph.scope.launch {
            message.value = block().fold(
                { "Welcome, ${it.handle}. People can now find you by your username." },
                { it.message ?: "Something went wrong" },
            )
        }
    }

    private fun copyKey() {
        val key = graph.accounts.backupKey() ?: return
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return
        val clip = android.content.ClipData.newPlainText("Runout account key", key)
        // Keeps the key out of the clipboard preview on Android 13+.
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(clip)
        message.value = "Account key copied. Keep it somewhere private: anyone with it can be you."
    }

    private fun search(query: String) {
        people.update { it.copy(searching = true, error = null) }
        graph.scope.launch {
            val r = runCatching { graph.accounts.search(query) }
            people.update {
                it.copy(searching = false, searched = query.trim(), results = r.getOrDefault(emptyList()), error = r.exceptionOrNull()?.message)
            }
        }
    }

    private fun openPerson(pubkey: String) {
        val known = people.value.results.firstOrNull { it.pubkey == pubkey }
        people.update { it.copy(viewing = known ?: it.viewing, loadingProfile = true, error = null) }
        graph.scope.launch {
            val p = runCatching { graph.accounts.profile(pubkey) }.getOrNull()
            people.update {
                when {
                    p != null -> it.copy(viewing = p, loadingProfile = false)
                    it.viewing != null -> it.copy(loadingProfile = false)
                    else -> it.copy(loadingProfile = false, error = "Couldn't load that profile right now")
                }
            }
        }
    }

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}
