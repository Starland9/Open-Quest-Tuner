package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SystemResourcesTest {
    private val at = 1_700_000_000_500L
    private val official = "FPS=72/72,CPU4/GPU=2/2,1171/441MHz,Mem=2092MHz,Free=2975MB,App=4.49ms,Stale=0,GPU%=0.43,CPU%=0.37(W0.50)"
    private fun log(body: String = official, pid: Int = 123, time: Long = at): String = "${time / 1000}.${(time % 1000).toString().padStart(3, '0')} $pid 456 I VrApi : $body"
    @Test fun `Meta example maps fractions memory levels and clocks correctly`() {
        val row = VrApiMetricsParser.latest(log(), at + 500)!!
        assertEquals(37.0, row.cpuPercent!!, .001); assertEquals(50.0, row.worstCorePercent!!, .001)
        assertEquals(43.0, row.gpuPercent!!, .001); assertEquals(2975.0, row.availableMemoryMb!!, .001)
        assertEquals(2, row.cpuLevel); assertEquals(2, row.gpuLevel)
        assertEquals(1171, row.cpuClockMhz); assertEquals(441, row.gpuClockMhz)
    }
    @Test fun `zero and full scale are real readings rather than missing values`() {
        val row = VrApiMetricsParser.latest(log("CPU%=0(W1.0),GPU%=1,Free=0MB"), at)!!
        assertEquals(0.0, row.cpuPercent!!, .001); assertEquals(100.0, row.gpuPercent!!, .001)
        assertEquals(0.0, row.availableMemoryMb!!, .001)
    }
    @Test fun `invalid fractions never clamp to convincing valid percentages`() {
        for (bad in listOf("1.2", "-0.2", "NaN", "Infinity")) {
            val row = VrApiMetricsParser.latest(log("CPU%=$bad,GPU%=$bad,Free=2975MB"), at)!!
            assertNull(row.cpuPercent); assertNull(row.gpuPercent)
        }
    }
    @Test fun `missing fields stay unknown and memory clock is not available RAM`() {
        val row = VrApiMetricsParser.latest(log("FPS=72/72,Mem=2092MHz"), at)!!
        assertNull(row.availableMemoryMb); assertNull(row.cpuPercent); assertNull(row.gpuPercent)
    }
    @Test fun `resource lines without FPS cannot masquerade as game FPS`() {
        val line = log("CPU%=0.2,GPU%=0.3,Free=2000MB")
        assertNotNull(VrApiMetricsParser.latest(line, at))
        assertNull(VrTelemetryParser.latest(line, setOf(123), at))
    }
    @Test fun `reject stale future invalid tags and bad headers`() {
        assertNull(VrApiMetricsParser.latest(log(), at + 6001))
        assertNull(VrApiMetricsParser.latest(log(), at - 1))
        assertNull(VrApiMetricsParser.latest(log().replace("VrApi", "OtherTag"), at))
        assertNull(VrApiMetricsParser.latest(official, at))
        assertNull(VrApiMetricsParser.latest(log("CPU%=NaN,GPU%=Infinity,Free=badMB"), at))
    }
    @Test fun `most recent timestamp wins and app PID filtering remains strict`() {
        val text = log("CPU%=0.7,Free=2000MB", time = at) + "\n" + log("CPU%=0.1,Free=2000MB", time = at - 100)
        assertEquals(70.0, VrApiMetricsParser.latest(text, at)!!.cpuPercent!!, .01)
        assertNull(VrApiMetricsParser.latest(text, at, setOf(999)))
        assertNull(VrTelemetryParser.latest(log(), emptySet(), at))
    }
    @Test fun `process accounting and device CPU counters remain separate`() {
        val row = VrTelemetryParser.latest(log(), setOf(123), at)!!
        assertEquals(43.0, row.gpuPercent!!, .001); assertEquals(2975.0, row.availableMemoryMb!!, .001)
        val legacy = Json.decodeFromString<RuntimeTelemetry>("""{"cpuPercent":180.0}""")
        assertFalse(legacy.isVr); assertNull(legacy.gpuPercent); assertNull(legacy.availableMemoryMb)
    }
    @Test fun `proc CPU reports aggregate busy percentage and counts iowait as idle`() {
        val a = CpuTicks.parse("cpu 100 0 50 850 0 0 0 0 200 0")
        val b = CpuTicks.parse("cpu 120 0 60 910 10 0 0 0 400 0")
        assertEquals(30.0, CpuTicks.usage(a, b)!!, .001)
    }
    @Test fun `initial duplicate reset or denied CPU counters are unavailable`() {
        val a = CpuTicks.parse("cpu 100 0 50 850")!!
        assertNull(CpuTicks.usage(null, a)); assertNull(CpuTicks.usage(a, a))
        assertNull(CpuTicks.usage(a, CpuTicks.parse("cpu 1 0 1 8")))
        assertNull(CpuTicks.parse("Permission denied")); assertNull(CpuTicks.parse("cpu 1 -2 3 4"))
    }
    @Test fun `RAM percentage requires real total capacity and consistent units`() {
        val ram = ResourceValue(2000.0, at, "test")
        assertEquals(75, SystemResourceSnapshot(availableRam = ram, totalRamMb = 8000.0).ramUsedPercent)
        assertNull(SystemResourceSnapshot(availableRam = ram).ramUsedPercent)
        assertNull(SystemResourceSnapshot(availableRam = ram, totalRamMb = 1000.0).ramUsedPercent)
        assertNull(SystemResourceSnapshot(availableRam = ram, totalRamMb = 0.0).ramUsedPercent)
    }
    @Test fun `freshness expires every displayed metric even if no new state arrives`() {
        val value = ResourceValue(25.0, at, "test")
        val snapshot = SystemResourceSnapshot(value, value, value, 8000.0)
        assertNotNull(snapshot.fresh(at + 6000).gpu)
        val expired = snapshot.fresh(at + 6001)
        assertNull(expired.cpu); assertNull(expired.gpu); assertNull(expired.ramUsedPercent)
        assertNull(snapshot.fresh(at - 1).cpu)
    }
    @Test fun `VR primary uses one log read and avoids unnecessary proc calls`() = runTest {
        val shell = FakeShellBackend(); shell.respond("logcat", ShellResult(0, log()))
        val sampler = SystemResourceSampler(shell, { SystemMemory(8000.0, 4000.0) }, { at })
        val state = sampler.poll()
        assertEquals(37.0, state.cpu!!.value, .01); assertEquals(43.0, state.gpu!!.value, .01)
        assertEquals(2975.0, state.availableRam!!.value, .01); assertEquals(63, state.ramUsedPercent)
        assertTrue(state.availableRam!!.source.contains("VrApi"))
        assertEquals(listOf(ShellCommand.vrLogs().text), shell.executed)
        assertEquals(log(), sampler.runtimeLogs())
    }
    @Test fun `no VR session uses measured Android CPU and memory without inventing GPU`() = runTest {
        val shell = FakeShellBackend()
        var now = at
        shell.respond("head", ShellResult(0, "cpu 100 0 50 850 0 0 0 0"))
        val sampler = SystemResourceSampler(shell, { SystemMemory(8000.0, 4000.0) }, { now })
        assertNull(sampler.poll().cpu)
        now += 1000; shell.respond("head", ShellResult(0, "cpu 120 0 60 920 0 0 0 0"))
        val state = sampler.poll()
        assertEquals(30.0, state.cpu!!.value, .01); assertNull(state.gpu)
        assertEquals(50, state.ramUsedPercent); assertTrue(state.cpu!!.source.contains("/proc/stat"))
        assertTrue(shell.executed.none { it.contains("setprop") || it.contains("ovrgpuprofiler") })
    }
    @Test fun `stale VR records do not survive through fresh memory fallback`() = runTest {
        val shell = FakeShellBackend(); shell.respond("logcat", ShellResult(0, log()))
        var now = at; val sampler = SystemResourceSampler(shell, { SystemMemory(8000.0, 4000.0) }, { now })
        sampler.poll(); now += 7000
        val state = sampler.poll()
        assertNull(state.cpu); assertNull(state.gpu); assertEquals(50, state.ramUsedPercent)
        assertTrue(state.availableRam!!.source.contains("Android MemoryInfo"))
    }
    @Test fun `disconnect clears CPU GPU and cache but native RAM remains usable`() = runTest {
        val shell = FakeShellBackend(); shell.respond("logcat", ShellResult(0, log()))
        val sampler = SystemResourceSampler(shell, { SystemMemory(8000.0, 4000.0) }, { at })
        sampler.poll(); shell.state.value = ConnectionState.Disconnected
        val state = sampler.poll()
        assertNull(state.cpu); assertNull(state.gpu); assertEquals(50, state.ramUsedPercent); assertEquals("", sampler.runtimeLogs())
    }
    @Test fun `impossible VR free memory falls back to valid native RAM`() = runTest {
        val shell = FakeShellBackend(); shell.respond("logcat", ShellResult(0, log("CPU%=0.4,GPU%=0.5,Free=99999MB")))
        val state = SystemResourceSampler(shell, { SystemMemory(8000.0, 4000.0) }, { at }).poll()
        assertEquals(50, state.ramUsedPercent); assertTrue(state.availableRam!!.source.contains("Android MemoryInfo"))
    }
    @Test fun `unknown native capacity shows available MB without fabricating RAM percent`() = runTest {
        val shell = FakeShellBackend(); shell.respond("logcat", ShellResult(0, log()))
        val state = SystemResourceSampler(shell, { null }, { at }).poll()
        assertEquals(2975.0, state.availableRam!!.value, .01); assertNull(state.ramUsedPercent)
    }
    @Test fun `denied native counters are not reported as zero usage`() = runTest {
        val shell = FakeShellBackend(); shell.respond("head", ShellResult(1, "Permission denied"))
        val state = SystemResourceSampler(shell, { SystemMemory(0.0, 0.0) }, { at }).poll()
        assertNull(state.cpu); assertNull(state.gpu); assertNull(state.availableRam); assertNull(state.ramUsedPercent)
    }
    @Test fun `per-game telemetry reuses the resource reader cache`() = runTest {
        val now = System.currentTimeMillis(); val shell = FakeShellBackend()
        shell.respond("logcat", ShellResult(0, log(time = now))); shell.respond("pidof", ShellResult(0, "123"))
        val sampler = SystemResourceSampler(shell, { SystemMemory(8000.0, 4000.0) }, { now })
        sampler.poll()
        val tuner = Tuner(shell, QuestModel.QUEST_3, FakeDisplayRates(), sampler::runtimeLogs)
        assertTrue(tuner.readRuntimeTelemetry("com.example.game", 72).isVr)
        assertEquals(1, shell.executed.count { it.startsWith("logcat") })
    }
}
