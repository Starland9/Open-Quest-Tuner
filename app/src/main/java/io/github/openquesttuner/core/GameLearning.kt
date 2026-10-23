package io.github.openquesttuner.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable data class LearningContext(val packageName: String, val appVersion: Long, val systemBuild: String, val headset: String)
@Serializable enum class LearningMode { LIVE, RELAUNCH }
@Serializable data class LearningConfig(val goal: TuneGoal = TuneGoal.BALANCED, val seconds: Int = 60, val halfRate: Boolean = false, val mode: LearningMode = LearningMode.LIVE, val autoKeep: Boolean = false, val warmupSeconds: Int = 15, val manageOvrCapture: Boolean = true, val automatic: Boolean = false) {
    fun valid() = seconds in listOf(30, 60, 120) && warmupSeconds in listOf(15, 30, 60, 120)
}
@Serializable data class LearnedWindow(
    val context: LearningContext, val profile: GameProfile, val runId: String, val stage: String,
    val startedAt: Long, val endedAt: Long, val count: Int, val hz: Int, val halfRate: Boolean,
    val fpsP10: Double, val fpsMean: Double, val gpuP95: Double, val staleMean: Double,
    val compliance: Double, val thermalPercent: Double, val cpuMean: Double?, val gpuMean: Double?,
    val observedCpu: Int?, val observedGpu: Int?, val verifiedTrial: Boolean = false, val observedFoveation: Int? = null, val observedEyeTexture: EyeTexture? = null, val renderVerified: Boolean = false,
) {
    val targetFps get() = hz.toDouble() / if (halfRate) 2 else 1
    val healthy get() = compliance >= .95 && fpsP10 >= targetFps * .97 && gpuP95 <= 1000.0 / targetFps * .95 && staleMean <= .5 && thermalPercent == 0.0
}
@Serializable data class LearnedRecommendation(val context: LearningContext, val goal: TuneGoal, val profile: GameProfile,
    val windows: Int, val observedSeconds: Int, val fpsP10: Double, val gpuP95: Double, val reason: String)
@Serializable data class LearningUndo(val bootId: String, val context: LearningContext, val original: GameProfile, val lastWritten: GameProfile, val runId: String, val previousWritten: GameProfile? = null)
@Serializable enum class LearningResult { APPLIED, SAVED_FOR_LAUNCH, REVIEW, UNCHANGED, INCONCLUSIVE, STOPPED, UNAVAILABLE, RECOVERY }
@Serializable data class LearningEvent(val timestampMs: Long, val packageName: String, val runId: String, val message: String, val profile: GameProfile, val observedCpu: Int? = null, val observedGpu: Int? = null, val observedEyeTexture: EyeTexture? = null, val observedHz: Int? = null)

@Serializable data class LearningTrialState(val packageName: String? = null, val active: Boolean = false, val phase: String = "Idle", val detail: String = "No test running.", val candidate: Int = 0, val stage: Int = 0, val progress: Float = 0f, val waitingRelaunch: Boolean = false, val recoveryRequired: Boolean = false,
    val goal: TuneGoal? = null, val requestedProfile: GameProfile? = null,
    val observedCpu: Int? = null, val observedGpu: Int? = null, val cpuMhz: Int? = null, val gpuMhz: Int? = null,
    val countersAt: Long? = null, val requestedAt: Long? = null, val computeChange: Boolean = false, val runtimeConfirmed: Boolean = false,
    val completedWindows: Int = 0, val totalWindows: Int? = null, val remainingSeconds: Int? = null,
    val result: LearningResult? = null,
    val renderChecksRequired: Boolean = false, val renderConfirmed: Boolean = false,
    val observedEyeTexture: EyeTexture? = null, val observedHz: Int? = null,
    val renderCountersAt: Long? = null, val phaseStartedAt: Long = 0, val finishedAtMs: Long = 0)

interface LearningStore {
    fun event(event: LearningEvent): Boolean = true
    fun windows(): List<LearnedWindow>
    fun add(window: LearnedWindow): Boolean
    fun undo(): LearningUndo?
    fun saveUndo(undo: LearningUndo): Boolean
    fun clearUndo(): Boolean
    fun invalidate(context: LearningContext, profile: GameProfile): Boolean
    fun clear(packageName: String)
}

