package io.github.openquesttuner.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Principe IV : le cœur reste du Kotlin pur, testable sans casque ni Android. */
class CoreArchitectureTest {

    // Les tests unitaires tournent depuis le module `app`, parfois depuis la racine du projet.
    private val coreDir = listOf("src/main/java", "app/src/main/java")
        .map { File(it, "io/github/openquesttuner/core") }
        .first { it.isDirectory }

    @Test
    fun `le coeur n'importe rien d'Android`() {
        val sources = coreDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Aucun fichier trouvé dans $coreDir", sources.isNotEmpty())

        val offenders = sources.flatMap { file ->
            file.readLines()
                .filter { it.trimStart().startsWith("import android.") || it.trimStart().startsWith("import androidx.") }
                .map { "${file.name}: ${it.trim()}" }
        }
        assertTrue("Imports Android interdits dans core/ :\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
