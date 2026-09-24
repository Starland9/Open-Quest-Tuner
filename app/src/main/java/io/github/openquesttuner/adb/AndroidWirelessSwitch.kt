package io.github.openquesttuner.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import io.github.openquesttuner.core.WirelessDebuggingSwitch

/**
 * [WirelessDebuggingSwitch] sur les réglages du casque (contracts/wireless-switch.md). Ce fichier
 * contient la seule écriture de réglage système de toute l'appli, vérifiée par
 * `SystemSettingsWriteTest` (FR-015, principe I).
 */
class AndroidWirelessSwitch(private val context: Context) : WirelessDebuggingSwitch {

    private val resolver get() = context.contentResolver

    override fun hasWriteRight(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    override fun isDebuggingEnabled(): Boolean = try {
        Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 1) == 1
    } catch (e: Exception) {
        true
    }

    override fun isOnWifi(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    override fun isWirelessDebuggingEnabled(): Boolean = try {
        Settings.Global.getInt(resolver, KEY_ADB_WIFI_ENABLED, 0) == 1
    } catch (e: Exception) {
        false
    }

    override fun authorizationLifetimeMs(): Long? {
        val raw = Settings.Global.getString(resolver, KEY_ADB_ALLOWED_CONNECTION_TIME) ?: return null
        return raw.trim().toLongOrNull() ?: UNREADABLE_LIFETIME
    }

    override fun enableWirelessDebugging() {
        Settings.Global.putInt(resolver, KEY_ADB_WIFI_ENABLED, 1)
    }

    private companion object {
        const val KEY_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        const val KEY_ADB_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time"

        /** Valeur non numérique : hors plage, le choix « sans expiration » n'est pas proposé (FR-024). */
        const val UNREADABLE_LIFETIME = -1L
    }
}
