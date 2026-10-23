package app.runout

import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.model.Source
import app.runout.soulseek.SoulseekTransfer
import app.runout.soulseek.SoulseekTransferState
import app.runout.ui.RecordCard
import app.runout.ui.shareSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A record sent in a Soulseek message: readable in any client, recognised by Runout. */
class RecordCardTest {
    @Test fun `a record card survives the round trip and reads as plain text elsewhere`() {
        val card = RecordCard("Air", "Moon Safari & Friends", "FLAC · 24-bit · 96 kHz")
        val text = card.encode()
        assertTrue(text.startsWith("♪ Moon Safari & Friends — Air"))
        assertEquals(card, RecordCard.parse(text))
    }

    @Test fun `a card carries the peer and folder it came from`() {
        val card = RecordCard("Air", "Moon Safari", "FLAC · 16-bit · 44.1 kHz", "dj ro", "Music/Air - Moon Safari (1998) [FLAC]")
        val text = card.encode()
        assertTrue(text.startsWith("♪ Moon Safari — Air · FLAC"))
        assertEquals(card, RecordCard.parse(text))
        // No quality: the source still reads.
        val bare = card.copy(quality = null)
        assertEquals(bare, RecordCard.parse(bare.encode()))
    }

    @Test fun `cards from older versions still read, with no source`() {
        val old = "♪ Moon Safari — Air · FLAC\nrunout:record?artist=Air&album=Moon+Safari&q=FLAC"
        assertEquals(RecordCard("Air", "Moon Safari", "FLAC"), RecordCard.parse(old))
        assertEquals(RecordCard("Air", "Moon Safari", null), RecordCard.parse("runout:record?artist=Air&album=Moon+Safari"))
    }

    @Test fun `only Soulseek downloads have somewhere to send from`() {
        val base = Album("a", "Moon Safari", "Air", 1998, "", emptyList(), AudioQuality("FLAC", 16, 44_100))
        assertNull(shareSource(base, emptyList()))
        assertNull(shareSource(base.copy(source = Source.RECEIVED, from = "maya", remoteFolder = "x"), emptyList()))
        val kept = base.copy(source = Source.SOULSEEK, from = "ro", remoteFolder = "Music/Air/Moon Safari")
        assertEquals("ro" to "Music/Air/Moon Safari", shareSource(kept, emptyList()))
        // Downloaded before the folder was kept: its first finished download says where.
        val older = base.copy(source = Source.SOULSEEK, from = "ro")
        fun transfer(id: String, user: String, state: SoulseekTransferState, album: String? = "a") =
            SoulseekTransfer(id, user, "Music\\Air\\Moon Safari\\0$id.flac", 1, 1, state, albumId = album)
        assertNull(shareSource(older, listOf(transfer("1", "ro", SoulseekTransferState.FAILED), transfer("2", "ro", SoulseekTransferState.COMPLETED, album = "b"))))
        assertEquals("kai" to "Music/Air/Moon Safari", shareSource(older, listOf(transfer("1", "ro", SoulseekTransferState.FAILED), transfer("2", "kai", SoulseekTransferState.COMPLETED))))
    }

    @Test fun `ordinary messages are not cards`() {
        assertNull(RecordCard.parse("have you heard Moon Safari?"))
        assertNull(RecordCard.parse("runout:record?artist=&album="))
    }
}
