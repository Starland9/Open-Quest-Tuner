# Contrat : ShellBackend et connexion

`ShellBackend` est la seule porte d'accès au shell du casque (constitution, principe IV). Le cœur
(`core/`) ne connaît que cette interface. Pour l'instant, la seule implémentation est
`AdbShellBackend` (libadb-android) ; une implémentation Shizuku pourrait être ajoutée plus tard,
sans toucher au cœur.

## Interface (cœur, Kotlin pur)

```kotlin
interface ShellBackend {
    /** État courant de la connexion, observé par l'interface. */
    val state: StateFlow<ConnectionState>

    /**
     * Exécute une commande de la liste fermée (voir shell-commands.md).
     * @throws ShellUnavailableException si aucune connexion n'est établie ou si elle vient
     *         d'être perdue. Dans ce cas, state passe à Disconnected.
     */
    suspend fun exec(command: ShellCommand): ShellResult
}

data class ShellResult(val exitCode: Int?, val output: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

class ShellUnavailableException(cause: Throwable? = null) : IOException(cause)
```

## États de connexion

```kotlin
enum class ConnectionMethod { WIRELESS, PC }

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Pairing : ConnectionState
    data class Connecting(val method: ConnectionMethod) : ConnectionState
    data class Connected(val method: ConnectionMethod) : ConnectionState
    data class Failed(val method: ConnectionMethod?, val reason: FailureReason) : ConnectionState
}

enum class FailureReason {
    PORT_CLOSED,            // connexion refusée : port 5555 fermé, ou débogage sans fil désactivé
    NOT_AUTHORIZED,         // invite d'autorisation refusée ou délai dépassé
    PAIRING_REQUIRED,       // le port TLS exige un appairage préalable
    PAIRING_CODE_REJECTED,  // code faux ou expiré
    SERVICE_NOT_FOUND,      // découverte mDNS du port de connexion sans résultat
    UNKNOWN,
}
```

Les transitions sont détaillées dans [data-model.md](../data-model.md#connexion). Chaque
`FailureReason` correspond à un message localisé et à une action suggérée (FR-004).

## API propre à AdbShellBackend (couche Android)

Ces fonctions ne font pas partie de l'interface, qui ne concerne que l'exécution. Elles sont
appelées uniquement par le ViewModel.

| Fonction | Effet | Notes |
|---|---|---|
| `suspend fun pair(port: Int, code: String): Boolean` | Appairage TLS sur `127.0.0.1:port`, puis `connectWireless()` en cas de succès | `code` : exactement 6 chiffres ; `port` dans 1–65535. La validation est faite par le cœur (`ConnectionInput`) avant l'appel. |
| `suspend fun connectWireless(port: Int? = null): Boolean` | Découverte mDNS du port TLS (`port == null`), ou connexion au port donné | Délai de découverte : 10 s |
| `suspend fun switchToWireless(): WirelessSwitchResult` | Passage en sans fil depuis `Connected(PC)` : C7, puis attente de `adb_wifi_enabled=1` (60 s max, C8), puis `connectWireless()`. En cas d'échec du sans-fil, reconnexion silencieuse au port 5555. | Résultats : `SWITCHED`, `NOT_ACCEPTED` (délai dépassé, l'appli reste connectée via PC), `WIRELESS_FAILED`, `NOT_CONNECTED` |
| `suspend fun connectPc(): Boolean` | Connexion à `127.0.0.1:5555` | Délai d'autorisation : 30 s, pour laisser l'utilisateur accepter l'invite dans le casque |
| `suspend fun reconnectLast()` | Rejoue la dernière méthode réussie, puis l'autre en repli, sans passer par l'état `Failed`. En sans fil : découverte mDNS d'abord, car le port TLS change à chaque activation, puis le port manuel enregistré. Via PC : `127.0.0.1:5555`. | Ordre donné par `ConnectionPolicy.reconnectAttempts`. Sur Quest 3, le port 5555 survit au redémarrage alors que le TLS est coupé. Si tout échoue, l'état est `Disconnected` (FR-005) |
| `suspend fun disconnect()` | Ferme la connexion | Appelle `disconnect()` de libadb, **jamais** `close()`, qui détruirait la clé privée |

Les décisions de connexion vivent dans le cœur, dans `core/ConnectionPolicy.kt`, testé en JVM :
- classification des exceptions en `FailureReason` ;
- boucle « connexion, probe, nouvelles tentatives » ;
- ordre des cibles de reconnexion.

`AdbShellBackend` ne fait que brancher libadb-android dessus (principe IV).

Règles d'implémentation, reprises des pièges constatés dans libadb-android 3.1.1 :
- `setApi(Build.VERSION.SDK_INT)` est obligatoire. Avec la valeur par défaut (`BASE`), le TLS
  n'est pas négocié.
- `setTimeout(...)` est obligatoire, car le délai par défaut est infini.
- `connect()` renvoie `false` si le délai expire. Il faut alors appeler `disconnect()` pour
  libérer le socket à moitié ouvert.
- La lecture d'un `AdbStream` peut lever `IOException("Stream closed.")` après la dernière donnée.
  C'est une fin de flux normale dès lors que des données ont été reçues.
- Les opérations de connexion sont sérialisées par un `Mutex` et exécutées sur `Dispatchers.IO`.
- **Délais maximaux** : libadb peut bloquer indéfiniment, soit parce que la réponse à l'ouverture
  d'un flux lui échappe (réveil perdu, research.md R3), soit parce qu'adbd ne répond pas (constaté
  sur Quest 3 après un redémarrage). Chaque exécution passe par `withTimeoutOrNull` et
  `runInterruptible` : 2 s pour le probe, 3 s pour une commande.
- **Essais d'une commande** (amendé le 2026-09-23) : une commande sans réponse est rejouée sur la
  même connexion, jusqu'à 3 essais au total (`ConnectionPolicy.withRetries`). La connexion n'est
  déclarée perdue qu'après 3 échecs consécutifs. Toutes les commandes C1 à C8 sont idempotentes :
  les rejouer est sans effet de bord.
- **Fin de lecture** : la lecture d'une sortie s'arrête dès la ligne complète du marqueur
  `__OQT_EXIT__:<code>`, toujours imprimée en dernier (`ShellOutput.isComplete`), sans attendre la
  fermeture du flux.
- **Vérification après connexion** (libadb-android #34, research.md R3) : dès que `connect()`
  renvoie `true`, exécuter C6 `probe()`. S'il échoue, appeler `disconnect()` et refaire la
  connexion, au maximum 2 nouvelles tentatives. L'état ne passe à `Connected` qu'après un
  `probe()` réussi.
