package io.github.openquesttuner.games

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import io.github.openquesttuner.core.ShellCommand
import io.github.openquesttuner.core.searchKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Jeu VR installé ; [launchActivity] est le nom qualifié complet, validé pour la commande C4. */
data class InstalledGame(
    val packageName: String,
    val label: String,
    val launchActivity: String,
)

/** Jeux VR installés par l'utilisateur, lus avec le PackageManager (research.md R5). */
class GameRepository(context: Context) {

    private val pm: PackageManager = context.packageManager
    private val ownPackage: String = context.packageName

    suspend fun loadGames(): List<InstalledGame> = withContext(Dispatchers.IO) {
        // Applis panneau d'Horizon OS : elles portent parfois les marqueurs VR sans être des jeux.
        val panels = queryServices(Intent(ACTION_SHELL_MAIN)).mapTo(HashSet()) { it.serviceInfo.packageName }
        fun eligible(app: ApplicationInfo) =
            app.enabled && app.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                app.packageName != ownPackage && app.packageName !in panels

        val games = LinkedHashMap<String, InstalledGame>()

        // 1. Activités de catégorie VR, exigée par Meta pour les applis OpenXR.
        queryActivities(Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_VR))
            .map { it.activityInfo }
            .filter { eligible(it.applicationInfo) }
            .groupBy { it.packageName }
            .forEach { (packageName, activities) ->
                // Plusieurs activités VR (YouTube VR en a 5) : on garde celle du lanceur.
                val frontDoor = frontDoor(packageName)
                val activity = activities.firstOrNull { it.name == frontDoor } ?: activities.first()
                toGame(activity.applicationInfo, activity.name)?.let { games[packageName] = it }
            }

        // 2. Anciens titres sans la catégorie, reconnus à leurs meta-data d'application.
        installedApplications()
            .filter { app ->
                app.packageName !in games && eligible(app) &&
                    app.metaData?.let { meta -> LEGACY_VR_META_DATA.any(meta::containsKey) } == true
            }
            .forEach { app ->
                frontDoor(app.packageName)?.let { toGame(app, it) }?.let { games[app.packageName] = it }
            }

        Log.i(TAG, "${games.size} jeux VR trouvés")
        games.values.sortedWith(compareBy({ searchKey(it.label) }, { it.packageName }))
    }

    fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Une entrée dont le nom ne passerait pas les fabriques de [ShellCommand] est ignorée. */
    private fun toGame(app: ApplicationInfo, activity: String): InstalledGame? {
        if (!ShellCommand.isValidPackageName(app.packageName) || !ShellCommand.isValidClassName(activity)) return null
        return InstalledGame(app.packageName, app.loadLabel(pm).toString().trim(), activity)
    }

    /** Point d'entrée du paquet : activité `LAUNCHER`, sinon `INFO`. */
    private fun frontDoor(packageName: String): String? =
        listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_INFO).firstNotNullOfOrNull { category ->
            queryActivities(Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(packageName))
                .firstOrNull()?.activityInfo?.name
        }

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun queryServices(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }

    private fun installedApplications(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

    private companion object {
        const val TAG = "OqtGames"
        const val CATEGORY_VR = "com.oculus.intent.category.VR"
        const val ACTION_SHELL_MAIN = "com.oculus.vrshell.SHELL_MAIN"
        val LEGACY_VR_META_DATA = listOf("com.samsung.android.vr.application.mode", "com.oculus.ossplash")
    }
}
