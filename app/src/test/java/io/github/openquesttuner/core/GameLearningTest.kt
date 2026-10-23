package io.github.openquesttuner.core

import org.junit.Assert.*
import org.junit.Test

class GameLearningTest {
    private val ctx=LearningContext("com.test.game",1,"build1","QUEST_3")
    private val profile=GameProfile(90,EyeTexture(1680,1760),2,2,FoveationLevel.MEDIUM,true)
    private fun samples(fps:Double=90.0,gpu:Double=5.0,stale:Int=0,thermal:ThermalLevel=ThermalLevel.NONE,half:Boolean=false):List<PerformanceSample> = (0..30).map { i ->
        val at=100_000L+i*2000
        PerformanceSample(at,ctx.packageName,90,thermal,profile.toPropertyValues().mapKeys { it.key.key }.mapValues { it.value.toString() },
            RuntimeTelemetry(source="VrApi",measuredFps=if(half)45.0 else fps,appGpuMs=gpu,staleFrames=stale,reportedRefreshRate=90,capturedAtMs=at,cpuPercent=30.0,gpuPercent=40.0,cpuLevel=2,gpuLevel=2,processId=123,observedEyeTexture=profile.eyeTexture,renderCapturedAtMs=at,renderProcessId=123,renderSource="Native runtime",renderRefreshRate=90),readbackVerified=true)
    }
    private fun summary(rows:List<PerformanceSample> = samples(), config:LearningConfig=LearningConfig(),verified:Boolean=true)=GameLearning.summarize(ctx,profile,"run1","B1",rows,config,verified)
    @Test fun `legacy override only evidence cannot produce visual recommendations`() {
        val old=summary()!!.copy(renderVerified=false,observedEyeTexture=null)
        val rec=GameLearning.recommendations(ctx,listOf(old,old.copy(stage="B3")),QuestModel.QUEST_3,false)
        assertFalse(rec.any { it.goal==TuneGoal.QUALITY || it.goal==TuneGoal.HD_PLUS })
        assertTrue(rec.any { it.goal==TuneGoal.PERFORMANCE })
    }
    @Test fun `guided windows require exact fresh stable actual eye buffers and refresh`() {
        val config=LearningConfig(mode=LearningMode.RELAUNCH)
        assertNotNull(summary(config=config))
        val base=samples()
        for(t in listOf(base[0].telemetry.copy(observedEyeTexture=null),base[0].telemetry.copy(observedEyeTexture=EyeTexture(512,512)),base[0].telemetry.copy(renderSource=null),base[0].telemetry.copy(renderProcessId=999),base[0].telemetry.copy(renderCapturedAtMs=1),base[0].telemetry.copy(renderRefreshRate=72))) {
            assertNull(summary(base.mapIndexed { i,s -> if(i==0)s.copy(telemetry=t)else s },config))
        }
    }
    @Test fun `measured default buffers must match between repeated controls`() {
        val a=summary()!!
        assertFalse(GameLearning.repeatable(a,a.copy(observedEyeTexture=EyeTexture(512,512))))
    }
    @Test fun `valid full minute VR window records real performance`() {
        val w=summary()!!;assertEquals(31,w.count);assertEquals(90.0,w.fpsP10,0.0);assertEquals(1.0,w.compliance,0.0);assertTrue(w.healthy)
    }
    @Test fun `short windows cannot pass by sample count alone`() { assertNull(summary(samples().take(20))) }
    @Test fun `replayed timestamps cannot inflate evidence`() { val s=samples()[0];assertNull(summary(List(100){s})) }
    @Test fun `Android UI timing never counts as VR evidence`() { assertNull(summary(samples().map { it.copy(telemetry=it.telemetry.copy(source="Android UI",frameCount=50,p90FrameMs=1.0)) })) }
    @Test fun `stale future missing and non finite metrics rejected`() {
        val s=samples()[0]
        for(t in listOf(s.telemetry.copy(capturedAtMs=s.timestampMs-6001),s.telemetry.copy(capturedAtMs=s.timestampMs+1),s.telemetry.copy(appGpuMs=null),s.telemetry.copy(measuredFps=Double.NaN),s.telemetry.copy(appGpuMs=Double.POSITIVE_INFINITY),s.telemetry.copy(staleFrames=null))) assertFalse(GameLearning.usable(s.copy(telemetry=t),ctx.packageName,s.timestampMs))
    }
    @Test fun `wrong package properties rate and discontinuous windows rejected`() {
        assertNull(summary(samples().map { it.copy(packageName="com.other.game") }))
        assertNull(summary(samples().map { it.copy(activeProperties=emptyMap()) }))
        assertNull(summary(samples().mapIndexed { i,s -> if(i==5)s.copy(telemetry=s.telemetry.copy(reportedRefreshRate=72))else s }))
        assertNull(summary(samples().filterIndexed { i,_ -> i !in 10..14 }))
    }
    @Test fun `half rate requires explicit user mode and never inferred from low fps`() {
        assertFalse(summary(samples(half=true))!!.healthy)
        assertTrue(summary(samples(half=true),LearningConfig(halfRate=true))!!.healthy)
    }
    @Test fun `average fps cannot hide repeated frame misses`() {
        val rows=samples().mapIndexed { i,s -> if(i%4==0)s.copy(telemetry=s.telemetry.copy(measuredFps=60.0))else s }
        assertFalse(summary(rows)!!.healthy)
    }
    @Test fun `GPU budget stale frames and thermals gate eligibility`() {
        assertFalse(summary(samples(gpu=12.0))!!.healthy)
        assertFalse(summary(samples(stale=2))!!.healthy)
        assertFalse(summary(samples(thermal=ThermalLevel.MODERATE))!!.healthy)
    }
    @Test fun `workload drift or changed context makes controls incomparable`() {
        val a=summary()!!;assertTrue(GameLearning.repeatable(a,a.copy(stage="A2")))
        assertFalse(GameLearning.repeatable(a,a.copy(gpuP95=8.0)))
        assertFalse(GameLearning.repeatable(a,a.copy(fpsMean=80.0)))
        assertFalse(GameLearning.repeatable(a,a.copy(context=ctx.copy(appVersion=2))))
        assertFalse(GameLearning.repeatable(a,a.copy(hz=72)))
        assertFalse(GameLearning.repeatable(a,a.copy(halfRate=true)))
    }
    @Test fun `observational windows alone never generate tested recommendation`() {
        val a=summary(verified=false)!!
        assertTrue(GameLearning.recommendations(ctx,listOf(a,a.copy(stage="B2")),QuestModel.QUEST_3,false).isEmpty())
    }
    @Test fun `one test window and duplicate identity cannot produce recommendation`() {
        val a=summary()!!
        assertTrue(GameLearning.recommendations(ctx,listOf(a),QuestModel.QUEST_3,false).isEmpty())
        assertTrue(GameLearning.recommendations(ctx,listOf(a,a),QuestModel.QUEST_3,false).isEmpty())
    }
    @Test fun `two repeated healthy windows yield goal recommendations with evidence`() {
        val a=summary()!!
        val rec=GameLearning.recommendations(ctx,listOf(a,a.copy(stage="B3")),QuestModel.QUEST_3,false)
        assertEquals(5,rec.size);assertTrue(rec.all { it.windows==2 && it.observedSeconds==120 && it.profile==profile })
    }
    @Test fun `game version OS headset and half rate histories never mix`() {
        val a=summary()!!
        for(other in listOf(a.copy(context=ctx.copy(appVersion=2)),a.copy(context=ctx.copy(systemBuild="build2")),a.copy(context=ctx.copy(headset="QUEST_2")),a.copy(halfRate=true)))
            assertTrue(GameLearning.recommendations(ctx,listOf(a,other.copy(stage="B3")),QuestModel.QUEST_3,false).isEmpty())
    }
    @Test fun `battery goal requires observed load and cannot claim measured battery life`() {
        val a=summary()!!.copy(cpuMean=null)
        val rec=GameLearning.recommendations(ctx,listOf(a,a.copy(stage="B3")),QuestModel.QUEST_3,false)
        assertFalse(rec.any { it.goal==TuneGoal.BATTERY })
    }
    @Test fun `bad verified window disqualifies otherwise healthy profile`() {
        val a=summary()!!
        val rec=GameLearning.recommendations(ctx,listOf(a,a.copy(stage="B3"),a.copy(stage="bad",thermalPercent=20.0)),QuestModel.QUEST_3,false)
        assertTrue(rec.isEmpty())
    }
    @Test fun `profile reader preserves default zero off and rejects malformed pairs`() {
        assertEquals(GameProfile(),GameLearning.profile(emptyMap()))
        val p=GameProfile(cpuLevel=0,dynamicFoveation=false)
        assertEquals(p,GameLearning.profile(mapOf(QuestProperty.CPU_LEVEL.key to "0",QuestProperty.FOVEATION_DYNAMIC.key to "0")))
        assertNull(GameLearning.profile(mapOf(QuestProperty.TEXTURE_WIDTH.key to "2048")))
        assertNull(GameLearning.profile(mapOf(QuestProperty.CPU_LEVEL.key to "oops")))
        assertNull(GameLearning.profile(mapOf(QuestProperty.FOVEATION_DYNAMIC.key to "9")))
    }
    @Test fun `live candidates change a single compute setting only and stay in model ranges`() {
        for(model in QuestModel.entries) for(goal in TuneGoal.entries) {
            for(candidate in GameLearning.liveCandidate(profile,model,goal,2,2)) {
                val changed=candidate.toPropertyValues().filter { (k,v) -> profile.toPropertyValues()[k]!=v }.keys
                assertEquals(1,changed.size);assertTrue(changed.single() in listOf(QuestProperty.CPU_LEVEL,QuestProperty.GPU_LEVEL));assertTrue(candidate.validateFor(model).isEmpty())
            }
        }
    }
    @Test fun `guided candidates remain bounded and valid`() {
        for(model in QuestModel.entries) for(goal in TuneGoal.entries) assertTrue(GameLearning.restartCandidates(GameProfile(),model,goal).all { it.validateFor(model).isEmpty() })
        assertFalse(LearningConfig(seconds=0).valid());assertFalse(LearningConfig(seconds=600).valid())
    }

