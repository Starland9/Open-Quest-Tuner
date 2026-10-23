package io.github.openquesttuner.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Telemetry parsed only from platform counters returned by the connected headset. Null means unavailable. */
@Serializable
data class RuntimeTelemetry(
    val frameCount: Int = 0,
    val averageFrameMs: Double? = null,
    val p90FrameMs: Double? = null,
    val estimatedFps: Double? = null,
    val jankPercent: Int? = null,
    val cpuPercent: Double? = null,
    val source: String = "Android UI",
    val measuredFps: Double? = null,
    val appGpuMs: Double? = null,
    val staleFrames: Int? = null,
    val reportedRefreshRate: Int? = null,
    val capturedAtMs: Long? = null,
    val gpuPercent: Double? = null,
    val availableMemoryMb: Double? = null,
    val worstCorePercent: Double? = null,
    val cpuLevel: Int? = null,
    val gpuLevel: Int? = null,
    val cpuClockMhz: Int? = null,
    val gpuClockMhz: Int? = null,
    val processId: Int? = null,
    val observedFoveation: Int? = null,
    val observedEyeTexture: EyeTexture? = null,
    val renderRefreshRate: Int? = null,
    val renderCapturedAtMs: Long? = null,
    val renderProcessId: Int? = null,
    val renderSource: String? = null,
    val powerLevel: Int? = null,

) {
    val isVr: Boolean get() = source == "VrApi"
    val hasFrameTiming: Boolean get() = frameCount >= 5 && averageFrameMs != null
}

object RuntimeTelemetryParser {
    fun parse(frameStats: String?, cpuInfo: String?, refreshRate: Int?): RuntimeTelemetry {
        val durations = parseFrameDurations(frameStats.orEmpty())
        val avg = durations.takeIf { it.isNotEmpty() }?.average()
        val p90 = durations.takeIf { it.isNotEmpty() }?.sorted()?.let { it[((it.size - 1) * .90).roundToInt().coerceIn(0, it.lastIndex)] }
        val budget = refreshRate?.takeIf { it > 0 }?.let { 1000.0 / it }
        val jank = if (budget != null && durations.isNotEmpty()) durations.count { it > budget * 1.15 } * 100 / durations.size else null
        val cpu = Regex("([0-9]+(?:\\.[0-9]+)?)%.*").find(cpuInfo.orEmpty())?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        return RuntimeTelemetry(durations.size, avg, p90, avg?.takeIf { it > 0 }?.let { 1000.0 / it }, jank, cpu)
    }

    private fun parseFrameDurations(text: String): List<Double> {
        var intendedIndex = 1
        var completedIndex = 13
        return text.lineSequence().mapNotNull { line ->
            val parts = line.trim().trimEnd(',').split(',')
            if ("IntendedVsync" in parts) {
                intendedIndex = parts.indexOf("IntendedVsync"); completedIndex = parts.indexOf("FrameCompleted")
                return@mapNotNull null
            }
            if (parts.firstOrNull()?.toIntOrNull() != 0 || completedIndex < 0) return@mapNotNull null
            val intended = parts.getOrNull(intendedIndex)?.toLongOrNull() ?: return@mapNotNull null
            val completed = parts.getOrNull(completedIndex)?.toLongOrNull() ?: return@mapNotNull null
            ((completed - intended) / 1_000_000.0).takeIf { it in 0.1..250.0 }
        }.toList().takeLast(120)
    }
}

@Serializable
data class TuneTrialResult(
    val packageName: String,
    val goal: TuneGoal,
    val timestampMs: Long,
    val refreshRate: Int?,
    val samples: Int,
    val p90FrameMs: Double?,
    val jankPercent: Int?,
    val thermalPressurePercent: Int,
    val verdict: TrialVerdict,
)

enum class TrialVerdict { LEARNING, PASS, BORDERLINE, FAIL }

object ClosedLoopTuningEngine {
    fun evaluate(packageName: String, goal: TuneGoal, recent: List<PerformanceSample>): TuneTrialResult {
        val usable = recent.filter { it.packageName == packageName }
        val telemetry = usable.mapNotNull { it.telemetry.takeIf { t -> t.isVr && t.hasFrameTiming } }
        val p90 = telemetry.mapNotNull { it.p90FrameMs }.takeIf { it.isNotEmpty() }?.average()
        val jank = telemetry.mapNotNull { it.jankPercent }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val thermal = if (usable.isEmpty()) 0 else usable.count { it.thermal.warning } * 100 / usable.size
        val hz = usable.lastOrNull()?.refreshRate
        val budget = hz?.takeIf { it > 0 }?.let { 1000.0 / it }
        val verdict = when {
            usable.size < 10 || p90 == null || budget == null -> TrialVerdict.LEARNING
            thermal >= 25 || p90 > budget * 1.35 || (jank ?: 0) >= 20 -> TrialVerdict.FAIL
            thermal >= 10 || p90 > budget * 1.15 || (jank ?: 0) >= 10 -> TrialVerdict.BORDERLINE
            else -> TrialVerdict.PASS
        }
        return TuneTrialResult(packageName, goal, System.currentTimeMillis(), hz, usable.size, p90, jank, thermal, verdict)
    }

    fun nextGoal(current: TuneGoal, verdict: TrialVerdict): TuneGoal? = when (verdict) {
        TrialVerdict.FAIL -> when (current) { TuneGoal.HD_PLUS, TuneGoal.QUALITY -> TuneGoal.BALANCED; TuneGoal.BALANCED -> TuneGoal.PERFORMANCE; else -> null }
        TrialVerdict.PASS -> when (current) { TuneGoal.BATTERY, TuneGoal.PERFORMANCE -> TuneGoal.BALANCED; TuneGoal.BALANCED -> TuneGoal.QUALITY; TuneGoal.QUALITY -> TuneGoal.HD_PLUS; TuneGoal.HD_PLUS -> null }
        else -> null
    }
}
