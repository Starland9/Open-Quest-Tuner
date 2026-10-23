package io.github.openquesttuner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

private const val SETTINGS_PACKAGE = "com.android.settings"

/**
 * Opens Android Settings using the same approach as the Quest GoToSettings helper:
 * PackageManager launch intent first, then the package-details screen as fallback.
 */
fun openAndroidSettings(context: Context, onFailure: () -> Unit = {}) {
    try {
        val intent = context.packageManager
            .getLaunchIntentForPackage(SETTINGS_PACKAGE)
            ?: throw IllegalStateException("No launch intent for $SETTINGS_PACKAGE")

        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_TASK_ON_HOME
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$SETTINGS_PACKAGE")
            ).apply {
                setPackage(SETTINGS_PACKAGE)
                setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_TASK_ON_HOME
                )
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            onFailure()
        }
    }
}
