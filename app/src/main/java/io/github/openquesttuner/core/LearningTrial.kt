package io.github.openquesttuner.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface LearningDevice {
    suspend fun reserve(): Boolean
    suspend fun release()
    suspend fun bootId(): String?
    suspend fun profile(): GameProfile?
    suspend fun apply(profile: GameProfile, relaunch: Boolean, live: Boolean): Boolean
    fun context(packageName: String): LearningContext?
    suspend fun keep(context: LearningContext, goal: TuneGoal, profile: GameProfile): Boolean
}

/** A/B/A/B/A trials. Only repeated B results between repeatable A controls are promoted. */
class LearningTrial(private val device: LearningDevice, private val store: LearningStore, private val model: QuestModel,
    private val declaredRates: () -> Set<Int> = { emptySet() }, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()
    private val _state = MutableStateFlow(LearningTrialState(recoveryRequired = store.undo() != null))
    val state = _state.asStateFlow()
    private var config = LearningConfig()
    private var hadDrift = false
    private var confirmedThisPhase = false
    private var renderConfirmedThisPhase = false
    private var pendingFinal: LearnedWindow?=null
    private var completedWindows = 0
    private var undo: LearningUndo? = null
    private var candidates = emptyList<GameProfile>()
    private var candidateIndex = 0
    private var stage = 0
    private var expected = GameProfile()
    private var phaseWarmup=15
    private var since = 0L
    private var lastValid = 0L
    private var hasGame = false
    private var latestPid: Int? = null
    private var requireDifferentPid: Int? = null
    private var rows = mutableListOf<PerformanceSample>()
    private val phases = mutableListOf<LearnedWindow>()
    private val winners = mutableListOf<LearnedWindow>()

    suspend fun unavailable(context: LearningContext,reason: String) = lock.withLock {
        if(!_state.value.active && store.undo()==null)_state.value=LearningTrialState(packageName=context.packageName,detail=reason,result=LearningResult.UNAVAILABLE)
    }
    suspend fun start(context: LearningContext, requested: LearningConfig) = lock.withLock {
        if (_state.value.active || store.undo() != null) { _state.value = _state.value.copy(detail="Stop or restore the previous test first.", recoveryRequired=store.undo()!=null); return@withLock }
        if (!requested.valid() || !device.reserve()) { _state.value = LearningTrialState(packageName=context.packageName,detail="Invalid duration or another tuning operation is active.",result=LearningResult.UNAVAILABLE); return@withLock }
        val original = device.profile(); val boot = device.bootId()
        if (original == null || boot == null || original.validateFor(model,declaredRates()).isNotEmpty()) { device.release(); _state.value = LearningTrialState(packageName=context.packageName,detail="Cannot verify the current overrides and boot. No changes made.",result=LearningResult.UNAVAILABLE); return@withLock }
        config=requested; pendingFinal=null; phaseWarmup=config.warmupSeconds; hadDrift=false; confirmedThisPhase=false; renderConfirmedThisPhase=false; completedWindows=0; expected=original; candidateIndex=0; stage=0; candidates=emptyList(); rows.clear(); phases.clear(); winners.clear(); hasGame=false; latestPid=null; requireDifferentPid=null
        val initial = LearningUndo(boot, context, original, original, "${clock()}-${context.packageName}")
        if (!store.saveUndo(initial)) { device.release(); _state.value=LearningTrialState(packageName=context.packageName,detail="Cannot save restore state; no changes made.",result=LearningResult.UNAVAILABLE); return@withLock }
        undo=initial; since=clock(); lastValid=since
        _state.value=LearningTrialState(context.packageName,true,"Waiting for game","Open the selected game and repeat the same scene. No settings changed yet.",goal=config.goal,requestedProfile=original,renderChecksRequired=config.mode==LearningMode.RELAUNCH,phaseStartedAt=since)
        record("Learning started")
    }
    suspend fun launchPhase() = lock.withLock { launchPhaseLocked() }
    private suspend fun launchPhaseLocked() {
        val u=undo ?: return
        if (!_state.value.active || !_state.value.waitingRelaunch) return
        val target=targetProfile()
        if (!change(target,true,false)) { finish("Could not apply and relaunch; restore requested."); return }
        requireDifferentPid=latestPid
        expected=target; phaseWarmup=if(config.automatic)60 else config.warmupSeconds; since=clock(); lastValid=since; rows.clear(); hasGame=false
        _state.value=_state.value.copy(waitingRelaunch=false,phase="Warm-up",detail="Game relaunched. Return to the same scene; first ${if(config.automatic) 60 else config.warmupSeconds} seconds excluded.",progress=0f,requestedProfile=target,runtimeConfirmed=false,remainingSeconds=phaseWarmup,phaseStartedAt=since,renderConfirmed=false)
    }
    private fun targetProfile(): GameProfile = if(stage%2==1) candidates[candidateIndex] else undo!!.original

    suspend fun tick(sample: PerformanceSample?) = lock.withLock {
        val u=undo ?: return@withLock
        if (!_state.value.active) return@withLock
        try {
            if (device.context(u.context.packageName) != u.context) { finish("Game or headset version changed; test stopped."); return@withLock }
            if (_state.value.waitingRelaunch) {
                if (sample?.packageName != null && sample.packageName != u.context.packageName) { finish("Another game became active; test stopped."); return@withLock }
                if(clock()-since>300_000) finish("Relaunch step timed out.")
                return@withLock
            }
            if (sample?.packageName != u.context.packageName) {
                if(sample?.packageName!=null || (hasGame && clock()-lastValid>20_000) || clock()-since>180_000) finish("Selected game is no longer active; test stopped.")
                return@withLock
            }
            if (!hasGame) { hasGame=true; since=clock(); lastValid=since; _state.value=_state.value.copy(phaseStartedAt=since) }
            if(config.automatic && !AutomaticTuningPolicy.safe(sample.telemetry,sample.thermal)) {
                finish("Thermal/power safety unavailable or unsafe; test stopped and original settings requested.",result=LearningResult.UNAVAILABLE);return@withLock
            }
            if (sample.thermal.warning) { store.invalidate(u.context,expected); finish("Thermal pressure detected; baseline restored instead of testing further."); return@withLock }
            if (!expected.isActiveOn(sample.activeProperties)) { finish("Overrides changed or readback is unavailable; test stopped."); return@withLock }
            if (!GameLearning.usable(sample,u.context.packageName,clock())) {
                if(clock()-lastValid > if(rows.isEmpty()) phaseWarmup*1000L+20_000 else 8_000) finish("Fresh app-attributed VR counters unavailable; no recommendation generated.",result=LearningResult.UNAVAILABLE)
                return@withLock
            }
            if (requireDifferentPid != null && sample.telemetry.processId == requireDifferentPid) {
                if(clock()-since>30_000) finish("A new game process was not confirmed after relaunch; result rejected.")
                return@withLock
            }
            requireDifferentPid=null
            if (latestPid != null && sample.telemetry.processId != latestPid && rows.isNotEmpty()) {
                finish("Game process restarted during measurement; result rejected."); return@withLock
            }
            latestPid=sample.telemetry.processId
            lastValid=clock()
            if(pendingFinal!=null) { verifyFinal(sample,pendingFinal!!);return@withLock }
            val warm=clock()-since
            val computeChange=stage%2==1 && (expected.cpuLevel!=u.original.cpuLevel || expected.gpuLevel!=u.original.gpuLevel)
            _state.value=_state.value.copy(requestedProfile=expected,observedCpu=sample.telemetry.cpuLevel,observedGpu=sample.telemetry.gpuLevel,
                cpuMhz=sample.telemetry.cpuClockMhz,gpuMhz=sample.telemetry.gpuClockMhz,countersAt=sample.telemetry.capturedAtMs,
                computeChange=computeChange,runtimeConfirmed=false,observedEyeTexture=sample.telemetry.observedEyeTexture,
                observedHz=sample.telemetry.reportedRefreshRate,renderCountersAt=sample.telemetry.renderCapturedAtMs,renderConfirmed=false)
            if(warm<phaseWarmup*1000L || sample.telemetry.capturedAtMs!!<since+phaseWarmup*1000L) {
                _state.value=_state.value.copy(phase="Warm-up",detail="${((phaseWarmup*1000L-warm).coerceAtLeast(0)/1000)}s before measurement; repeat the same scene.",remainingSeconds=((phaseWarmup*1000L-warm).coerceAtLeast(0)/1000).toInt()); return@withLock
            }
            if(config.mode==LearningMode.RELAUNCH || config.automatic && candidates.any { it.eyeTexture!=u.original.eyeTexture }) {
                val rendering=RenderVerification.matches(expected,sample.telemetry,clock()) && sample.telemetry.renderCapturedAtMs!!>=since
                _state.value=_state.value.copy(renderConfirmed=rendering)
                if(!rendering) {
                    _state.value=_state.value.copy(phase="Verifying render settings",detail="Waiting for fresh native eye buffers and matching display Hz. Requested ${expected.eyeTexture ?: "game default"} at ${expected.refreshRate ?: "default"}Hz; observed ${sample.telemetry.observedEyeTexture ?: "unavailable"} at ${sample.telemetry.reportedRefreshRate ?: "?"}Hz.")
                    if(rows.isNotEmpty() || warm>phaseWarmup*1000L+20_000) {
                        store.invalidate(u.context,expected)
                        finish("Resolution/refresh verification failed or became stale. Native selected eye-buffer counters are required; no rendering result accepted.",result=LearningResult.UNAVAILABLE)
                    }
                    return@withLock
                }
                if(!renderConfirmedThisPhase) { record("Resolution and refresh confirmed",eye=sample.telemetry.observedEyeTexture,hz=sample.telemetry.reportedRefreshRate);renderConfirmedThisPhase=true }
            } else if(expected.refreshRate!=null && expected.refreshRate!=sample.telemetry.reportedRefreshRate) {
                if(warm>phaseWarmup*1000L+20_000) if(!rejectAutomatic("Requested refresh was not observed"))finish("Display refresh did not match the requested rate; no result accepted.",result=LearningResult.UNAVAILABLE)
                return@withLock
            }
            // Verify actual granted CPU/GPU levels for live changes, not just setprop success.
            if(stage%2==1) {
                val base=u.original
                val cpuOk=expected.cpuLevel==null || expected.cpuLevel==base.cpuLevel || sample.telemetry.cpuLevel==expected.cpuLevel
                val gpuOk=expected.gpuLevel==null || expected.gpuLevel==base.gpuLevel || sample.telemetry.gpuLevel==expected.gpuLevel
                val requestedFoveation=expected.foveationLevel
                val fovOk=requestedFoveation==null || requestedFoveation==base.foveationLevel || sample.telemetry.observedFoveation==requestedFoveation.code
                if(!cpuOk || !gpuOk || !fovOk) { if(warm>phaseWarmup*1000L+15_000) { store.invalidate(u.context,expected); if(!rejectAutomatic("Requested levels were not observed"))finish("Runtime counters did not confirm the requested levels; candidate rejected and baseline restored.",result=LearningResult.UNAVAILABLE) }; return@withLock }
            }
            if(computeChange) {
                _state.value=_state.value.copy(runtimeConfirmed=true)
                if(!confirmedThisPhase) { record("Runtime CPU/GPU levels confirmed",sample.telemetry.cpuLevel,sample.telemetry.gpuLevel); confirmedThisPhase=true }
            }
            if(rows.lastOrNull()?.telemetry?.capturedAtMs==sample.telemetry.capturedAtMs) return@withLock
            rows+=sample
            val severeRegression = stage%2==1 && rows.takeLast(3).size==3 && rows.takeLast(3).all {
                val target=it.telemetry.reportedRefreshRate!!.toDouble() / if(config.halfRate) 2 else 1
                it.telemetry.measuredFps!! < target*.70 || it.telemetry.staleFrames!! > it.telemetry.reportedRefreshRate!!*.20
            }
            val automaticRegression=config.automatic && stage%2==1 && rows.size>=3 && phases.isNotEmpty() && rows.takeLast(3).all {
                it.telemetry.measuredFps!! < phases.first().fpsMean*.98 || it.telemetry.staleFrames!!>1 ||
                    (it.telemetry.appGpuMs!!>1000.0/it.telemetry.reportedRefreshRate!! && it.telemetry.measuredFps!!<it.telemetry.reportedRefreshRate!!*.98)
            }
            if(automaticRegression && rejectAutomatic("Repeated FPS/GPU/stale-frame regression"))return@withLock
            if(severeRegression) { store.invalidate(u.context,expected); if(rejectAutomatic("Frame delivery regressed"))return@withLock; finish("Severe frame-rate regression detected; candidate rejected and baseline restore requested."); return@withLock }
            val elapsed=sample.telemetry.capturedAtMs!!-(rows.first().telemetry.capturedAtMs ?: clock())
            _state.value=_state.value.copy(phase="Measuring",detail="${if(stage%2==0) "Baseline A" else "Candidate B"} · ${elapsed/1000}/${config.seconds}s · ${rows.size} unique VR observations",candidate=candidateIndex+1,stage=stage+1,progress=(elapsed.toFloat()/(config.seconds*1000)).coerceIn(0f,1f),remainingSeconds=(config.seconds-elapsed/1000).toInt().coerceAtLeast(0))
            if(elapsed<config.seconds*1000) return@withLock
            val window=GameLearning.summarize(u.context,expected,u.runId,"$candidateIndex:$stage",rows,config)
            if(config.automatic && AutomaticTuningPolicy.coverage(rows)<45_000) { finish("Fewer than 45 valid telemetry seconds; result inconclusive.",result=LearningResult.INCONCLUSIVE);return@withLock }
            if(window==null) { finish("Measurement incomplete, process/rate changed, or FPS did not match selected render mode; result rejected."); return@withLock }
            if(!store.add(window)) { finish("Could not save measurement; baseline restored."); return@withLock }
            phases+=window
            completedWindows++
            if(stage==0 && candidates.isEmpty()) {
                candidates=if(config.automatic) AutomaticTuningPolicy.candidates(u.original,model,window,declaredRates()) else if(config.mode==LearningMode.LIVE) GameLearning.liveCandidate(u.original,model,config.goal,window.observedCpu,window.observedGpu)
                    else GameLearning.restartCandidates(u.original,model,config.goal,window)
                if(candidates.isEmpty()) {
                    if(config.automatic && AutomaticTuningPolicy.healthy(window)) candidates=listOf(u.original)
                    else { finish("No valid adjacent step within the headset limits. Baseline retained.",result=LearningResult.UNCHANGED);return@withLock }
                }
            }
            _state.value=_state.value.copy(completedWindows=completedWindows,totalWindows=candidates.size*5,progress=0f)
            if(stage==4) {
                val repeatable=GameLearning.repeatable(phases[0],phases[2]) && GameLearning.repeatable(phases[2],phases[4])
                val bs=listOf(phases[1],phases[3])
                if(!repeatable) hadDrift=true
                if(repeatable) {
                    for(w in phases) if(!store.add(w.copy(verifiedTrial=true))) { finish("Could not save verified trial; baseline retained."); return@withLock }
                    val a=phases.filterIndexed { index,_ -> index%2==0 }.map { GameLearning.utility(it,config.goal,model) }.average()
                    if(bs.all { if(config.automatic) AutomaticTuningPolicy.improves(it,phases.filterIndexed { index,_ -> index%2==0 },model) else it.healthy && (config.goal!=TuneGoal.BATTERY || it.cpuMean!=null && it.gpuMean!=null && it.observedCpu!=null && it.observedGpu!=null) && GameLearning.utility(it,config.goal,model)>a+1.0 }) winners+=bs
                }
                if(config.automatic && repeatable && winners.isEmpty() && phases.filterIndexed { index,_ -> index%2==0 }.all(AutomaticTuningPolicy::healthy)) winners+=phases[4]
                candidateIndex++
                if(candidateIndex>=candidates.size) { finish(if(repeatable) "Repeated measurements complete." else "Workload changed between controls; affected results are inconclusive.",true); return@withLock }
                stage=0; phases.clear(); rows.clear(); since=clock(); lastValid=since
                _state.value=_state.value.copy(phase="Warm-up",detail="Next candidate: collecting a new A baseline.",candidate=candidateIndex+1,stage=1,progress=0f,runtimeConfirmed=false,remainingSeconds=phaseWarmup,phaseStartedAt=since,renderConfirmed=false)
                renderConfirmedThisPhase=false
                return@withLock
            }
            stage++; rows.clear(); since=clock(); lastValid=since
            if(config.mode==LearningMode.RELAUNCH || config.automatic && AutomaticTuningPolicy.relaunch(expected,targetProfile())) {
                _state.value=_state.value.copy(phase="Relaunch required",detail=if(config.automatic) "Restarting automatically for the next test. Return to the same scene after loading." else "Next phase needs a restart. Save game progress, then choose Apply phase & relaunch.",waitingRelaunch=true,stage=stage+1,progress=0f,runtimeConfirmed=false,remainingSeconds=null)
                if(config.automatic) launchPhaseLocked()
            } else {
                val target=targetProfile()
                if(!change(target,false,true)) { finish("Live change was not verified; baseline restore requested."); return@withLock }
                expected=target; phaseWarmup=config.warmupSeconds; since=clock(); lastValid=since
                _state.value=_state.value.copy(phase="Warm-up",detail="Settings read back; waiting for fresh runtime levels.",stage=stage+1,progress=0f,requestedProfile=target,runtimeConfirmed=false,remainingSeconds=phaseWarmup,phaseStartedAt=since,renderConfirmed=false)
            }
        } catch(e: CancellationException) { withContext(NonCancellable) { finish("Test cancelled; restore requested.") }; throw e }
        catch(_: Exception) { finish("Test interrupted; restore requested.") }
    }
    private suspend fun verifyFinal(sample: PerformanceSample,winner: LearnedWindow) {
        val u=undo ?: return
        val t=sample.telemetry
        _state.value=_state.value.copy(phase="Verifying final profile",observedCpu=t.cpuLevel,observedGpu=t.gpuLevel,cpuMhz=t.cpuClockMhz,gpuMhz=t.gpuClockMhz,countersAt=t.capturedAtMs,observedHz=t.reportedRefreshRate)
        val elapsed=clock()-since
        if(elapsed<phaseWarmup*1000L || t.capturedAtMs!!<since+phaseWarmup*1000L) {
            _state.value=_state.value.copy(remainingSeconds=((phaseWarmup*1000L-elapsed).coerceAtLeast(0)/1000).toInt());return
        }
        val adopted=(expected.cpuLevel==null || expected.cpuLevel==t.cpuLevel) && (expected.gpuLevel==null || expected.gpuLevel==t.gpuLevel) &&
            (expected.foveationLevel?.let { it.code==t.observedFoveation } != false) && winner.hz==t.reportedRefreshRate &&
            (expected.eyeTexture==u.original.eyeTexture || RenderVerification.matches(expected,t,clock()))
        if(!adopted) {
            if(elapsed>phaseWarmup*1000L+20_000) { pendingFinal=null;finish("Final runtime adoption was not confirmed; profile not saved.",result=LearningResult.UNAVAILABLE) }
            return
        }
        if(rows.lastOrNull()?.telemetry?.capturedAtMs==t.capturedAtMs)return
        rows+=sample
        val measured=t.capturedAtMs!!-rows.first().telemetry.capturedAtMs!!
        _state.value=_state.value.copy(runtimeConfirmed=true,detail="Final runtime levels confirmed; ${measured/1000}/60s gameplay stability check before saving.",remainingSeconds=(60-measured/1000).toInt().coerceAtLeast(0))
        if(measured<60_000)return
        val final=GameLearning.summarize(u.context,expected,u.runId,"final",rows,config.copy(mode=LearningMode.LIVE),verified=true)
        if(final==null || AutomaticTuningPolicy.coverage(rows)<45_000 || !AutomaticTuningPolicy.healthy(final) || !store.add(final)) {
            pendingFinal=null;finish("Final profile was not stable or could not be recorded; originals restored.",result=LearningResult.INCONCLUSIVE);return
        }
        if(!device.keep(u.context,config.goal,expected)) { pendingFinal=null;finish("Verified profile could not be saved; originals restored.",result=LearningResult.REVIEW);return }
        record("Profile verified, saved and applied",t.cpuLevel,t.gpuLevel,expected)
        val cleared=store.clearUndo()
        pendingFinal=null
        if(cleared){undo=null;device.release()}
        _state.value=LearningTrialState(packageName=u.context.packageName,active=false,phase=if(cleared)"Profile applied" else "Restore pending",
            detail=if(cleared)"Profile verified, saved to this game and applied. CPU ${t.cpuLevel ?: "default"}, GPU ${t.gpuLevel ?: "default"}, ${t.reportedRefreshRate}Hz. Future launches use this profile without learning." else "Profile saved, but restore record cleanup failed. Reconnect and restore before another test.",
            recoveryRequired=!cleared,goal=config.goal,requestedProfile=expected,observedCpu=t.cpuLevel,observedGpu=t.gpuLevel,cpuMhz=t.cpuClockMhz,gpuMhz=t.gpuClockMhz,countersAt=t.capturedAtMs,
            observedHz=t.reportedRefreshRate,runtimeConfirmed=cleared,completedWindows=completedWindows,totalWindows=candidates.size*5,result=if(cleared)LearningResult.APPLIED else LearningResult.RECOVERY,finishedAtMs=clock())
    }
    private suspend fun rejectAutomatic(reason: String): Boolean {
        if(!config.automatic || stage%2==0)return false
        val u=undo ?: return false
        record("Candidate rejected: $reason")
        val restart=AutomaticTuningPolicy.relaunch(expected,u.original)
        if(!change(u.original,restart,!restart)) { finish("Rejected candidate could not be restored; recovery required.");return true }
        expected=u.original;candidateIndex++;stage=0;rows.clear();phases.clear();hasGame=false
        phaseWarmup=if(restart)60 else 15;since=clock();lastValid=since
        if(restart)requireDifferentPid=latestPid
        if(candidateIndex>=candidates.size) { finish("Supported tests finished; rejected candidates were restored.",completed=winners.isNotEmpty(),result=if(winners.isEmpty()) LearningResult.INCONCLUSIVE else null);return true }
        _state.value=_state.value.copy(phase="Warm-up",detail="Candidate rejected and baseline restored. Testing next supported setting.",candidate=candidateIndex+1,stage=1,remainingSeconds=phaseWarmup,phaseStartedAt=since,requestedProfile=expected,runtimeConfirmed=false)
        return true
    }
    private suspend fun change(target: GameProfile, relaunch: Boolean, live: Boolean): Boolean {
        val u=undo ?: return false
        if (device.profile() != expected) return false
        val next=u.copy(lastWritten=target,previousWritten=u.lastWritten)
        if(!store.saveUndo(next)) return false
        undo=next
        confirmedThisPhase=false;renderConfirmedThisPhase=false
        record(if(relaunch) "Requested profile and relaunch" else "Requested settings change",profile=target)
        _state.value=_state.value.copy(phase="Applying",detail=if(relaunch) "Applying settings and restarting the game for verification." else "Applying settings; waiting for native confirmation.",waitingRelaunch=false,requestedProfile=target,requestedAt=clock(),runtimeConfirmed=false,renderConfirmed=false,renderCountersAt=null,countersAt=null,observedCpu=null,observedGpu=null,cpuMhz=null,gpuMhz=null,computeChange=target.cpuLevel!=u.original.cpuLevel || target.gpuLevel!=u.original.gpuLevel)
        return device.apply(target,relaunch,live)
    }
    private fun record(message: String,cpu: Int?=null,gpu: Int?=null,profile: GameProfile=expected,eye: EyeTexture?=null,hz: Int?=null) {
        val u=undo ?: return
        store.event(LearningEvent(clock(),u.context.packageName,u.runId,message,profile,cpu,gpu,eye,hz))
    }
    suspend fun stop() = lock.withLock { finish("Stopped by user; baseline restore requested.") }
    suspend fun recover() = lock.withLock {
        if(_state.value.active) return@withLock
        undo=store.undo() ?: return@withLock
        if(!device.reserve()) return@withLock
        finish("Interrupted test: restoring original overrides.")
    }
    /** Explicit user action only; never silently discard failed recovery. */
    suspend fun discardRecovery() = lock.withLock {
        if(_state.value.active) return@withLock
        if(store.clearUndo()) { undo=null; device.release(); _state.value=LearningTrialState(detail="Restore record discarded by user. Existing headset settings were not changed.") }
    }
    private suspend fun finish(reason: String, completed: Boolean=false, result: LearningResult?=null) {
        val u=undo ?: return
        var detail=reason
        var restored=false
        var outcome=result ?: if(completed) {
            if(winners.isNotEmpty()) LearningResult.REVIEW else if(hadDrift) LearningResult.INCONCLUSIVE else LearningResult.UNCHANGED
        } else LearningResult.STOPPED
        _state.value=_state.value.copy(phase="Restoring",runtimeConfirmed=false,renderConfirmed=false,remainingSeconds=null)
        record("Restoring original overrides",profile=u.original)
        try {
            val boot=device.bootId()
            if(boot!=null && boot!=u.bootId) { restored=store.clearUndo(); detail+=" Reboot cleared temporary overrides; stale restore discarded." }
            else if(boot==u.bootId) {
                val current=device.profile()
                val compatible=current!=null && current.toPropertyValues().all { (k,v) -> v==u.original.toPropertyValues()[k] || v==u.lastWritten.toPropertyValues()[k] || (u.previousWritten!=null && v==u.previousWritten.toPropertyValues()[k]) }
                if(compatible) {
                    restored=current==u.original || device.apply(u.original,false,false)
                    if(restored) expected=u.original
                    val best=winners.filter { if(config.automatic) AutomaticTuningPolicy.healthy(it) else it.healthy }.maxByOrNull { if(config.automatic)AutomaticTuningPolicy.score(it,model) else GameLearning.utility(it,config.goal,model) }
                    if(restored && completed && config.autoKeep && best!=null) {
                        if(config.automatic) {
                            val restart=AutomaticTuningPolicy.relaunch(u.original,best.profile)
                            if(!change(best.profile,restart,!restart)) {
                                restored=device.apply(u.original,false,false);outcome=LearningResult.REVIEW;detail+=" Final profile application failed; originals requested."
                            } else {
                                pendingFinal=best;expected=best.profile;rows.clear();since=clock();lastValid=since;phaseWarmup=if(restart)60 else 15;hasGame=false
                                if(restart)requireDifferentPid=latestPid
                                _state.value=_state.value.copy(active=true,phase="Verifying final profile",detail="Applying the best repeat-tested settings; checking actual gameplay before saving.",requestedProfile=expected,phaseStartedAt=since,remainingSeconds=phaseWarmup)
                                return
                            }
                        } else {
                        val finalRestart=false
                        val applied=if(config.automatic) change(best.profile,finalRestart,!finalRestart) else if(config.mode==LearningMode.LIVE) change(best.profile,false,true) else true
                        if(applied && device.keep(u.context,config.goal,best.profile)) {
                            detail+=" Tested winner saved${if(config.mode==LearningMode.LIVE) " and applied" else " for the next launch"}."
                            outcome=if(config.mode==LearningMode.LIVE) LearningResult.APPLIED else LearningResult.SAVED_FOR_LAUNCH
                        }
                        else { outcome=LearningResult.REVIEW; restored=device.apply(u.original,false,false); detail+=" Winner could not be kept; original settings requested." }
                        }
                    } else if(completed) detail+=" Original overrides restored; review measured goal recommendations before applying."
                    if(restored) restored=store.clearUndo()
                } else detail+=" External settings detected; restore withheld to preserve them."
            }
        } catch(_: Exception) { restored=false }
        if (restored && !completed && u.original.eyeTexture != u.lastWritten.eyeTexture) detail+=" Relaunch the game to restore its active render buffers."
        record((if(restored) "Finished: $outcome" else "Recovery required")+": "+detail,profile=if(outcome==LearningResult.APPLIED) undo?.lastWritten ?: u.original else u.original)
        if(restored) { undo=null; device.release() }
        _state.value=LearningTrialState(u.context.packageName,false,if(restored) "Finished" else "Restore pending",detail+(if(restored) "" else " Reconnect and retry Restore; undo is retained."),recoveryRequired=!restored,goal=config.goal,completedWindows=completedWindows,totalWindows=candidates.size.takeIf { it>0 }?.times(5),result=if(restored) outcome else LearningResult.RECOVERY)
    }
}
