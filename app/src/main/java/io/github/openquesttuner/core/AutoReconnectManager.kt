package io.github.openquesttuner.core

/**
 * Séquences de la reconnexion autonome qui passent par le shell (C9 à C13), selon
 * specs/002-standalone-reconnect/contracts/shell-commands.md.
 */
class AutoReconnectManager(
    private val shell: ShellBackend,
    private val switch: WirelessDebuggingSwitch,
    private val store: AutoReconnectStore,
) {

    fun status(): AutoReconnectStatus = AutoReconnectPolicy.status(store.autoReconnect, switch.hasWriteRight())

    /**
     * Séquence « Activer », aussi utilisée par « Réactiver ». N'envoie C9 que si la permission est
     * absente, et ne retient `rightGrantedByApp` que si C9 l'a fait passer d'absente à détenue.
     * La relecture fait foi (FR-004) : un code de sortie perdu ne doit pas cacher un octroi réel,
     * que l'appli doit ensuite pouvoir retirer (principe I, condition 3).
     */
    suspend fun enable(): EnableResult {
        if (shell.state.value !is ConnectionState.Connected) return EnableResult.NOT_CONNECTED
        if (!switch.hasWriteRight()) {
            try {
                shell.exec(ShellCommand.grantWriteSecureSettings())
            } catch (e: ShellUnavailableException) {
                return EnableResult.NOT_CONNECTED
            }
            if (!switch.hasWriteRight()) return EnableResult.UNAVAILABLE
            store.rightGrantedByApp = true
        }
        store.autoReconnect = true
        return EnableResult.ENABLED
    }

    /**
     * Séquence « Désactiver » (FR-013, FR-014). Les préférences sont écrites avant C10 ; la
     * permission n'est retirée que si c'est l'appli qui l'a accordée (principe I, condition 3), et
     * le délai d'expiration est rétabli si le choix « sans expiration » est actif. Ne coupe ni la
     * connexion ni le débogage sans fil.
     */
    suspend fun disable(): DisableResult {
        store.autoReconnect = false
        store.revokePending = store.rightGrantedByApp
        if (expiryStatus() == ExpiryChoiceStatus.ON) setNeverExpire(false)
        if (!store.rightGrantedByApp) return DisableResult.KEPT_EXTERNAL_GRANT
        if (shell.state.value !is ConnectionState.Connected) return DisableResult.REVOKE_PENDING
        return if (revokeNow()) DisableResult.REVOKED else DisableResult.REVOKE_PENDING
    }

    fun lifetime(): AuthorizationLifetime = ExpiryChoicePolicy.lifetimeOf(switch.authorizationLifetimeMs())

    fun expiryStatus(): ExpiryChoiceStatus = ExpiryChoicePolicy.status(
        store.expiryOriginal,
        store.expiryRestorePending,
        switch.authorizationLifetimeMs(),
    )

    /**
     * Séquences « Ne jamais faire expirer les autorisations » ([on] vrai) et « Rétablir le délai »
     * ([on] faux). La valeur d'origine est retenue avant C11, et n'est rétablie que si le délai
     * vaut encore `0` : l'appli ne rétablit que ce qu'elle a changé (FR-023, principe I).
     */
    suspend fun setNeverExpire(on: Boolean): ExpiryChangeResult = if (on) disableExpiry() else restoreExpiry()

    /**
     * À chaque passage à `Connected` : rétablit le délai et retire la permission s'ils sont en
     * attente depuis une désactivation hors connexion.
     */
    suspend fun onConnected() {
        if (store.expiryRestorePending) {
            val original = store.expiryOriginal
            if (original == null) store.expiryRestorePending = false else restoreNow(original)
        }
        if (store.revokePending) revokeNow()
    }

    /**
     * C10 si la permission est encore détenue, puis oubli du retrait en attente. Succès si le code
     * de sortie vaut 0 ou si la permission est relue comme absente.
     * @return `false` si le retrait reste en attente (connexion perdue, ou C10 refusée).
     */
    private suspend fun revokeNow(): Boolean {
        if (switch.hasWriteRight()) {
            val result = try {
                shell.exec(ShellCommand.revokeWriteSecureSettings())
            } catch (e: ShellUnavailableException) {
                return false
            }
            if (!result.isSuccess && switch.hasWriteRight()) return false
        }
        store.revokePending = false
        store.rightGrantedByApp = false
        return true
    }

    private suspend fun disableExpiry(): ExpiryChangeResult {
        if (store.expiryOriginal != null) {
            return if (store.expiryRestorePending) ExpiryChangeResult.RESTORE_PENDING else ExpiryChangeResult.APPLIED
        }
        if (shell.state.value !is ConnectionState.Connected) return ExpiryChangeResult.NOT_CONNECTED
        val current = switch.authorizationLifetimeMs()
        // FR-024 : déjà sans expiration, ou valeur que l'appli ne saurait pas rétablir.
        if (ExpiryChoicePolicy.lifetimeOf(current) !is AuthorizationLifetime.Days) return ExpiryChangeResult.NOT_APPLICABLE
        store.expiryOriginal = ExpiryChoicePolicy.encodeOriginal(current)
        try {
            shell.exec(ShellCommand.disableAuthorizationExpiry())
        } catch (e: ShellUnavailableException) {
            // C11 a pu passer : la valeur reste retenue, pour pouvoir la rétablir.
            if (switch.authorizationLifetimeMs() == 0L) return ExpiryChangeResult.APPLIED
            return ExpiryChangeResult.NOT_CONNECTED
        }
        if (switch.authorizationLifetimeMs() != 0L) {
            store.expiryOriginal = null
            return ExpiryChangeResult.FAILED
        }
        return ExpiryChangeResult.APPLIED
    }

    private suspend fun restoreExpiry(): ExpiryChangeResult {
        val original = store.expiryOriginal ?: return ExpiryChangeResult.NOT_APPLICABLE
        if (shell.state.value !is ConnectionState.Connected) {
            store.expiryRestorePending = true
            return ExpiryChangeResult.RESTORE_PENDING
        }
        return restoreNow(original)
    }

    private suspend fun restoreNow(original: String): ExpiryChangeResult {
        // Délai différent de 0 : un autre outil l'a changé, on n'écrit rien (FR-023).
        if (switch.authorizationLifetimeMs() == 0L) {
            val command = try {
                ExpiryChoicePolicy.restoreCommand(original)
            } catch (e: IllegalArgumentException) {
                forgetExpiry()
                return ExpiryChangeResult.FAILED
            }
            val result = try {
                shell.exec(command)
            } catch (e: ShellUnavailableException) {
                store.expiryRestorePending = true
                return ExpiryChangeResult.RESTORE_PENDING
            }
            if (!result.isSuccess) {
                // Nouvel essai à la prochaine connexion : C12 et C13 sont idempotentes.
                store.expiryRestorePending = true
                return ExpiryChangeResult.FAILED
            }
        }
        forgetExpiry()
        return ExpiryChangeResult.APPLIED
    }

    private fun forgetExpiry() {
        store.expiryOriginal = null
        store.expiryRestorePending = false
    }
}
