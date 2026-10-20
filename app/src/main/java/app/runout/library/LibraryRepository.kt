package app.runout.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.runout.model.Album
import kotlinx.coroutines.CoroutineScope
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
            coverPrefs.edit().putBoolean("policy-initialized", true).putBoolean("notice-pending", existing).commit()
        }
        coverPrefs.getBoolean("notice-pending", false)
    })
    val coverLookupNoticePending: StateFlow<Boolean> = _coverLookupNoticePending.asStateFlow()

    fun setCoverLookupEnabled(enabled: Boolean) {
        coverPrefs.edit().putBoolean("enabled", enabled).apply()
        _coverLookupEnabled.value = enabled
    }

    fun dismissCoverLookupNotice() {
        coverPrefs.edit().putBoolean("notice-pending", false).apply()
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

    init {
        scope.launch {
            val cached = withContext(Dispatchers.IO) { AlbumJson.readValidatedList(context, cacheFile) }
            if (_albums.value.isEmpty()) _albums.value = cached
            // Cached shelf but a folder that may have changed: refresh quietly.
            if (_folder.value != null && cached.isEmpty()) rescan()
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

    fun rescan(): Job? {
        val tree = _folder.value ?: return null
        scanJob?.cancel()
        val job = scope.launch {
            _scan.value = ScanState.Scanning(ScanProgress(0, 0))
            _report.value = null
            val previous = _albums.value
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
            if (report.error != null || report.unreadable > 0) {
                // Partial scans cannot prove that absent records/tracks were intentionally removed.
                _albums.value = previous
                _scan.value = null
                return@launch
            }
            _albums.value = sortShelf(found)
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
        return job
    }

    private fun Album.withCover(file: File?): Album =
        if (file == null) this else copy(coverPath = file.absolutePath, tint = ArtworkRepository.averageColor(file))

    companion object {
        private const val KEY_FOLDER = "folder"

        fun sortShelf(list: List<Album>): List<Album> =
            list.sortedWith(compareBy({ it.artist.lowercase() }, { it.year ?: 0 }, { it.title.lowercase() }))
    }
}
