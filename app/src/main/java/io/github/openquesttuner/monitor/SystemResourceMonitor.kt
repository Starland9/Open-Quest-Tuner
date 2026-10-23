package io.github.openquesttuner.monitor

import android.app.ActivityManager
import android.content.Context
import io.github.openquesttuner.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Read-only background monitoring shared by dashboard and per-game captures. No profiler mode changes. */
class SystemResourceMonitor(context: Context, shell: ShellBackend, scope: CoroutineScope) {
    private val manager = context.applicationContext.getSystemService(ActivityManager::class.java)
    private val sampler = SystemResourceSampler(shell, ::memory, System::currentTimeMillis)
    private val data = MutableStateFlow(SystemResourceSnapshot())
    val state = data.asStateFlow()
    fun runtimeLogs(): String = sampler.runtimeLogs()
    private fun memory(): SystemMemory? = try {
        val info = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(info)
        SystemMemory(info.totalMem / 1048576.0, info.availMem / 1048576.0).takeIf { it.valid }
    } catch (_: RuntimeException) { null }
    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                data.value = sampler.poll()
                delay(1000)
            }
        }
    }
}
