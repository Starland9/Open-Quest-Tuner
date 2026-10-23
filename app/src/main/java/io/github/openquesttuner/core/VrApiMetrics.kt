package io.github.openquesttuner.core

/** A single timestamped runtime line. CPU/GPU percentages here are normalized to 0..100. */
data class VrApiMetrics(
    val pid: Int, val timestampMs: Long,
    val cpuPercent: Double?, val worstCorePercent: Double?, val gpuPercent: Double?, val availableMemoryMb: Double?,
    val cpuLevel: Int?, val gpuLevel: Int?, val cpuClockMhz: Int?, val gpuClockMhz: Int?,
    val fps: Double?, val refreshRate: Int?, val appGpuMs: Double?, val staleFrames: Int?, val observedFoveation: Int? = null, val powerLevel: Int? = null,
)
object VrApiMetricsParser {
    private val header = Regex("""^\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+[VDIWEF]\s+(?:VrApi|crcs)\s*:""")
    fun latest(log: String, now: Long, pids: Set<Int>? = null): VrApiMetrics? = log.lineSequence().mapNotNull { line ->
        val h = header.find(line) ?: return@mapNotNull null
        val pid = h.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        if (pids != null && pid !in pids) return@mapNotNull null
        val seconds = h.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it < Long.MAX_VALUE / 1000.0 } ?: return@mapNotNull null
        val at = (seconds * 1000).toLong()
        if (now - at !in 0..6000) return@mapNotNull null
        val body = line.substring(h.range.last + 1)
        fun token(key: String): String? = Regex("(?:^|[, ])${Regex.escape(key)}=([^, ]+)").find(body)?.groupValues?.get(1)
        fun number(text: String?): Double? = text?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        fun fraction(text: String?): Double? = number(text)?.takeIf { it <= 1 }?.times(100)
        val cpu = token("CPU%")
        val mean = fraction(cpu?.substringBefore('('))
        val worst = fraction(cpu?.let { Regex("""\(W([0-9.]+)\)""").find(it)?.groupValues?.get(1) })
        val gpu = fraction(token("GPU%"))
        val free = token("Free")?.let { Regex("""([0-9.]+)MB""").matchEntire(it)?.groupValues?.get(1) }?.let(::number)
        val rates = token("FPS")?.split('/')
        val fps = number(rates?.getOrNull(0))?.takeIf { it <= 240 }
        val hz = number(rates?.getOrNull(1))?.takeIf { it in 60.0..240.0 && it % 1.0 == 0.0 }?.toInt()
        if (mean == null && gpu == null && free == null && (fps == null || hz == null)) return@mapNotNull null
        val levels = Regex("""(?:^|[, ])CPU\d*/GPU=(\d+)/(\d+),(\d+)/(\d+)MHz(?:,|$)""").find(body)
        fun level(index: Int) = levels?.groupValues?.get(index)?.toIntOrNull()?.takeIf { it in 0..100 }
        fun clock(index: Int) = levels?.groupValues?.get(index)?.toIntOrNull()?.takeIf { it in 1..10000 }
        VrApiMetrics(pid, at, mean, worst, gpu, free, level(1), level(2), clock(3), clock(4), fps, hz,
            number(token("App")?.removeSuffix("ms"))?.takeIf { it <= 1000 }, number(token("Stale"))?.takeIf { it % 1 == 0.0 && it <= Int.MAX_VALUE }?.toInt(), token("Fov")?.takeWhile { it.isDigit() }?.toIntOrNull()?.takeIf { it in 0..4 }, token("PLS")?.toIntOrNull()?.takeIf { it in 0..2 })
    }.maxByOrNull { it.timestampMs }
}
