package app.runout

import app.runout.ui.RecordCard
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

    @Test fun `ordinary messages are not cards`() {
        assertNull(RecordCard.parse("have you heard Moon Safari?"))
        assertNull(RecordCard.parse("runout:record?artist=&album="))
    }
}