object GameLearning {
    /** Keep repeated evidence for every profile; passive play must not evict learned results. */
    fun retain(windows: List<LearnedWindow>): List<LearnedWindow> = windows.groupBy { it.context.packageName }.values.flatMap { game ->
        val verified = game.filter { it.verifiedTrial }.groupBy { listOf(it.context,it.profile,it.hz,it.halfRate) }.values
            .flatMap { it.sortedBy { w -> w.endedAt }.takeLast(4) }.sortedBy { it.endedAt }.takeLast(48)
        verified + game.filterNot { it.verifiedTrial }.sortedBy { it.endedAt }.takeLast(12)
    }.sortedWith(compareBy<LearnedWindow> { it.verifiedTrial }.thenBy { it.endedAt }).takeLast(4000).sortedBy { it.endedAt }

    fun profile(active: Map<String, String>): GameProfile? { return try {
        fun number(key: QuestProperty): Int? = active[key.key]?.takeIf { it.isNotBlank() }?.let { it.toIntOrNull() ?: error("Invalid override") }
        val w = number(QuestProperty.TEXTURE_WIDTH); val h = number(QuestProperty.TEXTURE_HEIGHT)
        if ((w == null) != (h == null)) return null
        val f = number(QuestProperty.FOVEATION_LEVEL)
        val d = number(QuestProperty.FOVEATION_DYNAMIC)
        if (f != null && FoveationLevel.entries.none { it.code == f } || d != null && d !in 0..1) return null
        GameProfile(number(QuestProperty.REFRESH_RATE), w?.let { EyeTexture(it, h!!) }, number(QuestProperty.CPU_LEVEL), number(QuestProperty.GPU_LEVEL), f?.let { code -> FoveationLevel.entries.first { it.code == code } }, d?.let { it == 1 })
    } catch (_: IllegalStateException) { null } }

    fun usable(s: PerformanceSample, pkg: String, now: Long): Boolean {
        val t = s.telemetry
        return s.packageName == pkg && s.readbackVerified && t.processId?.let { it > 0 } == true && t.isVr && t.capturedAtMs != null && now - t.capturedAtMs in 0..6000 &&
            t.measuredFps?.let { it.isFinite() && it > 0 && it <= 240 } == true && t.reportedRefreshRate in 60..240 &&
            t.appGpuMs?.let { it.isFinite() && it in 0.0..1000.0 } == true && t.staleFrames?.let { it >= 0 } == true
    }
    private fun percentile(values: List<Double>, fraction: Double): Double = values.sorted()[((values.size - 1) * fraction).toInt()]
    fun summarize(context: LearningContext, profile: GameProfile, runId: String, stage: String,
        samples: List<PerformanceSample>, config: LearningConfig, verified: Boolean = false): LearnedWindow? {
        if (!config.valid() || samples.size < 10) return null
        val rows = samples.distinctBy { it.telemetry.capturedAtMs }.sortedBy { it.telemetry.capturedAtMs }
        if (rows.size < 10 || rows.any { !usable(it, context.packageName, it.timestampMs) || !profile.isActiveOn(it.activeProperties) }) return null
        val times = rows.map { it.telemetry.capturedAtMs!! }
        if (times.last() - times.first() < config.seconds * 1000L || times.zipWithNext().any { (a,b) -> b-a !in 1..8000 }) return null
        if (rows.map { it.telemetry.processId }.distinct().size != 1) return null
        val rates = rows.map { it.telemetry.reportedRefreshRate!! }.distinct()
        if (rates.size != 1 || profile.refreshRate != null && profile.refreshRate != rates[0]) return null
        val renderVerified=rows.all { RenderVerification.matches(profile,it.telemetry,it.timestampMs) }
        val eyes=rows.mapNotNull { it.telemetry.observedEyeTexture }.distinct()
        if(config.mode==LearningMode.RELAUNCH && (!renderVerified || eyes.size!=1))return null
        val hz = rates.single(); val target = hz.toDouble() / if (config.halfRate) 2 else 1
        val fps = rows.map { it.telemetry.measuredFps!! }; val gpu = rows.map { it.telemetry.appGpuMs!! }
        if(fps.any { it>hz*1.10 } || config.halfRate && fps.any { it>target*1.10 }) return null
        fun mean(selector: (RuntimeTelemetry) -> Double?): Double? = rows.mapNotNull { selector(it.telemetry)?.takeIf { v -> v.isFinite() && v in 0.0..100.0 } }.takeIf { it.size == rows.size }?.average()
        fun level(selector: (RuntimeTelemetry) -> Int?): Int? = rows.mapNotNull { selector(it.telemetry) }.takeIf { it.size == rows.size }?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key
        return LearnedWindow(context, profile, runId, stage, times.first(), times.last(), rows.size, hz, config.halfRate,
            percentile(fps,.1), fps.average(), percentile(gpu,.95), rows.map { it.telemetry.staleFrames!! }.average(),
            rows.count { it.telemetry.measuredFps!! >= target * .97 && it.telemetry.appGpuMs!! <= 1000.0/target * .95 }.toDouble()/rows.size,
            rows.count { it.thermal.warning }.toDouble()*100/rows.size, mean { it.cpuPercent }, mean { it.gpuPercent }, level { it.cpuLevel }, level { it.gpuLevel }, verified, level { it.observedFoveation }, eyes.singleOrNull().takeIf { renderVerified }, renderVerified)
    }
    fun compatible(a: LearnedWindow, b: LearnedWindow) = a.context == b.context && a.hz == b.hz && a.halfRate == b.halfRate
    /** Surround B with A controls. Changing workload makes this inconclusive, not a false improvement. */
    fun repeatable(before: LearnedWindow, after: LearnedWindow): Boolean = compatible(before,after) && before.profile == after.profile &&
        ( !before.renderVerified || !after.renderVerified || before.observedEyeTexture==after.observedEyeTexture ) &&
        abs(before.gpuP95-after.gpuP95) <= maxOf(1.0, before.gpuP95*.15) && abs(before.fpsMean-after.fpsMean) <= before.targetFps*.05 && abs(before.staleMean-after.staleMean) <= 1.0

