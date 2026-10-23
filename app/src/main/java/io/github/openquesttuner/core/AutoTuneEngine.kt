package io.github.openquesttuner.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class TuneGoal(val label: String) {
    HD_PLUS("HD+"), QUALITY("Quality"), BALANCED("Balanced"), PERFORMANCE("Performance"), BATTERY("Battery")
}

@Serializable
data class AutoTuneRecommendation(
    val goal: TuneGoal,
    val profile: GameProfile,
    val confidence: Int,
    val rationale: List<String>,
    val source: String = "OpenQuest Auto Tune",
)

/**
 * Conservative device-aware recommendation engine. It never writes properties itself: it only
 * produces a normal GameProfile, so the existing validation/safety path remains authoritative.
 */
object AutoTuneEngine {
    fun recommend(model: QuestModel, goal: TuneGoal, declaredRates: Set<Int> = emptySet()): AutoTuneRecommendation {
        val available = RefreshRatePolicy.available(model, declaredRates).ifEmpty { model.refreshRates }
        fun nearest(target: Int) = available.minByOrNull { abs(it - target) }
        fun texture(step: Int, rate: Int?): EyeTexture {
            val capped = RefreshRatePolicy.maxResolutionStep(rate)?.let { minOf(step, it) } ?: step
            return EyeTexture.forStep(model.defaultEyeTexture, capped.coerceIn(70, 150))
        }
        val safeCpu = model.alwaysAvailableCpuMax.coerceIn(model.cpuLevels)
        val safeGpu = model.alwaysAvailableGpuMax.coerceIn(model.gpuLevels)
        val maxSafeRate = available.filter { it <= 120 }.maxOrNull() ?: available.maxOrNull()

        val profile = when (goal) {
            TuneGoal.BATTERY -> GameProfile(
                refreshRate = nearest(72), eyeTexture = texture(80, nearest(72)),
                cpuLevel = model.cpuLevels.first, gpuLevel = model.gpuLevels.first,
                foveationLevel = FoveationLevel.HIGH, dynamicFoveation = true,
            )
            TuneGoal.BALANCED -> GameProfile(
                refreshRate = nearest(90), eyeTexture = texture(100, nearest(90)),
                cpuLevel = safeCpu, gpuLevel = safeGpu,
                foveationLevel = FoveationLevel.MEDIUM, dynamicFoveation = true,
            )
            TuneGoal.QUALITY -> GameProfile(
                refreshRate = nearest(90), eyeTexture = texture(120, nearest(90)),
                cpuLevel = safeCpu, gpuLevel = safeGpu,
                foveationLevel = FoveationLevel.MEDIUM, dynamicFoveation = true,
            )
            TuneGoal.HD_PLUS -> GameProfile(
                refreshRate = nearest(90), eyeTexture = texture(130, nearest(90)),
                cpuLevel = safeCpu, gpuLevel = safeGpu,
                foveationLevel = FoveationLevel.HIGH, dynamicFoveation = true,
            )
            TuneGoal.PERFORMANCE -> GameProfile(
                refreshRate = maxSafeRate, eyeTexture = texture(100, maxSafeRate),
                cpuLevel = safeCpu, gpuLevel = safeGpu,
                foveationLevel = FoveationLevel.MEDIUM, dynamicFoveation = true,
            )
        }
        val unknownPenalty = if (model == QuestModel.UNKNOWN) 20 else 0
        val conditionalPenalty = if ((profile.cpuLevel ?: 0) > model.alwaysAvailableCpuMax || (profile.gpuLevel ?: 0) > model.alwaysAvailableGpuMax) 10 else 0
        val rationale = buildList {
            add("Built for ${model.displayName} using only refresh rates currently available to the headset.")
            when (goal) {
                TuneGoal.BATTERY -> add("Reduces refresh rate, render resolution and compute levels to favour battery life and thermals.")
                TuneGoal.BALANCED -> add("Targets a 90 Hz-class experience at native render scale with conservative compute levels.")
                TuneGoal.QUALITY -> add("Raises render resolution while keeping a 90 Hz-class target and dynamic foveation.")
                TuneGoal.HD_PLUS -> add("Prioritises image clarity with stronger foveation to offset the higher render resolution.")
                TuneGoal.PERFORMANCE -> add("Uses the highest normal refresh rate available up to 120 Hz while holding render scale near native.")
            }
            if (model == QuestModel.UNKNOWN) add("Headset model is unknown, so this recommendation uses conservative Quest 3 fallback limits.")
        }
        return AutoTuneRecommendation(goal, profile, (90 - unknownPenalty - conditionalPenalty).coerceIn(40, 95), rationale)
    }

    fun all(model: QuestModel, declaredRates: Set<Int> = emptySet()): List<AutoTuneRecommendation> =
        TuneGoal.entries.map { recommend(model, it, declaredRates) }
}
