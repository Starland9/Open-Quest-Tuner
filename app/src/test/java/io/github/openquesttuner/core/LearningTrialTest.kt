package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LearningTrialTest {
    private val model=QuestModel.QUEST_3
    private val ctx=LearningContext("com.test.game",1,"os","QUEST_3")
    private val base=GameProfile(refreshRate=90,cpuLevel=2,gpuLevel=2)
    private class Store : LearningStore {
        val events=mutableListOf<LearningEvent>()
        override fun event(event: LearningEvent): Boolean { events+=event;return true }
        val values=mutableListOf<LearnedWindow>();var pending:LearningUndo?=null;var writable=true
        override fun windows()=values.toList()
        override fun add(window:LearnedWindow):Boolean { if(!writable)return false;values.removeAll { it.runId==window.runId && it.stage==window.stage };values+=window;return true }
        override fun undo()=pending
        override fun saveUndo(undo:LearningUndo):Boolean { if(writable)pending=undo;return writable }
        override fun clearUndo():Boolean { if(writable)pending=null;return writable }
        override fun invalidate(context:LearningContext,profile:GameProfile):Boolean { values.replaceAll { if(it.context==context && it.profile==profile)it.copy(verifiedTrial=false)else it };return true }
        override fun clear(packageName:String) { values.removeAll { it.context.packageName==packageName } }
    }
    private inner class Device : LearningDevice {
        var pid=123
        var current=base;var boot:String?="boot1";var currentContext=ctx;var reserved=false;var failed=false;var keepSuccess=true
        val changes=mutableListOf<Triple<GameProfile,Boolean,Boolean>>();val kept=mutableListOf<GameProfile>()
        override suspend fun reserve():Boolean { reserved=true;return true }
        override suspend fun release() { reserved=false }
        override suspend fun bootId()=boot
        override suspend fun profile():GameProfile?=if(boot==null)null else current
        override suspend fun apply(profile:GameProfile,relaunch:Boolean,live:Boolean):Boolean {
            changes+=Triple(profile,relaunch,live)
            if(failed)return false
            if(relaunch)pid++
            current=profile;return true
        }
        override fun context(packageName:String)=currentContext
        override suspend fun keep(context:LearningContext,goal:TuneGoal,profile:GameProfile):Boolean { if(keepSuccess)kept+=profile;return keepSuccess }
    }
    private class Clock { var now=100_000L }
    private fun sample(device:Device,clock:Clock,fps:Double=90.0,gpu:Double=5.0,thermal:ThermalLevel=ThermalLevel.NONE,granted:Boolean=true):PerformanceSample =
        PerformanceSample(clock.now,ctx.packageName,90,thermal,device.current.toPropertyValues().mapNotNull { (k,v) -> v?.let { k.key to it.toString() } }.toMap(),
            RuntimeTelemetry(source="VrApi",measuredFps=fps,appGpuMs=gpu,staleFrames=0,reportedRefreshRate=90,capturedAtMs=clock.now,cpuPercent=30.0,gpuPercent=40.0,cpuLevel=if(granted)device.current.cpuLevel else 0,gpuLevel=device.current.gpuLevel,processId=device.pid,observedFoveation=device.current.foveationLevel?.code ?: 2,observedEyeTexture=device.current.eyeTexture ?: model.defaultEyeTexture,renderCapturedAtMs=clock.now,renderProcessId=device.pid,renderSource="Native runtime",renderRefreshRate=90),readbackVerified=true)
    private suspend fun phase(trial:LearningTrial,device:Device,clock:Clock,gpu:Double=5.0) {
        repeat(24) { if(trial.state.value.active && !trial.state.value.waitingRelaunch) { clock.now+=2000;trial.tick(sample(device,clock,gpu=gpu)) } }
    }
    @Test fun `invalid settling duration does not reserve or change settings`() = runTest {
        val d=Device();val s=Store();val t=LearningTrial(d,s,model)
        t.start(ctx,LearningConfig(warmupSeconds=1))
        assertFalse(t.state.value.active);assertFalse(d.reserved);assertNull(s.pending);assertTrue(d.changes.isEmpty())
    }
    @Test fun `selected settling duration excludes loading samples before measurement`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,warmupSeconds=60));t.tick(sample(d,c))
        repeat(29) { c.now+=2000;t.tick(sample(d,c)) }
        assertEquals("Warm-up",t.state.value.phase);assertTrue(s.values.isEmpty());assertTrue(d.changes.isEmpty())
        repeat(16) { c.now+=2000;t.tick(sample(d,c)) }
        assertEquals(1,s.values.size);assertTrue(s.values.single().startedAt>=160_000L)
        t.stop()
    }
    @Test fun `overlay distinguishes property request from confirmed runtime levels and journals both`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));phase(t,d,c)
        assertTrue(t.state.value.computeChange);assertFalse(t.state.value.runtimeConfirmed)
        val requested=s.events.first { it.message=="Requested settings change" }
        assertEquals(d.current,requested.profile);assertNull(requested.observedCpu)
        c.now+=2000;t.tick(sample(d,c));assertFalse(t.state.value.runtimeConfirmed)
        repeat(8) { c.now+=2000;t.tick(sample(d,c)) }
        assertTrue(t.state.value.runtimeConfirmed);assertEquals(d.current.cpuLevel,t.state.value.observedCpu)
        val confirmed=s.events.single { it.message=="Runtime CPU/GPU levels confirmed" }
        assertTrue(confirmed.timestampMs>requested.timestampMs);assertEquals(d.current.cpuLevel,confirmed.observedCpu)
        assertTrue(t.state.value.completedWindows>=1);assertTrue(t.state.value.totalWindows!!>=5)
        t.stop();assertEquals(LearningResult.STOPPED,t.state.value.result)
    }
    @Test fun `restart action cannot restart a live run or duplicate a guided restart`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));t.launchPhase();assertTrue(d.changes.isEmpty());t.stop()
        t.start(ctx,LearningConfig(seconds=30,mode=LearningMode.RELAUNCH));phase(t,d,c)
        assertTrue(t.state.value.waitingRelaunch);t.launchPhase();val count=d.changes.size;t.launchPhase()
        assertEquals(count,d.changes.size);t.stop()
    }
    @Test fun `guided baseline missing real buffers stops before any candidate writes`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,mode=LearningMode.RELAUNCH))
        repeat(30) { if(t.state.value.active) { c.now+=2000;val row=sample(d,c);t.tick(row.copy(telemetry=row.telemetry.copy(observedEyeTexture=null,renderSource=null))) } }
        assertEquals(LearningResult.UNAVAILABLE,t.state.value.result);assertTrue(d.changes.isEmpty());assertTrue(s.values.isEmpty())
    }
    @Test fun `successful property readback with ignored resolution never passes candidate`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,mode=LearningMode.RELAUNCH,goal=TuneGoal.QUALITY,autoKeep=true));phase(t,d,c)
        assertTrue(t.state.value.waitingRelaunch);t.launchPhase();assertNotNull(d.current.eyeTexture)
        repeat(30) { if(t.state.value.active) { c.now+=2000;val row=sample(d,c);t.tick(row.copy(telemetry=row.telemetry.copy(observedEyeTexture=model.defaultEyeTexture))) } }
        assertEquals(LearningResult.UNAVAILABLE,t.state.value.result);assertEquals(base,d.current);assertTrue(d.kept.isEmpty());assertTrue(s.values.none { it.verifiedTrial })
    }
    @Test fun `display rate must match independently of matching FPS and property readback`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));repeat(30) { if(t.state.value.active) { c.now+=2000;val row=sample(d,c);t.tick(row.copy(telemetry=row.telemetry.copy(reportedRefreshRate=72,measuredFps=72.0))) } }
        assertEquals(LearningResult.UNAVAILABLE,t.state.value.result);assertTrue(d.changes.isEmpty());assertTrue(s.values.isEmpty())
    }
    @Test fun `render confirmation is journalled only after fresh matching measurements`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,mode=LearningMode.RELAUNCH));repeat(10) { c.now+=2000;t.tick(sample(d,c)) }
        assertTrue(t.state.value.renderConfirmed);assertEquals(model.defaultEyeTexture,t.state.value.observedEyeTexture)
        val event=s.events.single { it.message=="Resolution and refresh confirmed" }
        assertEquals(model.defaultEyeTexture,event.observedEyeTexture);assertEquals(90,event.observedHz)
        t.stop()
    }
    @Test fun `start saves undo before any changes and stop restores original`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));assertTrue(t.state.value.active);assertNotNull(s.pending);assertTrue(d.changes.isEmpty())
        t.stop();assertFalse(t.state.value.active);assertEquals(base,d.current);assertNull(s.pending);assertFalse(d.reserved)
    }
    @Test fun `failed durable undo prevents test and all mutations`() = runTest {
        val d=Device();val s=Store();s.writable=false;val t=LearningTrial(d,s,model)
        t.start(ctx,LearningConfig());assertFalse(t.state.value.active);assertTrue(d.changes.isEmpty());assertFalse(d.reserved)
    }
    @Test fun `live phases only request compute writes and observe warmup`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));t.tick(sample(d,c));c.now+=10_000;t.tick(sample(d,c));assertTrue(s.values.isEmpty())
        phase(t,d,c);assertTrue(d.changes.any { it.third });assertTrue(d.changes.none { it.second })
        t.stop();assertEquals(base,d.current)
    }
    @Test fun `unconfirmed runtime levels cannot pass a candidate`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));phase(t,d,c)
        repeat(24) { if(t.state.value.active) { c.now+=2000;t.tick(sample(d,c,granted=false)) } }
        assertFalse(t.state.value.active);assertEquals(base,d.current);assertTrue(s.values.none { it.verifiedTrial })
    }
    @Test fun `guided phases wait for explicit relaunch and do not kill game silently`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,mode=LearningMode.RELAUNCH));phase(t,d,c)
        assertTrue(t.state.value.waitingRelaunch);assertTrue(d.changes.isEmpty())
        t.launchPhase();assertTrue(d.changes.last().second);assertFalse(t.state.value.waitingRelaunch)
        t.stop();assertEquals(base,d.current)
    }
    @Test fun `thermal event stops test and never keeps a winner`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,autoKeep=true));t.tick(sample(d,c,thermal=ThermalLevel.MODERATE))
        assertFalse(t.state.value.active);assertTrue(d.kept.isEmpty());assertEquals(base,d.current)
    }
    @Test fun `game switch and changed version stop without promotion`() = runTest {
        for(changeVersion in listOf(false,true)) {
            val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
            t.start(ctx,LearningConfig(seconds=30));t.tick(sample(d,c));c.now+=2000
            if(changeVersion)d.currentContext=ctx.copy(appVersion=2)
            t.tick(if(changeVersion)sample(d,c)else sample(d,c).copy(packageName="com.other.app"))
            assertFalse(t.state.value.active);assertTrue(s.values.none { it.verifiedTrial });assertTrue(d.kept.isEmpty())
        }
    }
    @Test fun `lost connection retains undo and reconnect can restore`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig());d.boot=null;t.stop();assertTrue(t.state.value.recoveryRequired);assertNotNull(s.pending)
        d.boot="boot1";t.recover();assertFalse(t.state.value.recoveryRequired);assertNull(s.pending)
    }
    @Test fun `external settings are preserved until user resolves recovery`() = runTest {
        val d=Device();val s=Store();val t=LearningTrial(d,s,model)
        t.start(ctx,LearningConfig());d.current=base.copy(refreshRate=72);t.stop()
        assertEquals(72,d.current.refreshRate);assertTrue(t.state.value.recoveryRequired);assertNotNull(s.pending)
        t.discardRecovery();assertNull(s.pending);assertFalse(d.reserved)
    }
    @Test fun `process recreation restores original from durable undo without resuming test`() = runTest {
        val d=Device();val s=Store();s.pending=LearningUndo("boot1",ctx,base,base.copy(cpuLevel=3),"run");d.current=base.copy(cpuLevel=3)
        val recreated=LearningTrial(d,s,model);recreated.recover()
        assertEquals(base,d.current);assertFalse(recreated.state.value.active);assertNull(s.pending)
    }
    @Test fun `new boot invalidates old undo without applying stale properties`() = runTest {
        val d=Device();val s=Store();s.pending=LearningUndo("old",ctx,base,base,"run");d.boot="new"
        val t=LearningTrial(d,s,model);t.recover();assertTrue(d.changes.isEmpty());assertNull(s.pending)
    }
    @Test fun `full repeated live test saves evidence and opt out restores baseline`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30))
        repeat(600) { if(t.state.value.active) { c.now+=2000;t.tick(sample(d,c,gpu=if(d.current.cpuLevel==3)4.0 else 5.0)) } }
        assertFalse(t.state.value.active);assertTrue(s.values.count { it.verifiedTrial }>=5);assertTrue(d.kept.isEmpty());assertEquals(base,d.current)
        assertTrue(GameLearning.recommendations(ctx,s.values,model,false).isNotEmpty())
    }
    @Test fun `automatic keep requires repeatable measured improvement`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,goal=TuneGoal.PERFORMANCE,autoKeep=true))
        repeat(600) { if(t.state.value.active) { c.now+=2000;t.tick(sample(d,c,gpu=if(d.current.cpuLevel==3)2.0 else 5.0)) } }
        assertFalse(t.state.value.active);assertEquals(1,d.kept.size);assertEquals(d.kept.single(),d.current);assertEquals(LearningResult.APPLIED,t.state.value.result)
    }
    @Test fun `scene drift never promotes candidate windows or automatically keeps it`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,autoKeep=true))
        repeat(600) { if(t.state.value.active) { c.now+=2000;val gpu=if(t.state.value.stage>=3 && d.current==base)8.0 else 5.0;t.tick(sample(d,c,gpu=gpu)) } }
        assertTrue(d.kept.isEmpty());assertEquals(base,d.current)
        assertTrue(s.values.filter { it.stage.startsWith("0:") }.none { it.verifiedTrial });assertEquals(LearningResult.INCONCLUSIVE,t.state.value.result)
    }

    @Test fun `severe frame regression restores before completing a candidate window`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));phase(t,d,c)
        repeat(15) { if(t.state.value.active) { c.now+=2000;t.tick(sample(d,c,fps=20.0)) } }
        assertFalse(t.state.value.active);assertEquals(base,d.current);assertTrue(d.kept.isEmpty())
    }
    @Test fun `duplicate or missing VR data never finishes measurement`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));t.tick(sample(d,c))
        val old=sample(d,c)
        repeat(30) { if(t.state.value.active) { c.now+=2000;t.tick(old.copy(timestampMs=c.now)) } }
        assertFalse(t.state.value.active);assertTrue(s.values.isEmpty());assertTrue(d.kept.isEmpty())
    }
    @Test fun `guided automatic keep saves for next launch and leaves current baseline active`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30,mode=LearningMode.RELAUNCH,goal=TuneGoal.QUALITY,autoKeep=true))
        repeat(600) {
            if(t.state.value.active) {
                if(t.state.value.waitingRelaunch)t.launchPhase()
                c.now+=2000;t.tick(sample(d,c))
            }
        }
        assertFalse(t.state.value.active);assertTrue(d.kept.isNotEmpty());assertEquals(base,d.current)
        assertTrue(d.changes.any { it.second });assertNull(s.pending);assertEquals(LearningResult.SAVED_FOR_LAUNCH,t.state.value.result)
    }
    @Test fun `partial writes can restore from original previous and target known values`() = runTest {
        val d=Device();val s=Store();val previous=base.copy(cpuLevel=3);val target=base.copy(gpuLevel=3)
        s.pending=LearningUndo("boot1",ctx,base,target,"partial",previous)
        d.current=base.copy(cpuLevel=3,gpuLevel=3)
        val t=LearningTrial(d,s,model);t.recover();assertEquals(base,d.current);assertNull(s.pending)
    }
    @Test fun `failed restore cannot clear durable undo or release reservation`() = runTest {
        val d=Device();val s=Store();s.pending=LearningUndo("boot1",ctx,base,base.copy(cpuLevel=3),"failed")
        d.current=base.copy(cpuLevel=3);d.failed=true
        val t=LearningTrial(d,s,model);t.recover();assertNotNull(s.pending);assertTrue(t.state.value.recoveryRequired);assertTrue(d.reserved)
        d.failed=false;t.recover();assertNull(s.pending);assertFalse(d.reserved)
    }

    @Test fun `process restart mid measurement stops test without promoting evidence`() = runTest {
        val d=Device();val s=Store();val c=Clock();val t=LearningTrial(d,s,model){c.now}
        t.start(ctx,LearningConfig(seconds=30));repeat(12) { c.now+=2000;t.tick(sample(d,c)) }
        d.pid++;c.now+=2000;t.tick(sample(d,c))
        assertFalse(t.state.value.active);assertTrue(s.values.none { it.verifiedTrial });assertEquals(base,d.current)
    }
}
