package app.runout

import app.runout.discovery.*
import app.runout.model.*
import app.runout.soulseek.SoulseekSearchResult
import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    private fun album(title: String = "Moonlight", artist: String = "Beyoncé") = Album("a-$title",title,artist,2024,"",listOf(Track("t","file:///a.flac","Opening",1,1,1000,"01 Opening.flac")),AudioQuality("FLAC",24,96000))
    @Test fun `local metadata matching accepts accents punctuation and typos without opening files`() {
        val index = LocalMusicIndex(listOf(album()))
        assertEquals(2,index.search("BEYONCE — Moonlight").size)
        assertEquals(1,index.search("beyonce openign",SearchKind.SONGS).size)
        assertTrue(index.search("unrelated").isEmpty())
        assertNull(index.search("Moonlight",SearchKind.ALBUMS).single().track)
        assertNotNull(index.search("Opening",SearchKind.SONGS).single().track)
    }
    @Test fun `grouping retains the full album across old page boundaries and separates editions and peers`() {
        val files = (1..201).map { SoulseekSearchResult("a","Music/Album/${it.toString().padStart(3,'0')} Song.flac",100) }
        val alternative = SoulseekSearchResult("b","Music/Album/01 Song.flac",100)
        val edition = SoulseekSearchResult("a","Music/Album Remastered/01 Song.flac",100)
        val groups = remoteAlbums("Album",files+files+alternative+edition,DiscoveryOptions())
        assertEquals(3,groups.size)
        assertEquals(201,groups.single { it.username == "a" && it.directory == "Music/Album" }.tracks.size)
    }
    @Test fun `match wins over resolution and availability breaks ties without losing unknown metadata`() {
        val exact = SoulseekSearchResult("queued","Artist/Album/01 Moonlight.flac",100,bitDepth=16,sampleRate=44100,freeSlots=0,queueLength=10)
        val hi = SoulseekSearchResult("fast","Artist/Other/Moonlight Remix.flac",100,bitDepth=24,sampleRate=96000,freeSlots=1,uploadSpeed=1000000)
        assertEquals("queued",remoteAlbums("Moonlight",listOf(hi,exact),DiscoveryOptions()).first().username)
        assertEquals("fast",remoteAlbums("Moonlight",listOf(hi,exact),DiscoveryOptions(sort=SearchSort.QUALITY)).first().username)
        val unknown=SoulseekSearchResult("unknown","Album/Moonlight.flac",100)
        assertEquals(listOf("unknown"),remoteAlbums("Moonlight",listOf(hi,exact,unknown),DiscoveryOptions(quality=SearchQuality.UNKNOWN)).map {it.username})
        assertEquals(listOf("fast"),remoteAlbums("Moonlight",listOf(hi,exact),DiscoveryOptions(availableOnly=true)).map {it.username})
    }
    @Test fun `cached identities preserve the persisted SHA256 encoding and path semantics`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", app.runout.soulseek.SoulseekPolicy.digest("abc"))
        val file = SoulseekSearchResult("peer", "Artist\\Album\\01 Song.flac", 100)
        assertEquals("01 Song.flac", file.fileName)
        assertEquals("Artist/Album", file.directory)
        assertEquals(app.runout.soulseek.SoulseekPolicy.remoteId("peer", file.path, 100), file.id)
        assertNotEquals(file.id, file.copy(size = 101).id)
    }
    @Test fun `metadata search remains bounded and fast with a large local library and remote shortlist`() {
        val albums=(0 until 100).map { i -> album("Album $i","Artist $i").copy(id="album-$i",tracks=(0 until 100).map { n -> Track("$i-$n","file:///$i/$n.flac","Track $n",n+1,1,1000) }) }
        val index=LocalMusicIndex(albums)
        repeat(3) { index.search("Artist 42 Track 17",SearchKind.SONGS) }
        val local=System.nanoTime()
        val matches=index.search("Artist 42 Track 17",SearchKind.SONGS)
        val localMs=(System.nanoTime()-local)/1_000_000.0
        assertTrue(matches.any { it.album.artist == "Artist 42" && it.track?.title == "Track 17" })
        val files=(0 until 5000).map { SoulseekSearchResult("peer-${it%50}","Artist/Album ${it%50}/Track $it.flac",100,bitDepth=24,sampleRate=96000,freeSlots=it%2) }
        val remote=System.nanoTime();val groups=remoteAlbums("Artist",files,DiscoveryOptions())
        val remoteMs=(System.nanoTime()-remote)/1_000_000.0
        assertEquals(50,groups.size)
        assertEquals(5000,groups.sumOf {it.tracks.size})
        println("Discovery benchmark: 10000 local tracks=${"%.1f".format(localMs)}ms; 5000 remote files=${"%.1f".format(remoteMs)}ms")
        // Broad ceiling catches accidental file IO/quadratic regressions without machine-dependent frame assertions.
        assertTrue("Local search regressed: $localMs ms",localMs<1000)
        assertTrue("Remote reduction regressed: $remoteMs ms",remoteMs<2000)
    }
}
