package io.github.openquesttuner.core
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TunerForegroundTest {
    @Test fun windowFallbackReachesGameWhenActivityFormatIsUnavailable()=runTest {
        val shell=FakeShellBackend()
        shell.respond("dumpsys activity",ShellResult(0,"No resumed marker"))
        shell.respond("dumpsys window",ShellResult(0,"mCurrentFocus=Window{1 u0 com.Raetae.AIRoommate/.Main}"))
        val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        assertEquals("com.Raetae.AIRoommate",tuner.readForegroundPackage(setOf("com.Raetae.AIRoommate")))
        assertTrue(tuner.foregroundEvidence.contains("No recognised"))
        assertTrue(shell.executed.any { it.startsWith("dumpsys window windows") })
    }
    @Test fun preferredGameNeverOverridesExplicitMetaPanel()=runTest {
        val shell=FakeShellBackend()
        shell.respond("dumpsys activity",ShellResult(0,"topResumedActivity=ActivityRecord{1 u0 com.oculus.vrshell/.Main}"))
        shell.respond("dumpsys window",ShellResult(0,"mCurrentFocus=Window{1 u0 com.test.game/.Main}"))
        val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        assertEquals("com.oculus.vrshell",tuner.readForegroundPackage(setOf("com.test.game")))
        assertFalse(shell.executed.any { it.startsWith("dumpsys window windows") })
    }
    @Test fun failedQueriesRemainUnknownAndExplainWhy()=runTest {
        val shell=FakeShellBackend()
        shell.respond("dumpsys activity",ShellResult(1,"Permission Denial: activity"))
        shell.respond("dumpsys window",ShellResult(1,"Permission Denial: window"))
        val tuner=Tuner(shell,QuestModel.QUEST_3,FakeDisplayRates())
        assertNull(tuner.readForegroundPackage())
        assertTrue(tuner.foregroundEvidence.contains("Permission Denial"))
        assertTrue(tuner.foregroundEvidence.contains("success=false"))
    }
}
