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
@Config(sdk=[35],application=Application::class)
class SoulseekConnectionTest {
    private class Wire : SoulseekTransport {
        var closed=false
        var polls=0
        var refuseLogin=false
        val events=ArrayDeque<JSONObject>()
        override fun command(request:String):String {
            if(JSONObject(request).optString("op")=="login") {
                if(refuseLogin) return """{"ok":false,"error":"No route to the server"}"""
                events.add(JSONObject().put("type","session").put("state","connected"))
            }
            return """{"ok":true}"""
        }
        override fun poll():String { polls++; return JSONArray().also { while(events.isNotEmpty()) it.put(events.removeFirst()) }.toString() }
        override fun close() { closed=true }
    }
    @Test fun `network loss keeps identity and reconnects once without credentials entry`() = runTest {
        val wires=mutableListOf<Wire>()
        val received=mutableListOf<String>()
        val manager=SoulseekConnectionManager(Fixtures.context,backgroundScope,{Wire().also {wires+=it}},{false},{event,_->received+=event.optString("state")},{},StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("listener","private-fixture",false);runCurrent()
            assertTrue(manager.session.value.connected)
            manager.networkChanged(false)
            assertFalse(manager.session.value.connected);assertTrue(manager.session.value.hasAccount)
            assertTrue(wires[0].closed)
            advanceTimeBy(31000);runCurrent();assertEquals(1,wires.size)
            manager.networkChanged(true);runCurrent()
            assertEquals(2,wires.size);assertTrue(manager.session.value.connected)
        } finally {manager.disconnect(SoulseekDisconnectReason.MANUAL,true)}
    }
    @Test fun `displacement does not fight the other client and explicit reconnect works`() = runTest {
        val wires=mutableListOf<Wire>()
        val manager=SoulseekConnectionManager(Fixtures.context,backgroundScope,{Wire().also {wires+=it}},{false},{_,_->},{},StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("listener","private-fixture",false);runCurrent()
            wires[0].events.add(JSONObject().put("type","session").put("state","disconnected").put("reasonCode","DISPLACED"))
            advanceTimeBy(200);runCurrent()
            assertEquals(SoulseekDisconnectReason.DISPLACED,manager.session.value.reason)
            assertTrue(manager.session.value.hasAccount)
            advanceTimeBy(65000);runCurrent();assertEquals(1,wires.size)
            manager.reconnect();runCurrent();assertEquals(2,wires.size);assertTrue(manager.session.value.connected)
        } finally {manager.disconnect(SoulseekDisconnectReason.MANUAL,true)}
    }
    @Test fun `folder picker does not pause the session and ordinary background pause resumes`() = runTest {
        val wires=mutableListOf<Wire>()
        val manager=SoulseekConnectionManager(Fixtures.context,backgroundScope,{Wire().also {wires+=it}},{false},{_,_->},{},StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("listener","private-fixture",false);runCurrent()
            manager.externalFlow(true);manager.foregroundChanged(false)
            advanceTimeBy(10000);runCurrent();assertTrue(manager.session.value.connected)
            manager.externalFlow(false);advanceTimeBy(6000);runCurrent()
            assertEquals(SoulseekConnection.PAUSED,manager.session.value.state)
            assertTrue(manager.session.value.hasAccount)
            manager.foregroundChanged(true);runCurrent();assertEquals(2,wires.size);assertTrue(manager.session.value.connected)
        } finally {manager.disconnect(SoulseekDisconnectReason.MANUAL,true)}
    }
    @Test fun `manual disconnect stays paused and replaced sessions cannot deliver stale events`() = runTest {
        val wires=mutableListOf<Wire>();val owners=mutableListOf<String>()
        val manager=SoulseekConnectionManager(Fixtures.context,backgroundScope,{Wire().also {wires+=it}},{true},{_,owner->owners+=owner},{},StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("first","fixture",false)
            manager.signIn("second","fixture",false);runCurrent()
            assertTrue(wires[0].closed);assertEquals(listOf("second"),owners)
            manager.disconnect(SoulseekDisconnectReason.MANUAL)
            manager.foregroundChanged(false);manager.foregroundChanged(true);advanceTimeBy(65000);runCurrent()
            assertEquals(2,wires.size);assertTrue(manager.session.value.hasAccount);assertFalse(manager.session.value.connected)
        } finally {manager.disconnect(SoulseekDisconnectReason.MANUAL,true)}
    }
    @Test fun `events are polled ten times a second only while something is under way`() = runTest {
        val wires=mutableListOf<Wire>()
        var busy=false
        val manager=SoulseekConnectionManager(Fixtures.context,backgroundScope,{Wire().also {wires+=it}},{true},{_,_->},{},StandardTestDispatcher(testScheduler),busy={busy})
        try {
            manager.signIn("listener","private-fixture",false);runCurrent()
            assertTrue(manager.session.value.connected)
            assertEquals("just signed in",POLL_ACTIVE_MS,manager.pollIntervalMs)
            advanceTimeBy(3000);runCurrent()
            assertEquals("quiet for a while",POLL_IDLE_MS,manager.pollIntervalMs)
            val idle=wires[0].polls;advanceTimeBy(5000);runCurrent()
            assertTrue("about once a second when idle, was ${wires[0].polls-idle}",wires[0].polls-idle in 4..6)
            busy=true;advanceTimeBy(1100);runCurrent()
            assertEquals("a transfer or search keeps it fast",POLL_ACTIVE_MS,manager.pollIntervalMs)
            val fast=wires[0].polls;advanceTimeBy(1000);runCurrent()
            assertTrue("about ten a second, was ${wires[0].polls-fast}",wires[0].polls-fast >= 9)
            busy=false;advanceTimeBy(3000);runCurrent()
            assertEquals(POLL_IDLE_MS,manager.pollIntervalMs)
            // A command wakes the slow poll at once, so its answer isn't a second late.
            val before=wires[0].polls
            manager.command("search"){put("id","s")};advanceTimeBy(1);runCurrent()
            assertEquals(before+1,wires[0].polls)
            assertEquals(POLL_ACTIVE_MS,manager.pollIntervalMs)
            // So does an event arriving while idle, within the second.
            advanceTimeBy(3000);runCurrent();assertEquals(POLL_IDLE_MS,manager.pollIntervalMs)
            wires[0].events.add(JSONObject().put("type","presence").put("username","maya").put("online",true))
            advanceTimeBy(1000);runCurrent()
            assertEquals(POLL_ACTIVE_MS,manager.pollIntervalMs)
        } finally {manager.disconnect(SoulseekDisconnectReason.MANUAL,true)}
    }
    @Test fun `capability updates on the same network do not cut the back-off short`() = runTest {
        val wires=mutableListOf<Wire>()
        val manager=SoulseekConnectionManager(Fixtures.context,backgroundScope,{Wire().also {it.refuseLogin=true;wires+=it}},{false},{_,_->},{},StandardTestDispatcher(testScheduler))
        try {
            manager.signIn("listener","private-fixture",false);runCurrent()
            assertEquals(1,wires.size);assertFalse(manager.session.value.connected)
            // Mobile data reporting new signal or bandwidth: still the same network.
            repeat(3) { manager.networkChanged(true,fresh=false);runCurrent() }
            assertEquals("waits out its back-off",1,wires.size)
            advanceTimeBy(1100);runCurrent();assertEquals("then retries",2,wires.size)
            // A different network is worth trying at once.
            manager.networkChanged(true);runCurrent()
            assertEquals(3,wires.size)
        } finally {manager.disconnect(SoulseekDisconnectReason.MANUAL,true)}
    }
    @Test fun `sharing and background consent persist independently for each exact account`() {
        val first=SoulseekPreferences(Fixtures.context)
        first.setSharing("first",true);first.setBackground("first",true);first.setStep(3)
        first.setSharedFolder("first","content://first-folder"); first.setSharedFolder("second","content://second-folder")
        val restored=SoulseekPreferences(Fixtures.context)
        assertTrue(restored.sharing("first"));assertTrue(restored.background("first"))
        assertEquals("content://first-folder",restored.sharedFolder("first"));assertEquals("content://second-folder",restored.sharedFolder("second"))
        assertFalse(restored.sharing("second"));assertFalse(restored.background("second"));assertEquals(3,restored.step.value)
        restored.setSharing("first",false)
        assertFalse(SoulseekPreferences(Fixtures.context).sharing("first"))
        assertTrue(SoulseekPreferences(Fixtures.context).background("first"))
    }
}
