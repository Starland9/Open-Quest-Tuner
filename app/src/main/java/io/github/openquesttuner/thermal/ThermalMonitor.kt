package io.github.openquesttuner.thermal

import android.content.Context
import android.os.PowerManager
import android.util.Log
import io.github.openquesttuner.core.ThermalLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * État thermique du casque en direct, lu avec `PowerManager` : sans permission ni connexion ADB
 * (FR-031, research.md R10). En cas d'erreur de l'API, l'état vaut [ThermalLevel.UNKNOWN].
 */
class ThermalMonitor(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val powerManager: PowerManager? = appContext.getSystemService(PowerManager::class.java)

    val level: StateFlow<ThermalLevel> = callbackFlow {
        val pm = powerManager
        if (pm == null) {
            trySend(ThermalLevel.UNKNOWN)
            awaitClose { }
            return@callbackFlow
        }
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            trySend(ThermalLevel.fromAndroidStatus(status))
        }
        try {
            trySend(ThermalLevel.fromAndroidStatus(pm.currentThermalStatus))
            pm.addThermalStatusListener(appContext.mainExecutor, listener)
        } catch (e: RuntimeException) {
            Log.w(TAG, "État thermique indisponible : ${e.javaClass.simpleName}")
            trySend(ThermalLevel.UNKNOWN)
        }
        awaitClose { runCatching { pm.removeThermalStatusListener(listener) } }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), currentLevel())

    private fun currentLevel(): ThermalLevel = try {
        powerManager?.let { ThermalLevel.fromAndroidStatus(it.currentThermalStatus) } ?: ThermalLevel.UNKNOWN
    } catch (e: RuntimeException) {
        ThermalLevel.UNKNOWN
    }

    private companion object {
        const val TAG = "OqtThermal"

        // L'écouteur reste branché quelques secondes après la dernière collecte (rotation, retour).
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
