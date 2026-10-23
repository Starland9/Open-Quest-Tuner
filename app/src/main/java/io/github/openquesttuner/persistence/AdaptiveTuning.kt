package io.github.openquesttuner.persistence

import io.github.openquesttuner.core.*
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OptimizationHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("optimization_history_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _history = MutableStateFlow(load())
    val history: StateFlow<Map<String, GameOptimizationHistory>> = _history.asStateFlow()

    fun record(sample: PerformanceSample, expected: GameProfile?, profileName: String?) {
        val pkg = sample.packageName ?: return
        val old = _history.value[pkg] ?: GameOptimizationHistory(pkg)
        val moderate = sample.thermal == ThermalLevel.MODERATE
        val hot = sample.thermal.name in setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
        val matching = expected?.isActiveOn(sample.activeProperties) ?: true
        val updated = old.copy(
            totalSamples = old.totalSamples + 1,
            totalObservedMs = old.totalObservedMs + 2_000L,
            matchingSamples = old.matchingSamples + if (matching) 1 else 0,
            moderateThermalSamples = old.moderateThermalSamples + if (moderate) 1 else 0,
            hotThermalSamples = old.hotThermalSamples + if (hot) 1 else 0,
            lastSeenMs = sample.timestampMs,
            lastProfileName = profileName ?: old.lastProfileName,
        )
        persist(_history.value + (pkg to updated))
    }

    fun clear(packageName: String? = null) {
        persist(if (packageName == null) emptyMap() else _history.value - packageName)
    }

    private fun persist(value: Map<String, GameOptimizationHistory>) {
        prefs.edit().putString(KEY, json.encodeToString(value)).apply()
        _history.value = value
    }

    private fun load(): Map<String, GameOptimizationHistory> = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString<Map<String, GameOptimizationHistory>>(it) }
    }.getOrNull().orEmpty()

    private companion object { const val KEY = "history" }
}
