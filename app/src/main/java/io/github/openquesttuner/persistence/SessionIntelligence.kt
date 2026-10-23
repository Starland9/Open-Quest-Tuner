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

private data class MutableSession(
    val id: Long,
    val packageName: String,
    val startedAtMs: Long,
    var lastSeenMs: Long,
    var profileName: String?,
    var samples: Int = 0,
    var matching: Int = 0,
    var thermal: Int = 0,
    var hot: Int = 0,
)

class SessionHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("session_history_v10", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _sessions = MutableStateFlow(load())
    val sessions: StateFlow<Map<String, List<GameSession>>> = _sessions.asStateFlow()
    private var active: MutableSession? = null

    @Synchronized fun record(sample: PerformanceSample, expected: GameProfile?, profileName: String?) {
        val pkg = sample.packageName
        val current = active
        if (pkg == null) { finish(sample.timestampMs); return }
        if (current == null || current.packageName != pkg) {
            finish(sample.timestampMs)
            active = MutableSession(sample.timestampMs, pkg, sample.timestampMs, sample.timestampMs, profileName)
        }
        val s = active ?: return
        s.lastSeenMs = sample.timestampMs
        s.profileName = profileName ?: s.profileName
        s.samples++
        if (expected?.isActiveOn(sample.activeProperties) != false) s.matching++
        if (sample.thermal.warning) s.thermal++
        if (sample.thermal.name in setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")) s.hot++
    }

    @Synchronized fun finish(atMs: Long = System.currentTimeMillis()) {
        val s = active ?: return
        if (s.samples >= 2) {
            val session = GameSession(s.id, s.packageName, s.startedAtMs, maxOf(s.lastSeenMs, atMs), s.profileName, s.samples, s.matching, s.thermal, s.hot)
            val updated = (_sessions.value[s.packageName].orEmpty() + session).takeLast(30)
            persist(_sessions.value + (s.packageName to updated))
        }
        active = null
    }

    @Synchronized fun clear(packageName: String? = null) {
        if (packageName == null) { active = null; persist(emptyMap()) }
        else persist(_sessions.value - packageName)
    }

    private fun persist(value: Map<String, List<GameSession>>) {
        prefs.edit().putString(KEY, json.encodeToString(value)).apply(); _sessions.value = value
    }
    private fun load(): Map<String, List<GameSession>> = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString<Map<String, List<GameSession>>>(it) }
    }.getOrNull().orEmpty()
    private companion object { const val KEY = "sessions" }
}

class ProductExperienceStore(context: Context) {
    private val prefs = context.getSharedPreferences("product_experience_v11", Context.MODE_PRIVATE)
    private val _onboardingComplete = MutableStateFlow(prefs.getBoolean("onboarding_complete", false))
    val onboardingComplete = _onboardingComplete.asStateFlow()
    private val _safeAutoRecommendations = MutableStateFlow(prefs.getBoolean("safe_auto_recommendations", false))
    val safeAutoRecommendations = _safeAutoRecommendations.asStateFlow()
    private val _notifications = MutableStateFlow(prefs.getBoolean("assistant_notifications", true))
    val assistantNotifications = _notifications.asStateFlow()
    fun setOnboardingComplete(v: Boolean) { prefs.edit().putBoolean("onboarding_complete", v).apply(); _onboardingComplete.value = v }
    fun setSafeAutoRecommendations(v: Boolean) { prefs.edit().putBoolean("safe_auto_recommendations", v).apply(); _safeAutoRecommendations.value = v }
    fun setNotifications(v: Boolean) { prefs.edit().putBoolean("assistant_notifications", v).apply(); _notifications.value = v }
}
