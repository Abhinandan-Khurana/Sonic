package app.runout

import android.graphics.BitmapFactory
import android.util.Base64
import app.runout.model.Album
import app.runout.model.AudioQuality
import app.runout.social.Profile
import app.runout.social.ShelfItem
import app.runout.social.SocialRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Suite 7 — friends without accounts: your profile, friends' shelves, and records they sent. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class FriendsTest {

    private val context get() = Fixtures.context
    private var now = 1_000L
    private fun repo() = SocialRepository(context) { now }

    private fun item(title: String, artist: String) = ShelfItem(title, artist, 2020, "FLAC", null)
    private fun card(id: String, title: String) = Album(id, title, "Someone", null, "", emptyList(), AudioQuality("MP3"))

    @Test
    fun `you get a private identity once, and it stays yours`() {
        val a = repo()
        val id = a.profile.value.id
        assertEquals("You", a.profile.value.name)
        assertTrue(a.profile.value.accent in Profile.ACCENTS)
        a.rename("  Abhi  ")
        a.rename("   ") // blank names are ignored
        a.setAccent(Profile.ACCENTS[2])
        val b = repo()
        assertEquals(id, b.profile.value.id)
        assertEquals("Abhi", b.profile.value.name)
        assertEquals(Profile.ACCENTS[2], b.profile.value.accent)
        a.rename("x".repeat(100))
        assertEquals(40, a.profile.value.name.length)
    }

    @Test
    fun `adding a friend, then refreshing their shelf`() {
        val r = repo()
        val maya = Profile("m", "Maya", Profile.ACCENTS[1])
        assertTrue(r.upsertFriend(maya, listOf(item("Night Drive", "Vela Park"))))
        now = 2_000L
        assertFalse(r.upsertFriend(maya.copy(name = "Maya K"), listOf(item("Night Drive", "Vela Park"), item("Paper Moons", "Ines Varga"))))
        val f = r.friends.value.single()
        assertEquals("Maya K", f.profile.name)
        assertEquals(2, f.shelf.size)
        assertEquals(2_000L, f.updatedAt)
    }

    @Test
    fun `friends are kept in name order and survive a restart`() {
        val r = repo()
        r.upsertFriend(Profile("z", "zoe", 0), emptyList())
        r.upsertFriend(Profile("a", "Arjun", 0), emptyList())
        r.upsertFriend(Profile("m", "Maya", 0), listOf(item("A", "B")))
        assertEquals(listOf("Arjun", "Maya", "zoe"), r.friends.value.map { it.profile.name })
        val again = repo()
        assertEquals(listOf("Arjun", "Maya", "zoe"), again.friends.value.map { it.profile.name })
        assertEquals(1, again.friends.value[1].shelf.size)
        again.removeFriend("m")
        assertEquals(listOf("Arjun", "zoe"), repo().friends.value.map { it.profile.name })
    }

    @Test
    fun `you can't befriend yourself`() {
        val r = repo()
        assertFalse(r.upsertFriend(r.profile.value, emptyList()))
        assertTrue(r.friends.value.isEmpty())
    }

    @Test
    fun `records a friend sends are kept with them, without duplicates`() {
        val r = repo()
        val maya = Profile("m", "Maya", 0)
        r.addCard(maya, card("c1", "Paper Moons")) // a stranger's record makes them a friend
        r.addCard(maya, card("c1", "Paper Moons"))
        r.addCard(maya, card("c2", "Night Drive"))
        assertEquals(listOf("Paper Moons", "Night Drive"), r.friends.value.single().cards.map { it.title })
        assertEquals(2, repo().friends.value.single().cards.size)
    }

    @Test
    fun `shelves match across spelling and punctuation`() {
        val a = Album("x", "Night Drive", "Vela Park", 2023, "", emptyList(), AudioQuality("FLAC"))
        assertTrue(item("night drive!", "VELA PARK").matches(a))
        assertTrue(item("Night  Drive", "Vela-Park").matches(a))
        assertFalse(item("Night Drive II", "Vela Park").matches(a))
    }

    @Test
    fun `your shelf travels with small sleeve thumbnails`() {
        val a = Fixtures.nightDrive()
        val s = ShelfItem.from(a)
        assertEquals("Night Drive", s.title)
        assertEquals("FLAC · 24-bit · 96 kHz", s.quality)
        val bytes = Base64.decode(s.thumb, Base64.NO_WRAP)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(96, bmp.width)
        assertTrue("thumbnails stay small", bytes.size < 12_000)
        assertEquals(s, ShelfItem.fromJson(s.toJson()))
        assertEquals(null, ShelfItem.from(a.copy(coverPath = null)).thumb)
        assertNotEquals(ShelfItem.norm("Ab"), ShelfItem.norm("Ba"))
    }
}
