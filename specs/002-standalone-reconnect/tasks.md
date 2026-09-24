---

description: "Liste des tâches : Reconnexion autonome après un redémarrage (sans PC)"
---

# Tasks: Reconnexion autonome après un redémarrage (sans PC)

**Input**: documents de conception dans `specs/002-standalone-reconnect/` :
- [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md) ;
- contrats : [shell-commands.md](contracts/shell-commands.md) (C9 à C13),
  [wireless-switch.md](contracts/wireless-switch.md) ;
- [quickstart.md](quickstart.md).

**Prerequisites**: plan.md et spec.md (obligatoires), research.md, data-model.md, contracts/. Le
MVP (`specs/001-game-profiles-mvp/`) est implémenté : ce document ne liste que les changements.

**Tests**: **OBLIGATOIRES** pour le cœur `core/`. La constitution (principe IV) l'impose :
« Chaque règle métier du cœur DOIT être couverte par des tests unitaires JVM ». Ce sont des tests
JUnit 4 et kotlinx-coroutines-test dans `app/src/test/`. Chaque test est écrit avant le code
qu'il couvre, et doit échouer tant que ce code n'existe pas. La couche Android (Settings,
ConnectivityManager, UI) est validée sur casque avec [quickstart.md](quickstart.md).

**Organization**: les tâches sont regroupées par user story, dans l'ordre des priorités de la
spec : US1 (P1), puis US2 et US4 (P2), puis US3 (P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]** : peut être faite en parallèle (fichier différent, aucune dépendance sur une tâche
  inachevée).
- **[Story]** : user story concernée (US1 à US4, voir [spec.md](spec.md)).
- Chaque description donne le chemin exact du fichier.

## Path Conventions

- Projet Android, un seul module `app/`.
- Code : `app/src/main/java/io/github/openquesttuner/` (abrégé `…/` ci-dessous).
- Tests JVM : `app/src/test/java/io/github/openquesttuner/` (abrégé `test/…/` ci-dessous).
- Le package `core/` NE DOIT importer aucune classe `android.*` ou `androidx.*` (principe IV,
  vérifié par `CoreArchitectureTest`).
- Toute chaîne visible par l'utilisateur va dans `app/src/main/res/values/strings.xml`
  (anglais) **et** dans `app/src/main/res/values-fr/strings.xml` (français), jamais en dur
  (FR-020). `StringsParityTest` vérifie la parité. Les textes français proposés ci-dessous sont
  des suggestions ; l'anglais doit en être l'équivalent exact.
- Journaux : tag `OqtAdb` pour la connexion. Jamais de matériel de clé dans les journaux (FR-027
  du MVP).

---

## Phase 1: Setup

**Purpose**: déclarer la permission et partir d'une base verte.

- [X] T001 Dans `app/src/main/AndroidManifest.xml`, ajouter à côté des autres permissions :
  `<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" tools:ignore="ProtectedPermissions" />`.
  Ajouter au-dessus le commentaire : « Accordée une seule fois par le shell de l'appli (C9), sur
  choix de l'utilisateur, pour réactiver le débogage sans fil après un redémarrage (spec 002,
  research.md R1). Jamais accordée à l'installation. » Sans cette déclaration, `pm grant` échoue
  avec « has not requested permission ».
- [X] T002 Lancer `./gradlew test assembleDebug` avant tout changement, et noter que la base est
  verte. En cas d'échec, s'arrêter et le signaler.

---

## Phase 2: Foundational (prérequis bloquants)

**Purpose**: interfaces du cœur, commandes C9 et C10, gardes de sécurité, stockage et switch
Android. Toutes les user stories en dépendent.

**⚠️ CRITICAL**: aucune user story ne peut commencer avant la fin de cette phase.

### Tests (écrits d'abord, ils doivent échouer)

- [X] T003 [P] Dans `test/…/core/ShellCommandsTest.kt`, ajouter des tests pour C9 et C10
  ([contracts/shell-commands.md](contracts/shell-commands.md)) :
  - `ShellCommand.APP_PACKAGE == "io.github.openquesttuner"` ;
  - `ShellCommand.grantWriteSecureSettings().text ==
    "pm grant 'io.github.openquesttuner' android.permission.WRITE_SECURE_SETTINGS"` ;
  - `ShellCommand.revokeWriteSecureSettings().text ==
    "pm revoke 'io.github.openquesttuner' android.permission.WRITE_SECURE_SETTINGS"` ;
  - ajouter les deux commandes au test existant qui vérifie qu'aucune commande générée ne
    contient `persist.`.
- [X] T004 [P] Créer `test/…/AppPackageTest.kt` (garde de build, contrat C9/C10). Trouver le
  fichier de build comme `StringsParityTest` trouve `res` :
  `listOf("build.gradle.kts", "app/build.gradle.kts").map(::File).first { it.isFile && it.readText().contains("applicationId") }`.
  Vérifier :
  - que la valeur capturée par `Regex("""applicationId\s*=\s*"([^"]+)"""")` est égale à
    `ShellCommand.APP_PACKAGE` ;
  - que le fichier ne contient pas `applicationIdSuffix`.
- [X] T005 [P] Créer `test/…/SystemSettingsWriteTest.kt` (règle d'écriture unique,
  [contracts/wireless-switch.md](contracts/wireless-switch.md), FR-015). Parcourir tous les
  `.kt` de `src/main/java` ou `app/src/main/java` et chercher
  `Regex("""Settings\.(Global|Secure|System)\.put\w*\(""")`. Le test échoue :
  - si une correspondance apparaît dans un autre fichier qu'`adb/AndroidWirelessSwitch.kt` ;
  - si ce fichier en contient zéro ou plus d'une ;
  - si la ligne trouvée ne contient pas `KEY_ADB_WIFI_ENABLED, 1)`.

  Le message d'échec liste les fichiers et les lignes en cause.
- [X] T006 [P] Créer `test/…/core/FakeWirelessDebuggingSwitch.kt`, qui implémente
  `WirelessDebuggingSwitch` :
  - propriétés modifiables : `rightHeld = false`, `debuggingEnabled = true`, `onWifi = true`,
    `wirelessEnabled = false`, `lifetimeMs: Long? = 0L`, `readThrows = false` ;
  - `acceptAfterReads: Int? = 1` : après `enableWirelessDebugging()`, la valeur lue passe à
    `true` au bout de N lectures ; `null` = jamais (fenêtre refusée) ;
  - compteurs `enableCalls` et `reads` ;
  - `enableWirelessDebugging()` lève `SecurityException` si `rightHeld` est faux ;
  - `isWirelessDebuggingEnabled()` lève `IllegalStateException` si `readThrows`.
- [X] T007 [P] Créer `test/…/core/InMemoryAutoReconnectStore.kt`, qui implémente
  `AutoReconnectStore` avec des propriétés en mémoire, aux mêmes valeurs par défaut que
  data-model.md : `false`, `false`, `false`, `null`, `false`.
- [X] T008 [P] Dans `test/…/core/FakeShellBackend.kt`, ajouter
  `var onExec: (String) -> Unit = {}`, appelé avec le texte de la commande juste après
  `executed += command.text`. Il permet aux tests de simuler l'effet d'une commande, par exemple
  la permission accordée après C9. Les tests existants ne changent pas.

