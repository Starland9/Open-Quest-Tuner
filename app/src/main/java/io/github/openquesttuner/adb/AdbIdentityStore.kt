package io.github.openquesttuner.adb

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/** Identité de l'appli auprès d'adbd : clé RSA et certificat auto-signé (appairage TLS). */
class AdbIdentity(val privateKey: PrivateKey, val certificate: X509Certificate)

/**
 * Crée puis conserve l'identité ADB dans le stockage privé de l'appli. La clé n'est jamais
 * journalisée, exportée ni transmise (FR-027) ; `allowBackup=false` l'exclut des sauvegardes.
 */
class AdbIdentityStore(private val dir: File) {

    private val keyFile = File(dir, "adbkey.pk8")
    private val certFile = File(dir, "adbkey.crt")

    @Synchronized
    fun loadOrCreate(): AdbIdentity = load() ?: create()

    private fun load(): AdbIdentity? {
        if (!keyFile.isFile || !certFile.isFile) return null
        return try {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            val certificate = certFile.inputStream().use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            AdbIdentity(key, certificate)
        } catch (e: GeneralSecurityException) {
            null // Fichiers illisibles : on régénère une paire (il faudra réappairer).
        } catch (e: IOException) {
            null
        }
    }

    private fun create(): AdbIdentity {
        dir.mkdirs()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair()
        val subject = X500Name("CN=OpenQuestTuner")
        val now = System.currentTimeMillis()
        val certificate = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                // Un jour de marge pour une horloge légèrement en retard.
                Date(now - TimeUnit.DAYS.toMillis(1)),
                Date(now + TimeUnit.DAYS.toMillis(VALIDITY_DAYS)),
                subject,
                keyPair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)),
        )
        writeAtomically(keyFile, keyPair.private.encoded)
        writeAtomically(certFile, certificate.encoded)
        return AdbIdentity(keyPair.private, certificate)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) throw IOException("Impossible d'écrire ${target.name}")
    }

    private companion object {
        const val KEY_SIZE = 2048
        const val VALIDITY_DAYS = 30L * 365
    }
}
