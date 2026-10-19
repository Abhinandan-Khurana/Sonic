package app.runout

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import app.runout.soulseek.NativeSoulseek
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Explicit manual check. Credentials are provisioned in private app storage, never runner arguments. */
class SoulseekLiveNetworkTest {
    @Test fun publicNetworkSessionAndSearch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runout.live") == "true")
        val secret = File(instrumentation.targetContext.filesDir, "runout-live-account.env")
        assertTrue("Provision a private test account file first", secret.exists())
        val values = secret.readLines().mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0,it) to line.substring(it+1) } }.toMap()
        val client = NativeSoulseek()
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000
        var connected = false
        var loss: String? = null
        var found = 0
        fun events(): List<JSONObject> {
            val array = JSONArray(client.poll())
            return (0 until array.length()).map { array.getJSONObject(it) }.onEach { event ->
                if (event.optString("type") == "session") {
                    val state = event.optString("state")
                    Log.i("RunoutLive", "session=$state elapsedMs=${elapsed()} reasonCode=${event.optString("reasonCode")} reason=${event.optString("reason")}")
                    if (state == "connected") connected = true
                    if (state == "disconnected" || state == "error") loss = event.optString("reason", state)
                }
            }
        }
        try {
            client.command(JSONObject().put("op","login").put("username",values.getValue("ARGENT_SECRET_SLSK_USERNAME")).put("password",values.getValue("ARGENT_SECRET_SLSK_PASSWORD")).toString())
            while (!connected && loss == null && elapsed() < 70_000) { events(); Thread.sleep(100) }
            assertTrue("Public login did not succeed: $loss", connected)
            for ((index, query) in listOf("Pink Floyd", "Radiohead", "Miles Davis").withIndex()) {
                if (loss != null) break
                val id = "live-$index"
                val response = JSONObject(client.command(JSONObject().put("op","search").put("id",id).put("query",query).put("timeoutMs",30000).toString()))
                assertTrue("Public search command rejected", response.optBoolean("ok"))
                val until = elapsed() + 32_000
                var done = false
                var count = 0
                val searchStarted = elapsed()
                var firstResultMs: Long? = null
                while (!done && loss == null && elapsed() < until) {
                    events().filter { it.optString("id") == id }.forEach { event ->
                        when(event.optString("type")) {
                            "search_result" -> count++
                            "search_batch" -> {
                                val batchSize = event.optJSONArray("files")?.length() ?: 0
                                if (batchSize > 0 && firstResultMs == null) firstResultMs = elapsed() - searchStarted
                                count += batchSize
                            }
                            "search_done" -> { done = true; Log.i("RunoutLive", "search=$index rawFiles=${event.optInt("rawFiles")} peerReplies=${event.optInt("peerReplies")}") }
                        }
                    }
                    Thread.sleep(100)
                }
                found += count
                Log.i("RunoutLive", "search=$index flacResults=$count firstResultMs=$firstResultMs done=$done elapsedMs=${elapsed()}")
            }
            val duration = InstrumentationRegistry.getArguments().getString("runout.liveSeconds")?.toLongOrNull()?.coerceIn(0,1200) ?: 900
            while (loss == null && elapsed() < duration * 1000) { events(); Thread.sleep(250) }
            Log.i("RunoutLive", "finished flacResults=$found elapsedMs=${elapsed()} disconnected=${loss != null}")
            assertNull("Public session disconnected: $loss", loss)
            assertTrue("Public searches returned no FLAC results", found > 0)
        } finally { client.close() }
    }
}
