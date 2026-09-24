package io.github.openquesttuner

import io.github.openquesttuner.core.ShellCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Garde de build des contrats C9 et C10 : ces commandes visent le paquet réel de l'appli, et
 * aucune autre (specs/002-standalone-reconnect/contracts/shell-commands.md).
 */
class AppPackageTest {

    // Les tests unitaires tournent depuis le module `app`, parfois depuis la racine du projet.
    private val buildFile = listOf("build.gradle.kts", "app/build.gradle.kts")
        .map(::File)
        .first { it.isFile && it.readText().contains("applicationId") }

    @Test
    fun `applicationId est egal a APP_PACKAGE`() {
        val applicationId = Regex("""applicationId\s*=\s*"([^"]+)"""").find(buildFile.readText())?.groupValues?.get(1)
        assertEquals(ShellCommand.APP_PACKAGE, applicationId)
    }

    @Test
    fun `aucune variante ne change l'identifiant de l'appli`() {
        assertFalse(
            "applicationIdSuffix ferait viser un autre paquet à C9 et C10",
            buildFile.readText().contains("applicationIdSuffix"),
        )
    }
}
