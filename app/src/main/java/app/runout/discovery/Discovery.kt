package app.runout.discovery

import app.runout.model.Album
import app.runout.model.Track
import app.runout.soulseek.SoulseekSearchResult
import app.runout.soulseek.SoulseekSearchStatus
import java.text.Normalizer
import java.util.Locale

enum class SearchSource(val label: String) { ALL("All sources"), LOCAL("On this device"), SOULSEEK("Soulseek") }
enum class SearchKind(val label: String) { ALL("All"), SONGS("Songs"), ALBUMS("Albums") }
enum class SearchSort(val label: String) { MATCH("Best match"), AVAILABILITY("Availability"), QUALITY("Quality") }
enum class SearchQuality(val label: String) { ANY("Any quality"), CD("16-bit"), HI_RES("24-bit+"), UNKNOWN("Unknown") }
data class DiscoveryOptions(val source: SearchSource = SearchSource.ALL, val kind: SearchKind = SearchKind.ALL, val sort: SearchSort = SearchSort.MATCH, val quality: SearchQuality = SearchQuality.ANY, val availableOnly: Boolean = false)
data class LocalMatch(val album: Album, val track: Track?, val score: Int) {
    val id = if (track == null) "album:${album.id}" else "track:${album.id}:${track.id}"
}
data class RemoteAlbum(val username: String, val directory: String, val tracks: List<SoulseekSearchResult>, val score: Int) {
    val id = "$username\u0000$directory"
    val title = directory.substringAfterLast('/').ifBlank { "Shared FLAC" }
    val available = tracks.any { it.freeSlots > 0 }
    val queue = tracks.mapNotNull { it.queueLength }.minOrNull()
    val speed = tracks.maxOfOrNull { it.uploadSpeed } ?: 0
    val bits = tracks.mapNotNull { it.bitDepth }.minOrNull()
    val rate = tracks.mapNotNull { it.sampleRate }.minOrNull()
}
data class DiscoveryState(
    val query: String = "",
    val options: DiscoveryOptions = DiscoveryOptions(),
    val local: List<LocalMatch> = emptyList(),
    val remote: List<RemoteAlbum> = emptyList(),
    val online: SoulseekSearchStatus = SoulseekSearchStatus(),
)

/** Built once per library revision; searches use metadata only, never open audio files. */
class LocalMusicIndex(albums: List<Album>) {
    private data class Entry(val album: Album, val track: Track?, val title: String, val text: String)
    private val entries = albums.flatMap { album ->
        val albumText = normalize("${album.artist} ${album.title}")
        listOf(Entry(album,null,normalize(album.title),albumText)) + album.tracks.map { track ->
            Entry(album,track,normalize(track.title),"$albumText ${normalize(track.title)}")
        }
    }
    fun search(query: String, kind: SearchKind = SearchKind.ALL): List<LocalMatch> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        val tokens = normalized.split(' ')
        return entries.asSequence().filter { kind == SearchKind.ALL || (it.track == null) == (kind == SearchKind.ALBUMS) }
            .mapNotNull { entry ->
                val score = matchScore(normalized,tokens,entry.title,entry.text,true)
                if (score == 0) null else LocalMatch(entry.album,entry.track,score)
            }.sortedWith(compareByDescending<LocalMatch> { it.score }.thenBy { it.album.title }.thenBy { it.id }).toList()
    }
}

private val marks = Regex("\\p{M}+")
private val punctuation = Regex("[^\\p{L}\\p{N}]+")
private val spaces = Regex("\\s+")
private val trackNumber = Regex("^\\d+[ ._-]+")
fun normalize(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFD).replace(marks,"")
    .lowercase(Locale.ROOT).replace(punctuation," ").trim().replace(spaces," ")

