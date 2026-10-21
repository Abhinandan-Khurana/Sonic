package app.runout.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import app.runout.model.Album
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ScanState {
    data class Scanning(val progress: ScanProgress) : ScanState
    data class FindingCovers(val done: Int, val total: Int) : ScanState
}

/** The user's shelf from their folder, cached on the phone between launches. */
class LibraryRepository(
    private val context: Context,
    private val scanner: LibraryScanner,
    private val artwork: ArtworkRepository,
    private val scope: CoroutineScope,
    private val lookupCoversOnline: Boolean = false,
) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "library.json")
    private val coverPrefs = context.getSharedPreferences("cover-lookup", Context.MODE_PRIVATE)
    private val _coverLookupEnabled = MutableStateFlow(coverPrefs.getBoolean("enabled", lookupCoversOnline))
    val coverLookupEnabled: StateFlow<Boolean> = _coverLookupEnabled.asStateFlow()
    private val _coverLookupNoticePending = MutableStateFlow(run {
        if (!coverPrefs.contains("policy-initialized")) {
            val existing = prefs.contains(KEY_FOLDER) || listOf("library.json", "pressings.json", "received.json", "drops.json")
                .any { File(context.filesDir, it).exists() } ||
                context.getSharedPreferences("soulseek-setup", Context.MODE_PRIVATE).getBoolean("onboarded", false)
            coverPrefs.edit(commit = true) { putBoolean("policy-initialized", true); putBoolean("notice-pending", existing) }
        }
        coverPrefs.getBoolean("notice-pending", false)
    })
    val coverLookupNoticePending: StateFlow<Boolean> = _coverLookupNoticePending.asStateFlow()

    fun setCoverLookupEnabled(enabled: Boolean) {
        coverPrefs.edit { putBoolean("enabled", enabled) }
        _coverLookupEnabled.value = enabled
    }

    fun dismissCoverLookupNotice() {
        coverPrefs.edit { putBoolean("notice-pending", false) }
        _coverLookupNoticePending.value = false
    }

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _folder = MutableStateFlow(prefs.getString(KEY_FOLDER, null)?.let(Uri::parse))
    val folder: StateFlow<Uri?> = _folder.asStateFlow()

    private val _scan = MutableStateFlow<ScanState?>(null)
    val scan: StateFlow<ScanState?> = _scan.asStateFlow()

    private val _report = MutableStateFlow<ScanReport?>(null)
    val report: StateFlow<ScanReport?> = _report.asStateFlow()

    private var scanJob: Job? = null
    private val scanLock = Any()

    init {
        scope.launch {
            val cached = withContext(Dispatchers.IO) { AlbumJson.readValidatedList(context, cacheFile) }
            if (_albums.value.isEmpty()) _albums.value = cached
            // Cached shelf but a folder that may have changed: refresh quietly.
            val tree = _folder.value
            if (tree != null && cached.isEmpty()) refreshQuietly(tree)
        }
    }

    fun setFolder(uri: Uri): Job? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        _folder.value = uri
        // Keep the previous shelf until the selected root has actually been listed.
        return rescan()
    }

    /**
     * Rebuilds the shelf from the folder alone: forgets the saved index, so albums that are gone
     * or broken drop off, and the report lists every file that couldn't be read and why. Music
     * files are only read, never changed.
     */
    fun repair(): Job? = rescan(fresh = true)

    fun rescan(fresh: Boolean = false): Job? {
        val tree = _folder.value ?: return null
        return synchronized(scanLock) {
            scanJob?.cancel()
            startScan(tree, fresh)
        }
    }

    /**
     * The first scan of a saved folder, started quietly. It never cancels a scan that is already
     * running, and never starts a second one beside it: a scan started since startup owns the
     * shelf, and cancelling it would leave the shelf empty — the "albums vanish" phone bug.
     */
    private fun refreshQuietly(tree: Uri): Job? = synchronized(scanLock) {
        if (scanJob?.isActive == true) null else startScan(tree, fresh = false)
    }

    private fun startScan(tree: Uri, fresh: Boolean): Job {
        // LAZY so that [scanJob] is set before the body can run: a scan that starts late must
        // already be the live one when another scan asks whether anything is running.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            _scan.value = ScanState.Scanning(ScanProgress(0, 0))
            _report.value = null
            val previous = _albums.value
            if (fresh) _albums.value = emptyList()
            val found = mutableListOf<Album>()
            val report = try {
                scanner.scan(
                    treeUri = tree,
                    onProgress = { p -> _scan.value = ScanState.Scanning(p) },
                    onAlbum = { s ->
                        val album = withContext(Dispatchers.IO) {
                            val cover = artwork.cachedFile(s.album.id)
                                ?: s.embeddedArt?.let { artwork.saveBytes(s.album.id, it) }
                                ?: s.folderImage?.let { artwork.copyFromUri(s.album.id, it) }
                            s.album.withCover(cover)
                        }
                        found += album
                        // Show each album the moment it's read.
                        _albums.update { list -> sortShelf(list.filterNot { it.id == album.id } + album) }
                    },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ScanReport(0, 0, 0, found.size, emptyMap(), error = e.message ?: e.javaClass.simpleName)
            }
            _report.value = report
            if (report.error != null && found.isEmpty()) {
                // Nothing was listed: keep the shelf rather than empty it.
                _albums.value = previous
                _scan.value = null
                return@launch
            }
            // A partial scan (some files unreadable, or the listing broke off) can't prove that the
            // albums it didn't see were removed, so keep them next to everything it did read.
            val partial = report.error != null || report.unreadable > 0
            _albums.value = sortShelf(if (partial && !fresh) mergePartial(previous, found) else found)
            withContext(Dispatchers.IO) { AlbumJson.writeList(cacheFile, _albums.value) }

            if (coverLookupEnabled.value) {
                val missing = _albums.value.filter { it.coverPath == null }
                missing.forEachIndexed { i, album ->
                    if (!coverLookupEnabled.value) return@forEachIndexed
                    _scan.value = ScanState.FindingCovers(i, missing.size)
                    val file = artwork.lookupOnline(album.id, album.artist, album.title)
                    if (file != null) {
                        val updated = withContext(Dispatchers.IO) { album.withCover(file) }
                        _albums.update { list -> list.map { if (it.id == album.id) updated else it } }
                    }
                }
                if (missing.isNotEmpty()) withContext(Dispatchers.IO) { AlbumJson.writeList(cacheFile, _albums.value) }
            }
            _scan.value = null
        }
        scanJob = job
        job.start()
        return job
    }

    private fun Album.withCover(file: File?): Album =
        if (file == null) this else copy(coverPath = file.absolutePath, tint = ArtworkRepository.averageColor(file))

    companion object {
        private const val KEY_FOLDER = "folder"

        /**
         * A partial scan's shelf: everything it read, plus whatever it couldn't see from before.
         * An album read only in part keeps its earlier tracks next to the ones read now.
         */
        fun mergePartial(previous: List<Album>, found: List<Album>): List<Album> {
            val byId = found.associateBy { it.id }
            val merged = found.map { now ->
                val before = previous.firstOrNull { it.id == now.id } ?: return@map now
                val missing = before.tracks.filter { old -> now.tracks.none { it.id == old.id } }
                if (missing.isEmpty()) now
                else now.copy(tracks = (now.tracks + missing).sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.fileName })))
            }
            return merged + previous.filter { it.id !in byId }
        }

        fun sortShelf(list: List<Album>): List<Album> =
            list.sortedWith(compareBy({ it.artist.lowercase() }, { it.year ?: 0 }, { it.title.lowercase() }))
    }
}
