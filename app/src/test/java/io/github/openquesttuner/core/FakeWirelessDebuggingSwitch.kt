package io.github.openquesttuner.core

/** Réglages système simulés (contracts/wireless-switch.md). */
class FakeWirelessDebuggingSwitch : WirelessDebuggingSwitch {

    var rightHeld = false
    var debuggingEnabled = true
    var onWifi = true
    var wirelessEnabled = false
    var lifetimeMs: Long? = 0L
    var readThrows = false

    /**
     * Après [enableWirelessDebugging], la valeur lue passe à `true` au bout de N lectures ;
     * `null` : jamais (fenêtre « autoriser sur ce réseau » refusée).
     */
    var acceptAfterReads: Int? = 1

    /**
     * Horloge des tests et durée pendant laquelle la lecture renvoie encore l'écriture de l'appli,
     * avant qu'Horizon OS ne remette le réglage à 0 pour afficher sa fenêtre (constaté sur Quest 3).
     */
    var now: () -> Long = { 0L }
    var echoWindowMs = 0L

    private var enabledAt: Long? = null

    var enableCalls = 0
        private set
    var reads = 0
        private set

    private var readsSinceEnable: Int? = null

    override fun hasWriteRight(): Boolean = rightHeld

    override fun isDebuggingEnabled(): Boolean = debuggingEnabled

    override fun isOnWifi(): Boolean = onWifi

    override fun isWirelessDebuggingEnabled(): Boolean {
        reads++
        if (readThrows) throw IllegalStateException("lecture impossible")
        enabledAt?.let { if (now() - it < echoWindowMs) return true }
        readsSinceEnable?.let { count ->
            readsSinceEnable = count + 1
            val threshold = acceptAfterReads
            if (threshold != null && count + 1 >= threshold) wirelessEnabled = true
        }
        return wirelessEnabled
    }

    override fun authorizationLifetimeMs(): Long? = lifetimeMs

    override fun enableWirelessDebugging() {
        if (!rightHeld) throw SecurityException("WRITE_SECURE_SETTINGS absente")
        enableCalls++
        enabledAt = now()
        if (readsSinceEnable == null) readsSinceEnable = 0
    }
}
