package io.github.openquesttuner.monitor

import android.app.*
import android.content.Intent
import android.os.IBinder
import io.github.openquesttuner.MainActivity
import io.github.openquesttuner.OqtApplication
import io.github.openquesttuner.R
import io.github.openquesttuner.core.*
import kotlinx.coroutines.*

class PerformanceRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var learningOnly=false
    @Volatile private var foreground: String?=null
    private var notificationText=""
    companion object { const val LEARNING_ONLY="io.github.openquesttuner.LEARN_ONLY" }
    private val container get() = (application as OqtApplication).container
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("performance", "Game performance capture", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(201, Notification.Builder(this, "performance").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("OQT performance capture").setContentText("Recording game metrics locally • tap to open")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", stop).build()).build())
        scope.launch(Dispatchers.Main.immediate) {
            while(isActive) {
                val state=container.learning.trial.state.value
                container.immersiveHud.update(state,container.learningStore.overlayEnabled.value,container.performanceLog.samples.value.lastOrNull(),foreground,container.tuner.foregroundEvidence)
                val title=if(state.active || state.recoveryRequired) LearningExperience.title(state) else "OQT performance capture"
                val body=if(state.result==LearningResult.APPLIED) "Profile verified, saved and applied" else if(state.active) "${state.completedWindows} steps complete · tap to open" else if(state.recoveryRequired) "Reconnect and restore your settings" else "Recording game metrics locally · tap to open"
                if(notificationText!=title+body) {
                    notificationText=title+body
                    getSystemService(NotificationManager::class.java).notify(201,Notification.Builder(this@PerformanceRecordingService,"performance").setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title).setContentText(body).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
                        .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,"Stop",stop).build()).build())
                }
                delay(1000)
            }
        }
        container.recording.value = true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if(intent?.action!=LEARNING_ONLY) learningOnly=false
        else if(job==null) learningOnly=true
        if (job == null) job = scope.launch {
            try {
                val games = container.games.loadGames().map { it.packageName }.toSet()
                while (isActive) {
                    val stateNow=container.learning.trial.state.value
                    val completionVisible=stateNow.result==LearningResult.APPLIED && stateNow.finishedAtMs>0 && System.currentTimeMillis()-stateNow.finishedAtMs in 0..8000
                    if(LearningExperience.stopLearningCapture(learningOnly,stateNow) && !completionVisible) { stopSelf(); break }
                    if (container.adb.state.value is ConnectionState.Connected) {
                        val pkg = container.tuner.readForegroundPackage(games)
                        foreground=pkg
                        if (pkg != null && pkg in games) {
                            val generation = container.tuner.generation
                            val diagnostic = container.tuner.readDiagnostic()
                            val properties = diagnostic?.active.orEmpty()
                            val telemetry = container.tuner.readRuntimeTelemetry(pkg, container.display.currentRefreshRate.value)
                            val sample = PerformanceSample(System.currentTimeMillis(), pkg, telemetry.reportedRefreshRate ?: container.display.currentRefreshRate.value,
                                container.thermal.level.value, properties, telemetry, readbackVerified = diagnostic != null)
                            if(container.tuner.readForegroundPackage(games)!=pkg) { foreground=null;container.learning.observe(null);delay(2000);continue }
                            if (generation != container.tuner.generation) { delay(2000); continue }
                            container.performanceLog.record(sample)
                            container.learning.observe(sample)
                            val expected = container.profileStore.profiles.value[pkg]
                            val name = container.profileLibraryStore.libraries.value[pkg]?.selected
                            container.optimizationHistory.record(sample, expected, name)
                            container.sessionHistory.record(sample, expected, name)
                        } else { container.sessionHistory.finish(); container.learning.observe(null) }
                    }
                    delay(2000)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { stopSelf() }
            finally { withContext(NonCancellable) { if (container.learning.trial.state.value.active) container.learning.trial.stop();container.learning.cleanupCapture() }; container.performanceLog.flush() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { container.immersiveHud.close(); if (container.learning.trial.state.value.active) container.appScope.launch { container.learning.trial.stop();container.learning.cleanupCapture() }; scope.cancel(); container.performanceLog.flush(); container.sessionHistory.finish(); container.recording.value = false; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
