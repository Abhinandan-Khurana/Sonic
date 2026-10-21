package app.runout

import app.runout.ui.fallbackColor
import org.junit.Assert.assertEquals
import org.junit.Test

class Slice0RecordArtTest {
    @Test fun `minimum integer title hash has a stable fallback colour`() {
        val album = app.runout.model.Album("min", "polygenelubricants", "Artist", null, "", emptyList(), app.runout.model.AudioQuality("FLAC"))
        assertEquals(Int.MIN_VALUE, album.title.hashCode())
        assertEquals(fallbackColor(album), fallbackColor(album))
    }
}
