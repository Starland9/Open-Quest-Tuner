package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class QuestRuntimeToolsTest {
    private val now = 1_700_000_001_000L
    private fun log(pid: Int = 123, time: String = "1700000000.500") = "$time $pid 456 I VrApi : FPS=89.5/90,Stale=2,App=7.25ms,CPU%=0.37(W0.5)"
    @Test fun `VR metrics require the game pid`() {
        assertNull(VrTelemetryParser.latest(log(), setOf(789), now))
        val value = VrTelemetryParser.latest(log(), setOf(123), now)!!
        assertEquals(89.5, value.measuredFps!!, .001); assertEquals(7.25, value.appGpuMs!!, .001)
        assertEquals(2, value.staleFrames); assertTrue(value.isVr); assertFalse(value.hasFrameTiming)
    }
    @Test fun `stale future malformed logs cannot become live stats`() {
        assertNull(VrTelemetryParser.latest(log(time = "1699999900.000"), setOf(123), now))
        assertNull(VrTelemetryParser.latest(log(time = "1700001000.000"), setOf(123), now))
        assertNull(VrTelemetryParser.latest("FPS=90/90", setOf(123), now))
        assertNull(VrTelemetryParser.latest(log().replace("89.5", "NaN"), setOf(123), now))
    }
    @Test fun `another process cannot overwrite the selected app measurement`() {
        val result = VrTelemetryParser.latest(log() + "\n" + log(789).replace("89.5", "12.0"), setOf(123), now)!!
        assertEquals(89.5, result.measuredFps!!, .01)
    }
    @Test fun `modern gfxinfo uses header FrameCompleted not the final column`() {
        val header = "Flags,IntendedVsync,Vsync,FrameCompleted,SwapBuffersCompleted,GpuCompleted,DequeueBufferDuration"
        val rows = (1..6).joinToString("\n") { "0,1000000000,1000000000,1010000000,1011000000,1015000000,0" }
        val result = RuntimeTelemetryParser.parse("$header\n$rows", null, 90)
        assertEquals(6, result.frameCount); assertEquals(10.0, result.averageFrameMs!!, .001)
        assertFalse(result.isVr); assertNull(result.measuredFps)
    }
    @Test fun `terminal quoting and exit handle missing newlines and marker decoys`() {
        val command = ShellCommand.terminal("printf '__OQT_EXIT__:0\nhello'; exit 7")
        assertFalse(command.retryable); assertNotEquals(ShellOutput.EXIT_MARKER, command.exitMarker)
        org.junit.Assume.assumeTrue(java.io.File("/bin/sh").canExecute())
        val process = ProcessBuilder("/bin/sh", "-c", command.wireText).redirectErrorStream(true).start()
        val raw = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor()); assertEquals(7, ShellOutput.parse(raw, command.exitMarker).exitCode)
        assertTrue(ShellOutput.parse(raw, command.exitMarker).output.contains("hello")); assertTrue(ShellOutput.isComplete(raw, command.exitMarker))
    }
    @Test fun `terminal and device side effects are never automatically replayed`() {
        assertFalse(ShellCommand.power(true).retryable); assertFalse(ShellCommand.switchUser(10).retryable)
        assertFalse(ShellCommand.deviceProperty(WmmProperty.TYPE, "3").retryable)
    }
    @Test fun `device command factories reject invalid targets and oversized input`() {
        assertTrue(runCatching { ShellCommand.processIds("com.game; reboot") }.isFailure)
        assertTrue(runCatching { ShellCommand.switchUser(-1) }.isFailure)
        assertTrue(runCatching { ShellCommand.terminal("x".repeat(8193)) }.isFailure)
        assertTrue(runCatching { ShellCommand.terminal("\u0000") }.isFailure)
    }
    @Test fun `accounts exclude managed restricted and disabled profiles`() {
        val users = HeadsetUsers.parse("UserInfo{0:Owner:c13}\nUserInfo{10:Alex:410}\nUserInfo{11:Work:30}\nUserInfo{12:Disabled:450}\nUserInfo{13:Restricted:418}")
        assertEquals(listOf(0, 10), users.map { it.id })
    }
    @Test fun `ignored profile writes cannot launch`() = runTest {
        val shell = FakeShellBackend(); shell.respond("getprop", ShellResult(0, ""))
        val result = Tuner(shell, QuestModel.QUEST_3, FakeDisplayRates()).applyAndLaunch("com.example.game", "com.example.Main", GameProfile(refreshRate = 90))
        assertTrue(result is TuneResult.StepFailed && result.step == TuneStep.Verify)
        assertTrue(shell.executed.none { it.startsWith("am start") })
    }
    @Test fun `failed readback cannot pass automatic application`() = runTest {
        val shell = FakeShellBackend(); shell.respond("getprop", ShellResult(1, "denied"))
        val result = Tuner(shell, QuestModel.QUEST_3, FakeDisplayRates()).applyProfile(GameProfile())
        assertTrue(result is TuneResult.StepFailed && result.step == TuneStep.Verify)
    }
    @Test fun `readback catches defaults that failed to clear`() = runTest {
        val shell = FakeShellBackend(); shell.respond("getprop", ShellResult(0, "[debug.oculus.cpuLevel]: [4]"))
        assertTrue(Tuner(shell, QuestModel.QUEST_3, FakeDisplayRates()).applyProfile(GameProfile()) is TuneResult.StepFailed)
    }
    @Test fun `ambiguous resumed panels cannot attribute gameplay`() = runTest {
        val shell = FakeShellBackend(); shell.respond("dumpsys activity", ShellResult(0, "mResumedActivity: ActivityRecord{123 u0 io.github.openquesttuner/.Main}\nmResumedActivity: ActivityRecord{456 u0 com.example.game/.Main}"))
        assertNull(Tuner(shell, QuestModel.QUEST_3, FakeDisplayRates()).readForegroundPackage(setOf("com.example.game")))
    }
    private fun samples(hz: Int = 90, fps: Double = 90.0, property: String = "90", start: Long = 0) = (1..12).map {
        PerformanceSample(start + it, "com.example.game", hz, ThermalLevel.NONE, mapOf("debug.oculus.refreshRate" to property),
            RuntimeTelemetry(source = "VrApi", measuredFps = fps, appGpuMs = 6.0, reportedRefreshRate = hz))
    }
    @Test fun `comparisons require new homogeneous data and changed settings`() {
        val before = samples()
        assertTrue(PerformanceComparison.describe(before, before).contains("10 new"))
        assertTrue(PerformanceComparison.describe(before, samples(start = 20)).contains("unchanged"))
        assertTrue(PerformanceComparison.describe(before, samples(property = "80", fps = 88.0, start = 20)).contains("-2.0"))
        assertTrue(PerformanceComparison.describe(before, samples(hz = 80, property = "80", start = 20)).contains("Refresh rate changed"))
        assertTrue(PerformanceComparison.describe(before, samples(property = "80", start = 20).take(5) + samples(property = "72", start = 40).take(5)).contains("10 new"))
    }
    @Test fun `Android UI frames cannot compare VR performance or pass a VR trial`() {
        val ui = samples().map { it.copy(telemetry = RuntimeTelemetry(frameCount = 120, averageFrameMs = 1.0, p90FrameMs = 1.0, jankPercent = 0)) }
        assertTrue(PerformanceComparison.describe(ui, samples(start = 20)).contains("Save a baseline"))
        assertEquals(TrialVerdict.LEARNING, ClosedLoopTuningEngine.evaluate("com.example.game", TuneGoal.BALANCED, ui).verdict)
    }
}

