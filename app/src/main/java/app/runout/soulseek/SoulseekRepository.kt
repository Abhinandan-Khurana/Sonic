package app.runout.soulseek

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.room.withTransaction
import app.runout.library.AlbumJson
import app.runout.library.FlacPolicy
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Source
import app.runout.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SoulseekRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val transportFactory: ((String) -> Int) -> SoulseekTransport = { NativeTransport(it) },
    database: SoulseekDatabase? = null,
) {
    private val db = database ?: Room.databaseBuilder(context.applicationContext, SoulseekDatabase::class.java, "soulseek.db").build()
    private val dao = db.soulseek()
    private val prefs = context.getSharedPreferences("soulseek", Context.MODE_PRIVATE)
    val preferences = SoulseekPreferences(context)
    private val accountMutex = Mutex()
    private val publicationMutex = Mutex()
    private val searchMutex = Mutex()
    private val archiveJobs = ConcurrentHashMap<String, Job>()
    private var ownerJob: Job? = null
    private var observedOwner: String? = null
    private val connection = SoulseekConnectionManager(context, scope, { transportFactory(::openSharedFile) },
        { backgroundConsented() || hasActiveTransfers() }, ::handleEvent, { _error.value = it })
    private val generation get() = connection.generation
    private val activeTransfers = ConcurrentHashMap.newKeySet<String>()
    private val publicationJobs = ConcurrentHashMap<String, Job>()
    private val pendingBrowse = ConcurrentHashMap<String, SoulseekSearchResult>()
    private val shares = ConcurrentHashMap<String, SharedFile>()
    private val shareRevision = java.util.concurrent.atomic.AtomicLong()
    private var searchId: String? = null
    val session = connection.session
    private val _searchResults = MutableStateFlow<List<SoulseekSearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _searchStatus = MutableStateFlow(SoulseekSearchStatus())
    val searchStatus = _searchStatus.asStateFlow()
    private val searchIndex = LinkedHashMap<String, SoulseekSearchResult>()
    private val _folderPreview = MutableStateFlow<SoulseekFolderPreview?>(null)
    val folderPreview = _folderPreview.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()
    private val _transfers = MutableStateFlow<List<SoulseekTransfer>>(emptyList())
    val transfers = _transfers.asStateFlow()
    private val _uploads = MutableStateFlow<List<SoulseekUpload>>(emptyList())
    val uploads = _uploads.asStateFlow()
    private val _contacts = MutableStateFlow<List<SoulseekContact>>(emptyList())
    val contacts = _contacts.asStateFlow()
    private val _conversations = MutableStateFlow<List<SoulseekConversation>>(emptyList())
    val conversations = _conversations.asStateFlow()
    private val _downloadAlbums = MutableStateFlow<List<Album>>(emptyList())
    val downloadAlbums = _downloadAlbums.asStateFlow()
    private val _destination = MutableStateFlow(prefs.getString("destination", null)?.let(Uri::parse))
    val destination = _destination.asStateFlow()
    private val storage = DownloadStorage(context) { destination.value }
    private val _sharedFolder = MutableStateFlow<Uri?>(null)
    val sharedFolder = _sharedFolder.asStateFlow()
    // Actual runtime activity is distinct from each account's saved consent.
    private val _sharing = MutableStateFlow(false)
    val sharing = _sharing.asStateFlow()
    private val _stayOnline = MutableStateFlow(false)
    val stayOnline = _stayOnline.asStateFlow()
    val savedUsername = connection.savedUsername
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val initialized = scope.async(Dispatchers.IO) {
        dao.pauseInterrupted("Interrupted. Sign in and resume to continue.")
        dao.offlineContacts()
        dao.failInterruptedMessages()
        connection.initialize()
    }

    init {
        SoulseekRuntime.repository = this
        scope.launch(Dispatchers.IO) {
            initialized.await()
            dao.albums().collectLatest { rows ->
                _downloadAlbums.value = rows.mapNotNull { runCatching { FlacPolicy.filterAlbum(context, AlbumJson.fromJson(JSONObject(it.json))) }.getOrNull() }
            }
        }
    }

    private fun work(block: suspend () -> Unit): Job = scope.launch(Dispatchers.IO) {
        try { initialized.await(); block() } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { _error.value = friendly(e) }
    }
    fun canDownload(): Boolean = session.value.signedIn
    fun requireSignedIn() { check(canDownload()) { "Sign in with your Soulseek account to download and keep music." } }
    fun clearError() { _error.value = null }

    fun signIn(username: String, password: String, remember: Boolean): Job = work {
        require(SoulseekPolicy.validUsername(username)) { "Use a Soulseek username of 1–30 printable ASCII characters, without surrounding spaces." }
        require(password.isNotEmpty() && password.length <= 128 && password.none { it == '\u0000' }) { "Enter a password of 1–128 characters." }
        accountMutex.withLock {
            connection.disconnect(SoulseekDisconnectReason.MANUAL)
            clearSessionWork("Account changed", true)
            connection.signIn(username, password, remember)
            observeOwner(username)
            _error.value = null
        }
    }
    fun signInSaved(): Job = work {
        accountMutex.withLock { connection.disconnect(SoulseekDisconnectReason.MANUAL); clearSessionWork("Account changed", true); connection.signInSaved() }
    }
    fun reconnect(): Job = work { connection.reconnect() }
    private fun observeOwner(username: String) {
        if (observedOwner == username) return
        observedOwner = username
        ownerJob?.cancel()
        ownerJob = scope.launch(Dispatchers.IO) {
            launch { dao.transfers(username).collectLatest { rows -> _transfers.value = rows.map { it.model() }; updateServices() } }
            launch { dao.contacts(username).collectLatest { _contacts.value = it.map { c -> SoulseekContact(c.username, c.online) } } }
            launch { dao.messages(username).collectLatest { rows -> _conversations.value = rows.groupBy { it.username }.map { (name, messages) -> SoulseekConversation(name, messages.map { it.model() }, messages.count { !it.outgoing && !it.read }) }.sortedByDescending { it.messages.lastOrNull()?.timestamp ?: 0 } } }
        }
    }
    fun logout(): Job = work {
        accountMutex.withLock {
            connection.disconnect(SoulseekDisconnectReason.MANUAL)
            clearSessionWork("Signed out. Sign in and resume to continue.", true)
            connection.disconnect(SoulseekDisconnectReason.MANUAL, clearAccount = true)
        }
    }
    private suspend fun clearSessionWork(reason: String, clearAccount: Boolean) {
        val owner = session.value.username
        publicationJobs.values.forEach { it.cancel() }; publicationJobs.clear()
        archiveJobs.values.forEach { it.cancel() }; archiveJobs.clear()
        activeTransfers.clear(); _uploads.value = emptyList(); pendingBrowse.clear(); _folderPreview.value = null
        searchMutex.withLock {
            searchId = null; _searching.value = false
            if (_searchStatus.value.phase == SearchPhase.SEARCHING) _searchStatus.update { it.copy(phase = SearchPhase.INTERRUPTED, error = reason) }
        }
        if (owner != null) { dao.pauseOwner(owner, reason); dao.failPendingMessages(owner) }
        dao.offlineContacts()
        shareRevision.incrementAndGet(); _sharing.value = false; shares.clear()
        val preserveBackground = !clearAccount && session.value.state == SoulseekConnection.RECONNECTING && _stayOnline.value && backgroundConsented()
        if (!preserveBackground) _stayOnline.value = false
        if (clearAccount) {
            ownerJob?.cancel(); observedOwner = null
            _contacts.value = emptyList(); _conversations.value = emptyList(); _transfers.value = emptyList()
            searchMutex.withLock { searchIndex.clear(); _searchResults.value = emptyList(); _searchStatus.value = SoulseekSearchStatus() }
        }
        stopService(SoulseekTransferService::class.java)
        if (!preserveBackground) stopService(SoulseekOnlineService::class.java)
    }
    private suspend fun command(op: String, body: JSONObject.() -> Unit = {}) = connection.command(op, body)

    fun search(query: String): Job = work { searchMutex.withLock {
        requireSignedIn()
        val text = query.trim().replace(Regex("\\s+"), " ")
        require(text.toByteArray(Charsets.UTF_8).size in 2..256) { "Search needs 2–256 bytes. Try an artist, album or song." }
        searchId?.let { old -> command("cancel_search") { put("id", old) } }
        val id = UUID.randomUUID().toString(); searchId = id
        searchIndex.clear(); _searchResults.value = emptyList(); _searching.value = true
        _searchStatus.value = SoulseekSearchStatus(id, text, SearchPhase.SEARCHING)
        try { command("search") { put("id", id); put("query", text); put("timeoutMs", 30000) } }
        catch (e: Exception) { _searching.value = false; _searchStatus.update { it.copy(phase = SearchPhase.FAILED, error = friendly(e)) }; throw e }
    } }
    fun stopSearch(): Job = work { searchMutex.withLock {
        searchId?.let { id -> command("cancel_search") { put("id", id) } }
        searchId = null; _searching.value = false
        _searchStatus.update { it.copy(phase = SearchPhase.CANCELLED) }
    } }
    fun removeAlbum(id: String): Job = work { dao.removeAlbum(id) }
    fun download(result: SoulseekSearchResult): Job = work { enqueue(result) }
    fun downloadFolder(result: SoulseekSearchResult): Job = previewFolder(result)
    fun previewFolder(result: SoulseekSearchResult): Job = work {
        requireSignedIn()
        val id = UUID.randomUUID().toString(); pendingBrowse.clear(); pendingBrowse[id] = result
        _folderPreview.value = SoulseekFolderPreview(id, result.username, result.directory)
        command("browse") { put("id", id); put("username", result.username); put("directory", result.directory) }
    }
    fun closeFolderPreview() { pendingBrowse.clear(); _folderPreview.value = null }
    fun confirmFolderDownload(): Job = work {
        val preview = folderPreview.value ?: return@work
        check(!preview.loading && preview.error == null) { "Wait for the complete folder listing first." }
        requireSignedIn(); storage.requireDestination()
        preview.files.forEach { enqueue(it) }
        _folderPreview.value = null
    }
    private suspend fun enqueue(result: SoulseekSearchResult) {
        requireSignedIn(); storage.requireDestination()
        require(FlacPolicy.isFlacName(result.fileName) && result.size > 0 && result.size <= 1L shl 40) { "Only FLAC music files can be downloaded." }
        val owner = session.value.username!!
        val id = SoulseekPolicy.accountKey(owner, result.id)
        val previous = dao.transfer(id)
        if (previous?.state == SoulseekTransferState.COMPLETED.name) return
        if (previous?.state in listOf("DOWNLOADING", "QUEUED", "VALIDATING", "PUBLISHING")) return
        val row = previous?.copy(state = "QUEUED", error = null, updatedAt = System.currentTimeMillis())
            ?: TransferRow(id, owner, result.username, result.path, result.size)
        dao.putTransfer(row)
        try { startService(SoulseekTransferService::class.java); startTransfer(row) }
        catch (e: Throwable) { dao.putTransfer(row.copy(state = "PAUSED", error = friendly(e))); throw e }
    }
    private suspend fun startTransfer(row: TransferRow) {
        requireSignedIn(); check(row.owner == session.value.username) { "Sign in with the account that started this download." }
        if (activeTransfers.contains(row.id)) command("resume") { put("id", row.id) }
        else {
            command("download") { put("id", row.id); put("username", row.username); put("path", row.path); put("size", row.size); put("localDirectory", storage.stagingDirectory(row.id).absolutePath) }
            activeTransfers.add(row.id)
        }
    }
    fun pause(id: String): Job = work {
        val row = ownTransfer(id)
        publicationJobs.remove(id)?.cancel()
        archiveJobs.remove(id)?.cancel()
        if (activeTransfers.contains(id)) command("pause") { put("id", id) }
        dao.putTransfer(row.copy(state = "PAUSED", speed = 0.0, error = null))
    }
    fun resume(id: String): Job = work {
        requireSignedIn(); storage.requireDestination(); val row = ownTransfer(id)
        if (row.state == "COMPLETED") return@work
        check(row.provider == "Soulseek") { "Retry this album from Friday Drops to continue an Internet Archive download." }
        startService(SoulseekTransferService::class.java)
        if (row.stagePath?.let { File(it).exists() && File(it).length() == row.size } == true) publishAsync(row)
        else { dao.putTransfer(row.copy(state = "QUEUED", error = null)); startTransfer(row) }
    }
    fun retry(id: String): Job = resume(id)
    fun cancel(id: String): Job = work {
        val row = ownTransfer(id)
        publicationJobs.remove(id)?.cancel()
        archiveJobs.remove(id)?.cancel()
        if (activeTransfers.contains(id)) command("cancel") { put("id", id) }
        activeTransfers.remove(id)
        dao.putTransfer(row.copy(state = "CANCELLED", speed = 0.0, error = null))
        // Cancellation never removes music already published to the user's folder.
    }
    private suspend fun ownTransfer(id: String): TransferRow {
        val row = dao.transfer(id) ?: throw IllegalArgumentException("This download no longer exists.")
        check(row.owner == session.value.username) { "Sign in with the account that started this download." }
        return row
    }

    fun addContact(username: String): Job = work {
        requireSignedIn(); require(SoulseekPolicy.validUsername(username)) { "Enter an exact Soulseek username." }
        dao.addContact(ContactRow(session.value.username!!, username))
        command("contact") { put("username", username) }
    }
    fun openConversation(username: String): Job = work {
        requireSignedIn(); require(SoulseekPolicy.validUsername(username)) { "Enter an exact Soulseek username." }
        dao.addContact(ContactRow(session.value.username!!, username)); dao.markRead(session.value.username!!, username)
        command("contact") { put("username", username) }
    }
    fun markRead(username: String): Job = work { session.value.username?.let { dao.markRead(it, username) } }
    fun sendMessage(username: String, text: String): Job = work {
        requireSignedIn(); require(SoulseekPolicy.validUsername(username)) { "Enter an exact Soulseek username." }
        val body = text.trim(); require(body.isNotEmpty() && body.toByteArray(Charsets.UTF_8).size <= 4096) { "Messages must contain 1–4,096 bytes of text." }
        val owner = session.value.username!!; val id = UUID.randomUUID().toString()
        dao.addContact(ContactRow(owner, username))
        dao.insertMessage(MessageRow(id, owner, username, body, System.currentTimeMillis(), true, "PENDING", true))
        try { command("message") { put("username", username); put("text", body); put("clientId", id) } }
        catch (e: Throwable) { dao.messageState(id, "FAILED"); throw e }
    }

    fun setDestination(uri: Uri): Job = work {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        check(DocumentFile.fromTreeUri(context, uri)?.canWrite() == true) { "Choose a writable music folder." }
        prefs.edit().putString("destination", uri.toString()).apply(); _destination.value = uri
    }
    fun setSharedFolder(uri: Uri): Job = work {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        check(DocumentFile.fromTreeUri(context, uri)?.canRead() == true) { "Choose a readable folder to share." }
        shareRevision.incrementAndGet(); _sharing.value = false; shares.clear()
        session.value.username?.let { preferences.setSharing(it, false) }
        if (session.value.signedIn) command("shares") { put("files", JSONArray()) }
        session.value.username?.let { preferences.setSharedFolder(it,uri.toString()) }
        prefs.edit().putString("sharedFolder", uri.toString()).apply(); _sharedFolder.value = uri
    }
    fun setSharing(enabled: Boolean): Job {
        val revision = shareRevision.incrementAndGet()
        return work {
            val owner = session.value.username ?: error("Sign in before choosing a shared folder.")
            val epoch = generation
            if (shareRevision.get() != revision) return@work
            preferences.setSharing(owner, enabled)
            if (!enabled) { _sharing.value = false; shares.clear(); if (session.value.signedIn) command("shares") { put("files", JSONArray()) }; _uploads.value = emptyList(); updateServices(); return@work }
            requireSignedIn()
            val tree = sharedFolder.value?.let { DocumentFile.fromTreeUri(context, it) } ?: throw IllegalStateException("Choose a folder to share first.")
            check(tree.canRead() && tree.isDirectory) { "Sharing folder access was lost. Choose the folder again." }
            val manifest = JSONArray(); val indexed = mutableMapOf<String, SharedFile>()
            val visited = mutableSetOf<String>()
            suspend fun visit(dir: DocumentFile, prefix: String, depth: Int) {
                currentCoroutineContext().ensureActive()
                if (depth > 64 || !visited.add(dir.uri.toString())) return
                dir.listFiles().forEach { doc ->
                    val name = doc.name ?: return@forEach
                    val virtual = if (prefix.isEmpty()) SoulseekPolicy.safeName(name) else prefix + "\\" + SoulseekPolicy.safeName(name)
                    if (doc.isDirectory) visit(doc, virtual, depth + 1)
                    else if (doc.isFile && FlacPolicy.isFlacName(name)) {
                        val info = FlacPolicy.read(context, doc.uri, name) ?: return@forEach
                        val key = SoulseekPolicy.digest(doc.uri.toString()); val size = doc.length()
                        if (size <= 0) return@forEach
                        indexed[key] = SharedFile(doc.uri, name, size)
                        manifest.put(JSONObject().put("key", key).put("path", "Music\\$virtual").put("size", size).apply {
                            put("durationSec", (info.durationMs / 1000).toInt()); info.sampleRate?.let { put("sampleRate", it) }; info.bits?.let { put("bitDepth", it) }
                        })
                    }
                }
            }
            visit(tree, "", 0)
            accountMutex.withLock {
                if (shareRevision.get() != revision || epoch != generation || session.value.username != owner || !session.value.connected) return@withLock
                shares.clear(); shares.putAll(indexed); _sharing.value = true
                try { command("shares") { put("files", manifest) } } catch (e: Throwable) { _sharing.value = false; shares.clear(); throw e }
            }
        }
    }
    private data class SharedFile(val uri: Uri, val name: String, val size: Long)
    private fun openSharedFile(key: String): Int {
        if (!sharing.value || !session.value.signedIn) return -1
        val file = shares[key] ?: return -1
        return runCatching {
            check(FlacPolicy.read(context, file.uri, file.name) != null) { "Shared file is no longer valid FLAC" }
            val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r") ?: return -1
            if (!sharing.value) { descriptor.close(); return -1 }
            val seekable = runCatching { android.system.Os.lseek(descriptor.fileDescriptor, 0, android.system.OsConstants.SEEK_SET); true }.getOrDefault(false)
            if (seekable && descriptor.statSize == file.size) descriptor.detachFd() else {
                descriptor.close()
                // Cloud-backed providers may supply pipes. Spool only this requested file;
                // native uploads need a seekable descriptor for resume offsets.
                val temporary = File.createTempFile("share-", ".flac", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var copied = 0L
                            while (true) {
                                check(sharing.value && session.value.signedIn)
                                val count = input.read(buffer); if (count < 0) break
                                copied += count; check(copied <= file.size)
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error("Shared file is unavailable")
                    check(temporary.length() == file.size && FlacPolicy.isValid(temporary))
                    android.os.ParcelFileDescriptor.open(temporary, android.os.ParcelFileDescriptor.MODE_READ_ONLY).detachFd()
                } finally { temporary.delete() }
            }
        }.getOrDefault(-1)
    }

    private fun backgroundConsented(): Boolean = session.value.username?.let { preferences.background(it) } == true && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun setStayOnline(enabled: Boolean): Job = work {
        val owner = session.value.username ?: error("Sign in before choosing a connection mode.")
        if (enabled) { requireSignedIn(); startService(SoulseekOnlineService::class.java) }
        else stopService(SoulseekOnlineService::class.java)
        preferences.setBackground(owner, enabled); _stayOnline.value = enabled
        connection.updatePolicy()
    }
    fun onForegroundChanged(isForeground: Boolean): Job = work { connection.foregroundChanged(isForeground) }
    fun onExternalFlow(active: Boolean) { connection.externalFlow(active) }
    fun onNetworkChanged(available: Boolean): Job = work { connection.networkChanged(available) }
    private fun hasActiveTransfers() = archiveJobs.isNotEmpty() || transfers.value.any { it.state in listOf(SoulseekTransferState.QUEUED, SoulseekTransferState.DOWNLOADING, SoulseekTransferState.VALIDATING, SoulseekTransferState.PUBLISHING) } || uploads.value.any { it.state == "uploading" || it.state == "queued" }
    private fun startService(type: Class<*>) { ContextCompat.startForegroundService(context, Intent(context, type)) }
    private fun stopService(type: Class<*>) { context.stopService(Intent(context, type)) }
    private suspend fun updateServices() {
        if (!hasActiveTransfers()) stopService(SoulseekTransferService::class.java)
        connection.updatePolicy()
    }
    fun onServiceTimeout(): Job = work {
        clearSessionWork(SoulseekDisconnectReason.SERVICE_TIMEOUT.label, false)
        connection.disconnect(SoulseekDisconnectReason.SERVICE_TIMEOUT)
        _error.value = "Android stopped background activity. Your progress is saved. Open Runout and reconnect."
    }
    fun disconnectFromNotification(): Job = work {
        clearSessionWork(SoulseekDisconnectReason.MANUAL.label, false)
        connection.disconnect(SoulseekDisconnectReason.MANUAL)
    }

    private suspend fun handleEvent(event: JSONObject, owner: String) {
        when (event.optString("type")) {
            "session" -> when (event.optString("state")) {
                "connected" -> {
                    observeOwner(owner)
                    _sharedFolder.value = preferences.sharedFolder(owner)?.let(Uri::parse)
                    if (backgroundConsented()) {
                        try { startService(SoulseekOnlineService::class.java); _stayOnline.value = true }
                        catch (_: RuntimeException) { _error.value = "Background connection is unavailable. Open Account to check notification permission." }
                    }
                    _contacts.value.forEach { contact -> command("contact") { put("username", contact.username) } }
                    if (preferences.sharing(owner) && preferences.sharedFolder(owner) != null) setSharing(true)
                }
                "error", "disconnected", "paused" -> clearSessionWork(event.optString("reason", "Connection interrupted. Reconnect and resume."), false)
            }
            "search_result", "search_batch" -> searchMutex.withLock { if (event.optString("id") == searchId) {
                val files = event.optJSONArray("files") ?: JSONArray().put(event)
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val result = parseResult(file, file.optString("username", event.optString("username")))
                    if (FlacPolicy.isFlacName(result.fileName) && result.size > 0 && searchIndex.size < 5000) searchIndex.putIfAbsent(result.id, result)
                }
                _searchResults.value = searchIndex.values.toList()
            } }
            "search_done" -> searchMutex.withLock { if (event.optString("id") == searchId) {
                _searching.value = false
                _searchStatus.update { it.copy(phase = if (event.optBoolean("cancelled")) SearchPhase.CANCELLED else SearchPhase.COMPLETE, limited = event.optBoolean("limited") || searchIndex.size >= 5000) }
            } }
            "browse_result" -> {
                val requested = pendingBrowse.remove(event.optString("id")) ?: return
                if (event.optString("username") != requested.username || event.optString("directory").replace('\\', '/') != requested.directory) return
                val files = event.optJSONArray("files") ?: JSONArray()
                val valid = (0 until files.length()).map { parseResult(files.getJSONObject(it), requested.username) }
                    .filter { it.directory == requested.directory && FlacPolicy.isFlacName(it.fileName) && it.size > 0 }.distinctBy { it.id }
                    .sortedWith(compareBy({ it.fileName.lowercase(java.util.Locale.ROOT) }, { it.path }))
                if (_folderPreview.value?.requestId == event.optString("id")) _folderPreview.value = SoulseekFolderPreview(event.optString("id"), requested.username, requested.directory, valid, loading = false)
            }
            "transfer" -> {
                val id = event.optString("id"); val row = dao.transfer(id) ?: return
                if (row.owner != owner || row.state in listOf("COMPLETED", "CANCELLED")) return
                val state = event.optString("state").uppercase()
                if (row.state == "PAUSED" && state !in listOf("PAUSED", "CANCELLED")) return
                val updated = row.copy(bytes = event.optLong("bytes", row.bytes).coerceIn(0, row.size), state = if (state == "COMPLETED") "VALIDATING" else state,
                    queuePosition = if (event.has("queuePosition") && !event.isNull("queuePosition")) event.optLong("queuePosition") else null,
                    speed = event.optDouble("speed", 0.0), stagePath = event.optString("path").ifBlank { row.stagePath }, error = if (event.isNull("error")) null else event.optString("error").takeIf { it.isNotBlank() }, updatedAt = System.currentTimeMillis())
                if (runCatching { SoulseekTransferState.valueOf(updated.state) }.isFailure) return
                dao.putTransfer(updated)
                if (state == "COMPLETED") { activeTransfers.remove(id); publishAsync(updated) }
                if (state == "FAILED" || state == "CANCELLED") activeTransfers.remove(id)
            }
            "message" -> {
                val serverId = event.getLong("id"); val username = event.getString("username")
                val id = SoulseekPolicy.messageId(owner, serverId)
                // Commit first. A crash before the ack simply replays the unique server message ID.
                db.withTransaction {
                    dao.addContact(ContactRow(owner, username))
                    dao.insertMessage(MessageRow(id, owner, username, event.getString("text"), event.optLong("timestamp", System.currentTimeMillis()).let { if (it < 10_000_000_000L) it * 1000 else it }, false, "RECEIVED"))
                }
                command("ack") { put("id", serverId) }
            }
            "message_sent" -> dao.messageState(event.getString("clientId"), "SENT")
            "upload" -> {
                val upload = SoulseekUpload(event.getString("username"), event.getString("path"), event.optLong("bytes"), event.optLong("total"), event.optString("state"))
                _uploads.update { list -> (list.filterNot { it.username == upload.username && it.path == upload.path } + upload).takeLast(100) }
                if (upload.state == "uploading" || upload.state == "queued") {
                    try { startService(SoulseekTransferService::class.java) }
                    catch (e: RuntimeException) {
                        _sharing.value = false; shares.clear(); _uploads.value = emptyList()
                        command("shares") { put("files", JSONArray()) }
                        _error.value = "Android paused background sharing. Open Runout to enable sharing again."
                    }
                } else updateServices()
            }
            "presence" -> dao.presence(owner, event.getString("username"), event.optBoolean("online"))
            "error" -> {
                val id = event.optString("id"); val message = event.optString("error", "Soulseek could not complete this action.")
                if (event.optString("operation") == "message" && id.isNotBlank()) dao.messageState(id, "FAILED")
                if (id == searchId) { _searching.value = false; _searchStatus.update { it.copy(phase = SearchPhase.FAILED, error = message) } }
                if (id == folderPreview.value?.requestId) _folderPreview.update { it?.copy(loading = false, error = message) }
                pendingBrowse.remove(id); _error.value = message
            }
        }
    }
    private fun parseResult(o: JSONObject, username: String) = SoulseekSearchResult(username, o.optString("path"), o.optLong("size"), o.intOrNull("durationSec"), o.intOrNull("sampleRate"), o.intOrNull("bitDepth"), o.intOrNull("bitrate"), o.optInt("freeSlots"), o.optLong("uploadSpeed"), if (o.has("queueLength") && !o.isNull("queueLength")) o.optLong("queueLength") else null)
    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key).takeIf { it > 0 } else null
    private fun publishAsync(row: TransferRow) {
        if (publicationJobs.containsKey(row.id)) return
        publicationJobs[row.id] = scope.launch(Dispatchers.IO) {
            try { publishSoulseek(row) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { dao.transfer(row.id)?.let { dao.putTransfer(it.copy(state = "FAILED", error = friendly(e), speed = 0.0)) }; _error.value = friendly(e) }
            finally { publicationJobs.remove(row.id) }
        }
    }
    private suspend fun publishSoulseek(initial: TransferRow) = publicationMutex.withLock {
        requireSignedIn(); check(initial.owner == session.value.username)
        val directory = storage.stagingDirectory(initial.id).canonicalFile
        val stage = initial.stagePath?.let(::File)?.canonicalFile ?: throw IOException("The completed download was not found.")
        require(stage.parentFile == directory && stage.length() == initial.size && FlacPolicy.isFlacName(stage.name)) { "The completed download has an invalid path or size." }
        val info = FlacPolicy.read(stage, wantPicture = true) ?: throw IOException("This file is not valid FLAC music.")
        val remoteDirectory = initial.path.replace('\\', '/').substringBeforeLast('/', "")
        val albumId = SoulseekPolicy.albumId(initial.username, remoteDirectory)
        val albumTitle = info.album ?: remoteDirectory.substringAfterLast('/').ifBlank { "Soulseek downloads" }
        val folder = storage.albumDirectory(albumTitle, albumId)
        var row = dao.transfer(initial.id) ?: return@withLock
        val finalName = initial.id.take(10) + " " + SoulseekPolicy.safeName(stage.name, "Track.flac")
        val partialName = ".${initial.id}.runout-part"
        val target = row.publishedUri?.let { DocumentFile.fromSingleUri(context, Uri.parse(it)) }?.takeIf { it.exists() }
            ?: folder.findFile(partialName)
            ?: folder.createFile("application/octet-stream", partialName)
            ?: throw IOException("Could not create a file in your download folder.")
        row = row.copy(state = "PUBLISHING", publishedUri = target.uri.toString()); dao.putTransfer(row)
        storage.copyValidated(stage, target.uri, initial.size)
        currentCoroutineContext().ensureActive(); requireSignedIn(); check(session.value.username == row.owner)
        if (target.name != finalName) check(target.renameTo(finalName)) { "This folder cannot publish a completed FLAC file. Choose another download folder and retry." }
        row = row.copy(publishedUri = target.uri.toString()); dao.putTransfer(row)
        val track = Track("soulseek-track-${row.id}", target.uri.toString(), info.title ?: stage.nameWithoutExtension, info.track ?: 1, info.disc ?: 1, info.durationMs, target.name ?: stage.name, info.trackGainDb)
        val existing = dao.album(albumId)?.let { AlbumJson.fromJson(JSONObject(it.json)) }
        val quality = AudioQuality("FLAC", listOfNotNull(existing?.quality?.bitsPerSample, info.bits).minOrNull(), listOfNotNull(existing?.quality?.sampleRateHz, info.sampleRate).minOrNull())
        val cover = info.picture?.let { bytes -> File(context.filesDir, "soulseek-art/$albumId.jpg").apply { parentFile?.mkdirs(); writeBytes(bytes) }.absolutePath } ?: existing?.coverPath
        val album = Album(albumId, albumTitle, info.albumArtist ?: info.artist ?: existing?.artist ?: "Unknown artist", info.year ?: existing?.year, folder.uri.toString(),
            (existing?.tracks.orEmpty().filterNot { it.id == track.id } + track).sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.fileName })), quality, coverPath = cover,
            source = Source.SOULSEEK, albumGainDb = info.albumGainDb, owned = false, from = initial.username)
        db.withTransaction { dao.putAlbum(DownloadAlbumRow(albumId, AlbumJson.toJson(album).toString())); dao.putTransfer(row.copy(state = "COMPLETED", bytes = row.size, speed = 0.0, error = null)) }
        stage.delete()
    }
    /** Internet Archive stays an independent HTTPS source; only publication uses this account gate. */
    suspend fun publishArchiveDownload(album: Album, stagingDir: File): Album = publicationMutex.withLock {
        initialized.await(); requireSignedIn()
        val expectedOwner = session.value.username
        val expectedGeneration = generation
        val folder = storage.albumDirectory(album.title, album.id)
        val tracks = album.tracks.mapIndexed { index, track ->
            currentCoroutineContext().ensureActive(); requireSignedIn()
            check(session.value.username == expectedOwner && generation == expectedGeneration) { "The Soulseek account changed. Retry this download." }
            val source = if (track.uri.startsWith("file:")) File(Uri.parse(track.uri).path!!) else File(stagingDir, track.fileName)
            require(source.canonicalFile.parentFile == stagingDir.canonicalFile && FlacPolicy.read(source) != null) { "Internet Archive supplied an invalid FLAC file." }
            val name = SoulseekPolicy.safeName(track.fileName.ifBlank { source.name }, "Track.flac")
            val partialName = ".runout-${SoulseekPolicy.digest(album.id).take(16)}-$index.part"
            val target = folder.findFile(name)?.takeIf { it.isFile } ?: folder.findFile(partialName) ?: folder.createFile("application/octet-stream", partialName) ?: throw IOException("Could not create a file in your download folder.")
            storage.copyValidated(source, target.uri, source.length())
            currentCoroutineContext().ensureActive(); requireSignedIn()
            if (target.name != name) check(target.renameTo(name)) { "This folder cannot publish a completed FLAC file. Choose another download folder and retry." }
            track.copy(uri = target.uri.toString(), fileName = target.name ?: name)
        }
        album.copy(folderUri = folder.uri.toString(), tracks = tracks)
    }
    /** Tracks Archive's existing HTTPS downloader without sending it Soulseek credentials. */
    suspend fun withArchiveDownload(
        identifier: String,
        title: String,
        block: suspend (onProgress: (Long, Long) -> Unit) -> Result<Album>,
    ): Result<Album> {
        initialized.await()
        return try {
            requireSignedIn(); storage.requireDestination()
            val owner = session.value.username!!
            val epoch = generation
            val id = SoulseekPolicy.accountKey(owner, "archive:$identifier")
            check(!archiveJobs.containsKey(id)) { "This Internet Archive album is already downloading." }
            val job = currentCoroutineContext().job
            archiveJobs[id] = job
            val row = TransferRow(id, owner, "Internet Archive", title, 1_000_000L, state = "DOWNLOADING", provider = "Internet Archive")
            dao.putTransfer(row)
            try {
                startService(SoulseekTransferService::class.java)
                val result = block { bytes, total ->
                    if (total > 0 && !job.isCancelled) scope.launch(Dispatchers.IO) {
                        val existing = dao.transfer(id)
                        if (existing != null && existing.state == "DOWNLOADING" && archiveJobs[id] === job) {
                            dao.putTransfer(existing.copy(bytes = bytes.coerceIn(0, total), size = total))
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(session.value.username == owner && session.value.signedIn && generation == epoch) { "The Soulseek session changed. Sign in and retry." }
                val current = dao.transfer(id) ?: row
                dao.putTransfer(current.copy(state = if (result.isSuccess) "COMPLETED" else "FAILED", bytes = if (result.isSuccess) current.size else current.bytes, error = result.exceptionOrNull()?.let(::friendly)))
                result
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    dao.transfer(id)?.let { current -> if (current.state != "CANCELLED") dao.putTransfer(current.copy(state = "PAUSED", error = "Interrupted. Retry this album from Friday Drops.")) }
                }
                throw e
            } catch (e: Throwable) {
                dao.transfer(id)?.let { dao.putTransfer(it.copy(state = "FAILED", error = friendly(e))) }
                Result.failure(e)
            } finally { archiveJobs.remove(id) }
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { Result.failure(e) }
    }
    private fun friendly(e: Throwable): String = when (e) {
        is SecurityException -> "Folder access was lost. Choose the folder again and retry."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "This action could not be completed. Please retry."
    }
}
