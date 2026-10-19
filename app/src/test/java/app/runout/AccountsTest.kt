package app.runout

import app.runout.account.AccountRepository
import app.runout.account.Bech32
import app.runout.account.NostrEvent
import app.runout.account.RelayPool
import app.runout.account.Schnorr
import app.runout.account.hex
import app.runout.account.unhex
import app.runout.social.SocialRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Suite 11 — accounts with no server of our own: keys and signatures (checked against the official
 * BIP-340 vectors and real events from a public relay), and profiles, search and friends over real
 * WebSockets against relays running inside the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AccountsTest {

    private val context get() = Fixtures.context
    private val relays = mutableListOf<FakeRelay>()

    @After
    fun tearDown() = relays.forEach { it.close() }

    private fun relayPool(n: Int = 2): RelayPool {
        val started = List(n) { FakeRelay().also { relays += it } }
        return RelayPool(started.map { it.url }, timeoutMs = 4_000)
    }

    /** A fresh phone: no account, a new offline identity. */
    private fun phone(pool: RelayPool, clock: () -> Long = System::currentTimeMillis): Pair<AccountRepository, SocialRepository> {
        context.getSharedPreferences("account", 0).edit().clear().commit()
        context.getSharedPreferences("social", 0).edit().clear().commit()
        File(context.filesDir, "friends.json").delete()
        val social = SocialRepository(context)
        return AccountRepository(context, pool, social, clock) to social
    }

    @Test
    fun `signatures match every official BIP-340 test vector`() {
        val rows = Fixtures.text("nostr/bip340-test-vectors.csv").lines().drop(1).filter { it.isNotBlank() }
        assertTrue(rows.size >= 15)
        for (row in rows) {
            val c = row.split(',')
            val (index, sk, pk, aux, msg, sig, result) = c
            val expected = result.trim() == "TRUE"
            if (sk.isNotBlank()) {
                assertEquals("pubkey #$index", pk.lowercase(), Schnorr.publicKey(sk.unhex()).hex())
                assertEquals("signature #$index", sig.lowercase(), Schnorr.sign(msg.unhex(), sk.unhex(), aux.unhex()).hex())
            }
            assertEquals("verify #$index (${c.getOrNull(7)})", expected, Schnorr.verify(msg.unhex(), pk.unhex(), sig.unhex()))
        }
    }

    @Test
    fun `real events from a public relay check out, and any change breaks them`() {
        val arr = JSONArray(Fixtures.text("nostr/real_events.json"))
        assertTrue(arr.length() >= 2)
        for (i in 0 until arr.length()) {
            val e = NostrEvent.fromJson(arr.getJSONObject(i))
            assertTrue("event $i", e.isValid())
            assertFalse(e.copy(content = e.content + " ").isValid())
            assertFalse(e.copy(createdAt = e.createdAt + 1).isValid())
        }
        // Our own events, including awkward characters, sign and verify.
        val sk = Schnorr.newSecretKey()
        val e = NostrEvent.sign(sk, 1_760_000_000, 1, listOf(listOf("t", "runout")), "line\nquote\" back\\slash tab\t ünïcødé 🎵 \u0001")
        assertTrue(e.isValid())
        assertTrue(NostrEvent.fromJson(JSONObject(e.toJson().toString())).isValid())
    }

    @Test
    fun `keys copy and paste as nsec and npub (NIP-19)`() {
        assertEquals("npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg",
            Bech32.encode("npub", "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e".unhex()))
        val (hrp, bytes) = Bech32.decode("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5")!!
        assertEquals("nsec", hrp)
        assertEquals("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa", bytes.hex())
        assertNull(Bech32.decode("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe6")) // one character off
        assertEquals(bytes.hex(), AccountRepository.parseSecret("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5")!!.hex())
        assertNull(AccountRepository.parseSecret("not a key"))
    }

    @Test
    fun `usernames are checked and searches understood`() {
        assertNull(AccountRepository.validateUsername("abhi"))
        assertNull(AccountRepository.validateUsername("vela.park_99"))
        listOf("ab", "x".repeat(21), "abhi!", ".abhi", "abhi.", "a..b", "Abhi").forEach {
            assertTrue(it, AccountRepository.validateUsername(it) != null)
        }
        assertEquals("abhi", AccountRepository.normalize("  @Abhi "))
        assertEquals("abhi" to "7f3a", AccountRepository.parseQuery("@Abhi·7F3A"))
        assertEquals("abhi" to "7f3a", AccountRepository.parseQuery("abhi#7f3a"))
        assertEquals("ab" to null, AccountRepository.parseQuery("ab"))
        assertNull(AccountRepository.parseQuery("a"))
        val tags = AccountRepository.profileTags("abhi")
        assertEquals(listOf("ab", "abh", "abhi"), tags.filter { it[0] == "n" }.map { it[1] })
    }

    @Test
    fun `making an account publishes a signed profile to every relay`() = runBlocking {
        val pool = relayPool(2)
        val (accounts, social) = phone(pool)
        val acc = accounts.create("abhi", "Abhi", 0xFF2B6BFF.toInt(), listOf(Fixtures.nightDrive(), Fixtures.paperMoons())).getOrThrow()
        assertEquals("@abhi·" + acc.pubkey.take(4).uppercase(), acc.handle)
        assertEquals("Profile is up on 2 of 2 relays", accounts.status.value!!.message)
        relays.forEach { r ->
            val e = r.stored.single()
            assertEquals(acc.pubkey, e.pubkey)
            assertEquals(AccountRepository.KIND, e.kind)
            assertTrue(e.isValid())
            assertEquals(2, JSONObject(e.content).getJSONArray("shelf").length())
        }
        // The phone now speaks as the account, and remembers it.
        assertEquals(acc.pubkey, social.profile.value.id)
        assertEquals("abhi", social.profile.value.username)
        assertEquals(acc, AccountRepository(context, pool, SocialRepository(context)).account.value)
        assertTrue(accounts.backupKey()!!.startsWith("nsec1"))
        // One account per phone.
        assertTrue(accounts.create("other", "x", 0, emptyList()).isFailure)
    }

    @Test
    fun `people find each other by username and see the shelf`() = runBlocking {
        val pool = relayPool(2)
        val (maya, _) = phone(pool)
        val m = maya.create("maya.k", "Maya", 0xFFE0569B.toInt(), listOf(Fixtures.nightDrive(), Fixtures.paperMoons())).getOrThrow()

        val (abhi, abhiSocial) = phone(pool)
        abhi.create("abhi", "Abhi", 0, emptyList()).getOrThrow()
        val found = abhi.search("may")
        assertEquals(1, found.size)
        val p = found.single()
        assertEquals(m.pubkey, p.pubkey)
        assertEquals("Maya", p.name)
        assertEquals(m.handle, p.handle)
        assertEquals(listOf("Night Drive", "Paper Moons"), p.shelf.map { it.title })
        assertEquals("FLAC · 24-bit · 96 kHz", p.shelf[0].quality)
        assertEquals(2, p.shelfCount)
        assertTrue(abhi.search("zz").isEmpty())

        // Add her as a friend; later her shelf changes and refreshing picks it up.
        assertTrue(abhi.addFriend(p))
        assertEquals(listOf("Maya"), abhiSocial.friends.value.map { it.profile.name })
        assertEquals(2, abhiSocial.friends.value.single().shelf.size)
    }

    @Test
    fun `two people with the same username are told apart by their key code`() = runBlocking {
        val pool = relayPool(1)
        val (a, _) = phone(pool)
        val first = a.create("sam", "Sam A", 0, emptyList()).getOrThrow()
        val (b, _) = phone(pool)
        val second = b.create("sam", "Sam B", 0, emptyList()).getOrThrow()
        val (c, _) = phone(pool)
        c.create("viewer", "V", 0, emptyList()).getOrThrow()
        val both = c.search("sam")
        assertEquals(setOf(first.pubkey, second.pubkey), both.map { it.pubkey }.toSet())
        assertNotEquals(both[0].handle, both[1].handle)
        val exact = c.search(first.handle)
        assertEquals(listOf(first.pubkey), exact.map { it.pubkey })
    }

    @Test
    fun `a newer profile replaces the old one, and a private shelf stays private`() = runBlocking {
        val pool = relayPool(1)
        var now = 1_760_000_000_000L
        val (a, _) = phone(pool) { now }
        val acc = a.create("dj.lena", "Lena", 0, listOf(Fixtures.nightDrive())).getOrThrow()
        now += 60_000
        a.publish(listOf(Fixtures.nightDrive(), Fixtures.paperMoons()))
        assertEquals(1, relays.single().stored.size)
        now += 60_000
        a.setShareShelf(false)
        a.publish(listOf(Fixtures.nightDrive(), Fixtures.paperMoons()))

        val (b, _) = phone(pool)
        b.create("viewer", "V", 0, emptyList()).getOrThrow()
        val p = b.profile(acc.pubkey)!!
        assertTrue(p.shelf.isEmpty())
        assertEquals(0, p.shelfCount)
    }

    @Test
    fun `a relay can't fake a profile`() = runBlocking {
        val pool = relayPool(1)
        val (a, _) = phone(pool)
        a.create("honest", "H", 0, listOf(Fixtures.nightDrive())).getOrThrow()
        val (b, _) = phone(pool)
        b.create("viewer", "V", 0, emptyList()).getOrThrow()
        relays.single().tamper = true
        assertTrue("altered events are dropped", b.search("hon").isEmpty())
        relays.single().tamper = false
        assertEquals(1, b.search("hon").size)
    }

    @Test
    fun `offline, the account still gets made and search says why it found nothing`() = runBlocking {
        val pool = RelayPool(listOf("ws://127.0.0.1:9"), timeoutMs = 2_000)
        val (a, _) = phone(pool)
        val acc = a.create("offline", "Off", 0, emptyList()).getOrThrow()
        assertEquals("offline", acc.username)
        assertFalse(a.status.value!!.ok)
        val err = runCatching { a.search("abc") }.exceptionOrNull()
        assertTrue(err?.message ?: "no error", err?.message?.contains("Couldn't reach") == true)
    }

    @Test
    fun `restoring from the account key brings the same account back`() = runBlocking {
        val pool = relayPool(2)
        val (a, _) = phone(pool)
        val acc = a.create("ravi", "Ravi S", 0xFF1E9E8A.toInt(), emptyList()).getOrThrow()
        val key = a.backupKey()!!

        val (fresh, social) = phone(pool) // a new phone
        val back = fresh.restore(key, emptyList()).getOrThrow()
        assertEquals(acc.pubkey, back.pubkey)
        assertEquals(acc.handle, back.handle)
        assertEquals("Ravi S", back.name)
        assertEquals(acc.pubkey, social.profile.value.id)
        assertTrue(fresh.restore("garbage", emptyList()).isFailure)
    }
}

private operator fun <T> List<T>.component6() = this[5]
private operator fun <T> List<T>.component7() = this[6]
