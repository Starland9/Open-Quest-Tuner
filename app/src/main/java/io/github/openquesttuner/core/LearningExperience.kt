package io.github.openquesttuner.core

/** Product language and defaults shared by the panel, notification and experimental HUD. */
object LearningExperience {
    val goals = listOf(TuneGoal.BALANCED, TuneGoal.QUALITY, TuneGoal.PERFORMANCE, TuneGoal.BATTERY)
    fun goalLabel(goal: TuneGoal) = when(goal) {
        TuneGoal.BALANCED -> "Balanced"
        TuneGoal.QUALITY, TuneGoal.HD_PLUS -> "Better visuals"
        TuneGoal.PERFORMANCE -> "Smoother gameplay"
        TuneGoal.BATTERY -> "Lower power use"
    }
    fun defaults(goal: TuneGoal = TuneGoal.BALANCED, halfRate: Boolean = false) = LearningConfig(
        goal=goal,seconds=60,halfRate=halfRate,autoKeep=true,
        mode=if(goal in listOf(TuneGoal.QUALITY,TuneGoal.HD_PLUS,TuneGoal.PERFORMANCE)) LearningMode.RELAUNCH else LearningMode.LIVE,
        warmupSeconds=if(goal in listOf(TuneGoal.QUALITY,TuneGoal.HD_PLUS,TuneGoal.PERFORMANCE)) 60 else 15)
    fun title(state: LearningTrialState): String = when {
        state.recoveryRequired -> "Your settings need restoring"
        state.active -> when {
            state.waitingRelaunch -> "Restart needed to continue"
            state.phase=="Waiting for game" -> "Ready to play"
            state.phase=="Applying" -> "Trying a setting…"
            state.phase=="Verifying render settings" -> "Checking the game’s visual settings…"
            state.phase=="Restoring" -> "Restoring your settings…"
            state.phase=="Warm-up" -> "Getting ready to measure…"
            state.stage%2==0 -> "Checking an improvement…"
            else -> "Learning your game…"
        }
        else -> when(state.result) {
            LearningResult.APPLIED -> "Optimisation complete — settings saved"
            LearningResult.SAVED_FOR_LAUNCH -> "Improved profile ready for your next launch"
            LearningResult.REVIEW -> "Learning complete — review your results"
            LearningResult.UNCHANGED -> "Your existing settings were kept"
            LearningResult.INCONCLUSIVE -> "More comparable gameplay needed"
            LearningResult.UNAVAILABLE -> "Learning is unavailable for this run"
            LearningResult.STOPPED -> "Learning stopped"
            LearningResult.RECOVERY -> "Your settings need restoring"
            null -> "Auto Learn"
        }
    }
    fun explanation(state: LearningTrialState): String = when {
        state.recoveryRequired -> "Reconnect your headset, then restore the previous settings before starting again."
        state.active && state.waitingRelaunch -> "Save your progress. Continue when you are ready to restart, then return to the same part of the game."
        state.active && state.phase=="Waiting for game" -> "Open the selected game and play a familiar section."
        state.active -> "Play a familiar section at a similar pace. We will keep an improvement only after repeated checks."
        else -> when(state.result) {
            LearningResult.APPLIED -> "An improvement passed the repeated checks. It is active now and saved for this game. You can adjust it afterwards."
            LearningResult.SAVED_FOR_LAUNCH -> "The improved profile is saved. Launch through OQT to use it; your running game's render settings were not silently changed."
            LearningResult.REVIEW -> "Your previous settings are restored. Review a measured profile below before applying it."
            LearningResult.UNCHANGED -> "No better eligible setting was found in this run. Your previous settings are restored."
            LearningResult.INCONCLUSIVE -> "The gameplay varied too much to choose reliably. Your previous settings are restored; try a familiar section again."
            LearningResult.UNAVAILABLE -> "We could not verify the game counters or tuning changes. Open Advanced for the reason; no result was accepted."
            LearningResult.STOPPED -> "Your previous overrides are restored. If a resolution test was interrupted, restart the game to finish restoring its visuals."
            LearningResult.RECOVERY -> "Reconnect and restore before another test."
            null -> "Choose what matters to you, then Learn & Play. We check supported settings and save a repeat-tested improvement automatically."
        }
    }
    fun progress(state: LearningTrialState): Float? = state.totalWindows?.takeIf { it>0 }?.let {
        ((state.completedWindows+if(state.active && state.phase=="Measuring") state.progress else 0f)/it).coerceIn(0f,1f)
    }
    fun stopLearningCapture(learningOnly: Boolean, state: LearningTrialState) = learningOnly && !state.active
}

data class LearningHud(val title: String, val lines: List<String>, val confirmed: Boolean)

object LearningOverlayPolicy {
    fun frame(state: LearningTrialState, enabled: Boolean, permission: Boolean, now: Long): LearningHud? {
        if(!enabled || !permission || !state.active) return null
        val fresh=state.countersAt?.let { now-it in 0..6000 }==true
        val profile=state.requestedProfile
        val renderFresh=state.renderCountersAt?.let { now-it in 0..6000 }==true
        val confirmed=if(state.renderChecksRequired) state.renderConfirmed && renderFresh && fresh else state.computeChange && state.runtimeConfirmed && fresh
        val lines=mutableListOf<String>()
        state.requestedAt?.let { lines+="Change requested at "+java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) }
        lines+="Requested: CPU ${profile?.cpuLevel ?: "default"} · GPU ${profile?.gpuLevel ?: "default"}"
        lines+=if(fresh) "Observed: CPU ${state.observedCpu ?: "?"} · GPU ${state.observedGpu ?: "?"}" else "Waiting for fresh game counters"
        if(fresh && (state.cpuMhz!=null || state.gpuMhz!=null)) lines+="CPU ${state.cpuMhz ?: "?"} MHz · GPU ${state.gpuMhz ?: "?"} MHz"
        if(state.renderChecksRequired) {
            lines+="Requested render: ${profile?.eyeTexture?.let { "${it.width}×${it.height}" } ?: "game default"} · ${profile?.refreshRate?.let { "${it}Hz" } ?: "default Hz"}"
            lines+=if(renderFresh) "Observed render: ${state.observedEyeTexture?.let { "${it.width}×${it.height}" } ?: "?"} · ${state.observedHz ?: "?"}Hz" else "Waiting for fresh native render counters"
        }
        lines+=when {
            state.waitingRelaunch -> "Restart from OQT to continue"
            confirmed && state.renderChecksRequired -> "Resolution / refresh confirmed"
            confirmed -> "CPU/GPU change confirmed"
            state.computeChange -> "Change not yet confirmed"
            else -> "${state.completedWindows} measurement steps complete"
        }
        state.remainingSeconds?.let { lines+="$it seconds in this step" }
        lines+="OQT Auto Learn · measured runtime status"
        return LearningHud(LearningExperience.title(state),lines,confirmed)
    }
}
