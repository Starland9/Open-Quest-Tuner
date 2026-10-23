package io.github.openquesttuner.core

import org.junit.Assert.*
import org.junit.Test

class LearningExperienceTest {
    @Test fun `simple defaults keep repeated learning and automatic saving`() {
        val config=LearningExperience.defaults()
        assertEquals(TuneGoal.BALANCED,config.goal);assertEquals(60,config.seconds)
        assertEquals(LearningMode.LIVE,config.mode);assertEquals(15,config.warmupSeconds)
        assertTrue(config.autoKeep);assertTrue(config.valid());assertFalse(config.halfRate)
    }
    @Test fun `visual and smoother goals use explicit restart mode and longer settling`() {
        for(goal in listOf(TuneGoal.QUALITY,TuneGoal.HD_PLUS,TuneGoal.PERFORMANCE)) {
            val config=LearningExperience.defaults(goal,true)
            assertEquals(LearningMode.RELAUNCH,config.mode);assertEquals(60,config.warmupSeconds);assertTrue(config.halfRate)
        }
        assertEquals(LearningMode.LIVE,LearningExperience.defaults(TuneGoal.BATTERY).mode)
    }
    @Test fun `unknown total does not invent progress and current measurement stays within run bounds`() {
        assertNull(LearningExperience.progress(LearningTrialState(active=true)))
        assertEquals(.25f,LearningExperience.progress(LearningTrialState(active=true,phase="Measuring",completedWindows=2,totalWindows=10,progress=.5f))!!,.001f)
        assertEquals(.2f,LearningExperience.progress(LearningTrialState(active=true,phase="Warm-up",completedWindows=2,totalWindows=10,progress=.5f))!!,.001f)
    }
    @Test fun `inconclusive or rejected measurements never report optimisation complete`() {
        for(result in listOf(LearningResult.INCONCLUSIVE,LearningResult.UNAVAILABLE,LearningResult.UNCHANGED,LearningResult.STOPPED,LearningResult.RECOVERY)) {
            assertFalse(LearningExperience.title(LearningTrialState(result=result)).contains("Optimisation complete"))
        }
        assertTrue(LearningExperience.title(LearningTrialState(result=LearningResult.APPLIED)).contains("Optimisation complete"))
        assertTrue(LearningExperience.title(LearningTrialState(result=LearningResult.SAVED_FOR_LAUNCH)).contains("next launch"))
    }
    @Test fun `manual recording survives completion but learning only capture stops`() {
        assertFalse(LearningExperience.stopLearningCapture(false,LearningTrialState()))
        assertFalse(LearningExperience.stopLearningCapture(true,LearningTrialState(active=true)))
        assertTrue(LearningExperience.stopLearningCapture(true,LearningTrialState(result=LearningResult.APPLIED)))
        assertTrue(LearningExperience.stopLearningCapture(true,LearningTrialState(recoveryRequired=true)))
    }
    @Test fun `recovery takes priority over an old success result`() {
        val state=LearningTrialState(recoveryRequired=true,result=LearningResult.APPLIED)
        assertEquals("Your settings need restoring",LearningExperience.title(state))
        assertTrue(LearningExperience.explanation(state).contains("Reconnect"))
    }

}
