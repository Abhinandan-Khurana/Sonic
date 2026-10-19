package app.runout

import app.runout.account.Account
import app.runout.account.AccountRepository
import app.runout.account.NostrEvent
import app.runout.account.RelayPool
import app.runout.account.Schnorr
import app.runout.account.hex
import app.runout.social.SocialRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Not part of the regular suites: run with -Prunout.live=true to check the account system against the
 * real public relays. It publishes a throwaway profile that the relays delete after ten minutes
 * (NIP-40 expiration), then finds it again by username.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LiveRelayCheck {
    @Test
    fun `a profile published to the public relays can be found by username`() = runBlocking {
        assumeTrue(System.getProperty("runout.live") == "true")
        val pool = RelayPool(RelayPool.DEFAULT, timeoutMs = 15_000)
        val sk = Schnorr.newSecretKey()
        val pk = Schnorr.publicKey(sk).hex()
        val username = "rotest_" + (100_000..999_999).random()
        val now = System.currentTimeMillis() / 1000
        val acc = Account(pk, username, "Runout self-test", 0)
        val tags = AccountRepository.profileTags(username) + listOf(listOf("expiration", (now + 600).toString()))
        val event = NostrEvent.sign(sk, now, AccountRepository.KIND, tags, AccountRepository.profileContent(acc, emptyList()))
        val published = pool.publish(event)
        println("LIVE published ${event.id} as @$username to ${published.accepted}/${published.total} relays")
        assertTrue("at least one relay accepted the profile", published.accepted >= 1)
        delay(1500)
        val repo = AccountRepository(Fixtures.context, pool, SocialRepository(Fixtures.context))
        val exact = repo.search(username)
        val prefix = repo.search(username.take(10))
        println("LIVE exact=${exact.map { it.handle }} prefix=${prefix.map { it.handle }} reachable=${pool.lastReachable}")
        assertTrue(exact.any { it.pubkey == pk })
        assertTrue(prefix.any { it.pubkey == pk })
    }
}
