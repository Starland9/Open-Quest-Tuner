package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Casque simulé : enregistre le texte de chaque commande exécutée et renvoie des résultats
 * scriptés par préfixe de commande (succès vide par défaut).
 */
class FakeShellBackend : ShellBackend {

    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected(ConnectionMethod.WIRELESS))

    private val properties = mutableMapOf<String, String>()
    val executed = mutableListOf<String>()

    private val responses = mutableListOf<Pair<String, ShellResult>>()

    /** Préfixe de commande à partir duquel la connexion est « perdue » ; `""` : dès la première. */
    var disconnectAt: String? = null

    /** Appelé avec le texte de chaque commande exécutée : simule son effet (permission accordée…). */
    var onExec: (String) -> Unit = {}

    /** La dernière réponse scriptée dont le préfixe correspond l'emporte. */
    fun respond(prefix: String, result: ShellResult) {
        responses += prefix to result
    }

    override suspend fun exec(command: ShellCommand): ShellResult {
        disconnectAt?.let { prefix ->
            if (command.text.startsWith(prefix)) {
                state.value = ConnectionState.Disconnected
                throw ShellUnavailableException()
            }
        }
        executed += command.text
        onExec(command.text)
        responses.lastOrNull { (prefix, _) -> command.text.startsWith(prefix) }?.second?.let { return it }
        Regex("setprop ([a-zA-Z0-9_.]+) (.*)").matchEntire(command.text)?.let { properties[it.groupValues[1]] = it.groupValues[2].removeSurrounding("'") }
        if (command.text == "getprop") return ShellResult(0, properties.entries.joinToString("\n") { "[${it.key}]: [${it.value}]" })
        if (command.text.startsWith("getprop ")) return ShellResult(0, properties[command.text.removePrefix("getprop ")].orEmpty())
        return ShellResult(0, "")
    }
}
