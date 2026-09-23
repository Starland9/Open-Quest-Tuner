package io.github.openquesttuner

import android.app.Application
import kotlinx.coroutines.launch

class OqtApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Reconnexion silencieuse avec la dernière méthode réussie, sans bloquer l'interface (FR-005).
        container.appScope.launch { container.adb.reconnectLast() }
    }
}