private fun matchScore(query: String, tokens: List<String>, title: String, text: String, fuzzy: Boolean): Int {
    if (query == title) return 1000
    if (query.isBlank()) return 1
    var score = 0
    val words by lazy { text.split(' ') }
    val titleWords = title.split(' ')
    for (token in tokens) {
        score += when {
            token in titleWords -> 100
            title.contains(token) -> 70
            text.contains(token) -> 40
            fuzzy && token.length >= 4 && words.any { oneEditAway(token,it) } -> 10
            else -> return 0
        }
    }
    return score + if (title.contains(query)) 150 else 0
}
private fun oneEditAway(a: String, b: String): Boolean {
    if (kotlin.math.abs(a.length-b.length) > 1) return false
    var i=0; var j=0; var differences=0
    while(i<a.length && j<b.length) {
        if(a[i]==b[j]) { i++;j++;continue }
        differences++
        if(differences>1) return false
        if (i+1<a.length && j+1<b.length && a[i]==b[j+1] && a[i+1]==b[j]) { i+=2;j+=2 }
        else if(a.length>b.length) i++ else if(b.length>a.length) j++ else { i++;j++ }
    }
    return differences + (a.length-i) + (b.length-j) <= 1
}

/** Group the complete shortlist before UI virtualization; never mix peer folders or editions. */
fun remoteAlbums(query: String, results: List<SoulseekSearchResult>, options: DiscoveryOptions): List<RemoteAlbum> {
    val normalized = normalize(query); val tokens = normalized.split(' ').filter { it.isNotBlank() }
    val candidates = results.asSequence().filter { !options.availableOnly || it.freeSlots > 0 }.filter {
        when(options.quality) { SearchQuality.ANY -> true; SearchQuality.CD -> it.bitDepth == 16; SearchQuality.HI_RES -> (it.bitDepth ?: 0) >= 24; SearchQuality.UNKNOWN -> it.bitDepth == null || it.sampleRate == null }
    }.distinctBy { it.id }.groupBy { it.username to it.directory }.map { (folder, tracks) ->
        val title = normalize(folder.second.substringAfterLast('/'))
        val titles = tracks.map { normalize(it.fileName.substringBeforeLast('.').replace(trackNumber,"")) }
        val text = normalize(folder.second) + " " + titles.joinToString(" ")
        // Shared folder text is checked once per query token, rather than once for
        // every track. Large multi-disc folders otherwise repeat quadratic scans.
        val folderMatches = tokens.map { text.contains(it) }
        val score = if (options.kind == SearchKind.ALBUMS) matchScore(normalized,tokens,title,text,false)
            else titles.maxOf { trackTitle ->
                if (trackTitle == normalized) 1000 else if (normalized.isBlank()) 1 else {
                    val words = trackTitle.split(' ')
                    var matched = true
                    var value = 0
                    tokens.forEachIndexed { i, token ->
                        value += when {
                            token in words -> 100
                            trackTitle.contains(token) -> 70
                            folderMatches[i] -> 40
                            else -> { matched = false; 0 }
                        }
                    }
                    if (matched) value + if (trackTitle.contains(normalized)) 150 else 0 else 0
                }
            }
        // Precompute collation keys: a comparator may inspect each filename many times.
        val ordered = tracks.map { it to it.fileName.lowercase(Locale.ROOT) }
            .sortedWith(compareBy({ it.second }, { it.first.path })).map { it.first }
        RemoteAlbum(folder.first,folder.second,ordered,score)
    }.filter { album -> album.score > 0 && album.tracks.all { it.fileName.substringAfterLast('.').equals("flac", true) } }
    val available = compareByDescending<RemoteAlbum> { it.available }.thenBy { it.queue ?: Long.MAX_VALUE }.thenByDescending { it.speed }
    val comparator = when(options.sort) {
        SearchSort.MATCH -> compareByDescending<RemoteAlbum> { it.score }.then(available)
        SearchSort.AVAILABILITY -> available.thenByDescending { it.score }
        SearchSort.QUALITY -> compareByDescending<RemoteAlbum> { it.bits ?: 0 }.thenByDescending { it.rate ?: 0 }.thenByDescending { it.score }.then(available)
    }.thenBy { it.id }
    return candidates.sortedWith(comparator)
}