### Implémentation

- [X] T009 [P] Créer `…/core/WirelessDebuggingSwitch.kt` : l'interface du contrat
  [wireless-switch.md](contracts/wireless-switch.md), avec six fonctions synchrones et leur KDoc
  :
  - `hasWriteRight(): Boolean` ;
  - `isDebuggingEnabled(): Boolean` ;
  - `isOnWifi(): Boolean` ;
  - `isWirelessDebuggingEnabled(): Boolean` ;
  - `authorizationLifetimeMs(): Long?` (`null` = clé absente, délai par défaut du système ;
    `0` = jamais) ;
  - `enableWirelessDebugging()` (« seule écriture de réglage système de l'appli »).
- [X] T010 [P] Créer `…/core/AutoReconnectStore.kt` : une interface de cinq `var`, avec une KDoc
  qui reprend le tableau « Option persistée » de data-model.md :
  - `autoReconnect: Boolean` ;
  - `rightGrantedByApp: Boolean` : « C9 l'a fait passer d'absente à détenue » ;
  - `revokePending: Boolean` ;
  - `expiryOriginal: String?` : `"default"` (clé absente sur le casque) ou un nombre de
    millisecondes ; absente = le choix n'a rien changé ;
  - `expiryRestorePending: Boolean`.
- [X] T011 Dans `…/core/ShellCommands.kt`, ajouter au companion :
  - `const val APP_PACKAGE = "io.github.openquesttuner"` ;
  - `/** C9 */ fun grantWriteSecureSettings()` et `/** C10 */ fun revokeWriteSecureSettings()`,
    sans paramètre, qui produisent exactement les textes de T003.

  La KDoc rappelle que ces deux commandes ne visent que l'appli elle-même (principe I). T003 et
  T004 passent.
- [X] T012 [P] Créer `…/core/AutoReconnectPolicy.kt`, avec pour l'instant les seuls types de
  data-model.md :
  - `enum class AutoReconnectStatus { INACTIVE, ACTIVE, NEEDS_REACTIVATION }` ;
  - `enum class ReconnectIssue { DEBUGGING_DISABLED, NO_WIFI, RIGHT_LOST, NETWORK_NOT_ALLOWED, AUTHORIZATION_LOST, WIRELESS_NOT_STARTED, UNKNOWN }`,
    dans cet ordre, qui est aussi l'ordre de priorité ;
  - `sealed interface PrepareResult { data object Ready; data class Issue(val issue: ReconnectIssue) }` ;
  - `sealed interface ReconnectOutcome { data object Connected; data class Failed(val issue: ReconnectIssue?) }` ;
  - `enum class EnableResult { ENABLED, UNAVAILABLE, NOT_CONNECTED }` ;
  - `enum class DisableResult { REVOKED, REVOKE_PENDING, KEPT_EXTERNAL_GRANT }`.
- [X] T013 [P] Dans `…/core/QuestModel.kt`, ajouter le dernier paramètre du constructeur,
  `val autoReconnectVerified: Boolean = false`. Tous les modèles gardent `false` (research.md
  R8). KDoc : « Reconnexion autonome vérifiée sur ce modèle et consignée dans
  docs/compatibility.md ; sinon, badge « expérimental » (FR-018). »
- [X] T014 Dans `…/adb/ConnectionPrefs.kt`, implémenter `AutoReconnectStore`. Clés :
  - `auto_reconnect`, `right_granted_by_app`, `revoke_pending` et `expiry_restore_pending` :
    `getBoolean`/`putBoolean`, défaut `false` ;
  - `expiry_original` : `getString`/`putString`, et `remove` quand on affecte `null`.

  Garder `lastMethod` et `lastWirelessPort` inchangés.
- [X] T015 Créer `…/adb/AndroidWirelessSwitch.kt(context: Context) : WirelessDebuggingSwitch`,
  conforme à [wireless-switch.md](contracts/wireless-switch.md) :
  - `private const val KEY_ADB_WIFI_ENABLED = "adb_wifi_enabled"` et
    `KEY_ADB_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time"` ;
  - `hasWriteRight()` : `context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PERMISSION_GRANTED` ;
  - `isDebuggingEnabled()` : `Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 1) == 1`,
    et toute exception donne `true` ;
  - `isOnWifi()` : `ConnectivityManager.getNetworkCapabilities(activeNetwork)?.hasTransport(TRANSPORT_WIFI) == true` ;
  - `isWirelessDebuggingEnabled()` : `Settings.Global.getInt(cr, KEY_ADB_WIFI_ENABLED, 0) == 1`,
    et toute exception donne `false` ;
  - `authorizationLifetimeMs()` :
    `Settings.Global.getString(cr, KEY_ADB_ALLOWED_CONNECTION_TIME)?.trim()?.toLongOrNull()` ;
  - `enableWirelessDebugging()` : `Settings.Global.putInt(cr, KEY_ADB_WIFI_ENABLED, 1)`, sur une
    seule ligne. C'est la seule écriture de `Settings` de toute l'appli ; laisser la
    `SecurityException` remonter.

  T005 passe.

**Checkpoint**: `./gradlew test` est vert (T003 à T005 compris). Le comportement de l'appli est
inchangé.

---

## Phase 3: User Story 1 - Se reconnecter sans PC après un redémarrage (Priority: P1) 🎯 MVP

**Goal**: une fois l'option activée, l'appli réactive le débogage sans fil à son démarrage et se
reconnecte sans PC (FR-001 à FR-008, FR-012, FR-016 à FR-020).

**Independent Test**: quickstart, scénarios 1.1 à 1.9. En particulier, après 5 redémarrages,
l'appli affiche « Connecté (sans fil) » en moins de 15 s, sans action ni PC.

### Tests for User Story 1 (écrits d'abord, ils doivent échouer)

- [X] T016 [P] [US1] Créer `test/…/core/AutoReconnectPolicyTest.kt`, avec
  `@OptIn(ExperimentalCoroutinesApi::class)` et `runTest`, qui saute les `delay`. À couvrir :
  - **`status(optionEnabled, rightHeld)`** : `(false, *)` → `INACTIVE`, `(true, true)` →
    `ACTIVE`, `(true, false)` → `NEEDS_REACTIVATION`.
  - **`prepareWireless(switch)`**, dans l'ordre de data-model.md, « Préparation du sans-fil » :
    1. `debuggingEnabled = false` → `Issue(DEBUGGING_DISABLED)`, `enableCalls == 0` ;
    2. `onWifi = false` → `Issue(NO_WIFI)`, `enableCalls == 0` ;
    3. `wirelessEnabled = true`, même avec `rightHeld = false` → `Ready`, `enableCalls == 0` ;
    4. `rightHeld = false` → `Issue(RIGHT_LOST)`, `enableCalls == 0` ;
    5. `rightHeld = true`, `acceptAfterReads = 3` → `Ready` et `enableCalls == 1` ;
    6. `acceptAfterReads = null` → `Issue(NETWORK_NOT_ALLOWED)`, avec
       `currentTime >= ConnectionPolicy.WIRELESS_ACCEPT_TIMEOUT_MS` ;
    7. `readThrows = true`, avec `rightHeld = true` → la lecture compte comme « désactivé »,
       l'appli écrit, et le résultat final est `Issue(NETWORK_NOT_ALLOWED)` au bout de 60 s.
  - **`issueFor(reason)`** : `PAIRING_REQUIRED` et `NOT_AUTHORIZED` → `AUTHORIZATION_LOST` ;
    `SERVICE_NOT_FOUND` → `WIRELESS_NOT_STARTED` ; toute autre valeur, et `null` → `UNKNOWN`.
  - **`reconnect(attempts, prepare, connect)`**, avec des lambdas qui enregistrent les appels :
    - aucune tentative marquée `prepareWireless` : `prepare` n'est jamais appelé, et si tout
      échoue le résultat est `Failed(null)` (comportement du MVP) ;
    - `prepare` renvoie `Issue(NO_WIFI)` : les tentatives `WIRELESS` restantes sont sautées, et
      celle du PC est essayée. PC réussi → `Connected` ; PC en échec → `Failed(NO_WIFI)` ;
    - `prepare` renvoie `Ready`, le sans-fil échoue avec `PAIRING_REQUIRED` puis le PC échoue :
      `Failed(AUTHORIZATION_LOST)` ;
    - dernière méthode PC, et le PC réussit : `prepare` n'est jamais appelé ;
    - `prepare` n'est appelé qu'une fois, même s'il y a deux tentatives sans fil.
  - **`visibleIssue(status, issue)`** : `null` si `INACTIVE`, sinon `issue` (les trois statuts).
  - **`shouldRetryOnWifi(issue, connected)`** : vrai seulement pour `(NO_WIFI, false)`. Faux pour
    `(NO_WIFI, true)`, `(null, false)` et toute autre cause.
- [X] T017 [P] [US1] Dans `test/…/core/ConnectionPolicyTest.kt`, ajouter des tests de
  `reconnectAttempts(lastMethod, lastWirelessPort, prepareWireless = true)` :
  - seule la **première** tentative `WIRELESS` porte `prepareWireless = true`, que la dernière
    méthode soit `WIRELESS` ou `PC` ;
  - l'ordre des tentatives est celui du MVP ;
  - avec `prepareWireless = false` (la valeur par défaut), aucune tentative n'est marquée. Les
    tests existants restent inchangés et verts.
- [X] T018 [P] [US1] Créer `test/…/core/AutoReconnectManagerTest.kt`, avec `FakeShellBackend`,
  `FakeWirelessDebuggingSwitch` et `InMemoryAutoReconnectStore`. Cas de `enable()` (séquence
  « Activer » du contrat) :
  - état du shell différent de `Connected` → `NOT_CONNECTED`, aucune commande exécutée ;
  - permission absente, et `onExec` qui passe `rightHeld = true` sur la commande C9 →
    `executed == [texte de C9]`, résultat `ENABLED`, `autoReconnect` et `rightGrantedByApp` à
    `true` ;
  - permission déjà détenue → aucune commande, `ENABLED`, `rightGrantedByApp` à `false` ;
  - C9 avec `ShellResult(1, "")`, ou permission toujours absente après C9 → `UNAVAILABLE`,
    `autoReconnect` reste `false` ;
  - `status()` reflète le stockage et le switch (les trois cas du tableau de data-model.md).

### Implementation for User Story 1

- [X] T019 [US1] Dans `…/core/ConnectionPolicy.kt` :
  - ajouter `val prepareWireless: Boolean = false` à `ReconnectAttempt` ;
  - ajouter le paramètre `prepareWireless: Boolean = false` à `reconnectAttempts`, qui marque la
    première tentative `WIRELESS` de la liste.

  KDoc : « la réactivation a lieu quand vient le tour du sans-fil (FR-006) ». T017 passe.
- [X] T020 [US1] Dans `…/core/AutoReconnectPolicy.kt`, ajouter
  `object AutoReconnectPolicy`. Chaque fonction porte en KDoc la référence du tableau
  correspondant de data-model.md.
  - `status(optionEnabled: Boolean, rightHeld: Boolean): AutoReconnectStatus`.
  - `suspend fun prepareWireless(switch: WirelessDebuggingSwitch, timeoutMs: Long = ConnectionPolicy.WIRELESS_ACCEPT_TIMEOUT_MS, pollMs: Long = ConnectionPolicy.WIRELESS_POLL_MS): PrepareResult`.
    Elle suit les étapes 1 à 6 de data-model.md. L'attente réutilise
    `ConnectionPolicy.awaitWirelessEnabled(read = { switch.isWirelessDebuggingEnabled() }, …)`.
    La lecture de l'étape 3 est protégée (exception = `false`). Une `SecurityException` à
    l'écriture donne `Issue(RIGHT_LOST)`.
  - `issueFor(reason: FailureReason?): ReconnectIssue`, selon la table de research.md R7.
  - `visibleIssue(status: AutoReconnectStatus, issue: ReconnectIssue?): ReconnectIssue?` et
    `shouldRetryOnWifi(issue: ReconnectIssue?, connected: Boolean): Boolean` (data-model.md,
    « Deux règles d'affichage et de relance »).
  - `suspend fun reconnect(attempts: List<ReconnectAttempt>, prepare: suspend () -> PrepareResult, connect: suspend (ReconnectAttempt) -> FailureReason?): ReconnectOutcome`.
    `connect` renvoie `null` en cas de succès. Règles :
    - `prepare` est appelé au plus une fois, avant la tentative marquée ;
    - sur `Issue`, la cause est retenue et les tentatives `WIRELESS` restantes sont sautées ;
    - sinon, un échec de connexion sans fil **après une préparation réussie** retient
      `issueFor(reason)`, seulement si aucune cause n'est déjà retenue ;
    - le premier succès rend `Connected` ;
    - à la fin : `Failed(cause retenue, ou null)`.

  T016 passe.
- [X] T021 [US1] Créer `…/core/AutoReconnectManager.kt`, la classe
  `AutoReconnectManager(shell: ShellBackend, switch: WirelessDebuggingSwitch, store: AutoReconnectStore)`,
  avec :
  - `fun status(): AutoReconnectStatus = AutoReconnectPolicy.status(store.autoReconnect, switch.hasWriteRight())` ;
  - `suspend fun enable(): EnableResult`, exactement la séquence « Activer » de
    [contracts/shell-commands.md](contracts/shell-commands.md) :
    1. si le shell n'est pas `Connected` → `NOT_CONNECTED` ;
    2. si `switch.hasWriteRight()` : pas de C9, `rightGrantedByApp` inchangé ;
    3. sinon : C9, puis relecture. Si la permission est détenue, `rightGrantedByApp = true` ;
       sinon, `UNAVAILABLE` ;
    4. `store.autoReconnect = true` → `ENABLED`.

    Une `ShellUnavailableException` donne `NOT_CONNECTED`. « Réactiver » appelle la même
    fonction.

  T018 passe.
- [X] T022 [US1] Dans `…/adb/AdbShellBackend.kt` :
  - `connect(...)` renvoie `FailureReason?` au lieu de `Boolean`, avec `null` = connecté :
    `NotAuthorized` → `NOT_AUTHORIZED`, `ProbeFailed` → `UNKNOWN`, exception →
    `ConnectionPolicy.classify(e, phase)`. `fail(...)` reste appelé comme aujourd'hui ;
  - adapter les appelants en gardant leurs signatures publiques booléennes (`== null`) :
    `pair`, `connectWireless`, `connectPc`, `switchToWireless` ;
  - remplacer `reconnectLast()` par
    `suspend fun reconnectLast(prepare: (suspend () -> PrepareResult)? = null): ReconnectIssue?`.
    Elle construit
    `ConnectionPolicy.reconnectAttempts(prefs.lastMethod, prefs.lastWirelessPort, prepareWireless = prepare != null)`,
    appelle `AutoReconnectPolicy.reconnect(attempts, prepare ?: { PrepareResult.Ready }, connect = { connect(it.method, it.target, silent = true) })`,
    et renvoie `null` si connecté, sinon la cause ;
  - ajouter
    `suspend fun connectWirelessPrepared(prepare: suspend () -> PrepareResult): ReconnectIssue?`
    (FR-005, « Se connecter » sans port). Elle appelle `AutoReconnectPolicy.reconnect` avec la
    seule tentative `ReconnectAttempt(WIRELESS, ConnectTarget.Discover, prepareWireless = true)`
    et `connect = { connect(it.method, it.target, silent = false) }` : un échec de connexion
    passe à `Failed`, comme dans le MVP ;
  - journaliser : « Préparation du sans-fil : <résultat> » et « Reconnexion : <résultat> »,
    sans matériel de clé.
- [X] T023 [US1] Créer `…/adb/AutoReconnectController.kt`, la classe
  `AutoReconnectController(context: Context, adb: AdbShellBackend, switch: WirelessDebuggingSwitch, store: AutoReconnectStore, model: QuestModel, scope: CoroutineScope)`,
  avec :
  - `val manager = AutoReconnectManager(adb, switch, store)` ;
  - `val experimental: Boolean = !model.autoReconnectVerified` ;
  - `StateFlow`s `status: StateFlow<AutoReconnectStatus>`, `lastIssue: StateFlow<ReconnectIssue?>`
    et `preparing: StateFlow<Boolean>` (FR-007, data-model.md, « Pendant la préparation ») ;
  - `fun refresh()`, qui recalcule `status` ;
  - une lambda privée `prepare`, qui passe `preparing` à `true`, appelle
    `AutoReconnectPolicy.prepareWireless(switch)`, puis remet `preparing` à `false` dans un
    `finally` ;
  - `suspend fun reconnectNow()`, protégée par un `Mutex` pour qu'une seule reconnexion tourne à
    la fois (`tryLock`, sinon retour immédiat). Elle fait `refresh()`, puis
    `adb.reconnectLast(prepare = if (status != INACTIVE) prepare else null)`. Ensuite,
    `lastIssue = AutoReconnectPolicy.visibleIssue(status, issue)` ;
  - `suspend fun connectWireless()`, sous le même `Mutex` : si le statut n'est pas `INACTIVE`,
    `lastIssue = visibleIssue(status, adb.connectWirelessPrepared(prepare))` ; sinon,
    `adb.connectWireless()` (FR-005) ;
  - `suspend fun enable(): EnableResult` = `manager.enable()`, puis `refresh()` ;
  - dans `init`, collecter `adb.state` dans `scope` : à chaque `Connected`, `refresh()` et
    `lastIssue = null`.
- [X] T024 [US1] Câblage :
  - `…/AppContainer.kt` : créer une seule instance `val connectionPrefs = ConnectionPrefs(appContext)`,
    partagée par `adb` et le contrôleur, puis `val wirelessSwitch = AndroidWirelessSwitch(appContext)`
    et `val autoReconnect = AutoReconnectController(appContext, adb, wirelessSwitch, connectionPrefs, questModel, appScope)` ;
  - `…/OqtApplication.kt` : remplacer `container.adb.reconnectLast()` par
    `container.autoReconnect.reconnectNow()` ;
  - `…/MainActivity.kt` : dans `onResume()`, appeler
    `(application as OqtApplication).container.autoReconnect.refresh()`. Cela relit la
    permission au retour au premier plan (data-model.md) ;
  - ne créer **aucun** récepteur `BOOT_COMPLETED` ni service (FR-008).
- [X] T025 [P] [US1] Ajouter les textes en anglais et en français (`values/` et `values-fr/`) :
  - `auto_reconnect_title` : « Reconnexion autonome » ;
  - `auto_reconnect_summary` : « Après un redémarrage du casque, l'appli réactive elle-même le
    débogage sans fil et se reconnecte, sans PC. » ;
  - `auto_reconnect_status_active` : « Active » ; `auto_reconnect_status_inactive` :
    « Inactive » ;
  - `auto_reconnect_enable` : « Activer » ; `auto_reconnect_reconnect` : « Se reconnecter » ;
  - `auto_reconnect_connect_first` : « Connecte l'appli pour activer cette option. » ;
  - `auto_reconnect_dialog_title` : « Activer la reconnexion autonome ? » ;
  - `auto_reconnect_proposal_title` : « Ne plus avoir besoin du PC ? » ;
  - `auto_reconnect_dialog_body`, quatre puces (FR-002) :
    - « À chaque ouverture, l'appli réactivera elle-même le débogage sans fil, puis s'y
      connectera. »
    - « Pour cela, elle s'accorde un droit système du casque, qu'elle garde jusqu'à ce que tu
      désactives l'option ou que tu désinstalles l'appli. »
    - « Elle ne s'en sert que pour cette seule action. »
    - « Le casque doit être connecté à un Wi-Fi. »
  - `auto_reconnect_dialog_confirm` : « Activer » ; `auto_reconnect_dialog_later` : « Plus
    tard » ;
  - `auto_reconnect_enabled` : « Reconnexion autonome activée. » ;
  - `auto_reconnect_unavailable` : « Le casque n'a pas accordé ce droit : option indisponible
    sur ce casque. « Passer en sans fil » reste possible. » ;
  - `auto_reconnect_not_connected` : « Connecte-toi d'abord. » ;
  - `auto_reconnect_preparing` : « Réactivation du débogage sans fil… » ;
  - `auto_reconnect_preparing_hint` : « Si le casque te demande d'autoriser le débogage sans fil
    sur ce réseau, accepte en cochant « Toujours autoriser ». » (FR-007) ;
  - modifier `pc_note` (FR-019) : « Une seule fois : ensuite, touche « Passer en sans fil »,
    puis active la reconnexion autonome. Le PC ne sera plus nécessaire. »
- [X] T026 [US1] Dans `…/ui/MainViewModel.kt` :
  - exposer `autoReconnectStatus` et `autoReconnectExperimental`, repris de
    `container.autoReconnect` ;
  - `_autoReconnectBusy: MutableStateFlow<Boolean>` ;
  - `fun enableAutoReconnect()` : message selon `EnableResult` (`auto_reconnect_enabled`,
    `auto_reconnect_unavailable` ou `auto_reconnect_not_connected`) ;
  - `fun reconnectNow()` = `viewModelScope.launch { container.autoReconnect.reconnectNow() }` ;
  - exposer `autoReconnectPreparing`, repris de `container.autoReconnect.preparing` ;
  - dans `connectWireless(portText)` : si le port est vide et que le statut n'est pas
    `INACTIVE`, appeler `container.autoReconnect.connectWireless()` ; sinon, garder le
    comportement actuel (FR-005) ;
  - proposition de FR-003 : `_autoReconnectProposal: MutableStateFlow<Boolean>` passe à `true`
    dans `switchToWireless()` quand le résultat est `SWITCHED` et que le statut est `INACTIVE`.
    `fun dismissAutoReconnectProposal()` le remet à `false`. Rien n'est persisté.
- [X] T027 [US1] Dans `…/ui/ConnectionScreen.kt`, `…/ui/components/ConnectionBadge.kt` et
  `…/ui/OqtApp.kt` :
  - **préparation (FR-007, E1)** : quand `preparing` est vrai, `StatusCard` affiche un
    `CircularProgressIndicator` (24 dp), `auto_reconnect_preparing` en `titleLarge`, puis
    `auto_reconnect_preparing_hint` en `bodyMedium`, à la place du libellé d'état.
    `ConnectionBadge` reçoit un paramètre `preparing: Boolean = false` : s'il est vrai, il
    affiche `state_connecting` avec la pastille orange. `OqtApp.kt` le transmet ;
  - ajouter `AutoReconnectCard`, via `SectionCard`, juste après `StatusCard`. Elle montre :
    - le titre, et `ExperimentalBadge` si `experimental` ;
    - le résumé, et le statut (« Active » ou « Inactive ») ;
    - « Activer » (`Button`, `heightIn(min = 48.dp)`), actif seulement si l'état est
      `Connected` ; sinon, le texte `auto_reconnect_connect_first` ;
    - quand le statut n'est pas `INACTIVE` et que l'appli n'est pas connectée : « Se
      reconnecter » (`OutlinedButton`, 48 dp), qui appelle `vm.reconnectNow()` (FR-005, US1 sc.
      6).
  - ajouter `AutoReconnectDialog` (`AlertDialog`, sur le modèle de celui de `ProfileScreen.kt`),
    avec le corps en quatre puces. « Activer » appelle `onConfirm` ; « Plus tard » et le rejet
    ferment la fenêtre. « Activer » dans la carte ouvre cette fenêtre (FR-002, FR-016 : rien
    n'est accordé sans confirmation) ;
  - dans `OqtApp.kt`, collecter la proposition : si elle est vraie, afficher la même fenêtre
    avec le titre `auto_reconnect_proposal_title`. Confirmer appelle
    `enableAutoReconnect()` ; dans tous les cas, appeler `dismissAutoReconnectProposal()`. C'est
    exactement la même fenêtre : T042 y ajoutera la phrase de délai et la case, qui vaudront
    donc aussi pour la proposition.

**Checkpoint**: `./gradlew test assembleDebug` vert. Sur casque : quickstart 1.1 à 1.9.

---

## Phase 4: User Story 2 - Comprendre pourquoi la reconnexion autonome n'a pas abouti (Priority: P2)

**Goal**: afficher la cause et l'étape suivante, retenter au retour du Wi-Fi, et permettre de
« Réactiver » (FR-009 à FR-011).

**Independent Test**: quickstart, scénarios 2.1 à 2.4.

### Tests for User Story 2

- [X] T028 [P] [US2] Dans `test/…/core/AutoReconnectManagerTest.kt`, ajouter la réactivation :
  avec `autoReconnect = true`, permission absente, et `onExec` qui l'accorde sur C9 :
  - avant l'appel, `status() == NEEDS_REACTIVATION` ;
  - `enable()` → `ENABLED`, `rightGrantedByApp = true` et `status() == ACTIVE`.

### Implementation for User Story 2

- [X] T029 [P] [US2] Ajouter les textes en anglais et en français :
  - `issue_debugging_disabled` : « Débogage désactivé sur le casque : réactive le mode
    développeur, puis rouvre l'appli. » ;
  - `issue_no_wifi` : « Pas de Wi-Fi : connecte le casque à un réseau Wi-Fi. L'appli réessaiera
    seule. » ;
  - `issue_right_lost` : « L'appli ne peut plus réactiver le débogage sans fil : connecte-la via
    PC, puis touche « Réactiver ». » ;
  - `issue_network_not_allowed` : « Le débogage sans fil ne s'est pas activé. Si le casque a
    affiché « autoriser sur ce réseau », touche « Se reconnecter » et accepte en cochant
    « Toujours autoriser ». Sinon, réessaie ou passe par le PC. » (F1 : les deux cas sont
    indiscernables) ;
  - `issue_authorization_lost` : « Le casque ne reconnaît plus l'autorisation de l'appli :
    refais une fois l'étape « via PC ». » ;
  - `issue_wireless_not_started` : « Le débogage sans fil est actif, mais l'appli ne le trouve
    pas : réessaie, ou passe par le PC. » ;
  - `issue_unknown` : « Reconnexion impossible : réessaie, ou passe par le PC. » ;
  - `auto_reconnect_status_needs_reactivation` : « À réactiver » ;
  - `auto_reconnect_needs_reactivation_hint` : « L'appli a perdu le droit de réactiver le
    débogage sans fil. Connecte-la, via PC si besoin, puis touche « Réactiver ». » ;
  - `auto_reconnect_reactivate` : « Réactiver ».
- [X] T030 [US2] Dans `…/ui/components/ConnectionBadge.kt`, ajouter
  `@StringRes fun ReconnectIssue.messageRes(): Int`, un `when` exhaustif sur les sept valeurs.
- [X] T031 [US2] Dans `…/adb/AutoReconnectController.kt`, surveiller le Wi-Fi (FR-010,
  research.md R4) :
  - dans `init`, `ConnectivityManager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)`,
    pour toute la vie du processus ;
  - `onAvailable` : si
    `AutoReconnectPolicy.shouldRetryOnWifi(lastIssue.value, adb.state.value is ConnectionState.Connected)`,
    lancer `scope.launch { reconnectNow() }`. Le `Mutex` de T023 empêche deux relances en
    parallèle.
- [X] T032 [US2] Dans `…/ui/MainViewModel.kt`, exposer `autoReconnectIssue`, repris de
  `container.autoReconnect.lastIssue`.
- [X] T033 [US2] Dans `…/ui/ConnectionScreen.kt` :
  - `StatusCard` : quand l'état est `Disconnected` et que la cause n'est pas nulle, afficher
    `stringResource(issue.messageRes())` sous le libellé, en
    `MaterialTheme.typography.bodyMedium` et `colorScheme.onSurfaceVariant`. Pas de couleur
    d'erreur : FR-009 demande « sans message d'erreur alarmant » ;
  - `AutoReconnectCard` : pour `NEEDS_REACTIVATION`, afficher le statut « À réactiver », le texte
    d'aide, et le bouton « Réactiver » (48 dp, actif seulement si connecté), qui appelle
    `vm.enableAutoReconnect()` sans nouvelle fenêtre de confirmation. FR-016 : la confirmation
    donnée à l'activation vaut pour « Réactiver » tant que l'option n'a pas été désactivée.

**Checkpoint**: quickstart 2.1 à 2.4.

---

## Phase 5: User Story 4 - Garder l'autorisation valide sans jamais repasser par le PC (Priority: P2)

**Goal**: choix explicite « Ne jamais faire expirer les autorisations de débogage », avec valeur
d'origine retenue et rétablie (FR-021 à FR-025, constitution v1.1.0, principe I).

**Independent Test**: quickstart, scénarios 4.1 à 4.8.

### Tests for User Story 4

- [X] T034 [P] [US4] Dans `test/…/core/ShellCommandsTest.kt`, ajouter :
  - `disableAuthorizationExpiry().text == "settings put global adb_allowed_connection_time 0"` ;
  - `restoreAuthorizationExpiry(604800000L).text == "settings put global adb_allowed_connection_time 604800000"` ;
  - `restoreAuthorizationExpiry(ShellCommand.MAX_KEY_LIFETIME_MS)` est accepté ;
  - `restoreAuthorizationExpiry(0L)`, `restoreAuthorizationExpiry(-1L)` et
    `restoreAuthorizationExpiry(ShellCommand.MAX_KEY_LIFETIME_MS + 1)` lèvent
    `IllegalArgumentException` (principe I : entier borné) ;
  - `ShellCommand.MAX_KEY_LIFETIME_MS == 315_360_000_000L` (3 650 jours) ;
  - `resetAuthorizationExpiry().text == "settings delete global adb_allowed_connection_time"` ;
  - ajouter ces trois commandes au test « aucun `persist.` ».
- [X] T035 [P] [US4] Créer `test/…/core/ExpiryChoiceTest.kt` :
  - `lifetimeOf(null) == Days(7)` (délai par défaut du système), `lifetimeOf(0) == Never`,
    `lifetimeOf(604_800_000) == Days(7)`, `lifetimeOf(86_400_000) == Days(1)`,
    `lifetimeOf(3_600_000) == Days(0)` (moins d'un jour), et `lifetimeOf(-1) == OutOfRange` ainsi
    que `lifetimeOf(MAX_KEY_LIFETIME_MS + 1) == OutOfRange` ;
  - `status(original, restorePending, currentMs)`, selon le tableau `ExpiryChoiceStatus` de
    data-model.md : `(null, false, 0)`, `(null, false, -1)` et
    `(null, false, MAX_KEY_LIFETIME_MS + 1)` → `NOT_APPLICABLE` ; `(null, false, 604800000)` et
    `(null, false, null)` → `OFF` ; `("604800000", false, *)` → `ON` ;
    `("default", true, *)` → `RESTORE_PENDING` ;
  - `encodeOriginal(null) == "default"`, `encodeOriginal(604800000) == "604800000"` ;
  - `restoreCommand("default")` a le texte de C13 ; `restoreCommand("604800000")` a celui de
    C12 ; `restoreCommand("abc")`, `restoreCommand("0")` et
    `restoreCommand("400000000000")` (hors plage) lèvent `IllegalArgumentException`.
- [X] T036 [US4] Dans `test/…/core/AutoReconnectManagerTest.kt`, ajouter les cas de
  `setNeverExpire(on)` (séquences « Ne jamais faire expirer » et « Rétablir le délai ») :
  - **activer, connecté, `lifetimeMs = 604800000`** : dans `onExec`, au moment de C11,
    `store.expiryOriginal == "604800000"` (la valeur est retenue avant d'écrire). `onExec` passe
    ensuite `lifetimeMs = 0`. Résultat `APPLIED` ;
  - **`lifetimeMs = 0` au départ**, ou hors plage (`-1`, `400000000000`) : `NOT_APPLICABLE`,
    aucune commande (FR-024) ;
  - **valeur retenue invalide** (`expiryOriginal = "abc"` ou `"400000000000"`, délai lu à 0),
    puis désactiver : aucune commande, `expiryOriginal == null`, résultat `FAILED` (C4) ;
  - **relecture différente de 0 après C11** : `FAILED`, et `expiryOriginal` redevient `null` ;
  - **`lifetimeMs = null` au départ** : `expiryOriginal == "default"` ;
  - **désactiver, connecté, délai lu à 0** : C12 avec `604800000` (ou C13 si `"default"`), puis
    `expiryOriginal == null`. Résultat `APPLIED` ;
  - **désactiver alors qu'un autre outil a changé le délai** (`lifetimeMs = 86400000`) : aucune
    commande, et `expiryOriginal == null` (FR-023) ;
  - **désactiver hors connexion** (`state = Disconnected`) : `RESTORE_PENDING`,
    `expiryRestorePending = true`, aucune commande ;
  - **`onConnected()` avec un rétablissement en attente** : même traitement que « connecté »,
    puis les deux champs sont remis à zéro.

### Implementation for User Story 4

- [X] T037 [US4] Dans `…/core/ShellCommands.kt`, ajouter :
  - `/** C11 */ fun disableAuthorizationExpiry()` ;
  - `const val MAX_KEY_LIFETIME_MS = 315_360_000_000L`, soit 3 650 jours, avec la KDoc
    « borne de C12, principe I : entiers bornés par des plages connues » ;
  - `/** C12 */ fun restoreAuthorizationExpiry(ms: Long)`, avec
    `require(ms in 1..MAX_KEY_LIFETIME_MS) { "Délai hors plage : $ms" }` ;
  - `/** C13 */ fun resetAuthorizationExpiry()` ;
  - une seule constante pour la clé : `private const val KEY_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time"`.

  T034 passe.
- [X] T038 [US4] Créer `…/core/ExpiryChoice.kt` :
  - `const val DEFAULT_KEY_LIFETIME_MS = 604_800_000L` ;
  - `sealed interface AuthorizationLifetime { data object Never; data object OutOfRange; data class Days(val days: Long) }`,
    où `Days(0)` signifie « moins d'un jour » ;
  - `fun lifetimeOf(ms: Long?): AuthorizationLifetime` : `null` → `Days(7)` ; `0` → `Never` ;
    de 1 à `ShellCommand.MAX_KEY_LIFETIME_MS` → `Days(ms / 86_400_000)` ; sinon → `OutOfRange` ;
  - `enum class ExpiryChoiceStatus { NOT_APPLICABLE, OFF, ON, RESTORE_PENDING }` ;
  - `enum class ExpiryChangeResult { APPLIED, RESTORE_PENDING, NOT_APPLICABLE, FAILED, NOT_CONNECTED }` ;
  - `object ExpiryChoicePolicy { fun status(original: String?, restorePending: Boolean, currentMs: Long?); fun encodeOriginal(currentMs: Long?): String; fun restoreCommand(original: String): ShellCommand }`.
    `status` renvoie `NOT_APPLICABLE` quand rien n'est retenu et que `lifetimeOf(currentMs)` vaut
    `Never` ou `OutOfRange`. `restoreCommand` lève `IllegalArgumentException` pour toute valeur
    autre que `"default"` ou un nombre de la plage de C12.

  T035 passe.
- [X] T039 [US4] Dans `…/core/AutoReconnectManager.kt`, ajouter :
  - `fun lifetime(): AuthorizationLifetime` ;
  - `fun expiryStatus(): ExpiryChoiceStatus` ;
  - `suspend fun setNeverExpire(on: Boolean): ExpiryChangeResult`, selon les séquences du
    contrat. Pour activer : retenir la valeur d'origine **avant** C11, relire, puis `FAILED` si
    la valeur n'est pas 0. Pour désactiver : rétablir seulement si le délai lu vaut `0`, puis
    oublier la valeur. Hors connexion : `expiryRestorePending = true` ;
  - `suspend fun onConnected()`, qui traite `expiryRestorePending` (US3 y ajoutera le retrait
    en attente).

  Une `ShellUnavailableException` pendant un rétablissement passe `expiryRestorePending` à
  `true` et renvoie `RESTORE_PENDING`. Une `IllegalArgumentException` de `restoreCommand`
  (valeur retenue invalide) fait oublier la valeur, sans aucune commande, et renvoie `FAILED`
  (C4, data-model.md). T036 passe.
- [X] T040 [US4] Dans `…/adb/AutoReconnectController.kt` :
  - `StateFlow`s `expiryStatus` et `lifetime`, recalculés par `refresh()` ;
  - `suspend fun enable(neverExpire: Boolean = false)` : `manager.enable()`, puis, si le
    résultat est `ENABLED` et `neverExpire` vrai, `manager.setNeverExpire(true)`. La fonction
    renvoie les deux résultats. Si C11 échoue, l'option reste active (data-model.md, « Résultats
    d'opérations ») ;
  - `suspend fun setNeverExpire(on: Boolean)` ;
  - dans la collecte de `adb.state`, à chaque `Connected` : `manager.onConnected()`, puis
    `refresh()`.
- [X] T041 [P] [US4] Textes en anglais et en français. Étendre aussi `test/…/StringsParityTest.kt`
  pour comparer les noms des balises `<plurals>` entre les deux langues.
  - `<plurals name="auto_reconnect_lifetime_days">` : « Sur ce casque, l'autorisation de débogage
    expire après %d jour(s) sans connexion via PC : il faudra alors refaire une fois l'étape
    PC. » Formes `one` et `other` ;
  - `auto_reconnect_lifetime_less_than_day` : même phrase, avec « en moins d'un jour » ;
  - `never_expire_label` : « Ne jamais faire expirer les autorisations de débogage » ;
  - `never_expire_consequences`, quatre puces (FR-022) :
    - « Vaut pour toutes les autorisations de débogage du casque, celle du PC comprise. »
    - « Reste en place après un redémarrage. »
    - « Reste en place si tu désinstalles l'appli sans l'avoir désactivé. »
    - « Tu peux revenir au délai d'origine à tout moment. »
  - `never_expire_status_on` : « Autorisations sans expiration : actif » ;
    `never_expire_status_off` : « … : inactif » ; `never_expire_status_pending` : « … : délai
    d'origine rétabli à la prochaine connexion » ;
  - boutons `never_expire_enable` (« Activer ») et `never_expire_disable` (« Rétablir le
    délai ») ;
  - messages `never_expire_applied`, `never_expire_restored`, `never_expire_pending`
    (« Le délai d'origine sera rétabli à la prochaine connexion. ») et `never_expire_failed`.
- [X] T042 [US4] Dans `…/ui/MainViewModel.kt`, `…/ui/ConnectionScreen.kt` et `…/ui/OqtApp.kt` :
  - **fenêtre d'activation**, qui est aussi celle de la proposition de FR-003 (B2) : si
    `lifetime` est `Days`, afficher la phrase de délai
    (pluriel, ou « moins d'un jour » pour `Days(0)`). Afficher dessous une `Checkbox` avec
    `never_expire_label`, **non cochée** au départ, dans une ligne cliquable de 48 dp, avec les
    conséquences en `bodySmall`. Confirmer appelle `enableAutoReconnect(neverExpire = coché)` ;
  - **`AutoReconnectCard`** : si le statut n'est pas `INACTIVE` et que `expiryStatus` n'est pas
    `NOT_APPLICABLE`, afficher l'état du choix. Ajouter un bouton (48 dp, actif seulement si
    connecté) : « Activer » ouvre une confirmation qui reprend les conséquences ; « Rétablir le
    délai » agit directement. Pas de bouton pour `RESTORE_PENDING` ;
  - **ViewModel** : `enableAutoReconnect(neverExpire: Boolean = false)`,
    `setNeverExpire(on: Boolean)`, flows `expiryStatus` et `lifetime`, et un message par
    `ExpiryChangeResult`.

**Checkpoint**: quickstart 4.1 à 4.8.

---

## Phase 6: User Story 3 - Désactiver la reconnexion autonome (Priority: P3)

**Goal**: désactiver en un geste, retirer la permission si c'est l'appli qui l'a accordée, et
rétablir le délai d'expiration (FR-013, FR-014, constitution I, condition 3).

**Independent Test**: quickstart, scénarios 3.1 à 3.4.

### Tests for User Story 3

- [X] T043 [P] [US3] Dans `test/…/core/AutoReconnectManagerTest.kt`, ajouter les cas de
  `disable()` (séquences « Désactiver » et « Retrait en attente ») :
  - **connecté et `rightGrantedByApp = true`** : dans `onExec`, au moment de C10,
    `store.autoReconnect == false` et `store.revokePending == true` (préférences écrites avant
    C10). Ensuite, `executed` contient C10, `revokePending` et `rightGrantedByApp` valent
    `false`, et le résultat est `REVOKED` ;
  - **`rightGrantedByApp = false`** : aucune commande, résultat `KEPT_EXTERNAL_GRANT`,
    `revokePending == false` ;
  - **déconnecté, `rightGrantedByApp = true`** : `REVOKE_PENDING`, aucune commande ;
  - **`onConnected()` avec `revokePending = true`** : C10 si la permission est détenue, rien
    sinon. Dans les deux cas, `revokePending` et `rightGrantedByApp` repassent à `false` ;
  - **choix d'expiration `ON` au moment de `disable()`** : le délai est rétabli aussi (C12 ou
    C13 si connecté, sinon `expiryRestorePending = true`) ;
  - `disable()` n'exécute jamais C7 et ne modifie pas `shell.state` (FR-014).

### Implementation for User Story 3

- [X] T044 [US3] Dans `…/core/AutoReconnectManager.kt`, ajouter
  `suspend fun disable(): DisableResult`, selon la séquence « Désactiver » du contrat. Si le
  choix d'expiration est `ON`, appeler `setNeverExpire(false)` (US4). Compléter `onConnected()`
  avec le retrait en attente. Une `ShellUnavailableException` pendant C10 laisse
  `revokePending = true` et renvoie `REVOKE_PENDING`. T043 passe.
- [X] T045 [P] [US3] Textes en anglais et en français :
  - `auto_reconnect_disable` : « Désactiver » ;
  - `auto_reconnect_disabled` : « Reconnexion autonome désactivée, droit retiré. » ;
  - `auto_reconnect_disabled_pending` : « Reconnexion autonome désactivée. Le droit sera retiré
    à la prochaine connexion. » ;
  - `auto_reconnect_disabled_kept` : « Reconnexion autonome désactivée. Le droit avait été
    accordé en dehors de l'appli : il reste en place. Pour le retirer depuis un PC :
    adb shell pm revoke io.github.openquesttuner android.permission.WRITE_SECURE_SETTINGS ».
- [X] T046 [US3] Dans `…/adb/AutoReconnectController.kt`, `…/ui/MainViewModel.kt` et
  `…/ui/ConnectionScreen.kt` :
  - contrôleur : `suspend fun disable()` = `manager.disable()`, puis `refresh()` ;
  - ViewModel : `fun disableAutoReconnect()`, avec un message par `DisableResult` ;
  - carte : un bouton « Désactiver » (`OutlinedButton`, 48 dp) quand le statut est `ACTIVE` ou
    `NEEDS_REACTIVATION`. Il est actif avec ou sans connexion, sans confirmation, puisqu'il
    réduit les droits. Il ne coupe ni la connexion ni le sans-fil (FR-014).

**Checkpoint**: quickstart 3.1 à 3.4. Toutes les user stories fonctionnent.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T047 [P] `README.md`, section « Connect the app to the headset » :
  - remplacer l'étape 3 par l'option **Auto-reconnect** ;
  - décrire le choix « never expire » avec ses conséquences ;
  - donner les commandes PC pour tout défaire après une désinstallation :
    `adb shell settings delete global adb_allowed_connection_time` (délai par défaut) ou
    `settings put global adb_allowed_connection_time <ms>`, et `pm revoke …` ;
  - mentionner la permission dans « Safe by design ».
- [X] T048 [P] Dans `specs/001-game-profiles-mvp/contracts/shell-commands.md`, ajouter sous le
  tableau : « C9 à C13 : voir l'amendement
  [specs/002-standalone-reconnect/contracts/shell-commands.md](../../002-standalone-reconnect/contracts/shell-commands.md). »
- [X] T049 Lancer `./gradlew test assembleDebug lintDebug`. Corriger tout échec ou tout nouvel
  avertissement de lint dans les fichiers touchés. `ProtectedPermissions` est ignoré
  explicitement dans le manifeste (T001).
- [ ] T050 Valider sur le Quest 3 en suivant [quickstart.md](quickstart.md), sections 2 à 6.
  Consigner une ligne par essai dans `docs/compatibility.md` :
  - octroi et persistance de la permission ;
  - reconnexion, avec le temps mesuré ;
  - fenêtre réseau ;
  - retrait sans arrêt du processus ;
  - valeur de `adb_allowed_connection_time` ;
  - C11 à C13 ;
  - exception levée quand une clé est refusée.
  *Avancement au 2026-09-24* : 1.1 à 1.4, 1.8 et §4 validés sur Quest 3 (docs/compatibility.md) ;
  correctif de la relecture trop précoce (research.md R3) vérifié sur casque.
- [ ] T051 Si T050 montre qu'une clé refusée en TLS donne `IOException("Connection failed")`, et
  non `AdbPairingRequiredException` (research.md R7) : dans `…/core/AutoReconnectPolicy.kt`,
  faire rendre `AUTHORIZATION_LOST` à un échec sans fil de cause `UNKNOWN` après une
  préparation réussie. Ajouter le test correspondant dans `AutoReconnectPolicyTest.kt`, et
  mettre à jour research.md R7. Sinon, noter dans R7 que le cas est confirmé.
- [X] T052 Si les scénarios 1.1 à 1.3 réussissent : dans `…/core/QuestModel.kt`, passer
  `QUEST_3` à `autoReconnectVerified = true`, avec un commentaire qui renvoie à la date de
  l'essai dans `docs/compatibility.md` (FR-018, principe III).
- [X] T053 Avant le commit, retirer le commentaire « Sync Impact Report » en tête de
  `.specify/memory/constitution.md` (temporaire, comme pour la v1.0.0).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001, T002)** : aucune dépendance.
- **Foundational (T003 à T015)** : après le Setup. Bloque toutes les user stories.
- **US1 (T016 à T027)** : après la phase 2. C'est le MVP.
- **US2 (T028 à T033)** : après US1, car elle affiche les causes que calcule `reconnect()` (T020)
  et enrichit la carte (T027).
- **US4 (T034 à T042)** : après US1, car elle enrichit la fenêtre et la carte d'US1.
  Indépendante d'US2.
- **US3 (T043 à T046)** : après US1. Si US4 est faite, `disable()` rétablit aussi le délai
  (T044) ; sinon, cette partie est à ajouter quand US4 arrive.
- **Polish (T047 à T053)** : après les user stories voulues. T050 à T052 exigent le casque.

### Fichiers partagés (donc séquentiels, jamais [P] entre eux)

- `…/core/AutoReconnectPolicy.kt` : T012 → T020 → T051.
- `…/core/AutoReconnectManager.kt` : T021 → T039 → T044.
- `test/…/core/AutoReconnectManagerTest.kt` : T018 → T028 → T036 → T043.
- `test/…/core/ShellCommandsTest.kt` : T003 → T034. `…/core/ShellCommands.kt` : T011 → T037.
- `…/adb/AutoReconnectController.kt` : T023 → T031 → T040 → T046.
- `…/ui/MainViewModel.kt`, `…/ui/ConnectionScreen.kt`, `…/ui/OqtApp.kt` : T026 → T027 → T032 →
  T033 → T042 → T046.
- `…/ui/components/ConnectionBadge.kt` : T027 → T030.
- `strings.xml` (les deux langues) : T025 → T029 → T041 → T045. Elles sont marquées [P] par
  rapport au code, pas entre elles.

### Within Each User Story

- Les tests d'abord : ils doivent échouer. Puis le cœur, la couche Android, et l'interface en
  dernier.
- Chaque checkpoint exige `./gradlew test` vert.

### Parallel Opportunities

- Phase 2 : T003 à T008 (tests et faux) en parallèle, puis T009, T010, T012 et T013 en
  parallèle. T011, T014 et T015 viennent après leurs interfaces.
- US1 : T016, T017 et T018 en parallèle, et T025 (textes) en parallèle du code.
- Après US1 : US2 et US4 peuvent avancer en parallèle, **sauf** sur les fichiers d'interface
  partagés (voir plus haut).

---

## Parallel Example: User Story 1

```bash
# Tests du cœur, en parallèle (fichiers distincts) :
Task: "T016 AutoReconnectPolicyTest.kt"
Task: "T017 ConnectionPolicyTest.kt (reconnectAttempts avec prepareWireless)"
Task: "T018 AutoReconnectManagerTest.kt (enable)"

# Pendant ce temps, les textes :
Task: "T025 strings.xml EN/FR de la carte et de la fenêtre"
```

---

## Implementation Strategy

### MVP first (User Story 1)

1. Phases 1 et 2.
2. US1 : T016 à T027.
3. **STOP** : quickstart 1.1 à 1.8 sur le Quest 3. C'est la réponse à l'issue.

### Livraison incrémentale

1. US1 : se reconnecter sans PC.
2. US2 : comprendre les échecs, retenter au retour du Wi-Fi.
3. US4 : autorisations sans expiration. Indispensable sur les casques à 7 jours, sans objet sur
   le Quest 3 de test.
4. US3 : désactiver proprement.
5. Polish : README, validation sur casque, badge « vérifié » pour le Quest 3.

---

## Notes

- 53 tâches.
- [P] = fichiers distincts, sans dépendance sur une tâche inachevée.
- Principe I : C9 à C13 sont les **seules** nouvelles commandes, et `AndroidWirelessSwitch` est
  la **seule** écriture de `Settings` (tests T004 et T005).
- Principe IV : toute la logique de décision est dans `core/` et testée. La couche Android ne
  fait que lire, écrire une valeur et câbler.
- Hors code : demander dans l'issue la valeur de
  `adb shell settings get global adb_allowed_connection_time` sur le casque du demandeur
  (research.md R6).
