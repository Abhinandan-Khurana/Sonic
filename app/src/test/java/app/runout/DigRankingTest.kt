package app.runout

import app.runout.discovery.DiscoveryOptions
import app.runout.discovery.folderNames
import app.runout.discovery.remoteAlbums
import app.runout.soulseek.SoulseekSearchResult
import app.runout.ui.cleanTrackName
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dig shows albums: whole folders, ranked by how well the folder itself matches. */
class DigRankingTest {
    private fun file(user: String, path: String) = SoulseekSearchResult(user, path, 30_000_000)

    @Test fun `the album that is called the search ranks above a compilation with a track of that name`() {
        val results = listOf(
            file("lao", "Music/BT/(2010) Active 2.0_ The BT Workout/07 Moon Safari.flac"),
            file("jules", "Music/Air - Moon Safari/Air - Moon Safari - 01 La femme d'argent.flac"),
            file("jules", "Music/Air - Moon Safari/Air - Moon Safari - 02 Sexy Boy.flac"),
        )
        val ranked = remoteAlbums("moon safari", results, DiscoveryOptions())
        assertEquals("Moon Safari", ranked.first().album)
        assertEquals("Air", ranked.first().parent)
    }

    @Test fun `artist and album come from the folder name, or the folder above unless it is generic`() {
        assertEquals("Air" to "Moon Safari", folderNames("Music/Air - Moon Safari (1998) [FLAC 24-96]").let { it.first to it.second.substringBefore(" (") })
        assertEquals("Air" to "Moon Safari", folderNames("Music/Air - 1998 - Moon Safari"))
        assertEquals("Air" to "Moon Safari", folderNames("Shares/Air/Moon Safari"))
        assertEquals(null to "Moon Safari", folderNames("Music/Moon Safari"))
    }

    @Test fun `track names drop the extension, the repeated album prefix and the number`() {
        assertEquals("01" to "La femme d'argent", cleanTrackName("Air - Moon Safari - 01 La femme d'argent.flac", "Air - Moon Safari", "Air", 1))
        assertEquals("02" to "Sexy Boy", cleanTrackName("02. Sexy Boy.flac", "Moon Safari", "Air", 2))
        assertEquals("03" to "All I Need", cleanTrackName("03 - Air - All I Need.flac", "Moon Safari", "Air", 3))
        assertEquals("04" to "Kelly Watch the Stars", cleanTrackName("Kelly Watch the Stars.flac", "Moon Safari", "Air", 4))
        // The folder carries a year the file names don't.
        assertEquals("01" to "La femme d'argent", cleanTrackName("Air - Moon Safari - 01 La femme d'argent.flac", "Air - Moon Safari (1998)", "Air", 1))
    }
}