    fun utility(w: LearnedWindow, goal: TuneGoal, model: QuestModel): Double {
        val pixels = (w.observedEyeTexture ?: w.profile.eyeTexture ?: model.defaultEyeTexture).let { it.width.toDouble()*it.height } / (model.defaultEyeTexture.width.toDouble()*model.defaultEyeTexture.height)
        val foveation = w.observedFoveation ?: w.profile.foveationLevel?.code ?: 2
        val quality = pixels - foveation * .04
        val load = (w.observedCpu ?: w.profile.cpuLevel ?: 0) + (w.observedGpu ?: w.profile.gpuLevel ?: 0)
        val penalty=(1.0-w.compliance)*200 + (w.targetFps-w.fpsP10).coerceAtLeast(0.0)*2 + w.thermalPercent*5 + w.staleMean*5
        return when(goal) {
            TuneGoal.HD_PLUS -> quality * 100 - w.gpuP95*.1
            TuneGoal.QUALITY -> quality * 80 + w.hz*.1 - w.gpuP95*.1
            TuneGoal.BALANCED -> quality*20 + minOf(w.targetFps,90.0)*.2 - load*.5 - w.gpuP95*.2
            TuneGoal.PERFORMANCE -> w.fpsP10*2 - w.gpuP95 - w.staleMean*5
            TuneGoal.BATTERY -> -load.toDouble()*5 - (w.cpuMean ?: 50.0)*.1 - (w.gpuMean ?: 50.0)*.1 - w.hz*.05
        } - penalty
    }
    fun recommendations(context: LearningContext, windows: List<LearnedWindow>, model: QuestModel, halfRate: Boolean): List<LearnedRecommendation> {
        val groups = windows.filter { it.context == context && it.halfRate == halfRate && it.verifiedTrial }
            .groupBy { it.profile to it.hz }.values.filter { rows -> rows.all { it.healthy } && rows.map { it.runId to it.stage }.distinct().size >= 2 && rows.sumOf { it.endedAt-it.startedAt } >= 60_000 }
        return TuneGoal.entries.mapNotNull { goal ->
            val best = groups.filter { rows -> (goal !in listOf(TuneGoal.QUALITY,TuneGoal.HD_PLUS) || rows.all { it.renderVerified && it.observedEyeTexture!=null }) &&
                (goal != TuneGoal.BATTERY || rows.all { it.cpuMean != null && it.gpuMean != null && it.observedCpu != null && it.observedGpu != null }) }
                .maxByOrNull { rows -> rows.map { utility(it,goal,model) }.average() } ?: return@mapNotNull null
            LearnedRecommendation(context,goal,best.first().profile,best.size,(best.sumOf { it.endedAt-it.startedAt }/1000).toInt(),best.minOf { it.fpsP10 },best.maxOf { it.gpuP95 },
                "${best.size} repeat-tested windows; frame-target compliance ≥95%, no thermal pressure. " + if(goal==TuneGoal.BATTERY) "Lower compute/load proxy; battery life was not measured." else "Best observed eligible settings for this goal, not a guarantee for every scene.")
        }
    }
    fun liveCandidate(base: GameProfile, model: QuestModel, goal: TuneGoal, cpu: Int?, gpu: Int?): List<GameProfile> {
        val direction = if(goal == TuneGoal.BATTERY) -1 else 1
        return listOfNotNull(cpu?.let { (it+direction).takeIf { n -> n in model.cpuLevels }?.let { n -> base.copy(cpuLevel=n) } },
            gpu?.let { (it+direction).takeIf { n -> n in model.gpuLevels }?.let { n -> base.copy(gpuLevel=n) } }).distinct().filter { it != base }
    }
    fun restartCandidates(base: GameProfile, model: QuestModel, goal: TuneGoal, baseline: LearnedWindow? = null): List<GameProfile> {
        val texture=baseline?.observedEyeTexture ?: base.eyeTexture ?: model.defaultEyeTexture
        val steps=RESOLUTION_STEPS.map { EyeTexture.forStep(model.defaultEyeTexture,it) }
            .filter { RefreshRatePolicy.allowed(base.refreshRate,it,model.defaultEyeTexture) }
        val lower=steps.lastOrNull { it.width<texture.width && it.height<texture.height }?.let { base.copy(eyeTexture=it) }
        val higher=steps.firstOrNull { it.width>texture.width && it.height>texture.height }?.let { base.copy(eyeTexture=it) }
        val observedFov=baseline?.observedFoveation ?: base.foveationLevel?.code
        fun fov(delta: Int)=observedFov?.let { n -> FoveationLevel.entries.firstOrNull { it.code==n+delta } }?.let { base.copy(foveationLevel=it,dynamicFoveation=false) }
        val unhealthy=baseline?.healthy==false
        val gpuBound=baseline?.let { it.gpuP95>1000.0/it.targetFps*.80 || (it.gpuMean ?: 0.0)>85 } ?: false
        val currentHz=baseline?.hz ?: base.refreshRate ?: 90
        val rates=model.refreshRates.filter { it in listOf(72,80,90,120) }
        val lowerHz=rates.lastOrNull { it<currentHz }?.let { base.copy(refreshRate=it) }
        val higherHz=rates.firstOrNull { it>currentHz }?.let { base.copy(refreshRate=it) }
        val compute=liveCandidate(base,model,TuneGoal.PERFORMANCE,baseline?.observedCpu ?: base.cpuLevel,baseline?.observedGpu ?: base.gpuLevel)
        val choices=when {
            goal==TuneGoal.BATTERY -> listOfNotNull(lowerHz,lower)+liveCandidate(base,model,goal,baseline?.observedCpu ?: base.cpuLevel,baseline?.observedGpu ?: base.gpuLevel)
            unhealthy && !gpuBound && compute.isNotEmpty() -> compute+listOfNotNull(lower,fov(1))
            unhealthy -> listOfNotNull(lower,fov(1))+compute
            goal==TuneGoal.PERFORMANCE -> listOfNotNull(higherHz)+compute+listOfNotNull(lower)
            else -> listOfNotNull(higher,fov(-1))+compute
        }
        return choices.distinct().filter { it!=base && it.validateFor(model).isEmpty() }.take(2)
    }
}
