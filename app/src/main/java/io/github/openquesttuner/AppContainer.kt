package io.github.openquesttuner

import android.content.Context
import android.os.Build
import io.github.openquesttuner.adb.AdbIdentityStore
import io.github.openquesttuner.adb.AdbShellBackend
import io.github.openquesttuner.adb.AndroidWirelessSwitch
import io.github.openquesttuner.adb.AutoReconnectController
import io.github.openquesttuner.adb.ConnectionPrefs
import io.github.openquesttuner.adb.PairingServiceDiscovery
import io.github.openquesttuner.core.ProfileStore
import io.github.openquesttuner.persistence.ProfileLibraryStore
import io.github.openquesttuner.core.GlobalProfileStore
import io.github.openquesttuner.core.QuestModel
import io.github.openquesttuner.core.Tuner
import io.github.openquesttuner.persistence.ProSettingsStore
import io.github.openquesttuner.persistence.OptimizationHistoryStore
import io.github.openquesttuner.persistence.CommunityProfileCatalogStore
import io.github.openquesttuner.persistence.CatalogUpdater
import io.github.openquesttuner.persistence.SessionHistoryStore
import io.github.openquesttuner.persistence.ProductExperienceStore
import io.github.openquesttuner.display.DisplayMonitor
import io.github.openquesttuner.games.GameRepository
import io.github.openquesttuner.thermal.ThermalMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Injection de dépendances manuelle : un seul graphe, créé par [OqtApplication]. */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val questModel: QuestModel = QuestModel.fromBuild(Build.DEVICE.orEmpty(), Build.MODEL.orEmpty())

    /** Travaux qui doivent survivre à l'écran courant (reconnexion au démarrage…). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Partagées par l'ADB et la reconnexion autonome. */
    val connectionPrefs = ConnectionPrefs(appContext)

    val adb = AdbShellBackend(
        context = appContext,
        identityStore = AdbIdentityStore(File(appContext.filesDir, "adb")),
        prefs = connectionPrefs,
    )

    val wirelessSwitch = AndroidWirelessSwitch(appContext)

    val pairingDiscovery = PairingServiceDiscovery(appContext)

    val autoReconnect = AutoReconnectController(appContext, adb, wirelessSwitch, connectionPrefs, questModel, appScope)

    val profileStore = ProfileStore(File(appContext.filesDir, "profiles.json"))

    val profileLibraryStore = ProfileLibraryStore(appContext)

    val proSettings = ProSettingsStore(appContext)

    val optimizationHistory = OptimizationHistoryStore(appContext)

    val communityCatalog = CommunityProfileCatalogStore(appContext)

    val catalogUpdater = CatalogUpdater(appContext, communityCatalog)

    val sessionHistory = SessionHistoryStore(appContext)

    val recording = kotlinx.coroutines.flow.MutableStateFlow(false)
    val performanceLog = io.github.openquesttuner.persistence.PerformanceLogStore(appContext)

    val productExperience = ProductExperienceStore(appContext)

    val globalProfileStore = GlobalProfileStore(File(appContext.filesDir, "global-profile.json")).also { it.load() }

    val games = GameRepository(appContext)

    /** Écran du casque : fréquences déclarées et fréquence réelle, sans connexion (spec 003). */
    val display = DisplayMonitor(appContext, appScope)

    val systemResources = io.github.openquesttuner.monitor.SystemResourceMonitor(appContext, adb, appScope)

    val tuner = Tuner(adb, questModel, display, systemResources::runtimeLogs)

    val thermal = ThermalMonitor(appContext, appScope)
    val learningOverlayStatus = kotlinx.coroutines.flow.MutableStateFlow("Not running")
    val learningStore = io.github.openquesttuner.persistence.GameLearningStore(appContext)
    val learningGameLaunched=kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity=1)
    val immersiveHud=io.github.openquesttuner.monitor.NativeImmersiveHud(appContext) { learningOverlayStatus.value=it }
    val learning = io.github.openquesttuner.monitor.GameLearningRuntime(this)
}
