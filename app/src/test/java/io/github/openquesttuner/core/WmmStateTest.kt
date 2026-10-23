package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WmmStateTest {
    private class Store : WmmSnapshotStore {
        var saved: WmmSnapshot? = null
        var writable = true
        override fun load() = saved
        override fun save(snapshot: WmmSnapshot): Boolean { if (writable) saved = snapshot; return writable }
        override fun clear() { saved = null }
    }
    private class Shell : ShellBackend {
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected(ConnectionMethod.WIRELESS))
        var boot = "12345678-1234-1234-1234-123456789abc"
        val values = mutableMapOf<String, String>()
        val sent = mutableListOf<String>()
        var denied = false
        var ignoredKey: String? = null
        var unavailable = false
        override suspend fun exec(command: ShellCommand): ShellResult {
            sent += command.text
            if (unavailable) throw ShellUnavailableException()
            if (denied) return ShellResult(1, "denied")
            if (command.text == ShellCommand.bootId().text) return ShellResult(0, boot)
            if (command.text.startsWith("getprop ")) return ShellResult(0, values[command.text.removePrefix("getprop ")].orEmpty())
            val match = Regex("setprop ([a-zA-Z0-9_.]+) '(.*)'").matchEntire(command.text)
            if (match != null && match.groupValues[1] != ignoredKey) values[match.groupValues[1]] = match.groupValues[2]
            return ShellResult(0, "")
        }
    }
    @Test fun `status goes off on off using readback and retains undo across controller recreation`() = runTest {
        val shell = Shell(); val store = Store(); val controller = WmmController(shell, QuestModel.QUEST_3, store)
        assertEquals(WmmState(WmmStatus.OFF), controller.readState())
        controller.setEnabled(true)
        assertEquals(WmmState(WmmStatus.ON, true), controller.readState())
        val reopened = WmmController(shell, QuestModel.QUEST_3, store)
        assertEquals(WmmState(WmmStatus.ON, true), reopened.readState())
        reopened.setEnabled(false)
        assertEquals(WmmState(WmmStatus.OFF), reopened.readState())
    }
    @Test fun `failed enable never reports on`() = runTest {
        val shell = Shell(); shell.ignoredKey = WmmProperty.TYPE.key
        val controller = WmmController(shell, QuestModel.QUEST_3, Store())
        assertTrue(controller.setEnabled(true).contains("failed"))
        assertEquals(WmmStatus.OFF, controller.readState().status)
    }
    @Test fun `external partial changes are visible and cannot be overwritten by restore`() = runTest {
        val shell = Shell(); val controller = WmmController(shell, QuestModel.QUEST_3, Store())
        controller.setEnabled(true); shell.values[WmmProperty.TYPE.key] = "9"
        assertEquals(WmmState(WmmStatus.MIXED, false), controller.readState())
    }
    @Test fun `readback is read only and denied or disconnected reads are unknown`() = runTest {
        val shell = Shell(); val controller = WmmController(shell, QuestModel.QUEST_3, Store())
        controller.readState(); assertTrue(shell.sent.none { it.startsWith("setprop") })
        shell.denied = true; assertEquals(WmmState(), controller.readState())
        shell.denied = false; shell.unavailable = true; assertEquals(WmmState(), controller.readState())
        shell.unavailable = false; shell.state.value = ConnectionState.Disconnected; shell.sent.clear()
        assertEquals(WmmState(), controller.readState()); assertTrue(shell.sent.isEmpty())
    }
    @Test fun `reboot clears stale undo and reads actual state instead of saved preference`() = runTest {
        val shell = Shell(); val store = Store(); val controller = WmmController(shell, QuestModel.QUEST_3, store)
        controller.setEnabled(true); shell.boot = "aaaaaaaa-1234-1234-1234-123456789abc"; shell.values.clear()
        assertEquals(WmmState(WmmStatus.OFF), controller.readState()); assertNull(store.saved)
    }
    @Test fun `unsupported device does not read or write properties`() = runTest {
        val shell = Shell()
        assertEquals(WmmState(WmmStatus.UNSUPPORTED), WmmController(shell, QuestModel.QUEST_2, Store()).readState())
        assertTrue(shell.sent.isEmpty())
    }
    @Test fun `externally enabled state does not create misleading undo snapshot`() = runTest {
        val shell = Shell(); val store = Store()
        shell.values.putAll(WmmProperty.entries.map { it.key }.zip(listOf("BODY_TRACKING", "1", "3")))
        val controller = WmmController(shell, QuestModel.QUEST_3, store)
        assertEquals(WmmState(WmmStatus.ON), controller.readState())
        controller.setEnabled(true)
        assertNull(store.saved); assertTrue(shell.sent.none { it.startsWith("setprop") })
    }
    @Test fun `restoring an original on state remains on rather than claiming off`() = runTest {
        val shell = Shell(); val store = Store()
        val original = WmmProperty.entries.map { it.key }.zip(listOf("BODY_TRACKING", "1", "3")).toMap()
        shell.values.putAll(original); store.saved = WmmSnapshot(shell.boot, original)
        val controller = WmmController(shell, QuestModel.QUEST_3, store)
        controller.setEnabled(false)
        assertEquals(WmmState(WmmStatus.ON), controller.readState())
    }

    @Test fun `live disable and enable change only type and retain setup and original undo`() = runTest {
        val shell = Shell(); val store = Store(); val controller = WmmController(shell, QuestModel.QUEST_3, store)
        controller.setEnabled(true); val undo = store.saved; shell.sent.clear()
        assertTrue(controller.setFusionEnabled(false).contains("readback verified"))
        assertEquals(listOf(ShellCommand.deviceProperty(WmmProperty.TYPE, "0").text), shell.sent.filter { it.startsWith("setprop") })
        assertEquals("BODY_TRACKING", shell.values[WmmProperty.FEATURES.key]); assertEquals("1", shell.values[WmmProperty.ALLOW.key])
        assertEquals(WmmState(WmmStatus.PAUSED, true), controller.readState()); assertEquals(undo, store.saved)
        shell.sent.clear(); controller.setFusionEnabled(true)
        assertEquals(listOf(ShellCommand.deviceProperty(WmmProperty.TYPE, "3").text), shell.sent.filter { it.startsWith("setprop") })
        assertEquals(WmmState(WmmStatus.ON, true), controller.readState()); assertEquals(undo, store.saved)
    }
    @Test fun `restore from fusion off restores exact pre setup values after reopen`() = runTest {
        val shell = Shell(); val store = Store(); val controller = WmmController(shell, QuestModel.QUEST_3, store)
        shell.values[WmmProperty.ALLOW.key] = "0"
        controller.setEnabled(true); controller.setFusionEnabled(false)
        val reopened = WmmController(shell, QuestModel.QUEST_3, store)
        assertEquals(WmmState(WmmStatus.PAUSED, true), reopened.readState())
        assertTrue(reopened.setEnabled(false).contains("restored"))
        assertEquals("0", shell.values[WmmProperty.ALLOW.key]); assertEquals("", shell.values[WmmProperty.FEATURES.key])
        assertNull(store.saved)
    }
    @Test fun `failed fusion write rolls back without removing setup or claiming disabled`() = runTest {
        val shell = Shell(); val controller = WmmController(shell, QuestModel.QUEST_3, Store())
        controller.setEnabled(true); shell.ignoredKey = WmmProperty.TYPE.key; shell.sent.clear()
        assertTrue(controller.setFusionEnabled(false).contains("failed"))
        assertEquals(WmmStatus.ON, controller.readState().status)
        assertTrue(shell.sent.filter { it.startsWith("setprop") }.all { it.startsWith("setprop ${WmmProperty.TYPE.key} ") })
    }
    @Test fun `external enabled state can pause safely and restore to original on state`() = runTest {
        val shell = Shell(); val store = Store()
        shell.values.putAll(WmmProperty.entries.map { it.key }.zip(listOf("BODY_TRACKING", "1", "3")))
        val controller = WmmController(shell, QuestModel.QUEST_3, store)
        controller.setFusionEnabled(false); assertEquals(WmmState(WmmStatus.PAUSED, true), controller.readState())
        assertEquals("3", store.saved!!.original[WmmProperty.TYPE.key])
        controller.setEnabled(false); assertEquals(WmmState(WmmStatus.ON), controller.readState())
    }
    @Test fun `fusion change requires durable undo and preserves unrelated type values`() = runTest {
        val shell = Shell(); val store = Store(); store.writable = false
        shell.values.putAll(WmmProperty.entries.map { it.key }.zip(listOf("BODY_TRACKING", "1", "3")))
        val controller = WmmController(shell, QuestModel.QUEST_3, store)
        assertTrue(controller.setFusionEnabled(false).contains("snapshot")); assertTrue(shell.sent.none { it.startsWith("setprop") })
        shell.values[WmmProperty.TYPE.key] = "9"; shell.sent.clear()
        assertTrue(controller.setFusionEnabled(false).contains("outside OQT")); assertTrue(shell.sent.none { it.startsWith("setprop") })
    }
    @Test fun `enable without setup performs complete setup while disable without setup does nothing`() = runTest {
        val shell = Shell(); val controller = WmmController(shell, QuestModel.QUEST_3, Store())
        assertTrue(controller.setFusionEnabled(false).contains("not configured")); assertTrue(shell.sent.none { it.startsWith("setprop") })
        controller.setFusionEnabled(true); assertEquals(WmmState(WmmStatus.ON, true), controller.readState())
    }
    @Test fun `fusion rejects unavailable reads invalid boots and unsupported devices`() = runTest {
        val shell = Shell(); val controller = WmmController(shell, QuestModel.QUEST_3, Store())
        shell.denied = true; controller.setFusionEnabled(false); assertTrue(shell.sent.none { it.startsWith("setprop") })
        shell.denied = false; shell.boot = "invalid"; controller.setFusionEnabled(true); assertTrue(shell.sent.none { it.startsWith("setprop") })
        shell.sent.clear(); WmmController(shell, QuestModel.QUEST_2, Store()).setFusionEnabled(true); assertTrue(shell.sent.isEmpty())
    }
}
