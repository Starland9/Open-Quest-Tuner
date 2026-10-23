package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AutomaticTuningTest {
    private val model=QuestModel.QUEST_3
    private val ctx=LearningContext("com.test.game",1,"build","QUEST_3")
    private val original=GameProfile(refreshRate=90,cpuLevel=2,gpuLevel=3,foveationLevel=FoveationLevel.entries.first { it.code==2 })
    private class Store: LearningStore {
        var pending: LearningUndo?=null
        val rows=mutableListOf<LearnedWindow>();val journal=mutableListOf<LearningEvent>()
        override fun windows()=rows.toList()
        override fun add(window: LearnedWindow): Boolean { rows.removeAll { it.runId==window.runId && it.stage==window.stage };rows+=window;return true }
        override fun event(event: LearningEvent): Boolean { journal+=event;return true }
        override fun undo()=pending
        override fun saveUndo(undo: LearningUndo): Boolean { pending=undo;return true }
        override fun clearUndo(): Boolean {pending=null;return true}
        override fun invalidate(context: LearningContext,profile: GameProfile)=true
        override fun clear(packageName: String) { rows.clear() }
    }
    private inner class Device: LearningDevice {
        var current=original;var pid=12;var now=100_000L;var reserved=false;var saves=0;var failSave=false
        val changes=mutableListOf<Pair<GameProfile,Boolean>>()
        override suspend fun reserve(): Boolean {reserved=true;return true}
        override suspend fun release() {reserved=false}
        override suspend fun bootId()="boot"
        override suspend fun profile()=current
        override suspend fun apply(profile: GameProfile,relaunch: Boolean,live: Boolean): Boolean {
            changes+=profile to relaunch;current=profile;if(relaunch)pid++;return true
        }
        override fun context(packageName: String)=ctx
        override suspend fun keep(context: LearningContext,goal: TuneGoal,profile: GameProfile): Boolean {if(failSave)return false;saves++;return true}
        fun sample(thermal: ThermalLevel=ThermalLevel.NONE,power: Int?=0,gpu: Double?=if(current.cpuLevel==1 && current.gpuLevel==2)4.0 else 5.0,adopted: Boolean=true): PerformanceSample {
            val hz=current.refreshRate ?: 90
            return PerformanceSample(now,ctx.packageName,hz,thermal,current.toPropertyValues().mapNotNull { (k,v)->v?.let { k.key to it.toString() } }.toMap(),
                RuntimeTelemetry(source="VrApi",measuredFps=hz.toDouble(),appGpuMs=gpu,staleFrames=0,reportedRefreshRate=hz,capturedAtMs=now,cpuPercent=30.0,gpuPercent=40.0,
                    cpuLevel=if(adopted)current.cpuLevel else 99,gpuLevel=current.gpuLevel,processId=pid,observedFoveation=current.foveationLevel?.code,powerLevel=power),readbackVerified=true)
        }
    }
    @Test fun `automatic run tests supported changes restarts verifies then saves without manual transitions`() = runTest {
        val d=Device();val s=Store();val t=LearningTrial(d,s,model,{setOf(72,80,90,120)}) {d.now}
        t.start(ctx,AutomaticTuningPolicy.setup())
        var sawFinal=false
        repeat(4000) {
            if(t.state.value.active){d.now+=2000;if(t.state.value.phase=="Verifying final profile"){sawFinal=true;assertEquals(0,d.saves)};t.tick(d.sample())}
        }
        assertTrue(sawFinal);assertFalse(t.state.value.active);assertEquals(LearningResult.APPLIED,t.state.value.result)
        assertEquals(1,d.saves);assertFalse(d.reserved);assertNull(s.pending)
        assertTrue(d.changes.any { it.second });assertEquals(120,d.current.refreshRate)
        assertTrue(s.rows.count { it.verifiedTrial }>=5)
        assertTrue(s.rows.any { it.stage=="final" && AutomaticTuningPolicy.healthy(it) })
        assertTrue(d.changes.none { it.first.eyeTexture!=null })
    }
    @Test fun `missing GPU time is unavailable and cannot save a profile`() = runTest {
        val d=Device();val s=Store();val t=LearningTrial(d,s,model,{setOf(90)}) {d.now}
        t.start(ctx,AutomaticTuningPolicy.setup())
        repeat(70){if(t.state.value.active){d.now+=2000;t.tick(d.sample(gpu=null))}}
        assertEquals(LearningResult.UNAVAILABLE,t.state.value.result);assertEquals(0,d.saves);assertTrue(s.rows.isEmpty());assertEquals(original,d.current)
    }
    @Test fun `thermal danger stops and restores without saving`() = runTest {
        val d=Device();val s=Store();val t=LearningTrial(d,s,model,{setOf(90)}) {d.now}
        t.start(ctx,AutomaticTuningPolicy.setup());repeat(40){d.now+=2000;t.tick(d.sample())}
        d.now+=2000;t.tick(d.sample(power=2))
        assertFalse(t.state.value.active);assertEquals(original,d.current);assertEquals(0,d.saves);assertNull(s.pending)
    }
    @Test fun `unknown safety is not normal`() {
        assertFalse(AutomaticTuningPolicy.safe(RuntimeTelemetry(),ThermalLevel.UNKNOWN))
        assertTrue(AutomaticTuningPolicy.safe(RuntimeTelemetry(powerLevel=0),ThermalLevel.UNKNOWN))
        assertFalse(AutomaticTuningPolicy.safe(RuntimeTelemetry(powerLevel=0),ThermalLevel.SEVERE))
        assertFalse(AutomaticTuningPolicy.safe(RuntimeTelemetry(powerLevel=1),ThermalLevel.NONE))
    }
    @Test fun `sparse observations cannot manufacture 45 valid seconds`() {
        val d=Device()
        val rows=(0..10).map { d.now=100_000L+it*6000;d.sample() }
        assertEquals(30_000L,AutomaticTuningPolicy.coverage(rows))
    }
    @Test fun `failed final runtime adoption never saves`() = runTest {
        val d=Device();val s=Store();val t=LearningTrial(d,s,model,{setOf(90)}) {d.now}
        t.start(ctx,AutomaticTuningPolicy.setup())
        repeat(2000){if(t.state.value.active){d.now+=2000;t.tick(d.sample(adopted=t.state.value.phase!="Verifying final profile"))}}
        assertFalse(t.state.value.active);assertEquals(0,d.saves);assertEquals(original,d.current)
    }
    @Test fun `save failure restores the baseline and remains retryable`() = runTest {
        val d=Device();d.failSave=true;val s=Store();val t=LearningTrial(d,s,model,{setOf(90)}) {d.now}
        t.start(ctx,AutomaticTuningPolicy.setup())
        repeat(2000){if(t.state.value.active){d.now+=2000;t.tick(d.sample())}}
        assertEquals(0,d.saves);assertEquals(original,d.current);assertEquals(LearningResult.REVIEW,t.state.value.result);assertNull(s.pending)
    }
    @Test fun `top Meta panel cannot be overridden by a running game`() {
        assertEquals("com.oculus.vrshell",ForegroundActivityParser.parse("mResumedActivity: ActivityRecord{123 u0 com.test.game/.Main}\ntopResumedActivity=ActivityRecord{456 u0 com.oculus.vrshell/.Main}"))
        assertNull(ForegroundActivityParser.parse("mResumedActivity: ActivityRecord{123 u0 com.test.game/.Main}\nmResumedActivity: ActivityRecord{456 u0 io.github.openquesttuner/.Main}"))
    }
    @Test fun `native render evidence must be fresh and correctly attributed`() {
        val t=RuntimeTelemetry(processId=12,renderProcessId=12,renderSource="Native runtime",renderCapturedAtMs=100_000,observedEyeTexture=EyeTexture(2000,2000),reportedRefreshRate=90)
        assertTrue(RenderVerification.matches(GameProfile(eyeTexture=EyeTexture(2000,2000)),t,100_001))
        assertFalse(RenderVerification.matches(GameProfile(),t.copy(renderProcessId=15),100_001))
        assertFalse(RenderVerification.matches(GameProfile(),t,107_000))
        assertFalse(RenderVerification.matches(GameProfile(eyeTexture=EyeTexture(2100,2100)),t,100_001))
    }
    @Test fun `power state is parsed from the selected native PID`() {
        val now=1_700_000_000_000L
        val line="1700000000.000 123 456 I VrApi : FPS=90/90,App=5ms,Stale=0,PLS=2"
        assertEquals(2,VrTelemetryParser.latest(line,setOf(123),now)!!.powerLevel)
        assertNull(VrTelemetryParser.latest(line,setOf(124),now))
    }
    @Test fun `unknown foreground receives grace but cannot contribute measurements`() = runTest {
        val d=Device();val s=Store();val t=LearningTrial(d,s,model,{setOf(90)}) {d.now}
        t.start(ctx,AutomaticTuningPolicy.setup());t.tick(d.sample());d.now+=2000;t.tick(null)
        assertTrue(t.state.value.active);assertTrue(s.rows.isEmpty())
        d.now+=21_000;t.tick(null)
        assertFalse(t.state.value.active);assertEquals(0,d.saves);assertEquals(original,d.current)
    }

}
