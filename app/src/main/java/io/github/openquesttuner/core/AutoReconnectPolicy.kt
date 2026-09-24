package io.github.openquesttuner.core

import kotlinx.coroutines.delay

/** État affiché de la reconnexion autonome, dérivé à chaque lecture (data-model.md). */
enum class AutoReconnectStatus { INACTIVE, ACTIVE, NEEDS_REACTIVATION }

/**
 * Cause d'un échec de reconnexion autonome (data-model.md, research.md R7). L'ordre des valeurs
 * est l'ordre de priorité : c'est celui des vérifications de [AutoReconnectPolicy.prepareWireless].
 */
enum class ReconnectIssue {
    DEBUGGING_DISABLED,
    NO_WIFI,
    RIGHT_LOST,

    /** Réseau non autorisé, ou casque qui n'active pas le débogage sans fil : indiscernables. */
    NETWORK_NOT_ALLOWED,
    AUTHORIZATION_LOST,
    WIRELESS_NOT_STARTED,
    UNKNOWN,
}

/** Issue de la préparation du sans-fil. */
sealed interface PrepareResult {
    data object Ready : PrepareResult
    data class Issue(val issue: ReconnectIssue) : PrepareResult
}

/** Issue d'une série de tentatives de reconnexion ; `issue` est `null` si aucune cause n'a été retenue. */
sealed interface ReconnectOutcome {
    data object Connected : ReconnectOutcome
    data class Failed(val issue: ReconnectIssue?) : ReconnectOutcome
}

enum class EnableResult { ENABLED, UNAVAILABLE, NOT_CONNECTED }

enum class DisableResult { REVOKED, REVOKE_PENDING, KEPT_EXTERNAL_GRANT }

/**
 * Décisions de la reconnexion autonome, isolées ici pour être testées sans casque (principe IV).
 * La couche Android ne fait que lire, écrire une valeur et câbler (spec 002, data-model.md).
 */
object AutoReconnectPolicy {

    /** data-model.md, « `AutoReconnectStatus` » : dérivé à chaque lecture, jamais stocké. */
    fun status(optionEnabled: Boolean, rightHeld: Boolean): AutoReconnectStatus = when {
        !optionEnabled -> AutoReconnectStatus.INACTIVE
        rightHeld -> AutoReconnectStatus.ACTIVE
        else -> AutoReconnectStatus.NEEDS_REACTIVATION
    }

    /**
     * data-model.md, « Préparation du sans-fil », étapes 1 à 6. N'écrit le réglage que si le
     * débogage sans fil est coupé et que tout le reste le permet, puis attend l'acceptation de la
     * fenêtre « autoriser sur ce réseau » au plus [timeoutMs].
     */
    suspend fun prepareWireless(
        switch: WirelessDebuggingSwitch,
        timeoutMs: Long = ConnectionPolicy.WIRELESS_ACCEPT_TIMEOUT_MS,
        pollMs: Long = ConnectionPolicy.WIRELESS_POLL_MS,
    ): PrepareResult {
        if (!switch.isDebuggingEnabled()) return PrepareResult.Issue(ReconnectIssue.DEBUGGING_DISABLED)
        if (!switch.isOnWifi()) return PrepareResult.Issue(ReconnectIssue.NO_WIFI)
        if (readSafely { switch.isWirelessDebuggingEnabled() }) return PrepareResult.Ready
        if (!switch.hasWriteRight()) return PrepareResult.Issue(ReconnectIssue.RIGHT_LOST)
        try {
            switch.enableWirelessDebugging()
        } catch (e: SecurityException) {
            return PrepareResult.Issue(ReconnectIssue.RIGHT_LOST)
        }
        // Horizon OS remet le réglage à 0 le temps de sa fenêtre « autoriser sur ce réseau », mais
        // quelques millisecondes après l'écriture : relire tout de suite renverrait l'écriture de
        // l'appli, prise à tort pour une acceptation (Quest 3, vros 207, docs/compatibility.md).
        delay(pollMs)
        val enabled = ConnectionPolicy.awaitWirelessEnabled(
            read = { switch.isWirelessDebuggingEnabled() },
            timeoutMs = timeoutMs - pollMs,
            pollMs = pollMs,
        )
        return if (enabled) PrepareResult.Ready else PrepareResult.Issue(ReconnectIssue.NETWORK_NOT_ALLOWED)
    }

    /** research.md R7 : cause d'un échec de connexion sans fil qui suit une préparation réussie. */
    fun issueFor(reason: FailureReason?): ReconnectIssue = when (reason) {
        FailureReason.PAIRING_REQUIRED, FailureReason.NOT_AUTHORIZED -> ReconnectIssue.AUTHORIZATION_LOST
        FailureReason.SERVICE_NOT_FOUND -> ReconnectIssue.WIRELESS_NOT_STARTED
        else -> ReconnectIssue.UNKNOWN
    }

    /** data-model.md, « Deux règles d'affichage et de relance » : option inactive, comportement du MVP. */
    fun visibleIssue(status: AutoReconnectStatus, issue: ReconnectIssue?): ReconnectIssue? =
        if (status == AutoReconnectStatus.INACTIVE) null else issue

    /** Idem : relance au retour du Wi-Fi seulement si c'est lui qui manquait (FR-010). */
    fun shouldRetryOnWifi(issue: ReconnectIssue?, connected: Boolean): Boolean =
        issue == ReconnectIssue.NO_WIFI && !connected

    /**
     * data-model.md, « Tentatives de reconnexion ». Essaie [attempts] dans l'ordre jusqu'au premier
     * succès ; [connect] renvoie `null` en cas de succès. [prepare] est appelé au plus une fois,
     * juste avant la tentative marquée. Si la préparation échoue, sa cause est retenue et les
     * tentatives sans fil restantes sont sautées. Sinon, la cause du premier échec sans fil qui
     * suit une préparation réussie est retenue. Si le repli échoue aussi, c'est elle qui est rendue.
     */
    suspend fun reconnect(
        attempts: List<ReconnectAttempt>,
        prepare: suspend () -> PrepareResult,
        connect: suspend (ReconnectAttempt) -> FailureReason?,
    ): ReconnectOutcome {
        var issue: ReconnectIssue? = null
        var prepareCalled = false
        var prepared = false
        var skipWireless = false
        for (attempt in attempts) {
            val wireless = attempt.method == ConnectionMethod.WIRELESS
            if (wireless && skipWireless) continue
            if (attempt.prepareWireless && !prepareCalled) {
                prepareCalled = true
                when (val result = prepare()) {
                    PrepareResult.Ready -> prepared = true
                    is PrepareResult.Issue -> {
                        issue = issue ?: result.issue
                        skipWireless = true
                        continue
                    }
                }
            }
            val reason = connect(attempt) ?: return ReconnectOutcome.Connected
            if (wireless && prepared && issue == null) issue = issueFor(reason)
        }
        return ReconnectOutcome.Failed(issue)
    }

    private inline fun readSafely(read: () -> Boolean): Boolean = try {
        read()
    } catch (e: Exception) {
        false
    }
}
