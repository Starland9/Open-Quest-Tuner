package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TunerLearningReservationTest {
    @Test fun `reservation blocks automatic apply manual launch and reset without writes`() = runTest {
        val shell=FakeShellBackend();val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        assertTrue(tuner.reserveLearning("test"))
        assertTrue(tuner.applyProfile(GameProfile()) is TuneResult.StepFailed)
        assertTrue(tuner.applyAndLaunch("com.test.game","com.test.Main",GameProfile()) is TuneResult.StepFailed)
        assertEquals(QuestProperty.entries,tuner.resetAll()!!.failed)
        assertTrue(shell.executed.isEmpty())
        assertFalse(tuner.reserveLearning("another"));tuner.releaseLearning("another")
        assertTrue(tuner.applyProfile(GameProfile()) is TuneResult.StepFailed)
        tuner.releaseLearning("test");assertEquals(TuneResult.Success,tuner.applyProfile(GameProfile()))
    }
    @Test fun `live trial can only mutate changed compute overrides`() = runTest {
        val shell=FakeShellBackend();val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        val base=GameProfile(refreshRate=90,cpuLevel=2,gpuLevel=2)
        tuner.applyProfile(base);shell.executed.clear();tuner.reserveLearning("test")
        assertEquals(TuneResult.Success,tuner.applyLearningLive(base.copy(cpuLevel=3),"test"))
        assertEquals(listOf(ShellCommand.setProperty(QuestProperty.CPU_LEVEL,3).text),shell.executed.filter { it.startsWith("setprop") })
        assertTrue(shell.executed.none { it.startsWith("am ") })
    }
    @Test fun `live texture refresh and rendering changes rejected before writes`() = runTest {
        val shell=FakeShellBackend();val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        tuner.reserveLearning("test")
        for(profile in listOf(GameProfile(refreshRate=90),GameProfile(eyeTexture=EyeTexture(2048,2048)),GameProfile(foveationLevel=FoveationLevel.HIGH))) {
            shell.executed.clear();assertTrue(tuner.applyLearningLive(profile,"test") is TuneResult.StepFailed)
            assertTrue(shell.executed.none { it.startsWith("setprop") })
        }
    }
    @Test fun `owner can restore all properties and release cleanly`() = runTest {
        val shell=FakeShellBackend();val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        tuner.reserveLearning("test")
        assertEquals(TuneResult.Success,tuner.applyProfile(GameProfile(cpuLevel=3),"test"))
        assertEquals(TuneResult.Success,tuner.applyProfile(GameProfile(),"test"))
        tuner.releaseLearning("test");assertEquals(TuneResult.Success,tuner.applyProfile(GameProfile()))
    }
    @Test fun `unreserved live writes rejected and ignored writes cannot succeed`() = runTest {
        val shell=FakeShellBackend();val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        assertTrue(tuner.applyLearningLive(GameProfile(cpuLevel=3),"test") is TuneResult.StepFailed);assertTrue(shell.executed.isEmpty())
        tuner.reserveLearning("test");shell.respond("setprop",ShellResult(0,""))
        assertTrue(tuner.applyLearningLive(GameProfile(cpuLevel=3),"test") is TuneResult.StepFailed)
    }
}
