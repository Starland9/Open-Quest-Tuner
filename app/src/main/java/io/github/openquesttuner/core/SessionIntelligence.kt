package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GameSession(
    val id: Long,
    val packageName: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val profileName: String? = null,
    val samples: Int = 0,
    val matchingSamples: Int = 0,
    val thermalPressureSamples: Int = 0,
    val hotSamples: Int = 0,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)
    val stabilityPercent: Int get() = if (samples == 0) 0 else matchingSamples * 100 / samples
    val thermalPressurePercent: Int get() = if (samples == 0) 0 else thermalPressureSamples * 100 / samples
}

enum class AssistantAction { NONE, APPLY_GOAL, REAPPLY_CURRENT }

data class OptimizationAdvice(
    val headline: String,
    val explanation: String,
    val action: AssistantAction = AssistantAction.NONE,
    val goal: TuneGoal? = null,
    val confidence: Int = 0,
    val safeForAutomaticApply: Boolean = false,
)

object OptimizationAssistant {
    fun advise(history: GameOptimizationHistory?, suggestion: AdaptiveSuggestion?, sessions: List<GameSession>, currentGoal: TuneGoal?): OptimizationAdvice {
        if (history == null || history.totalSamples < 15) return OptimizationAdvice("Learning this game", "Keep playing with the current profile while Open Quest Tuner gathers enough verified observations.", confidence = 40)
        if (history.stabilityPercent < 75) return OptimizationAdvice("Re-apply current profile", "The headset matched the saved profile in only ${history.stabilityPercent}% of observations. Fix application consistency before changing performance targets.", AssistantAction.REAPPLY_CURRENT, confidence = 92)
        val goal = suggestion?.recommendedGoal
        if (goal != null) {
            val cooling = suggestion.severity == AdaptiveSuggestion.Severity.HOT || suggestion.severity == AdaptiveSuggestion.Severity.WATCH
            return OptimizationAdvice(
                suggestion.headline,
                suggestion.reason,
                AssistantAction.APPLY_GOAL,
                goal,
                suggestion.confidence,
                safeForAutomaticApply = cooling && history.totalSamples >= 45 && sessions.size >= 2,
            )
        }
        val recent = sessions.takeLast(3)
        if (recent.size >= 3 && recent.all { it.stabilityPercent >= 95 && it.thermalPressurePercent <= 5 } && currentGoal != TuneGoal.HD_PLUS) {
            return OptimizationAdvice("Consistent headroom", "The last three sessions were stable and cool. A higher quality target is available for manual review.", AssistantAction.APPLY_GOAL, nextQuality(currentGoal), 84, false)
        }
        return OptimizationAdvice("Current setup recommended", "Recent observations are stable. No change is needed right now.", confidence = 90)
    }

    private fun nextQuality(goal: TuneGoal?): TuneGoal = when (goal) {
        TuneGoal.BATTERY, TuneGoal.PERFORMANCE -> TuneGoal.BALANCED
        TuneGoal.BALANCED, null -> TuneGoal.QUALITY
        TuneGoal.QUALITY, TuneGoal.HD_PLUS -> TuneGoal.HD_PLUS
    }
}