    @Test fun `missing property readback process identity and mixed processes cannot learn`() {
        assertNull(summary(samples().map { it.copy(readbackVerified=false) }))
        assertNull(summary(samples().map { it.copy(telemetry=it.telemetry.copy(processId=null)) }))
        assertNull(summary(samples().mapIndexed { i,s -> s.copy(telemetry=s.telemetry.copy(processId=if(i<15)123 else 456)) }))
    }

    @Test fun `native fps in half rate mode cannot manufacture a larger GPU budget`() {
        assertNull(summary(samples(),LearningConfig(halfRate=true)))
        assertNull(summary(samples(fps=200.0)))
    }

    @Test fun `passive logging cannot evict tested recommendations`() {
        val a=summary()!!
        val tested=listOf(a,a.copy(stage="B3"))
        val passive=(1..500).map { a.copy(runId="passive$it",endedAt=a.endedAt+it,verifiedTrial=false) }
        val kept=GameLearning.retain(tested+passive)
        assertEquals(2,kept.count { it.verifiedTrial });assertEquals(12,kept.count { !it.verifiedTrial })
        assertEquals(5,GameLearning.recommendations(ctx,kept,QuestModel.QUEST_3,false).size)
    }
    @Test fun `repeated baseline windows cannot crowd out other tested profiles`() {
        val a=summary()!!
        val another=a.copy(profile=profile.copy(cpuLevel=3),stage="other1")
        val rows=(1..100).map { a.copy(stage="A$it",endedAt=a.endedAt+it) }+listOf(another,another.copy(stage="other2"))
        val kept=GameLearning.retain(rows)
        assertEquals(4,kept.count { it.profile==profile });assertEquals(2,kept.count { it.profile!=profile })
    }
    @Test fun `78 game library retains per game measured evidence`() {
        val a=summary()!!
        val rows=(0 until 78).flatMap { index ->
            listOf(a.copy(context=ctx.copy(packageName="com.game.g$index")),a.copy(context=ctx.copy(packageName="com.game.g$index"),stage="B3"))
        }
        val kept=GameLearning.retain(rows)
        assertEquals(156,kept.size);assertEquals(78,kept.groupBy { it.context.packageName }.size)
    }

    @Test fun `guided search responds to measured GPU bottleneck and headroom`() {
        val hot=summary(samples(gpu=11.0))!!
        val down=GameLearning.restartCandidates(profile,QuestModel.QUEST_3,TuneGoal.QUALITY,hot)
        assertTrue(down.first().eyeTexture!!.width<profile.eyeTexture!!.width)
        val healthy=summary()!!
        val up=GameLearning.restartCandidates(profile,QuestModel.QUEST_3,TuneGoal.QUALITY,healthy)
        assertTrue(up.first().eyeTexture!!.width>profile.eyeTexture!!.width)
    }
    @Test fun `frame target recovery takes precedence over cosmetic or compute preferences`() {
        val failed=summary(samples(fps=60.0))!!
        val healthy=summary()!!
        for(goal in TuneGoal.entries) assertTrue(GameLearning.utility(healthy,goal,QuestModel.QUEST_3)>GameLearning.utility(failed,goal,QuestModel.QUEST_3)+1)
    }
}
