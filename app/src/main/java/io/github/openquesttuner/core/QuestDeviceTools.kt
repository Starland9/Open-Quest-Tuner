package io.github.openquesttuner.core

enum class WmmProperty(val key: String) {
    FEATURES("debug.oculus.forceEnablePerformanceFeatures"),
    ALLOW("debug.bodyapi.allowhandoverride"),
    TYPE("debug.bodyapi.handoverridetype"),
}
data class HeadsetUser(val id: Int, val name: String)
object HeadsetUsers {
    fun parse(output: String): List<HeadsetUser> = Regex("UserInfo\\{(\\d+):([^\\r\\n}]+):([0-9a-fA-F]+)\\}")
        .findAll(output).mapNotNull {
            val flags = it.groupValues[3].toIntOrNull(16) ?: return@mapNotNull null
            val id = it.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (flags and (0x20 or 0x08 or 0x40) != 0) null else HeadsetUser(id, it.groupValues[2])
        }.distinctBy { it.id }.toList()
}
/** Never attribute compositor-wide or stale log lines to an app. */
object VrTelemetryParser {
    fun latest(log: String, pids: Set<Int>, now: Long): RuntimeTelemetry? {
        val row = VrApiMetricsParser.latest(log, now, pids) ?: return null
        if (row.fps == null || row.refreshRate == null) return null
        return RuntimeTelemetry(source = "VrApi", measuredFps = row.fps, appGpuMs = row.appGpuMs,
            staleFrames = row.staleFrames, cpuPercent = row.cpuPercent, gpuPercent = row.gpuPercent,
            availableMemoryMb = row.availableMemoryMb, worstCorePercent = row.worstCorePercent,
            cpuLevel = row.cpuLevel, gpuLevel = row.gpuLevel, cpuClockMhz = row.cpuClockMhz, gpuClockMhz = row.gpuClockMhz,
            reportedRefreshRate = row.refreshRate, capturedAtMs = row.timestampMs, processId = row.pid, observedFoveation = row.observedFoveation, powerLevel = row.powerLevel)
    }
}
object PerformanceComparison {
    fun describe(baseline: List<PerformanceSample>, current: List<PerformanceSample>): String {
        fun homogeneous(rows: List<PerformanceSample>) = rows.takeLastWhile { it.activeProperties == rows.last().activeProperties }
        val before = homogeneous(baseline.filter { it.telemetry.isVr && it.telemetry.measuredFps != null })
        val after = homogeneous(current.filter { it.telemetry.isVr && it.telemetry.measuredFps != null && it.timestampMs > (before.lastOrNull()?.timestampMs ?: 0) }).takeLast(30)
        if (before.size < 10) return "Save a baseline after at least 10 fresh VR observations."
        if (after.size < 10) return "Baseline saved. Collect 10 new VR observations with the other profile."
        if (before.last().activeProperties == after.last().activeProperties) return "Settings are unchanged since the baseline; this is not a profile comparison."
        if (before.last().telemetry.reportedRefreshRate != after.last().telemetry.reportedRefreshRate) return "Refresh rate changed. Compare frame-budget compliance rather than raw FPS."
        val delta = after.mapNotNull { it.telemetry.measuredFps }.average() - before.mapNotNull { it.telemetry.measuredFps }.average()
        val gpuBefore = before.mapNotNull { it.telemetry.appGpuMs }.takeIf { it.isNotEmpty() }?.average()
        val gpuAfter = after.mapNotNull { it.telemetry.appGpuMs }.takeIf { it.isNotEmpty() }?.average()
        return "FPS change %+.1f".format(delta) + (if (gpuBefore != null && gpuAfter != null) " • GPU time %+.2f ms".format(gpuAfter - gpuBefore) else "") + ". Repeat the same scene; this is an observed difference, not proof of causation."
    }
}
