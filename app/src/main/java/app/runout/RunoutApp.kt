package app.runout

import android.app.Application
import app.runout.drops.DropsRepository
import app.runout.library.ArtworkRepository
import app.runout.library.LibraryRepository
import app.runout.library.LibraryScanner
import app.runout.model.Album
import app.runout.model.Source
import app.runout.net.DefaultHttp
import app.runout.net.Http
import app.runout.playback.Deck
import app.runout.playback.VinylSfx
import app.runout.pressing.PressingRepository
import app.runout.sharing.ShareRepository
import app.runout.social.SocialRepository
import app.runout.sound.SoundEngine
import app.runout.soulseek.SoulseekRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RunoutApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Everything the app shares, created once. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http: Http = DefaultHttp
    val artwork = ArtworkRepository(app)
    val library = LibraryRepository(app, LibraryScanner(app), artwork, scope)
    val sound = SoundEngine(app)
    val sfx = VinylSfx(scope)
    val deck = Deck(app, sfx, scope, sound)
    val social = SocialRepository(app)
    val share = ShareRepository(app, social)
    val pressing = PressingRepository(app)
    val soulseek = SoulseekRepository(app, scope)
    val drops = DropsRepository(app, http, canDownload = soulseek::canDownload, publishDownload = soulseek::publishArchiveDownload)

    /** One crate: your folder, records friends sent, Friday Drops you claimed and your own pressings. */
    val shelf: StateFlow<List<Album>> = combine(library.albums, share.received, drops.albums, pressing.records, soulseek.downloadAlbums) { lib, rx, dr, pr, sl ->
        mergeShelf(lib, rx, dr, pr, sl)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Pure mode also switches off the turntable's own effects.
        scope.launch {
            sound.settings.map { it.pure }.distinctUntilChanged().drop(1).collect { deck.onPureChanged(it) }
        }
        // Soulseek is the online identity. Legacy Nostr keys and friend cards stay on disk;
        // no relay connections or automatic shelf publication are started.
        scope.launch {
            soulseek.session.collect { session ->
                if (session.signedIn) social.rename(session.username!!)
            }
        }
    }

    companion object {
        fun mergeShelf(vararg sources: List<Album>): List<Album> {
            val albums = sources.flatMap { it }.distinctBy { it.id }
            val downloaded = albums.filter { it.source == Source.DROP || it.source == Source.SOULSEEK }
                .flatMap { it.tracks }.map { fileIdentity(it.uri) }.toSet()
            return LibraryRepository.sortShelf(albums.mapNotNull { album ->
                if (album.source != Source.FOLDER) album else {
                    val tracks = album.tracks.filterNot { fileIdentity(it.uri) in downloaded }
                    if (tracks.isEmpty()) null else album.copy(tracks = tracks)
                }
            })
        }

        private fun fileIdentity(value: String): String = runCatching {
            val uri = android.net.Uri.parse(value)
            if (uri.scheme == "content") runCatching {
                "${uri.authority}:${android.provider.DocumentsContract.getDocumentId(uri)}"
            }.getOrElse { uri.normalizeScheme().toString() } else uri.normalizeScheme().toString()
        }.getOrDefault(value)
    }
}
