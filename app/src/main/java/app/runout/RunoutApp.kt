package app.runout

import android.app.Application
import app.runout.account.AccountRepository
import app.runout.account.RelayPool
import app.runout.account.Relays
import app.runout.drops.DropsRepository
import app.runout.library.ArtworkRepository
import app.runout.library.LibraryRepository
import app.runout.library.LibraryScanner
import app.runout.model.Album
import app.runout.net.DefaultHttp
import app.runout.net.Http
import app.runout.playback.Deck
import app.runout.playback.VinylSfx
import app.runout.pressing.PressingRepository
import app.runout.sharing.ShareRepository
import app.runout.social.SocialRepository
import app.runout.sound.SoundEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
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
@OptIn(kotlinx.coroutines.FlowPreview::class)
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
    val drops = DropsRepository(app, http)
    val relays: Relays = RelayPool(RelayPool.DEFAULT)
    val accounts = AccountRepository(app, relays, social)

    /** One crate: your folder, records friends sent, Friday Drops you claimed and your own pressings. */
    val shelf: StateFlow<List<Album>> = combine(library.albums, share.received, drops.albums, pressing.records) { lib, rx, dr, pr ->
        mergeShelf(lib, rx, dr, pr)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Pure mode also switches off the turntable's own effects.
        scope.launch {
            sound.settings.map { it.pure }.distinctUntilChanged().drop(1).collect { deck.onPureChanged(it) }
        }
        // Keep the public profile in step with the shelf, once things have settled for a few seconds.
        scope.launch {
            combine(accounts.account, shelf) { acc, list -> if (acc == null) null else Triple(acc, list.map { it.id }, list) }
                .filterNotNull()
                .distinctUntilChanged { a, b -> a.first == b.first && a.second == b.second }
                .debounce(8_000)
                .collect { (_, _, list) -> runCatching { accounts.publish(list) } }
        }
    }

    companion object {
        fun mergeShelf(vararg sources: List<Album>): List<Album> =
            LibraryRepository.sortShelf(sources.flatMap { it }.distinctBy { it.id })
    }
}
