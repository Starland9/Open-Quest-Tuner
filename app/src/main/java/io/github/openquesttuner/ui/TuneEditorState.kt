package io.github.openquesttuner.ui

import io.github.openquesttuner.core.*

/** Diagnostic is a complete getprop snapshot: omitted/empty overrides mean game default. */
internal enum class TuneReadbackStatus { MATCH, DIFFERENT, UNAVAILABLE }
internal data class TuneReadbackRow(val property: QuestProperty, val requested: Int?, val device: String?, val status: TuneReadbackStatus)
internal fun tuneReadbackRows(profile: GameProfile, connected: Boolean, diagnostic: Diagnostic?): List<TuneReadbackRow> =
    profile.toPropertyValues().map { (property, expected) ->
        val known = connected && diagnostic != null
        val raw = if (known) diagnostic?.active?.get(property.key) else null
        val actual = raw?.trim()?.takeIf { it.isNotEmpty() }
        TuneReadbackRow(property, expected, actual, when {
            !known -> TuneReadbackStatus.UNAVAILABLE
            actual == expected?.toString() -> TuneReadbackStatus.MATCH
            else -> TuneReadbackStatus.DIFFERENT
        })
    }

/** Steppers visit allowed resolution presets only; custom values are preserved until edited. */
internal fun tuneResolutionStep(profile: GameProfile, model: QuestModel, direction: Int): EyeTexture? {
    val choices = RESOLUTION_STEPS.map { EyeTexture.forStep(model.defaultEyeTexture, it) }
        .filter { RefreshRatePolicy.allowed(profile.refreshRate, it, model.defaultEyeTexture) }
    val current = profile.eyeTexture ?: return if (direction > 0) choices.lastOrNull {
        it.width <= model.defaultEyeTexture.width && it.height <= model.defaultEyeTexture.height
    } else null
    return if (direction > 0) choices.firstOrNull { it.width > current.width || it.height > current.height } ?: current
    else choices.lastOrNull { it.width < current.width && it.height < current.height }
}

/** Same six preset values as the existing global editor, presented compactly in per-game Tune. */
internal fun tunePresetProfiles(model: QuestModel): List<Pair<String, GameProfile>> {
    val near90 = model.refreshRates.minByOrNull { kotlin.math.abs(it - 90) }
    return listOf(
        "Default" to GameProfile(),
        "Battery" to GameProfile(model.refreshRates.first(), EyeTexture.forStep(model.defaultEyeTexture, 80), model.cpuLevels.first, model.gpuLevels.first, FoveationLevel.HIGH, true),
        "Balanced" to GameProfile(near90, model.defaultEyeTexture, model.alwaysAvailableCpuMax, model.alwaysAvailableGpuMax, FoveationLevel.MEDIUM, true),
        "Performance" to GameProfile(model.refreshRates.maxOrNull(), model.defaultEyeTexture, model.cpuLevels.last, model.gpuLevels.last, FoveationLevel.MEDIUM, true),
        "Quality" to GameProfile(near90, EyeTexture.forStep(model.defaultEyeTexture, 120), model.alwaysAvailableCpuMax, model.alwaysAvailableGpuMax, FoveationLevel.MEDIUM, true),
        "HD+" to GameProfile(near90, EyeTexture.forStep(model.defaultEyeTexture, 130), model.cpuLevels.last, model.gpuLevels.last, FoveationLevel.HIGH, true),
    )
}
