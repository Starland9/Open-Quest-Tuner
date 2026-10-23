package io.github.openquesttuner.monitor

import android.app.Activity
import android.content.Context
import android.graphics.*
import io.github.openquesttuner.core.*
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

object NativeHudBridge {
    val available=runCatching { System.loadLibrary("openxr_loader");System.loadLibrary("oqt_hud");true }.getOrDefault(false)
    @JvmStatic external fun run(context: Context,activity: Activity,callback: NativeImmersiveHud,width: Int,height: Int)
    @JvmStatic external fun reset()
    @JvmStatic external fun stop()
    @JvmStatic external fun pixels(data: IntArray)
}
/** Native-session experiment only. Failure never creates an Android panel or another VR app. */
class NativeImmersiveHud(private val context: Context,private val publish: (String)->Unit) {
    private val prefs=context.getSharedPreferences("native_hud_diagnostics_v1",Context.MODE_PRIVATE)
    private var lastStatus=prefs.getString("last","Not checked") ?: "Not checked"
    init { publish(lastStatus) }
    @Synchronized private fun report(message: String) {
        if(message==lastStatus)return
        lastStatus=message
        val history=(prefs.getString("history","")!!.lineSequence().filter { it.isNotBlank() }.toList()+"${System.currentTimeMillis()} $message").takeLast(24).joinToString("\n")
        prefs.edit().putString("last",message).putString("history",history).commit()
        publish(message)
    }
    fun diagnostics()=prefs.getString("history","").orEmpty()
    private val worker=Executors.newSingleThreadExecutor()
    private var host=WeakReference<Activity>(null)
    @Volatile private var running=false
    private var attempted=false
    private var seenGame=false
    private val bitmap=Bitmap.createBitmap(768,384,Bitmap.Config.ARGB_8888)
    private val canvas=Canvas(bitmap)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL);textSize=25f }
    private val data=IntArray(768*384)
    fun host(activity: Activity) { host=WeakReference(activity) }
    // Called from the JNI worker. Publishing status does not launch an Activity.
    @androidx.annotation.Keep
    @Suppress("unused") fun onNativeStatus(code: Int,message: String) { report(message) }
    fun update(state: LearningTrialState,enabled: Boolean,sample: PerformanceSample?,foreground: String?,evidence: String = "") {
        val completion=state.result==LearningResult.APPLIED && state.finishedAtMs>0 && System.currentTimeMillis()-state.finishedAtMs in 0..8000
        if((!state.active && !completion) || !enabled) {
            if(running && NativeHudBridge.available) NativeHudBridge.stop()
            if(!state.active) { attempted=false;seenGame=false }
            if(!enabled) report("Immersive HUD disabled; Auto Tune continues without it")
            return
        }
        val game=state.packageName!=null && foreground==state.packageName
        if(game)seenGame=true
        if(!game && !attempted) report("HUD waiting for selected game: expected=${state.packageName}, detected=${foreground ?: "unknown"}; $evidence")
        if(seenGame && !game && running) {
            NativeHudBridge.stop();report("HUD stopped because game foreground is unknown or lost; measurements withheld")
        }
        if(state.active && game && !attempted && !running) {
            attempted=true
            val activity=host.get()
            if(activity==null || activity.isDestroyed) {report("Immersive HUD unavailable: no activity host; Auto Tune continues");return}
            if(!NativeHudBridge.available) {report("Immersive HUD native library unavailable on this device");return}
            report("Native permissions: render notification="+(context.checkSelfPermission("oculus.render_notification")==android.content.pm.PackageManager.PERMISSION_GRANTED)+", focus state="+(context.checkSelfPermission("horizonos.permission.READ_FOCUS_STATE")==android.content.pm.PackageManager.PERMISSION_GRANTED))
            running=true;NativeHudBridge.reset()
            worker.execute { try { NativeHudBridge.run(context.applicationContext,activity,this,768,384) }
                catch(e: Throwable) {report("Native HUD failed: ${e.javaClass.simpleName}")} finally {running=false} }
        }
        if(running && NativeHudBridge.available)draw(state,sample?.takeIf { it.packageName==state.packageName && System.currentTimeMillis()-it.timestampMs in 0..6000 })
    }
    private fun draw(state: LearningTrialState,sample: PerformanceSample?) {
        val t=sample?.telemetry?.takeIf { it.capturedAtMs?.let { at -> System.currentTimeMillis()-at in 0..6000 }==true }
        fun n(v: Number?,suffix: String="")=v?.let { if(it is Double) "%.1f%s".format(it,suffix) else "$it$suffix" } ?: "unavailable"
        val lines=listOf("OQT AUTO TUNE  |  ${state.phase}",
            "${state.completedWindows} checks  |  ${state.remainingSeconds?.let { "$it sec" } ?: "waiting"}",
            "FPS ${n(t?.measuredFps)}  /  ${n(t?.reportedRefreshRate," Hz")}",
            "App GPU ${n(t?.appGpuMs," ms")}  |  stale ${n(t?.staleFrames)}",
            "CPU ${n(t?.cpuLevel)} ${n(t?.cpuClockMhz,"MHz")}  GPU ${n(t?.gpuLevel)} ${n(t?.gpuClockMhz,"MHz")}",
            "Load CPU ${n(t?.cpuPercent,"%")}  GPU ${n(t?.gpuPercent,"%")}",
            "Free RAM ${n(t?.availableMemoryMb,"MB")}  Power ${n(t?.powerLevel)}",
            "Thermal ${sample?.thermal?.name ?: "unavailable"}",
            if(state.runtimeConfirmed) "CPU/GPU runtime levels confirmed" else "Requested settings are not proof of adoption",
            state.detail.take(58))
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
        paint.color=Color.argb(225,10,17,28);canvas.drawRoundRect(0f,0f,768f,384f,22f,22f,paint)
        lines.forEachIndexed { i,line -> paint.color=if(i==0)Color.rgb(110,218,255) else Color.WHITE;canvas.drawText(line.take(63),20f,34f+i*35f,paint) }
        bitmap.getPixels(data,0,768,0,0,768,384);NativeHudBridge.pixels(data)
    }
    fun retryOnNextUpdate() { attempted=false }
    fun close() { if(NativeHudBridge.available)NativeHudBridge.stop() }
}
