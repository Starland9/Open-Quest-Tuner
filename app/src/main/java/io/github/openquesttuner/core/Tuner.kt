package io.github.openquesttuner.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface TuneResult {
    data object Success : TuneResult
    data object NotConnected : TuneResult
    data class InvalidProfile(val violations: List<ProfileViolation>) : TuneResult
    data class StepFailed(val step: TuneStep, val output: String) : TuneResult
}

sealed interface TuneStep {
    data object ForceStop : TuneStep
    data class SetProperty(val property: QuestProperty) : TuneStep
    data object Verify : TuneStep
    data object Launch : TuneStep
}

/** Résultat de « Tout réinitialiser » ; une liste vide signifie un succès complet. */
data class ResetResult(val failed: List<QuestProperty>)

/** Propriétés `debug.oculus.*` non vides lues sur le casque, triées par clé (FR-024). */
data class Diagnostic(val active: Map<String, String>)

/**
 * Séquences de commandes de contracts/shell-commands.md, sur un [ShellBackend] quelconque.
 * [display] donne les fréquences que l'écran déclare (spec 003).
 */
class Tuner(
    private val shell: ShellBackend,
    private val model: QuestModel,
    private val display: DisplayRates,
    private val runtimeLogs: (() -> String)? = null,
) {
    private val profileLock = Mutex()
    private var learningOwner: String? = null
    suspend fun reserveLearning(owner: String): Boolean = profileLock.withLock {
        if (learningOwner != null && learningOwner != owner) false else { learningOwner = owner; true }
    }
    suspend fun releaseLearning(owner: String) = profileLock.withLock { if (learningOwner == owner) learningOwner = null }
    private fun learningBlocked(owner: String?) = learningOwner != null && learningOwner != owner
    @Volatile var generation: Long = 0
        private set
    suspend fun applyAndLaunch(packageName: String, activity: String, profile: GameProfile, owner: String? = null): TuneResult =
        profileLock.withLock { if (learningBlocked(owner)) TuneResult.StepFailed(TuneStep.Verify, "Stop the learning test before changing profiles.") else { generation++; applyAndLaunchInternal(packageName, activity, profile) } }
    suspend fun applyProfile(profile: GameProfile, owner: String? = null): TuneResult = profileLock.withLock {
        if (learningBlocked(owner)) TuneResult.StepFailed(TuneStep.Verify, "Stop the learning test before changing profiles.") else { generation++; applyProfileInternal(profile) }
    }
    suspend fun applyLearningLive(profile: GameProfile, owner: String): TuneResult = profileLock.withLock {
        if (learningOwner != owner) return@withLock TuneResult.StepFailed(TuneStep.Verify, "Learning reservation lost.")
        val current = readDiagnosticInternal() ?: return@withLock TuneResult.NotConnected
        val changes = profile.toPropertyValues().filter { (p,v) -> current.active[p.key] != v?.toString() }
        if (changes.keys.any { it !in setOf(QuestProperty.CPU_LEVEL, QuestProperty.GPU_LEVEL) })
            return@withLock TuneResult.StepFailed(TuneStep.Verify, "This change requires a guided relaunch.")
        val violations = profile.validateFor(model, display.declaredRefreshRates())
        if (violations.isNotEmpty()) return@withLock TuneResult.InvalidProfile(violations)
        generation++
        try {
            for ((p,v) in changes) {
                val r = shell.exec(v?.let { ShellCommand.setProperty(p,it) } ?: ShellCommand.resetProperty(p))
                if (!r.isSuccess) return@withLock TuneResult.StepFailed(TuneStep.SetProperty(p), r.output)
            }
            verify(profile) ?: TuneResult.Success
        } catch (_: ShellUnavailableException) { TuneResult.NotConnected }
    }

    /**
     * « Appliquer et lancer » (FR-019, FR-020) : arrêt du jeu, les 7 propriétés dans l'ordre de
     * l'enum (valeur du profil, ou remise à vide), puis lancement. S'arrête au premier échec.
     *
     * @throws IllegalArgumentException si [packageName] ou [activity] est invalide, avant tout envoi.
     */
    private suspend fun applyAndLaunchInternal(packageName: String, activity: String, profile: GameProfile): TuneResult {
        // Écran lu au moment du lancement : couvre un profil venu d'un autre casque, ou une mise à
        // jour d'Horizon OS qui retire une fréquence (spec 003, cas limites).
        val violations = profile.validateFor(model, display.declaredRefreshRates())
        if (violations.isNotEmpty()) return TuneResult.InvalidProfile(violations)

        // Toutes les commandes sont construites d'abord : une valeur ou un nom refusé par les
        // fabriques lève une exception sans qu'aucune commande n'ait été envoyée.
        val steps = buildList {
            add(TuneStep.ForceStop to ShellCommand.forceStop(packageName))
            for ((property, value) in profile.toPropertyValues()) {
                val command = value?.let { ShellCommand.setProperty(property, it) } ?: ShellCommand.resetProperty(property)
                add(TuneStep.SetProperty(property) to command)
            }
        }
        val launch = ShellCommand.launch(packageName, activity)

        return try {
            for ((step, command) in steps) {
                val result = shell.exec(command)
                if (!result.isSuccess) return TuneResult.StepFailed(step, result.output)
            }
            verify(profile)?.let { return it }
            val result = shell.exec(launch)
            if (result.isSuccess && !ShellOutput.isLaunchError(result.output)) {
                TuneResult.Success
            } else {
                TuneResult.StepFailed(TuneStep.Launch, result.output)
            }
        } catch (e: ShellUnavailableException) {
            TuneResult.NotConnected
        }
    }

    /** Apply a profile without stopping or launching an app. Used by automatic switching. */
    private suspend fun applyProfileInternal(profile: GameProfile): TuneResult {
        val violations = profile.validateFor(model, display.declaredRefreshRates())
        if (violations.isNotEmpty()) return TuneResult.InvalidProfile(violations)
        return try {
            for ((property, value) in profile.toPropertyValues()) {
                val command = value?.let { ShellCommand.setProperty(property, it) } ?: ShellCommand.resetProperty(property)
                val result = shell.exec(command)
                if (!result.isSuccess) return TuneResult.StepFailed(TuneStep.SetProperty(property), result.output)
            }
            verify(profile) ?: TuneResult.Success
        } catch (e: ShellUnavailableException) { TuneResult.NotConnected }
    }

    private suspend fun verify(profile: GameProfile): TuneResult.StepFailed? {
        val read = shell.exec(ShellCommand.readProperties())
        if (!read.isSuccess) return TuneResult.StepFailed(TuneStep.Verify, "Property readback failed: ${read.output}")
        val actual = ShellOutput.parseGetprop(read.output)
        val mismatches = profile.toPropertyValues().filter { (key, value) -> actual[key.key] != value?.toString() }
        return if (mismatches.isEmpty()) null else TuneResult.StepFailed(TuneStep.Verify,
            mismatches.entries.joinToString("\n") { (key, value) -> "${key.key}: requested ${value ?: "default"}, read ${actual[key.key] ?: "default"}" })
    }

    @Volatile var foregroundEvidence: String = "Not checked"
        private set
    @Suppress("UNUSED_PARAMETER")
    suspend fun readForegroundPackage(preferredPackages: Set<String> = emptySet()): String? = try {
        val activity=shell.exec(ShellCommand.readForegroundActivity())
        val resumed=if(activity.isSuccess) ForegroundActivityParser.parse(activity.output) else null
        if(resumed!=null) {
            foregroundEvidence="Activity: $resumed | "+ForegroundActivityParser.evidence(activity.output)
            resumed
        } else {
            val window=shell.exec(ShellCommand.readFocusedWindow())
            val focused=if(window.isSuccess) ForegroundActivityParser.parseWindow(window.output) else null
            foregroundEvidence="Activity success=${activity.isSuccess}: "+ForegroundActivityParser.evidence(activity.output)+
                " | Window success=${window.isSuccess}: "+ForegroundActivityParser.evidence(window.output)+" | Selected=${focused ?: "unknown"}"
            focused
        }
    } catch(e: ShellUnavailableException) {
        foregroundEvidence="ADB foreground query unavailable: ${e.message}";null
    }

    /** Best-effort platform telemetry. Missing counters are represented as null rather than estimated. */
    suspend fun readRuntimeTelemetry(packageName: String, refreshRate: Int?): RuntimeTelemetry = try {
        val pids = shell.exec(ShellCommand.processIds(packageName)).output.trim().split(Regex("\\s+")).mapNotNull(String::toIntOrNull).toSet()
        val logs = runtimeLogs?.invoke() ?: shell.exec(ShellCommand.vrLogs()).takeIf { it.isSuccess }?.output.orEmpty()
        VrTelemetryParser.latest(logs, pids, System.currentTimeMillis()) ?: run {
            val frames = shell.exec(ShellCommand.readFrameStats(packageName)).takeIf { it.isSuccess }?.output
            val cpu = shell.exec(ShellCommand.readCpuInfo(packageName)).takeIf { it.isSuccess }?.output
            RuntimeTelemetryParser.parse(frames, cpu, refreshRate)
        }
    } catch (_: ShellUnavailableException) { RuntimeTelemetry() }

    /**
     * « Tout réinitialiser » (FR-023) : remet les 7 propriétés à vide. Toutes sont tentées même
     * si l'une échoue, pour ne jamais laisser plus de réglages actifs que nécessaire (principe I).
     *
     * @return `null` si l'appli n'est pas connectée, ou si la connexion est perdue en cours de route.
     */
    suspend fun resetAll(): ResetResult? = profileLock.withLock { if (learningBlocked(null)) ResetResult(QuestProperty.entries) else { generation++; resetAllInternal() } }
    private suspend fun resetAllInternal(): ResetResult? = try {
        val failed = QuestProperty.entries.filterNot { property ->
            shell.exec(ShellCommand.resetProperty(property)).isSuccess
        }
        ResetResult(failed)
    } catch (e: ShellUnavailableException) {
        null
    }

    /**
     * Diagnostic (FR-024) : une seule lecture `getprop`, filtrée côté appli. Sert aussi à
     * l'indicateur « actif sur le casque » de l'écran profil (FR-033).
     *
     * @return `null` si l'appli n'est pas connectée ou si la lecture échoue.
     */
    suspend fun readDiagnostic(): Diagnostic? = profileLock.withLock { readDiagnosticInternal() }
    private suspend fun readDiagnosticInternal(): Diagnostic? = try {
        val result = shell.exec(ShellCommand.readProperties())
        if (result.isSuccess) Diagnostic(ShellOutput.parseGetprop(result.output)) else null
    } catch (e: ShellUnavailableException) {
        null
    }
}
