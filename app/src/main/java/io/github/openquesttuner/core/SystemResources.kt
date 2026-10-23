package io.github.openquesttuner.core

import kotlin.math.roundToInt

/** Android memory sizes converted to binary MB, matching the runtime's customary MB display. */
data class SystemMemory(val totalMb: Double, val availableMb: Double) {
    val valid: Boolean get() = totalMb.isFinite() && availableMb.isFinite() && totalMb > 0 && availableMb in 0.0..totalMb
}
data class ResourceValue(val value: Double, val timestampMs: Long, val source: String) {
    fun fresh(now: Long): Boolean = now - timestampMs in 0..6000
}
data class SystemResourceSnapshot(
    val cpu: ResourceValue? = null, val gpu: ResourceValue? = null, val availableRam: ResourceValue? = null,
    val totalRamMb: Double? = null, val vr: VrApiMetrics? = null,
) {
    fun fresh(now: Long) = copy(cpu = cpu?.takeIf { it.fresh(now) }, gpu = gpu?.takeIf { it.fresh(now) },
        availableRam = availableRam?.takeIf { it.fresh(now) }, vr = vr?.takeIf { now - it.timestampMs in 0..6000 })
    val ramUsedPercent: Int? get() {
        val available = availableRam?.value ?: return null
        val total = totalRamMb?.takeIf { it.isFinite() && it > 0 } ?: return null
        if (!available.isFinite() || available !in 0.0..total) return null
        return ((1 - available / total) * 100).roundToInt()
    }
}

/** Aggregate /proc/stat counters; guest fields are excluded because user/nice already include them. */
data class CpuTicks(val fields: List<Long>) {
    companion object {
        fun parse(text: String): CpuTicks? {
            val parts = text.lineSequence().firstOrNull { it.startsWith("cpu ") }?.trim()?.split(Regex("""\s+""")) ?: return null
            if (parts.size < 5) return null
            val values = parts.drop(1).take(8).map { it.toLongOrNull()?.takeIf { value -> value >= 0 } ?: return null }
            return CpuTicks(values + List(8 - values.size) { 0L })
        }
        fun usage(before: CpuTicks?, after: CpuTicks?): Double? {
            if (before == null || after == null) return null
            val delta = before.fields.zip(after.fields).map { (a, b) -> if (b < a) return null else b - a }
            val total = delta.sumOf { it.toDouble() }
            if (total <= 0) return null
            val idle = delta[3].toDouble() + delta[4]
            return ((total - idle) * 100 / total).takeIf { it.isFinite() && it in 0.0..100.0 }
        }
    }
}

/** One shared bounded logcat read per poll; callers can reuse it for app-attributed performance data. */
class SystemResourceSampler(
    private val shell: ShellBackend, private val memory: () -> SystemMemory?, private val clock: () -> Long,
) {
    private var previousCpu: CpuTicks? = null
    @Volatile private var cache: Pair<Long, String>? = null
    fun runtimeLogs(): String = cache?.takeIf { shell.state.value is ConnectionState.Connected && clock() - it.first in 0..3000 }?.second.orEmpty()
    suspend fun poll(): SystemResourceSnapshot {
        var vr: VrApiMetrics? = null
        var fallbackCpu: Double? = null
        if (shell.state.value is ConnectionState.Connected) {
            try {
                val result = shell.exec(ShellCommand.vrLogs())
                cache = if (result.isSuccess) clock() to result.output else null
                vr = cache?.let { VrApiMetricsParser.latest(it.second, clock()) }
                if (vr?.cpuPercent == null) {
                    val read = shell.exec(ShellCommand.readSystemCpu())
                    val current = if (read.isSuccess) CpuTicks.parse(read.output) else null
                    fallbackCpu = CpuTicks.usage(previousCpu, current)
                    previousCpu = current
                } else previousCpu = null
            } catch (_: ShellUnavailableException) { cache = null; previousCpu = null }
        } else { cache = null; previousCpu = null }
        if (shell.state.value !is ConnectionState.Connected) { vr = null; fallbackCpu = null }
        val now = clock()
        val mem = memory()?.takeIf { it.valid }
        val runtimeFree = vr?.availableMemoryMb?.takeIf { it.isFinite() && it >= 0 && (mem == null || it <= mem.totalMb) }
        return SystemResourceSnapshot(
            cpu = vr?.cpuPercent?.let { ResourceValue(it, vr.timestampMs, "VrApi · mean across CPU cores") }
                ?: fallbackCpu?.let { ResourceValue(it, now, "Android /proc/stat · mean across CPU cores") },
            gpu = vr?.gpuPercent?.let { ResourceValue(it, vr.timestampMs, "VrApi · GPU utilization") },
            availableRam = runtimeFree?.let { ResourceValue(it, vr!!.timestampMs, "VrApi · Android available memory") }
                ?: mem?.let { ResourceValue(it.availableMb, now, "Android MemoryInfo · available memory") },
            totalRamMb = mem?.totalMb, vr = vr,
        )
    }
}
