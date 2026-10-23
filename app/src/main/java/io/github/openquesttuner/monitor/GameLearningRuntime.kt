package io.github.openquesttuner.monitor

import io.github.openquesttuner.AppContainer
import io.github.openquesttuner.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One owner shared by foreground capture, navigation and process-recovery. */
class GameLearningRuntime(private val container: AppContainer) {
    private val observeLock=Mutex()
    private val owner="measured-learning"
    private val _renderStatus=kotlinx.coroutines.flow.MutableStateFlow("Native telemetry only; visual tests require directly observed game dimensions")
    val renderStatus=_renderStatus.asStateFlow()
    private var game: io.github.openquesttuner.games.InstalledGame?=null
    private val device=object : LearningDevice {
        override suspend fun reserve()=container.tuner.reserveLearning(owner)
        override suspend fun release()=container.tuner.releaseLearning(owner)
        override suspend fun bootId(): String? = try {
            container.adb.exec(ShellCommand.bootId()).takeIf { it.isSuccess }?.output?.trim()?.takeIf { Regex("[a-fA-F0-9-]{36}").matches(it) }
        } catch(_: ShellUnavailableException) { null }
        override suspend fun profile()=container.tuner.readDiagnostic()?.active?.let(GameLearning::profile)
        override suspend fun apply(profile: GameProfile, relaunch: Boolean, live: Boolean): Boolean {
            val target=game
            val result=when {
                relaunch && target!=null -> container.tuner.applyAndLaunch(target.packageName,target.launchActivity,profile,owner)
                relaunch -> return false
                live -> container.tuner.applyLearningLive(profile,owner)
                else -> container.tuner.applyProfile(profile,owner)
            }==TuneResult.Success
            if(result && relaunch)container.learningGameLaunched.tryEmit(Unit)
            return result
        }
        override fun context(packageName: String)=container.learningStore.contextFor(packageName)
        override suspend fun keep(context: LearningContext, goal: TuneGoal, profile: GameProfile): Boolean = runCatching {
            container.profileStore.save(context.packageName,profile)
            container.profileLibraryStore.saveNamed(context.packageName,goal.label,profile)
            container.learningStore.markCompleted(context,profile)
        }.getOrDefault(false)
    }
    val trial=LearningTrial(device,container.learningStore,container.questModel,declaredRates=container.display::declaredRefreshRates)
    private var passiveKey: Pair<LearningContext,GameProfile>?=null
    private var passiveRows=mutableListOf<PerformanceSample>()
    suspend fun start(game: io.github.openquesttuner.games.InstalledGame, config: LearningConfig) {
        observeLock.withLock {
        if (trial.state.value.active || trial.state.value.recoveryRequired) return
        this.game=game
        passiveKey=null; passiveRows.clear()
        val context=container.learningStore.contextFor(game.packageName) ?: return
        trial.start(context,config.copy(manageOvrCapture=false))
        }
    }
    /** Resume/start the selected game without force-stopping it or writing a profile. */
    suspend fun openSelectedGame(): Boolean = observeLock.withLock {
        val target=game ?: return@withLock false
        if(!trial.state.value.active || trial.state.value.waitingRelaunch) return@withLock false
        try {
            val result=container.adb.exec(ShellCommand.launch(target.packageName,target.launchActivity))
            (result.isSuccess && !ShellOutput.isLaunchError(result.output)).also { if(it)container.learningGameLaunched.tryEmit(Unit) }
        } catch(_: ShellUnavailableException) { false }
    }
    init {
        container.appScope.launch { container.adb.state.collectLatest { state ->
            if(state is ConnectionState.Connected)trial.recover() else if(trial.state.value.active)trial.stop()
        } }
        container.appScope.launch { container.thermal.level.collectLatest { level ->
            if(level in listOf(ThermalLevel.SEVERE,ThermalLevel.CRITICAL,ThermalLevel.EMERGENCY,ThermalLevel.SHUTDOWN) && trial.state.value.active)trial.stop()
        } }
    }
    suspend fun cleanupCapture() { /* No external recording is created or controlled. */ }
    suspend fun observe(sample: PerformanceSample?) {
        observeLock.withLock {
        if(trial.state.value.recoveryRequired) return
        if(trial.state.value.active) {
            trial.tick(sample)
            return
        }
        if(sample==null || !GameLearning.usable(sample,sample.packageName ?: return,System.currentTimeMillis())) { passiveRows.clear(); passiveKey=null; return }
        val ctx=container.learningStore.contextFor(sample.packageName!!) ?: return
        val profile=GameLearning.profile(sample.activeProperties) ?: return
        val key=ctx to profile
        if(passiveKey!=key || passiveRows.lastOrNull()?.let { sample.timestampMs-it.timestampMs>8000 || it.telemetry.reportedRefreshRate!=sample.telemetry.reportedRefreshRate }==true) { passiveRows.clear(); passiveKey=key }
        if(passiveRows.lastOrNull()?.telemetry?.capturedAtMs==sample.telemetry.capturedAtMs) return
        if(sample.thermal.warning) container.learningStore.invalidate(ctx,profile)
        passiveRows+=sample
        val summary=GameLearning.summarize(ctx,profile,"passive-${passiveRows.first().timestampMs}","observed",passiveRows,LearningConfig(halfRate=container.learningStore.halfRate(sample.packageName!!)))
        if(summary!=null) { container.learningStore.add(summary); passiveRows.clear() }
        if(passiveRows.size>100) passiveRows.clear()
        }
    }
}