class WmmControllerTest {
    private val boot = "12345678-1234-1234-1234-123456789abc"
    private class Store : WmmSnapshotStore {
        var snapshot: WmmSnapshot? = null; var writable = true
        override fun load() = snapshot
        override fun save(snapshot: WmmSnapshot): Boolean { if (writable) this.snapshot = snapshot; return writable }
        override fun clear() { snapshot = null }
    }
    private inner class Shell : ShellBackend {
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected(ConnectionMethod.WIRELESS))
        val values = mutableMapOf<String, String>(); val sent = mutableListOf<String>()
        var ignoredKey: String? = null; var liveBoot = boot
        override suspend fun exec(command: ShellCommand): ShellResult {
            sent += command.text
            if (command.text == ShellCommand.bootId().text) return ShellResult(0, liveBoot)
            if (command.text.startsWith("getprop ")) return ShellResult(0, values[command.text.removePrefix("getprop ")].orEmpty())
            val m = Regex("setprop ([a-zA-Z0-9_.]+) '(.*)'").matchEntire(command.text)
            if (m != null && m.groupValues[1] != ignoredKey) values[m.groupValues[1]] = m.groupValues[2]
            return ShellResult(0, "")
        }
    }
    @Test fun `enable readbacks and restore returns exact original values`() = runTest {
        val shell = Shell(); val store = Store(); shell.values[WmmProperty.ALLOW.key] = "0"
        val controller = WmmController(shell, QuestModel.QUEST_3, store)
        assertTrue(controller.setEnabled(true).contains("read back")); assertEquals("3", shell.values[WmmProperty.TYPE.key])
        assertTrue(controller.setEnabled(false).contains("restored")); assertEquals("0", shell.values[WmmProperty.ALLOW.key]); assertEquals("", shell.values[WmmProperty.FEATURES.key]); assertNull(store.snapshot)
    }
    @Test fun `existing unrelated feature overrides are never replaced`() = runTest {
        val shell = Shell(); shell.values[WmmProperty.FEATURES.key] = "OTHER_FEATURE"
        assertTrue(WmmController(shell, QuestModel.QUEST_3, Store()).setEnabled(true).contains("preserve"))
        assertTrue(shell.sent.none { it.startsWith("setprop") })
    }
    @Test fun `failed write rolls back all previous steps`() = runTest {
        val shell = Shell(); shell.ignoredKey = WmmProperty.TYPE.key
        val result = WmmController(shell, QuestModel.QUEST_3, Store()).setEnabled(true)
        assertTrue(result.contains("failed")); assertTrue(result.contains("restored and read back"))
        assertEquals("", shell.values[WmmProperty.FEATURES.key]); assertEquals("", shell.values[WmmProperty.ALLOW.key])
    }
    @Test fun `undo snapshot must be durable before mutation`() = runTest {
        val shell = Shell(); val store = Store(); store.writable = false
        assertTrue(WmmController(shell, QuestModel.QUEST_3, store).setEnabled(true).contains("snapshot"))
        assertTrue(shell.sent.none { it.startsWith("setprop") })
    }
    @Test fun `restore refuses to overwrite external changes`() = runTest {
        val shell = Shell(); val store = Store(); val controller = WmmController(shell, QuestModel.QUEST_3, store)
        controller.setEnabled(true); shell.values[WmmProperty.TYPE.key] = "9"; shell.sent.clear()
        assertTrue(controller.setEnabled(false).contains("outside OQT")); assertTrue(shell.sent.none { it.startsWith("setprop") })
    }
    @Test fun `reboot invalidates the old undo snapshot`() = runTest {
        val shell = Shell(); val store = Store(); val controller = WmmController(shell, QuestModel.QUEST_3, store)
        controller.setEnabled(true); shell.liveBoot = "aaaaaaaa-1234-1234-1234-123456789abc"; shell.sent.clear()
        assertTrue(controller.setEnabled(false).contains("No OQT")); assertTrue(shell.sent.none { it.startsWith("setprop") }); assertNull(store.snapshot)
    }
    @Test fun `unsupported hardware never executes commands`() = runTest {
        val shell = Shell(); assertTrue(WmmController(shell, QuestModel.QUEST_2, Store()).setEnabled(true).contains("requires")); assertTrue(shell.sent.isEmpty())
    }
}
