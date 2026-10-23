package io.github.openquesttuner.core

/** Conservative experimental engineering policy, not official Meta thermal limits. */
object AutomaticTuningPolicy {
    fun setup() = LearningConfig(goal=TuneGoal.BALANCED,seconds=60,mode=LearningMode.LIVE,
        autoKeep=true,warmupSeconds=15,manageOvrCapture=false,automatic=true)
    fun safe(t: RuntimeTelemetry, thermal: ThermalLevel): Boolean =
        thermal !in listOf(ThermalLevel.SEVERE,ThermalLevel.CRITICAL,ThermalLevel.EMERGENCY,ThermalLevel.SHUTDOWN) &&
        t.powerLevel != 1 && t.powerLevel != 2 && (thermal!=ThermalLevel.UNKNOWN || t.powerLevel==0)
    fun coverage(rows: List<PerformanceSample>): Long = rows.mapNotNull { it.telemetry.capturedAtMs }.distinct().sorted()
        .zipWithNext().sumOf { (a,b) -> (b-a).coerceIn(0,3000) }
    fun healthy(w: LearnedWindow) = w.fpsMean>=w.targetFps*.98 && w.fpsP10>=w.targetFps*.98 &&
        w.gpuP95<1000.0/w.hz && w.staleMean<=.1 && w.thermalPercent==0.0
    fun headroom(w: LearnedWindow) = healthy(w) && w.gpuP95<=1000.0/w.hz*.80 && w.gpuMean?.let { it<80 }==true
    fun improves(b: LearnedWindow, controls: List<LearnedWindow>, model: QuestModel): Boolean {
        if(!healthy(b) || controls.isEmpty())return false
        if(b.fpsMean<controls.map { it.fpsMean }.average()*.98 || b.staleMean>controls.map { it.staleMean }.average()+.1)return false
        val baseUtility=controls.map { score(it,model) }.average()
        return score(b,model)>baseUtility+.5
    }
    fun score(w: LearnedWindow,model: QuestModel)=GameLearning.utility(w,TuneGoal.BALANCED,model)+(w.hz-90).coerceAtLeast(0)*.1
    fun candidates(base: GameProfile, model: QuestModel, w: LearnedWindow, rates: Set<Int>): List<GameProfile> {
        val cpu=w.observedCpu;val gpu=w.observedGpu;val direction=if(healthy(w)) -1 else 1
        val compute=if(cpu!=null && gpu!=null) base.copy(
            cpuLevel=(cpu+direction).takeIf { it in model.cpuLevels } ?: cpu,
            gpuLevel=(gpu+direction).takeIf { it in model.gpuLevels } ?: gpu) else null
        val fov=w.observedFoveation?.let { current -> FoveationLevel.entries.firstOrNull { it.code==current+(if(headroom(w)) -1 else 1) } }
            ?.let { base.copy(foveationLevel=it,dynamicFoveation=false) }
        val hz=if(headroom(w)) model.refreshRates.sorted().firstOrNull { it>w.hz && it in rates }?.let { base.copy(refreshRate=it) } else null
        // No assumed eye size: visual candidates require actual native render evidence.
        val resolution=if(headroom(w) && w.renderVerified && w.observedEyeTexture!=null)
            GameLearning.restartCandidates(base,model,TuneGoal.QUALITY,w).firstOrNull { it.eyeTexture!=base.eyeTexture } else null
        return listOfNotNull(compute,fov,hz,resolution).distinct().filter { it!=base && it.validateFor(model,rates).isEmpty() }
    }
    fun relaunch(before: GameProfile, after: GameProfile) = before.eyeTexture!=after.eyeTexture || before.refreshRate!=after.refreshRate
}
