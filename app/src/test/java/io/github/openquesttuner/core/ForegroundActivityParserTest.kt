package io.github.openquesttuner.core

import org.junit.Assert.*
import org.junit.Test

class ForegroundActivityParserTest {
    @Test fun resumedActivityWithoutMPrefixIsRecognised() {
        assertEquals("com.Raetae.AIRoommate",ForegroundActivityParser.parse(
            "ResumedActivity: ActivityRecord{abc u0 com.Raetae.AIRoommate/.MainActivity t42}"))
    }
    @Test fun lowerCaseResumedActivityIsRecognised() {
        assertEquals("com.test.game",ForegroundActivityParser.parse("resumedActivity=ActivityRecord{1 u0 com.test.game/.Game}"))
    }
    @Test fun topPanelWinsOverBackgroundGame() {
        assertEquals("com.oculus.vrshell",ForegroundActivityParser.parse(
            "ResumedActivity: ActivityRecord{1 u0 com.test.game/.Game}\ntopResumedActivity=ActivityRecord{2 u0 com.oculus.vrshell/.Main}"))
    }
    @Test fun ambiguousActivitiesRemainUnknown() {
        assertNull(ForegroundActivityParser.parse("ResumedActivity: ActivityRecord{1 u0 com.test.one/.Game}\nResumedActivity: ActivityRecord{2 u0 com.test.two/.Game}"))
    }
    @Test fun focusedWindowIdentifiesGameWithoutActivityField() {
        assertEquals("com.Raetae.AIRoommate",ForegroundActivityParser.parseWindow("mCurrentFocus=Window{abc u0 com.Raetae.AIRoommate/com.unity3d.player.UnityPlayerActivity}"))
    }
    @Test fun focusedPanelWinsOverFocusedApp() {
        assertEquals("com.oculus.vrshell",ForegroundActivityParser.parseWindow(
            "mCurrentFocus=Window{abc u0 com.oculus.vrshell/.Main}\nmFocusedApp=ActivityRecord{def u0 com.test.game/.Main}"))
    }
    @Test fun nullFocusedWindowCannotFallBackToStaleApp() {
        assertNull(ForegroundActivityParser.parseWindow("mCurrentFocus=null\nmFocusedApp=ActivityRecord{1 u0 com.test.game/.Main}"))
    }
    @Test fun processOrTaskListingAloneCannotIdentifyForeground() {
        assertNull(ForegroundActivityParser.parse("ActivityRecord{1 u0 com.test.game/.Main}"))
        assertNull(ForegroundActivityParser.parseWindow("Window{1 u0 com.test.game/.Main}"))
    }
    @Test fun focusAppFallbackAndDenialDiagnostics() {
        assertEquals("com.test.game",ForegroundActivityParser.parseWindow("mFocusedApp=ActivityRecord{1 u0 com.test.game/.Main}"))
        assertTrue(ForegroundActivityParser.evidence("Permission Denial: dumpsys").contains("Denial"))
    }
}
