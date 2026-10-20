package app.runout

import android.app.Application
import app.runout.soulseek.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SessionIsolationTest {
    private class Wire : SoulseekTransport {
        val events = ArrayDeque<JSONObject>()
        var pollFailure = false
        override fun command(request: String): String {
            if (JSONObject(request).optString("op") == "login") events.add(event("session").put("state", "connected"))
            return """{"ok":true}"""
        }
        override fun poll(): String {
            if (pollFailure) { pollFailure = false; error("fixture transport failure") }
            return JSONArray().also { while (events.isNotEmpty()) it.put(events.removeFirst()) }.toString()
        }
        override fun close() = Unit
    }
    companion object { private fun event(type: String) = JSONObject().put("type", type) }

    @Test fun `handler failure cannot disconnect or skip later events`() = runTest {
        val wires = mutableListOf<Wire>()
        val received = mutableListOf<String>()
        val manager = SoulseekConnectionManager(Fixtures.context, backgroundScope,
            { Wire().also { wires += it } }, { false }, { e, _ ->
                if (e.optString("type") == "bad") error("fixture handler failure")
                received += e.optString("type")
            }, {}, StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("listener", "fixture", false); runCurrent()
            wires.single().events.add(event("bad"))
            wires.single().events.add(event("good"))
            advanceTimeBy(200); runCurrent()
            assertTrue("handler failure must not become session loss", manager.session.value.connected)
            assertTrue("later event in the same batch delivered", "good" in received)
            advanceTimeBy(65_000); runCurrent()
            assertEquals("no retry triggered by application handler", 1, wires.size)
        } finally { manager.disconnect(SoulseekDisconnectReason.MANUAL, true) }
    }

    @Test fun `connected handler failure keeps authenticated connection`() = runTest {
        val manager = SoulseekConnectionManager(Fixtures.context, backgroundScope, { Wire() }, { false },
            { _, _ -> error("fixture connected handler failure") }, {}, StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("listener", "fixture", false); runCurrent()
            assertTrue(manager.session.value.connected)
        } finally { manager.disconnect(SoulseekDisconnectReason.MANUAL, true) }
    }

    @Test fun `transport failure checks native displacement before scheduling retry`() = runTest {
        val wires = mutableListOf<Wire>()
        val manager = SoulseekConnectionManager(Fixtures.context, backgroundScope,
            { Wire().also { wires += it } }, { false }, { _, _ -> }, {}, StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("listener", "fixture", false); runCurrent()
            wires.single().events.add(event("session").put("state", "disconnected").put("reasonCode", "DISPLACED"))
            wires.single().pollFailure = true
            advanceTimeBy(200); runCurrent()
            assertEquals(SoulseekDisconnectReason.DISPLACED, manager.session.value.reason)
            assertTrue(manager.session.value.hasAccount)
            advanceTimeBy(65_000); runCurrent()
            assertEquals("terminal displacement never retries", 1, wires.size)
        } finally { manager.disconnect(SoulseekDisconnectReason.MANUAL, true) }
    }
}
