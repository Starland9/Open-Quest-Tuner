package io.github.openquesttuner.core

/** Lecture des sorties du shell (contracts/shell-commands.md). */
object ShellOutput {

    const val EXIT_MARKER = "__OQT_EXIT__:"

    private val GETPROP_LINE = Regex("""^\[(debug\.oculus\.[^\]]+)\]: \[(.*)\]$""")

    /**
     * Sépare la sortie du code de sortie, lu après le **dernier** marqueur. Sans marqueur (flux
     * coupé), le code vaut `null` et le résultat est un échec.
     */
    fun parse(raw: String): ShellResult {
        val text = raw.replace("\r", "")
        val markerIndex = text.lastIndexOf(EXIT_MARKER)
        if (markerIndex < 0) return ShellResult(null, text.trimEnd())
        val code = text.substring(markerIndex + EXIT_MARKER.length)
            .substringBefore('\n')
            .trim()
            .toIntOrNull()
        return ShellResult(code, text.substring(0, markerIndex).trimEnd())
    }

    /** Propriétés `debug.oculus.*` non vides d'une sortie `getprop`, triées par clé. */
    fun parseGetprop(output: String): Map<String, String> = output.lineSequence()
        .mapNotNull { GETPROP_LINE.matchEntire(it.trim()) }
        .map { it.groupValues[1] to it.groupValues[2] }
        .filter { (_, value) -> value.isNotEmpty() }
        .toMap()
        .toSortedMap()

    /** Un réglage `settings get` booléen est actif seulement s'il vaut `1`. */
    fun isSettingEnabled(output: String): Boolean = output.trim() == "1"

    /** `am start` peut renvoyer 0 tout en échouant : il l'annonce par une ligne `Error`. */
    fun isLaunchError(output: String): Boolean =
        output.lineSequence().any { it.trimStart().startsWith("Error") }
}
