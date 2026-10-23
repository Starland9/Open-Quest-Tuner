package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GameOptimizationHistory(
    val packageName: String,
    val totalSamples: Int = 0,
    val totalObservedMs: Long = 0,
    val matchingSamples: Int = 0,
    val moderateThermalSamples: Int = 0,
    val hotThermalSamples: Int = 0,
    val lastSeenMs: Long = 0,
    val lastProfileName: String? = null,
) {
    val stabilityPercent: Int get() = if (totalSamples == 0) 0 else (matchingSamples * 100 / totalSamples)
    val thermalPressurePercent: Int get() = if (totalSamples == 0) 0 else ((moderateThermalSamples + hotThermalSamples) * 100 / totalSamples)
}

@Serializable
data class AdaptiveSuggestion(
    val packageName: String,
    val headline: String,
    val reason: String,
    val recommendedGoal: TuneGoal? = null,
    val confidence: Int = 0,
    val severity: Severity = Severity.INFO,
) {
    enum class Severity { INFO, GOOD, WATCH, HOT }
}

object AdaptiveTuneEngine {
    fun suggest(history: GameOptimizationHistory?, currentGoal: TuneGoal? = null): AdaptiveSuggestion? {
        history ?: return null
        if (history.totalSamples < 15) return AdaptiveSuggestion(history.packageName, "Learning this game", "More runtime data is needed before changing its tune. ${history.totalSamples}/15 minimum samples collected.", confidence = (history.totalSamples * 4).coerceAtMost(55))
        val thermal = history.thermalPressurePercent
        val stable = history.stabilityPercent
        return when {
            history.hotThermalSamples >= 3 || thermal >= 35 -> AdaptiveSuggestion(history.packageName, "Reduce thermal load", "Thermal pressure appeared in $thermal% of observations. Step down to a cooler target and re-evaluate.", TuneGoal.BALANCED, 90, AdaptiveSuggestion.Severity.HOT)
            thermal >= 18 -> AdaptiveSuggestion(history.packageName, "Consider a cooler profile", "Moderate/high thermal pressure appeared in $thermal% of observations.", TuneGoal.QUALITY, 80, AdaptiveSuggestion.Severity.WATCH)
            stable < 80 -> AdaptiveSuggestion(history.packageName, "Profile application is inconsistent", "Only $stable% of sampled states matched the saved profile. Re-apply or investigate profile switching before increasing quality.", null, 88, AdaptiveSuggestion.Severity.WATCH)
            history.totalSamples >= 60 && thermal <= 5 && stable >= 95 && currentGoal != TuneGoal.HD_PLUS -> AdaptiveSuggestion(history.packageName, "Headroom detected", "The profile stayed cool and matched the headset in $stable% of samples. Try the next quality tier and observe it.", nextQuality(currentGoal), 78, AdaptiveSuggestion.Severity.GOOD)
            else -> AdaptiveSuggestion(history.packageName, "Current tune looks healthy", "Profile stability is $stable% with $thermal% thermal-pressure observations. Keep this tune and continue collecting data.", null, 86, AdaptiveSuggestion.Severity.GOOD)
        }
    }

    private fun nextQuality(goal: TuneGoal?): TuneGoal = when (goal) {
        TuneGoal.BATTERY -> TuneGoal.BALANCED
        TuneGoal.PERFORMANCE -> TuneGoal.BALANCED
        TuneGoal.BALANCED, null -> TuneGoal.QUALITY
        TuneGoal.QUALITY -> TuneGoal.HD_PLUS
        TuneGoal.HD_PLUS -> TuneGoal.HD_PLUS
    }
}
