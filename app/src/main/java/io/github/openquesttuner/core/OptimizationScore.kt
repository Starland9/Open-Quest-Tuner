package io.github.openquesttuner.core

data class OptimizationScore(
    val overall: Int,
    val application: Int?,
    val thermal: Int?,
    val observationMinutes: Int,
    val confidence: Int,
    val status: String,
    val detail: String,
)

object OptimizationScoreEngine {
    fun calculate(history: GameOptimizationHistory?, suggestion: AdaptiveSuggestion?, hasProfile: Boolean): OptimizationScore {
        if (!hasProfile) return OptimizationScore(0, null, null, 0, 0, "No profile", "Create, install, or Auto Tune a profile to begin optimisation learning.")
        if (history == null || history.totalSamples == 0) return OptimizationScore(45, null, null, 0, 35, "Ready to learn", "A profile exists. Launch the game to collect verified runtime observations.")
        val application = history.stabilityPercent.coerceIn(0, 100)
        val thermal = (100 - history.thermalPressurePercent * 2).coerceIn(0, 100)
        val observation = (history.totalSamples * 100 / 90).coerceIn(0, 100)
        val confidence = ((observation * 45 + application * 35 + thermal * 20) / 100).coerceIn(0, 100)
        val overall = ((application * 45 + thermal * 35 + observation * 20) / 100).coerceIn(0, 100)
        val status = when {
            suggestion?.severity == AdaptiveSuggestion.Severity.HOT -> "Thermal adjustment recommended"
            application < 80 -> "Profile application needs attention"
            overall >= 90 -> "Excellent"
            overall >= 75 -> "Healthy"
            overall >= 55 -> "Learning"
            else -> "Needs attention"
        }
        return OptimizationScore(overall, application, thermal, (history.totalObservedMs / 60_000L).toInt(), confidence, status,
            "Based on verified profile application, thermal pressure, and ${history.totalSamples} runtime observations. FPS is not estimated.")
    }
}
