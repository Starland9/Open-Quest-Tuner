package io.github.openquesttuner.core

import kotlinx.serialization.Serializable

@Serializable
data class PerformanceSample(
    val timestampMs: Long,
    val packageName: String?,
    val refreshRate: Int?,
    val thermal: ThermalLevel,
    val activeProperties: Map<String, String> = emptyMap(),
    val telemetry: RuntimeTelemetry = RuntimeTelemetry(),
    val readbackVerified: Boolean = false,
)

@Serializable
data class PerformanceAssessment(
    val status: Status,
    val headline: String,
    val details: List<String>,
) {
    enum class Status { GOOD, WATCH, HOT }
}

/** Uses signals the app can verify reliably without pretending to measure app FPS. */
object PerformanceIntelligence {
    fun assess(samples: List<PerformanceSample>, expected: GameProfile?): PerformanceAssessment {
        if (samples.isEmpty()) return PerformanceAssessment(PerformanceAssessment.Status.WATCH, "Waiting for live data", listOf("Connect to the headset and keep the monitor open to build a runtime history."))
        val recent = samples.takeLast(30)
        val latest = recent.last()
        val thermalNames = recent.map { it.thermal.name }
        val hot = thermalNames.count { it == "SEVERE" || it == "CRITICAL" || it == "EMERGENCY" || it == "SHUTDOWN" }
        val warm = thermalNames.count { it == "MODERATE" }
        val mismatches = expected?.let { p -> recent.count { !p.isActiveOn(it.activeProperties) } } ?: 0
        val details = buildList {
            add("${recent.size} recent samples • display ${latest.refreshRate?.let { "$it Hz" } ?: "rate unavailable"}.")
            if (expected != null) add(if (mismatches == 0) "Saved profile properties match the sampled headset state." else "Profile mismatch seen in $mismatches of ${recent.size} samples.")
            if (hot > 0) add("High thermal pressure appeared in $hot samples.") else if (warm > 0) add("Moderate thermal pressure appeared in $warm samples.") else add("No elevated thermal state was seen in this sample window.")
            val t = latest.telemetry
            if (t.hasFrameTiming) add("Android UI timing (not VR FPS): ${"%.1f".format(t.p90FrameMs)} ms p90 • ${t.jankPercent ?: 0}% over-budget frames${t.cpuPercent?.let { " • CPU ${"%.1f".format(it)}%" } ?: ""}.")
            else if (!t.isVr) add("No app-attributed VR counters are available. Direct runtime counters are required; Android UI frames are not VR telemetry.")
            if (t.isVr) {
                add("VrApi: ${t.measuredFps?.let { "%.1f".format(it) } ?: "—"} FPS • ${t.appGpuMs?.let { "%.2f".format(it) } ?: "—"} ms GPU • ${t.staleFrames ?: "unavailable"} stale frames.")
                val budget = t.reportedRefreshRate?.let { 1000.0 / it }
                if (budget != null && t.appGpuMs != null && t.appGpuMs > budget * .9) add("GPU time is near the frame budget. Try lower resolution or stronger foveation, then compare the same scene.")
                if ((t.staleFrames ?: 0) > 0) add("Stale frames observed. Capture a repeatable scene before raising refresh rate or resolution. SpaceWarp may intentionally render below display refresh rate.")
                if (recent.size < 10) add("Collect at least 10 fresh VR observations before judging a profile.")
            }
        }
        return when {
            hot > 0 -> PerformanceAssessment(PerformanceAssessment.Status.HOT, "Thermal pressure detected", details)
            mismatches > recent.size / 3 || warm > recent.size / 3 -> PerformanceAssessment(PerformanceAssessment.Status.WATCH, "Profile needs attention", details)
            else -> PerformanceAssessment(PerformanceAssessment.Status.GOOD, "Profile state looks stable", details)
        }
    }
}
