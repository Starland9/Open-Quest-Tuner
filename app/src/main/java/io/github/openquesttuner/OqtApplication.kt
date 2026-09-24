package io.github.openquesttuner

import android.app.Application
import kotlinx.coroutines.launch

class OqtApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.profileStore.load() }
        // Reconnexion silencieuse avec la dernière méthode réussie, sans bloquer l'interface (FR-005).
        // Si la reconnexion autonome est active, elle réactive d'abord le débogage sans fil ; jamais
        // au démarrage du casque, seulement à l'ouverture de l'appli (spec 002, FR-008).
        container.appScope.launch { container.autoReconnect.reconnectNow() }
    }
}
