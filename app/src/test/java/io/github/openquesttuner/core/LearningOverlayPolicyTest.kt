package io.github.openquesttuner.core

import org.junit.Assert.*
import org.junit.Test

class LearningOverlayPolicyTest {
    private val state=LearningTrialState(packageName="com.test.game",active=true,phase="Measuring",stage=2,
        requestedProfile=GameProfile(cpuLevel=3,gpuLevel=2),observedCpu=3,observedGpu=2,
        countersAt=10_000,computeChange=true,runtimeConfirmed=true,remainingSeconds=24)
    @Test fun `render confirmation needs fresh render and native counters`() {
        val render=state.copy(renderChecksRequired=true,renderConfirmed=true,observedEyeTexture=EyeTexture(2000,2000),observedHz=90,renderCountersAt=10_000)
        val hud=LearningOverlayPolicy.frame(render,true,true,10_000)!!
        assertTrue(hud.confirmed);assertTrue(hud.lines.contains("Resolution / refresh confirmed"))
        assertTrue(hud.lines.any { it.contains("2000×2000") })
        assertFalse(LearningOverlayPolicy.frame(render.copy(renderCountersAt=1),true,true,10_000)!!.confirmed)
        assertFalse(LearningOverlayPolicy.frame(render.copy(renderConfirmed=false),true,true,10_000)!!.confirmed)
    }
    @Test fun `off or missing permission never produces a window`() {
        assertNull(LearningOverlayPolicy.frame(state,false,true,10_000))
        assertNull(LearningOverlayPolicy.frame(state,true,false,10_000))
    }
    @Test fun `ordinary logging and completed tuning never display overlay`() {
        assertNull(LearningOverlayPolicy.frame(state.copy(active=false),true,true,10_000))
        assertNull(LearningOverlayPolicy.frame(LearningTrialState(result=LearningResult.APPLIED),true,true,10_000))
    }
    @Test fun `property request alone is never labelled confirmed`() {
        val frame=LearningOverlayPolicy.frame(state.copy(runtimeConfirmed=false),true,true,10_000)!!
        assertFalse(frame.confirmed);assertTrue(frame.lines.contains("Change not yet confirmed"))
        assertTrue(frame.lines.any { it.contains("CPU 3") && it.startsWith("Requested") })
    }
    @Test fun `fresh observed levels show confirmed independently of percentages`() {
        val frame=LearningOverlayPolicy.frame(state,true,true,12_000)!!
        assertTrue(frame.confirmed);assertTrue(frame.lines.contains("CPU/GPU change confirmed"))
        assertTrue(frame.lines.contains("24 seconds in this step"))
    }
    @Test fun `stale future or missing counter timestamps remove confirmation`() {
        for(at in listOf(3_999L,10_001L,null)) {
            val frame=LearningOverlayPolicy.frame(state.copy(countersAt=at),true,true,10_000)!!
            assertFalse(frame.confirmed);assertTrue(frame.lines.contains("Waiting for fresh game counters"))
        }
    }
    @Test fun `render only and baseline phases do not claim runtime compute adoption`() {
        val frame=LearningOverlayPolicy.frame(state.copy(computeChange=false),true,true,10_000)!!
        assertFalse(frame.confirmed);assertFalse(frame.lines.contains("CPU/GPU change confirmed"))
    }
}
