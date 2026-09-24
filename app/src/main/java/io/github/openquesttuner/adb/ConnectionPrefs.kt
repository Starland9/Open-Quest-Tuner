package io.github.openquesttuner.adb

import android.content.Context
import androidx.core.content.edit
import io.github.openquesttuner.core.AutoReconnectStore
import io.github.openquesttuner.core.ConnectionMethod

/**
 * Dernière méthode de connexion réussie, rejouée au démarrage (FR-005), et choix de la
 * reconnexion autonome (spec 002, data-model.md).
 */
class ConnectionPrefs(context: Context) : AutoReconnectStore {

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

    override var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RECONNECT, value) }

    override var rightGrantedByApp: Boolean
        get() = prefs.getBoolean(KEY_RIGHT_GRANTED_BY_APP, false)
        set(value) = prefs.edit { putBoolean(KEY_RIGHT_GRANTED_BY_APP, value) }

    override var revokePending: Boolean
        get() = prefs.getBoolean(KEY_REVOKE_PENDING, false)
        set(value) = prefs.edit { putBoolean(KEY_REVOKE_PENDING, value) }

    override var expiryOriginal: String?
        get() = prefs.getString(KEY_EXPIRY_ORIGINAL, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_EXPIRY_ORIGINAL) else putString(KEY_EXPIRY_ORIGINAL, value)
        }

    override var expiryRestorePending: Boolean
        get() = prefs.getBoolean(KEY_EXPIRY_RESTORE_PENDING, false)
        set(value) = prefs.edit { putBoolean(KEY_EXPIRY_RESTORE_PENDING, value) }

    private companion object {
        const val KEY_METHOD = "last_method"
        const val KEY_WIRELESS_PORT = "last_wireless_port"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"
        const val KEY_RIGHT_GRANTED_BY_APP = "right_granted_by_app"
        const val KEY_REVOKE_PENDING = "revoke_pending"
        const val KEY_EXPIRY_ORIGINAL = "expiry_original"
        const val KEY_EXPIRY_RESTORE_PENDING = "expiry_restore_pending"
    }
}
