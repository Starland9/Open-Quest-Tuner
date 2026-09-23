package io.github.openquesttuner

import android.content.Context
import android.os.Build
import io.github.openquesttuner.adb.AdbIdentityStore
import io.github.openquesttuner.adb.AdbShellBackend
import io.github.openquesttuner.adb.ConnectionPrefs
import io.github.openquesttuner.core.QuestModel
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

    val adb = AdbShellBackend(
        context = appContext,
        identityStore = AdbIdentityStore(File(appContext.filesDir, "adb")),
        prefs = ConnectionPrefs(appContext),
    )
}
