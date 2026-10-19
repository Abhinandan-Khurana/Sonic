package app.runout.discovery

import app.runout.model.Album
import app.runout.soulseek.SoulseekRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class)
class DiscoveryRepository(albums: StateFlow<List<Album>>, soulseek: SoulseekRepository, scope: CoroutineScope) {
    private data class LocalSelection(val query: String, val options: DiscoveryOptions, val matches: List<LocalMatch>)
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val options = MutableStateFlow(DiscoveryOptions())
    private val index = albums.map { LocalMusicIndex(it) }.flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, LocalMusicIndex(emptyList()))
    // Peer batches must not rescan the local library. Rebuild local matches only
    // when the query, options or library revision changes.
    private val selection = combine(_query.debounce(150), options, index) { q, filters, localIndex ->
        LocalSelection(q, filters, if (filters.source == SearchSource.SOULSEEK) emptyList() else localIndex.search(q,filters.kind))
    }.flowOn(Dispatchers.Default)
    val state = combine(selection, soulseek.searchResults, soulseek.searchStatus) { request, remote, status ->
        DiscoveryState(request.query,request.options,
            local = request.matches,
            remote = if (request.options.source == SearchSource.LOCAL || normalize(request.query) != normalize(status.query)) emptyList() else remoteAlbums(request.query,remote,request.options),
            online = status)
    }.flowOn(Dispatchers.Default).stateIn(scope,SharingStarted.Eagerly,DiscoveryState())
    fun setQuery(value: String) { _query.value = value.take(256) }
    fun setOptions(value: DiscoveryOptions) { options.value = value }
    fun broaden() {
        val text = query.value.replace(Regex("\\([^)]*\\)|\\[[^]]*\\]|\\b(19|20)\\d{2}\\b")," ")
        _query.value = text.trim().replace(Regex("\\s+")," ").ifBlank { query.value }
    }
}
