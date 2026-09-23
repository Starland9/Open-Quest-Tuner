package io.github.openquesttuner.adb

import android.content.Context
import androidx.core.content.edit
import io.github.openquesttuner.core.ConnectionMethod

/** Dernière méthode de connexion réussie, rejouée au démarrage (FR-005). */
class ConnectionPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)

    var lastMethod: ConnectionMethod?
        get() = prefs.getString(KEY_METHOD, null)?.let { name ->
            ConnectionMethod.entries.firstOrNull { it.name == name }
        }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_METHOD) else putString(KEY_METHOD, value.name)
        }

    /** Port de connexion sans fil saisi à la main ; simple repli après la découverte mDNS. */
    var lastWirelessPort: Int?
        get() = prefs.getInt(KEY_WIRELESS_PORT, 0).takeIf { it in 1..65535 }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_WIRELESS_PORT) else putInt(KEY_WIRELESS_PORT, value)
        }

    private companion object {
        const val KEY_METHOD = "last_method"
        const val KEY_WIRELESS_PORT = "last_wireless_port"
    }
}
