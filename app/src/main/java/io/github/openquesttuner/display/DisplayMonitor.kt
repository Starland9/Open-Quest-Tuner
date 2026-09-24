package io.github.openquesttuner.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import io.github.openquesttuner.core.DisplayRates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Écran du casque lu avec `DisplayManager`, sans permission ni connexion ADB
 * (specs/003-high-refresh-rates/contracts/display-rates.md). Toujours l'écran 0, jamais
 * `context.display` (research.md R1). Aucune écriture de mode d'affichage.
 */
class DisplayMonitor(context: Context, scope: CoroutineScope) : DisplayRates {

    private val displayManager: DisplayManager? =
        context.applicationContext.getSystemService(DisplayManager::class.java)

    override fun declaredRefreshRates(): Set<Int> = try {
        val modes = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.supportedModes.orEmpty()
        DisplayRates.ratesFromModes(modes.map { it.refreshRate })
    } catch (e: RuntimeException) {
        Log.w(TAG, "Modes d'affichage illisibles : ${e.javaClass.simpleName}")
        emptySet()
    }

    /**
     * Fréquence à laquelle l'écran tourne réellement, en Hz entiers ; `null` si illisible (FR-010).
     * Mise à jour par l'écouteur de l'écran 0, et relue toutes les 2 s par sécurité : Horizon OS
     * change de mode lui-même, et l'appel de l'écouteur reste à vérifier (research.md R2).
     */
    val currentRefreshRate: StateFlow<Int?> = callbackFlow {
        trySend(readRefreshRate())
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) trySend(readRefreshRate())
            }

            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        launch {
            while (isActive) {
                delay(POLL_MS)
                trySend(readRefreshRate())
            }
        }
        awaitClose { displayManager?.unregisterDisplayListener(listener) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), readRefreshRate())

    private fun readRefreshRate(): Int? = try {
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
            ?.let { DisplayRates.ratesFromModes(listOf(it)).singleOrNull() }
    } catch (e: RuntimeException) {
        null
    }

    private companion object {
        const val TAG = "OqtDisplay"
        const val POLL_MS = 2_000L

        // L'écouteur reste branché quelques secondes après la dernière collecte (rotation, retour).
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
