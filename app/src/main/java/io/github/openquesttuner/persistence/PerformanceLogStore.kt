package io.github.openquesttuner.persistence

import android.content.Context
import io.github.openquesttuner.core.PerformanceSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PerformanceLogStore(context: Context) {
    private val prefs = context.getSharedPreferences("performance_log_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val data = MutableStateFlow(runCatching { json.decodeFromString<List<PerformanceSample>>(prefs.getString("samples", "[]") ?: "[]") }.getOrDefault(emptyList()))
    val samples = data.asStateFlow()
    private var lastWrite = 0L
    @Synchronized fun record(sample: PerformanceSample) {
        if (sample.packageName == null) return
        val previous = data.value.lastOrNull { it.packageName == sample.packageName }
        if (sample.telemetry.isVr && previous?.telemetry?.capturedAtMs == sample.telemetry.capturedAtMs) return
        data.value = (data.value + sample).takeLast(3600)
        if (sample.timestampMs - lastWrite >= 10_000) flush()
    }
    @Synchronized fun flush() { prefs.edit().putString("samples", json.encodeToString(data.value)).apply(); lastWrite = System.currentTimeMillis() }
    @Synchronized fun clear() { data.value = emptyList(); prefs.edit().clear().apply(); flush() }
    fun baseline(pkg: String): List<PerformanceSample> = runCatching { json.decodeFromString<List<PerformanceSample>>(prefs.getString("baseline:$pkg", "[]") ?: "[]") }.getOrDefault(emptyList())
    fun saveBaseline(pkg: String) { prefs.edit().putString("baseline:$pkg", json.encodeToString(data.value.filter { it.packageName == pkg && it.telemetry.isVr }.takeLast(30))).apply() }
}
