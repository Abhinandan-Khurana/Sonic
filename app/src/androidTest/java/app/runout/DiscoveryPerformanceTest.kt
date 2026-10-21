package app.runout

import android.util.Log
import app.runout.discovery.*
import app.runout.model.*
import app.runout.soulseek.SoulseekSearchResult
import org.junit.Assert.*
import org.junit.Test

class DiscoveryPerformanceTest {
    @Test fun metadataSearchBenchmarks() {
        val albums=(0 until 100).map { i -> Album("album-$i","Album $i","Artist $i",2024,"",(0 until 100).map { n -> Track("$i-$n","file:///$i/$n.flac","Track $n",n+1,1,1000) },AudioQuality("FLAC",24,96000)) }
        val index=LocalMusicIndex(albums)
        val files=(0 until 5000).map { SoulseekSearchResult("peer-${it%50}","Artist/Album ${it%50}/Track $it.flac",100,bitDepth=24,freeSlots=it%2) }
        repeat(3) {index.search("Artist 42 Track 17");remoteAlbums("Artist",files,DiscoveryOptions())}
        fun measure(block:()->Unit): Double {
            val times=(0 until 10).map {val start=System.nanoTime();block();(System.nanoTime()-start)/1_000_000.0}.sorted()
            return times[9]
        }
        val local=measure {assertTrue(index.search("Artist 42 Track 17",SearchKind.SONGS).isNotEmpty())}
        val remote=measure {assertEquals(5000,remoteAlbums("Artist",files,DiscoveryOptions()).sumOf {it.tracks.size})}
        Log.i("RunoutBenchmark","10000 local tracks p95Ms=$local; 5000 remote files p95Ms=$remote")
        assertTrue("Local search exceeded the 150 ms target: $local",local<150)
        assertTrue("Remote reduction exceeded the 250 ms target: $remote",remote<250)
    }
}
