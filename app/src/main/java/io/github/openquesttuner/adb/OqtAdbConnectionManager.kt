package io.github.openquesttuner.adb

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire libadb-android configuré pour OpenQuestTuner.
 *
 * Ne **jamais** appeler [close] : libadb y détruit la clé privée. Utiliser [disconnect].
 */
class OqtAdbConnectionManager(identity: AdbIdentity) : AbsAdbConnectionManager() {

    private val key: PrivateKey = identity.privateKey
    private val cert: Certificate = identity.certificate

    init {
        // Obligatoire : avec la valeur par défaut (BASE), le TLS n'est jamais négocié (research.md R3).
        setApi(Build.VERSION.SDK_INT)
        // Délai infini par défaut. 30 s laissent le temps d'accepter l'invite d'autorisation.
        setTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = key

    override fun getCertificate(): Certificate = cert

    override fun getDeviceName(): String = "OpenQuestTuner"

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 30L
    }
}
