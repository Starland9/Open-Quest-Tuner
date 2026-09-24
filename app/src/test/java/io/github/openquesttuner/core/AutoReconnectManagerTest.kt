package io.github.openquesttuner.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Séquences de contracts/shell-commands.md (spec 002). */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoReconnectManagerTest {

    private val shell = FakeShellBackend()
    private val switch = FakeWirelessDebuggingSwitch()
    private val store = InMemoryAutoReconnectStore()
    private val manager = AutoReconnectManager(shell, switch, store)

    private val grant = ShellCommand.grantWriteSecureSettings().text

    /** Le casque accorde la permission quand C9 passe. */
    private fun grantOnC9() {
        shell.onExec = { if (it == grant) switch.rightHeld = true }
    }

    // --- Activer (US1)

    @Test
    fun `activer hors connexion ne fait rien`() = runTest {
        shell.state.value = ConnectionState.Disconnected
        grantOnC9()
        assertEquals(EnableResult.NOT_CONNECTED, manager.enable())
        assertEquals(emptyList<String>(), shell.executed)
        assertFalse(store.autoReconnect)
    }

    @Test
    fun `activer accorde la permission avec C9 et le retient`() = runTest {
        grantOnC9()
        assertEquals(EnableResult.ENABLED, manager.enable())
        assertEquals(listOf(grant), shell.executed)
        assertTrue(store.autoReconnect)
        assertTrue(store.rightGrantedByApp)
    }

    @Test
    fun `permission deja detenue, pas de C9 et rien a retirer plus tard`() = runTest {
        switch.rightHeld = true
        assertEquals(EnableResult.ENABLED, manager.enable())
        assertEquals(emptyList<String>(), shell.executed)
        assertTrue(store.autoReconnect)
        assertFalse(store.rightGrantedByApp)
    }

    @Test
    fun `C9 en echec, option indisponible`() = runTest {
        shell.respond("pm grant", ShellResult(1, ""))
        assertEquals(EnableResult.UNAVAILABLE, manager.enable())
        assertFalse(store.autoReconnect)
        assertFalse(store.rightGrantedByApp)
    }

    @Test
    fun `permission toujours absente apres C9, option indisponible`() = runTest {
        assertEquals(EnableResult.UNAVAILABLE, manager.enable())
        assertEquals(listOf(grant), shell.executed)
        assertFalse(store.autoReconnect)
    }

    @Test
    fun `connexion perdue pendant C9`() = runTest {
        shell.disconnectAt = "pm grant"
        assertEquals(EnableResult.NOT_CONNECTED, manager.enable())
        assertFalse(store.autoReconnect)
    }

    @Test
    fun `statut selon le choix et la permission`() {
        assertEquals(AutoReconnectStatus.INACTIVE, manager.status())
        store.autoReconnect = true
        switch.rightHeld = true
        assertEquals(AutoReconnectStatus.ACTIVE, manager.status())
        switch.rightHeld = false
        assertEquals(AutoReconnectStatus.NEEDS_REACTIVATION, manager.status())
    }

    // --- Réactiver (US2)

    @Test
    fun `reactiver apres la perte de la permission`() = runTest {
        store.autoReconnect = true
        grantOnC9()
        assertEquals(AutoReconnectStatus.NEEDS_REACTIVATION, manager.status())
        assertEquals(EnableResult.ENABLED, manager.enable())
        assertTrue(store.rightGrantedByApp)
        assertEquals(AutoReconnectStatus.ACTIVE, manager.status())
    }

    // --- Ne jamais faire expirer / rétablir le délai (US4)

    private val neverExpire = ShellCommand.disableAuthorizationExpiry().text
    private val sevenDays = 604_800_000L

    /** Le casque applique C11 à C13 au délai lu par le switch. */
    private fun applyExpiryCommands() {
        shell.onExec = { command ->
            when {
                command == neverExpire -> switch.lifetimeMs = 0L
                command.startsWith("settings put global adb_allowed_connection_time ") ->
                    switch.lifetimeMs = command.substringAfterLast(' ').toLong()
                command == ShellCommand.resetAuthorizationExpiry().text -> switch.lifetimeMs = null
            }
        }
    }

    @Test
    fun `activer retient la valeur d'origine avant C11`() = runTest {
        switch.lifetimeMs = sevenDays
        var retainedAtC11: String? = null
        shell.onExec = { command ->
            if (command == neverExpire) {
                retainedAtC11 = store.expiryOriginal
                switch.lifetimeMs = 0L
            }
        }
        assertEquals(ExpiryChangeResult.APPLIED, manager.setNeverExpire(true))
        assertEquals("604800000", retainedAtC11)
        assertEquals(listOf(neverExpire), shell.executed)
        assertEquals(ExpiryChoiceStatus.ON, manager.expiryStatus())
    }

    @Test
    fun `cle absente, la valeur d'origine est le defaut du systeme`() = runTest {
        switch.lifetimeMs = null
        applyExpiryCommands()
        assertEquals(ExpiryChangeResult.APPLIED, manager.setNeverExpire(true))
        assertEquals("default", store.expiryOriginal)
    }

    @Test
    fun `deja sans expiration ou hors plage, rien n'est propose ni ecrit`() = runTest {
        listOf(0L, -1L, 400_000_000_000L).forEach { current ->
            switch.lifetimeMs = current
            assertEquals(ExpiryChangeResult.NOT_APPLICABLE, manager.setNeverExpire(true))
            assertEquals(ExpiryChoiceStatus.NOT_APPLICABLE, manager.expiryStatus())
        }
        assertEquals(emptyList<String>(), shell.executed)
        assertNull(store.expiryOriginal)
    }

    @Test
    fun `sans expiration hors connexion ne fait rien`() = runTest {
        switch.lifetimeMs = sevenDays
        shell.state.value = ConnectionState.Disconnected
        assertEquals(ExpiryChangeResult.NOT_CONNECTED, manager.setNeverExpire(true))
        assertEquals(emptyList<String>(), shell.executed)
        assertNull(store.expiryOriginal)
    }

    @Test
    fun `delai relu different de 0 apres C11, la valeur est oubliee`() = runTest {
        switch.lifetimeMs = sevenDays
        assertEquals(ExpiryChangeResult.FAILED, manager.setNeverExpire(true))
        assertEquals(listOf(neverExpire), shell.executed)
        assertNull(store.expiryOriginal)
        assertEquals(ExpiryChoiceStatus.OFF, manager.expiryStatus())
    }

    @Test
    fun `retablir la valeur d'origine`() = runTest {
        switch.lifetimeMs = sevenDays
        applyExpiryCommands()
        manager.setNeverExpire(true)
        shell.executed.clear()

        assertEquals(ExpiryChangeResult.APPLIED, manager.setNeverExpire(false))
        assertEquals(listOf("settings put global adb_allowed_connection_time 604800000"), shell.executed)
        assertEquals(sevenDays, switch.lifetimeMs)
        assertNull(store.expiryOriginal)
        assertEquals(ExpiryChoiceStatus.OFF, manager.expiryStatus())
    }

    @Test
    fun `retablir le defaut du systeme supprime la cle`() = runTest {
        switch.lifetimeMs = 0L
        store.expiryOriginal = "default"
        applyExpiryCommands()
        assertEquals(ExpiryChangeResult.APPLIED, manager.setNeverExpire(false))
        assertEquals(listOf(ShellCommand.resetAuthorizationExpiry().text), shell.executed)
        assertNull(switch.lifetimeMs)
        assertNull(store.expiryOriginal)
    }

    @Test
    fun `delai change par un autre outil, rien n'est ecrit`() = runTest {
        switch.lifetimeMs = 86_400_000L
        store.expiryOriginal = "604800000"
        assertEquals(ExpiryChangeResult.APPLIED, manager.setNeverExpire(false))
        assertEquals(emptyList<String>(), shell.executed)
        assertNull(store.expiryOriginal)
        assertEquals(86_400_000L, switch.lifetimeMs)
    }

    @Test
    fun `valeur retenue invalide, oubliee sans aucune commande`() = runTest {
        listOf("abc", "400000000000").forEach { bad ->
            switch.lifetimeMs = 0L
            store.expiryOriginal = bad
            assertEquals(ExpiryChangeResult.FAILED, manager.setNeverExpire(false))
            assertNull(store.expiryOriginal)
        }
        assertEquals(emptyList<String>(), shell.executed)
    }

    @Test
    fun `retablir hors connexion, puis a la connexion suivante`() = runTest {
        switch.lifetimeMs = 0L
        store.expiryOriginal = "604800000"
        shell.state.value = ConnectionState.Disconnected
        applyExpiryCommands()

        assertEquals(ExpiryChangeResult.RESTORE_PENDING, manager.setNeverExpire(false))
        assertTrue(store.expiryRestorePending)
        assertEquals(ExpiryChoiceStatus.RESTORE_PENDING, manager.expiryStatus())
        assertEquals(emptyList<String>(), shell.executed)

        shell.state.value = ConnectionState.Connected(ConnectionMethod.WIRELESS)
        manager.onConnected()
        assertEquals(listOf("settings put global adb_allowed_connection_time 604800000"), shell.executed)
        assertNull(store.expiryOriginal)
        assertFalse(store.expiryRestorePending)
    }

    @Test
    fun `connexion perdue pendant le retablissement, il reste en attente`() = runTest {
        switch.lifetimeMs = 0L
        store.expiryOriginal = "604800000"
        shell.disconnectAt = "settings put"
        assertEquals(ExpiryChangeResult.RESTORE_PENDING, manager.setNeverExpire(false))
        assertTrue(store.expiryRestorePending)
        assertEquals("604800000", store.expiryOriginal)
    }

    @Test
    fun `duree de vie lue par le gestionnaire`() {
        switch.lifetimeMs = sevenDays
        assertEquals(AuthorizationLifetime.Days(7), manager.lifetime())
        switch.lifetimeMs = 0L
        assertEquals(AuthorizationLifetime.Never, manager.lifetime())
    }

    // --- Désactiver (US3)

    private val revoke = ShellCommand.revokeWriteSecureSettings().text

    /** Option active, permission accordée par l'appli. */
    private fun activeGrantedByApp() {
        store.autoReconnect = true
        store.rightGrantedByApp = true
        switch.rightHeld = true
    }

    @Test
    fun `desactiver retire la permission accordee par l'appli, prefs ecrites avant C10`() = runTest {
        activeGrantedByApp()
        var autoReconnectAtC10: Boolean? = null
        var revokePendingAtC10: Boolean? = null
        shell.onExec = { command ->
            if (command == revoke) {
                autoReconnectAtC10 = store.autoReconnect
                revokePendingAtC10 = store.revokePending
                switch.rightHeld = false
            }
        }
        assertEquals(DisableResult.REVOKED, manager.disable())
        assertEquals(false, autoReconnectAtC10)
        assertEquals(true, revokePendingAtC10)
        assertEquals(listOf(revoke), shell.executed)
        assertFalse(store.revokePending)
        assertFalse(store.rightGrantedByApp)
        assertEquals(AutoReconnectStatus.INACTIVE, manager.status())
    }

    @Test
    fun `permission accordee hors de l'appli, laissee en place`() = runTest {
        store.autoReconnect = true
        switch.rightHeld = true
        assertEquals(DisableResult.KEPT_EXTERNAL_GRANT, manager.disable())
        assertEquals(emptyList<String>(), shell.executed)
        assertFalse(store.revokePending)
        assertFalse(store.autoReconnect)
        assertTrue(switch.rightHeld)
    }

    @Test
    fun `desactiver hors connexion, retrait en attente`() = runTest {
        activeGrantedByApp()
        shell.state.value = ConnectionState.Disconnected
        assertEquals(DisableResult.REVOKE_PENDING, manager.disable())
        assertEquals(emptyList<String>(), shell.executed)
        assertTrue(store.revokePending)
        assertFalse(store.autoReconnect)
    }

    @Test
    fun `connexion perdue pendant C10, retrait en attente`() = runTest {
        activeGrantedByApp()
        shell.disconnectAt = "pm revoke"
        assertEquals(DisableResult.REVOKE_PENDING, manager.disable())
        assertTrue(store.revokePending)
    }

    @Test
    fun `retrait en attente a la connexion suivante`() = runTest {
        store.revokePending = true
        store.rightGrantedByApp = true
        switch.rightHeld = true
        shell.onExec = { if (it == revoke) switch.rightHeld = false }
        manager.onConnected()
        assertEquals(listOf(revoke), shell.executed)
        assertFalse(store.revokePending)
        assertFalse(store.rightGrantedByApp)
    }

    @Test
    fun `retrait en attente, permission deja absente`() = runTest {
        store.revokePending = true
        store.rightGrantedByApp = true
        switch.rightHeld = false
        manager.onConnected()
        assertEquals(emptyList<String>(), shell.executed)
        assertFalse(store.revokePending)
        assertFalse(store.rightGrantedByApp)
    }

    @Test
    fun `desactiver retablit aussi le delai d'expiration`() = runTest {
        activeGrantedByApp()
        store.expiryOriginal = "604800000"
        switch.lifetimeMs = 0L
        applyExpiryCommands()
        assertEquals(DisableResult.REVOKED, manager.disable())
        assertEquals(listOf("settings put global adb_allowed_connection_time 604800000", revoke), shell.executed)
        assertNull(store.expiryOriginal)
    }

    @Test
    fun `desactiver hors connexion met aussi le delai en attente`() = runTest {
        activeGrantedByApp()
        store.expiryOriginal = "default"
        switch.lifetimeMs = 0L
        shell.state.value = ConnectionState.Disconnected
        assertEquals(DisableResult.REVOKE_PENDING, manager.disable())
        assertTrue(store.expiryRestorePending)
        assertEquals(ExpiryChoiceStatus.RESTORE_PENDING, manager.expiryStatus())
    }

    @Test
    fun `desactiver ne coupe ni la connexion ni le sans-fil`() = runTest {
        activeGrantedByApp()
        val before = shell.state.value
        manager.disable()
        assertEquals(before, shell.state.value)
        assertFalse(shell.executed.any { it.startsWith("settings put global adb_wifi_enabled") })
    }
}
