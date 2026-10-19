package app.runout

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import app.runout.drops.DropItem
import app.runout.social.ShelfItem
import app.runout.model.Album
import app.runout.model.Source
import app.runout.pressing.PressingDraft
import app.runout.sharing.ImportResult
import app.runout.sharing.VinylPackage
import app.runout.sound.SoundSettings
import app.runout.ui.RoomActions
import app.runout.ui.RoomScreen
import app.runout.ui.RoomState
import app.runout.soulseek.SoulseekSearchResult
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
    private val activeConversation = MutableStateFlow<String?>(null)
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) graph.soulseek.setStayOnline(true)
        else message.value = "Stay-online mode needs notifications so you can see and disconnect the session. Local playback remains available."
    }

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
            val session by graph.soulseek.session.collectAsStateWithLifecycle()
            val results by graph.soulseek.searchResults.collectAsStateWithLifecycle()
            val searching by graph.soulseek.searching.collectAsStateWithLifecycle()
            val transfers by graph.soulseek.transfers.collectAsStateWithLifecycle()
            val uploads by graph.soulseek.uploads.collectAsStateWithLifecycle()
            val contacts by graph.soulseek.contacts.collectAsStateWithLifecycle()
            val conversations by graph.soulseek.conversations.collectAsStateWithLifecycle()
            val destination by graph.soulseek.destination.collectAsStateWithLifecycle()
            val sharedFolder by graph.soulseek.sharedFolder.collectAsStateWithLifecycle()
            val sharing by graph.soulseek.sharing.collectAsStateWithLifecycle()
            val stayOnline by graph.soulseek.stayOnline.collectAsStateWithLifecycle()
            val savedUsername by graph.soulseek.savedUsername.collectAsStateWithLifecycle()
            val networkError by graph.soulseek.error.collectAsStateWithLifecycle()
            val selectedConversation by activeConversation.collectAsStateWithLifecycle()

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
            val downloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) graph.soulseek.setDestination(uri)
            }
            val sharePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) graph.soulseek.setSharedFolder(uri)
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
                    override fun rename(name: String) = graph.social.rename(name)
                    override fun setAccent(accent: Int) = graph.social.setAccent(accent)
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

                    override fun signInSoulseek(username: String, password: String, remember: Boolean) { graph.soulseek.signIn(username, password, remember) }
                    override fun signInSavedSoulseek() { graph.soulseek.signInSaved() }
                    override fun signOutSoulseek() { activeConversation.value = null; graph.soulseek.logout() }
                    override fun searchSoulseek(query: String) { graph.soulseek.search(query) }
                    override fun stopSoulseekSearch() { graph.soulseek.stopSearch() }
                    override fun downloadSoulseek(result: SoulseekSearchResult) { graph.soulseek.download(result) }
                    override fun downloadSoulseekFolder(result: SoulseekSearchResult) { graph.soulseek.downloadFolder(result) }
                    override fun pauseSoulseekTransfer(id: String) { graph.soulseek.pause(id) }
                    override fun resumeSoulseekTransfer(id: String) { resumeDownload(id) }
                    override fun cancelSoulseekTransfer(id: String) { graph.soulseek.cancel(id) }
                    override fun retrySoulseekTransfer(id: String) { resumeDownload(id) }
                    override fun pickDownloadFolder() = downloadPicker.launch(null)
                    override fun pickSharedFolder() = sharePicker.launch(null)
                    override fun setSoulseekSharing(enabled: Boolean) { graph.soulseek.setSharing(enabled) }
                    override fun setSoulseekStayOnline(enabled: Boolean) {
                        if (enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else graph.soulseek.setStayOnline(enabled)
                    }
                    override fun addSoulseekContact(username: String) { graph.soulseek.addContact(username) }
                    override fun openSoulseekConversation(username: String) { activeConversation.value = username; graph.soulseek.openConversation(username) }
                    override fun closeSoulseekConversation() { activeConversation.value = null }
                    override fun sendSoulseekMessage(username: String, text: String) { graph.soulseek.sendMessage(username, text) }
                    override fun markSoulseekRead(username: String) { graph.soulseek.markRead(username) }
                    override fun clearSoulseekError() { graph.soulseek.clearError() }
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
                    soulseekSession = session,
                    soulseekResults = results,
                    soulseekSearching = searching,
                    soulseekTransfers = transfers,
                    soulseekUploads = uploads,
                    soulseekContacts = contacts,
                    soulseekConversations = conversations,
                    soulseekConversation = selectedConversation,
                    downloadFolder = destination?.let(::folderLabel),
                    sharedFolder = sharedFolder?.let(::folderLabel),
                    soulseekSharing = sharing,
                    soulseekStayOnline = stayOnline,
                    soulseekSavedUsername = savedUsername,
                    soulseekError = networkError,
                ),
                actions = actions,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    override fun onStart() { super.onStart(); graph.soulseek.onForegroundChanged(true) }
    override fun onStop() { graph.soulseek.onForegroundChanged(false); super.onStop() }

    private fun folderLabel(uri: Uri): String = runCatching {
        androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)?.name
    }.getOrNull() ?: "Selected music folder"

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
            Source.SOULSEEK -> graph.soulseek.removeAlbum(album.id)
            Source.FOLDER -> return
        }
        message.value = "Took “${album.title}” off the shelf"
    }

    private fun claimDrop(item: DropItem) {
        if (item.identifier in claiming.value) return
        claiming.update { it + (item.identifier to 0f) }
        graph.scope.launch {
            try {
                val result = graph.soulseek.withArchiveDownload(item.identifier, item.title) { report ->
                    graph.drops.claim(item) { p ->
                        report((p * 1_000_000).toLong(), 1_000_000L)
                        claiming.update { m -> if (item.identifier in m) m + (item.identifier to p) else m }
                    }
                }
                message.value = result.fold({ "“${it.title}” is in your crate" }, { "Couldn't claim it: ${it.message}" })
            } finally { claiming.update { it - item.identifier } }
        }
    }

    private fun resumeDownload(id: String) {
        val transfer = graph.soulseek.transfers.value.firstOrNull { it.id == id } ?: return
        if (transfer.provider != "Internet Archive") { graph.soulseek.resume(id); return }
        graph.scope.launch {
            val week = (graph.drops.refresh() as? app.runout.drops.DropState.Ready)?.week
            val owner = graph.soulseek.session.value.username
            val item = week?.items?.firstOrNull { app.runout.soulseek.SoulseekPolicy.accountKey(owner.orEmpty(), "archive:${it.identifier}") == id }
            if (item != null) claimDrop(item)
            else message.value = "This album is outside the current Friday Drop. Completed FLAC files are still in your music folder."
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

    private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}
