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
        val events=ArrayDeque<JSONObject>()
        override fun command(request:String):String {
            if(JSONObject(request).optString("op")=="login") events.add(JSONObject().put("type","session").put("state","connected"))
            return """{"ok":true}"""
        }
        override fun poll():String = JSONArray().also { while(events.isNotEmpty()) it.put(events.removeFirst()) }.toString()
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
