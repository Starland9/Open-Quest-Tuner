package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Casque simulé : enregistre le texte de chaque commande exécutée et renvoie des résultats
 * scriptés par préfixe de commande (succès vide par défaut).
 */
class FakeShellBackend : ShellBackend {

    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected(ConnectionMethod.WIRELESS))

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
        return responses.lastOrNull { (prefix, _) -> command.text.startsWith(prefix) }?.second
            ?: ShellResult(exitCode = 0, output = "")
    }
}
