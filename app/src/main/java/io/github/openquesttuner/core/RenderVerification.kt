package io.github.openquesttuner.core

object RenderVerification {
    fun fresh(t: RuntimeTelemetry,now: Long)=t.processId?.let { it>0 }==true && t.renderSource=="Native runtime" && t.renderCapturedAtMs?.let { now-it in 0..6000 }==true && t.renderProcessId==t.processId && t.observedEyeTexture!=null
    fun matches(profile: GameProfile,t: RuntimeTelemetry,now: Long): Boolean = fresh(t,now) &&
        (profile.eyeTexture==null || profile.eyeTexture==t.observedEyeTexture) &&
        (profile.refreshRate==null || profile.refreshRate==t.reportedRefreshRate) &&
        (t.renderRefreshRate==null || t.renderRefreshRate==t.reportedRefreshRate)
}
