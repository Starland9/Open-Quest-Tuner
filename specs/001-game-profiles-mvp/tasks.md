---

description: "Liste des tâches : Profils par jeu (MVP d'OpenQuestTuner)"
---

# Tasks: Profils par jeu (MVP d'OpenQuestTuner)

**Input**: documents de conception dans `specs/001-game-profiles-mvp/` :
- [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md) ;
- contrats : [shell-commands.md](contracts/shell-commands.md), [shell-backend.md](contracts/shell-backend.md),
  [profiles-json.md](contracts/profiles-json.md) ;
- [quickstart.md](quickstart.md).

**Prerequisites**: plan.md et spec.md (obligatoires), research.md, data-model.md, contracts/.

**Tests**: **OBLIGATOIRES** pour le cœur `core/`. La constitution (principe IV) l'impose :
« Chaque règle métier du cœur DOIT être couverte par des tests unitaires JVM ». Ce sont des
tests JUnit 4 dans `app/src/test/`. Chaque test est écrit avant le code qu'il couvre, et doit
échouer tant que ce code n'existe pas. La couche Android (ADB, PackageManager, UI) est validée
sur casque avec [quickstart.md](quickstart.md).

**Organization**: les tâches sont regroupées par user story, pour que chaque story puisse être
livrée et testée seule.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : peut être faite en parallèle (fichier différent, aucune dépendance sur une tâche
  inachevée).
- **[Story]** : user story concernée (US1 à US5, voir [spec.md](spec.md)).
- Chaque description donne le chemin exact du fichier.

## Path Conventions

- Projet Android, un seul module `app/` (voir la section Project Structure de [plan.md](plan.md)).
- Code : `app/src/main/java/io/github/openquesttuner/`.
- Tests JVM : `app/src/test/java/io/github/openquesttuner/`.
- Le package `core/` NE DOIT importer aucune classe `android.*` ou `androidx.*` (principe IV).
  Il n'utilise que Kotlin, `java.*`, kotlinx-coroutines et kotlinx-serialization.
- Package Kotlin racine : `io.github.openquesttuner`. applicationId et namespace sont identiques.
- Toute chaîne visible par l'utilisateur va dans `app/src/main/res/values/strings.xml` (anglais)
  **et** dans `app/src/main/res/values-fr/strings.xml` (français), jamais en dur (FR-029).

---

## Phase 1: Setup (infrastructure commune)

**Purpose**: projet Gradle qui se configure. Le wrapper Gradle (`gradlew`,
`gradle/wrapper/gradle-wrapper.jar`) et `LICENSE` (GPL-3.0) existent déjà.

- [X] T001 Créer `settings.gradle.kts` :
  - `pluginManagement` avec les dépôts `google()`, `mavenCentral()` et `gradlePluginPortal()` ;
  - `dependencyResolutionManagement` avec `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`
    et les dépôts `google()`, `mavenCentral()` et
    `maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.MuntashirAkon.*") } }`
    (le filtre JitPack est exigé par research.md, section Toolchain) ;
  - `rootProject.name = "OpenQuestTuner"` et `include(":app")`.
- [X] T002 [P] Créer trois fichiers :
  - `build.gradle.kts` (racine) avec les plugins en `apply false` :
    `com.android.application` 8.13.2, `org.jetbrains.kotlin.android` 2.2.20,
    `org.jetbrains.kotlin.plugin.compose` 2.2.20, `org.jetbrains.kotlin.plugin.serialization` 2.2.20 ;
  - `gradle.properties` avec `org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8`,
    `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`,
    `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official` ;
  - `gradle/wrapper/gradle-wrapper.properties` pointant sur
    `https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip`, avec
    `networkTimeout=10000` et `validateDistributionUrl=true`.
- [X] T003 [P] Créer `app/build.gradle.kts` :
  - plugins : android application, kotlin android, compose, serialization ;
  - `namespace` et `applicationId` = `io.github.openquesttuner`, `compileSdk = 35`, `minSdk = 29`,
    `targetSdk = 34`, `versionCode = 1`, `versionName = "0.1.0"` ;
  - `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }` : Quest plus émulateur, pour limiter les
    bibliothèques natives de spake2-android ;
  - `buildFeatures { compose = true; buildConfig = true }`, Java 17 en source et en cible,
    `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` ;
  - `packaging.resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")` ;
  - `testOptions.unitTests.isReturnDefaultValues = true` ;
  - dépendances :
    - `androidx.core:core-ktx:1.13.1`
    - `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`
    - `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`
    - `androidx.activity:activity-compose:1.9.3`
    - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`
    - `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0`
    - `platform("androidx.compose:compose-bom:2024.12.01")`, puis `androidx.compose.ui:ui`,
      `androidx.compose.foundation:foundation`, `androidx.compose.material3:material3`
    - `debugImplementation("androidx.compose.ui:ui-tooling")`
    - `com.github.MuntashirAkon:libadb-android:3.1.1`
    - `org.conscrypt:conscrypt-android:2.5.3`
    - `org.bouncycastle:bcpkix-jdk15to18:1.81`
    - `testImplementation("junit:junit:4.13.2")`
    - `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")`
- [X] T004 [P] Créer `.gitignore` avec `.gradle/`, `build/`, `local.properties`, `.idea/`,
  `*.iml`, `.kotlin/`, `captures/`, `.externalNativeBuild/` et `.cxx/`. Créer `local.properties`
  avec `sdk.dir=/home/landry/Android/Sdk` (ce fichier est ignoré par git).
- [X] T005 Créer `app/src/main/AndroidManifest.xml` :
  - permissions : `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `QUERY_ALL_PACKAGES`
    (avec `tools:ignore="QueryAllPackagesPermission"`, justifié par research.md R5) ;
  - `<uses-feature android:name="android.hardware.vr.headtracking" android:required="false"/>` ;
  - `<application>` : `android:name=".OqtApplication"`, `android:allowBackup="false"`
    (clé ADB, principe I), `android:label="@string/app_name"`, `android:icon="@mipmap/ic_launcher"`,
    `android:theme="@style/Theme.OpenQuestTuner"` ;
  - meta-data `com.oculus.supportedDevices` = `quest2|questpro|quest3|quest3s` ;
  - activité `.MainActivity` : `exported="true"`, filtre `MAIN`/`LAUNCHER`,
    `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density"`, et
    `<layout android:defaultWidth="1024dp" android:defaultHeight="640dp"/>` (taille du panneau,
    research.md R4).
- [X] T006 [P] Créer les ressources de base :
  - `app/src/main/res/values/themes.xml` : style `Theme.OpenQuestTuner`, parent
    `android:Theme.Material.NoActionBar`, fond `#FF101418` ;
  - `app/src/main/res/values/colors.xml` : `ic_launcher_background` = `#FF0B3D5C` ;
  - `app/src/main/res/drawable/ic_launcher_foreground.xml` : vecteur 108dp, silhouette simple de
    casque VR plus un curseur de réglage, en blanc ;
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` : icône adaptative (fond et avant-plan) ;
  - `app/src/main/res/values/strings.xml` et `app/src/main/res/values-fr/strings.xml` avec
    `app_name` = `OpenQuestTuner`.

---

## Phase 2: Foundational (prérequis bloquants)

**Purpose**: cœur Kotlin pur (propriétés, modèles de casque, profils, commandes shell, parsing),
interface `ShellBackend` et squelette d'interface. Toutes les stories en dépendent.

**⚠️ CRITICAL**: aucune user story ne commence avant la fin de cette phase.

### Tests du cœur (écrits d'abord, ils doivent échouer)

- [X] T007 [P] Écrire `app/src/test/java/io/github/openquesttuner/core/QuestModelTest.kt`. Cas à
  tester :
  - `fromBuild("eureka", "x") == QUEST_3`, `fromBuild("panther", "x") == QUEST_3S`,
    `fromBuild("hollywood", "x") == QUEST_2`, `fromBuild("seacliff", "x") == QUEST_PRO` ;
  - la casse est ignorée (`"EUREKA"`) ;
  - repli sur MODEL : `fromBuild("?", "Quest 3") == QUEST_3` ;
  - `fromBuild("sm-g981b", "Pixel") == UNKNOWN` ;
  - `UNKNOWN` a les mêmes valeurs que `QUEST_3`, avec `verified` vide ;
  - `verified` est vide pour tous les modèles ;
  - pour chaque modèle, `EyeTexture.forStep(defaultEyeTexture, 100) == defaultEyeTexture`.
- [X] T008 [P] Écrire `app/src/test/java/io/github/openquesttuner/core/GameProfileTest.kt`. Cas à
  tester :
  - `forStep(EyeTexture(1680,1760), 120) == EyeTexture(2016,2112)` ;
  - `forStep(EyeTexture(1440,1584), 70)` arrondit chaque dimension au multiple de 8 le plus
    proche ;
  - `RESOLUTION_STEPS == listOf(70,80,90,100,110,120,130,140,150)` ;
  - `stepOf` renvoie le palier, ou `null` pour une taille personnalisée ;
  - `GameProfile().isEmpty` ;
  - `toPropertyValues()` renvoie les 7 clés, avec `null` pour « par défaut », les codes
    `OFF=0`…`HIGH_TOP=4` et `true→1` / `false→0` ;
  - `validateFor(QUEST_PRO)` rejette `refreshRate = 120` ;
  - `validateFor(QUEST_3)` rejette `cpuLevel = 5`, `gpuLevel = 6` et `EyeTexture(4000,4000)`
    (bornes « [512, 3072] ») ;
  - `warnings` suit exactement la règle de data-model.md : `HEAT` si
    `cpuLevel ≥ cpuLevels.last − 1`, ou `gpuLevel ≥ gpuLevels.last − 1`, ou `r > 100` ;
    `SMOOTHNESS` si `r > 130`, avec `r = width × 100 / default.width` ;
  - aller-retour JSON avec `Json { ignoreUnknownKeys = true; encodeDefaults = false }` : les
    champs `null` ne sont pas écrits, et `foveationLevel` est sérialisé par son nom (`"MEDIUM"`).
- [X] T009 [P] Écrire `app/src/test/java/io/github/openquesttuner/core/ShellCommandsTest.kt`, en
  vérifiant les textes exacts de [contracts/shell-commands.md](contracts/shell-commands.md) :
  - `setProperty(REFRESH_RATE, 90).text == "setprop debug.oculus.refreshRate 90"` ;
  - `resetProperty(CPU_LEVEL).text == "setprop debug.oculus.cpuLevel ''"` ;
  - `forceStop("com.beatgames.beatsaber").text == "am force-stop --user current 'com.beatgames.beatsaber'"` ;
  - `launch("com.a.b", "com.unity3d.player.UnityPlayerActivity").text == "am start --user current -n 'com.a.b/com.unity3d.player.UnityPlayerActivity'"` ;
  - `readProperties().text == "getprop"` et `probe().text == "true"` ;
  - `wireText == text + "; echo __OQT_EXIT__:\$?"` ;
  - rejet par `IllegalArgumentException` de `"com.foo; reboot"`, `"com.foo'bar"`, `"\$(reboot)"`,
    `"com"` (un seul segment), `""`, d'une activité avec espace ou `;`, et d'une valeur hors de
    `absoluteRange` (`setProperty(TEXTURE_WIDTH, 5000)`, `setProperty(CPU_LEVEL, -1)`) ;
  - une activité avec `$` (classe interne) est acceptée et reste entre quotes simples ;
  - aucune commande générable (toutes les propriétés, reset et set à leurs bornes) ne contient
    `persist.`.
- [X] T010 [P] Écrire `app/src/test/java/io/github/openquesttuner/core/ShellOutputTest.kt`. Cas à
  tester :
  - `parse("hello\n__OQT_EXIT__:0\n")` donne `ShellResult(0, "hello")` ;
  - un code non nul (`__OQT_EXIT__:1`) est conservé ;
  - les `\r\n` sont normalisés ;
  - avec plusieurs marqueurs, seul le **dernier** compte ;
  - sans marqueur, `exitCode == null` et `isSuccess == false` ;
  - `parseGetprop` garde seulement les lignes `[debug.oculus.*]: [...]` à valeur non vide, triées
    par clé, et ignore les autres propriétés et les lignes malformées ;
  - `isLaunchError("Starting: Intent {...}\nError: Activity class {...} does not exist.") == true`.

### Implémentation du cœur

- [X] T011 [P] Créer `app/src/main/java/io/github/openquesttuner/core/QuestProperty.kt` :
  - `enum class QuestProperty(val key: String, val absoluteRange: IntRange)`, dans cet ordre, qui
    est aussi l'ordre d'application :
    - `REFRESH_RATE("debug.oculus.refreshRate", 60..240)`
    - `TEXTURE_WIDTH("debug.oculus.textureWidth", 512..3072)`
    - `TEXTURE_HEIGHT("debug.oculus.textureHeight", 512..3072)`
    - `CPU_LEVEL("debug.oculus.cpuLevel", 0..7)`
    - `GPU_LEVEL("debug.oculus.gpuLevel", 0..7)`
    - `FOVEATION_LEVEL("debug.oculus.foveation.level", 0..4)`
    - `FOVEATION_DYNAMIC("debug.oculus.foveation.dynamic", 0..1)`
  - `@Serializable enum class FoveationLevel(val code: Int)` : `OFF(0)`, `LOW(1)`, `MEDIUM(2)`,
    `HIGH(3)`, `HIGH_TOP(4)`.
- [X] T012 [P] Créer `app/src/main/java/io/github/openquesttuner/core/ShellBackend.kt`, en reprenant
  exactement [contracts/shell-backend.md](contracts/shell-backend.md) : `interface ShellBackend`
  (`state: StateFlow<ConnectionState>`, `suspend fun exec(command: ShellCommand): ShellResult`),
  `data class ShellResult(exitCode: Int?, output: String)` avec `isSuccess`,
  `class ShellUnavailableException : IOException`, `enum ConnectionMethod { WIRELESS, PC }`,
  `sealed interface ConnectionState` (`Disconnected`, `Pairing`, `Connecting(method)`,
  `Connected(method)`, `Failed(method?, reason)`), et `enum FailureReason` (`PORT_CLOSED`,
  `NOT_AUTHORIZED`, `PAIRING_REQUIRED`, `PAIRING_CODE_REJECTED`, `SERVICE_NOT_FOUND`, `UNKNOWN`).
- [X] T013 Créer deux fichiers (dépend de T011) :
  - `app/src/main/java/io/github/openquesttuner/core/EyeTexture.kt` :
    - `@Serializable data class EyeTexture(val width: Int, val height: Int)`, avec dans le
      companion `MIN_DIM = 512` et `MAX_DIM = 3072` (bornes « [512, 3072] ») ;
    - `val RESOLUTION_STEPS = (70..150 step 10).toList()` ;
    - `fun forStep(default: EyeTexture, percent: Int): EyeTexture`, qui arrondit chaque
      dimension « au multiple de 8 le plus proche » ;
    - `fun stepOf(default: EyeTexture, texture: EyeTexture): Int?` ;
  - `app/src/main/java/io/github/openquesttuner/core/QuestModel.kt` :
    - `enum class QuestModel(displayName, deviceCodenames, modelNames, defaultEyeTexture,
      refreshRates, cpuLevels, gpuLevels, alwaysAvailableCpuMax, alwaysAvailableGpuMax, verified)`
      avec les valeurs de research.md R2 :
      - `QUEST_3` : `{"eureka"}`, `{"Quest 3"}`, 1680×1760, `[72,80,90,96,100,120]`, CPU `0..4`,
        GPU `0..5`, toujours dispo CPU 3 / GPU 2 ;
      - `QUEST_3S` : `{"panther"}`, `{"Quest 3S"}`, 1680×1760, `[72,80,90,96,100,120]`, CPU `0..4`,
        GPU `0..5`, CPU 3 / GPU 2 ;
      - `QUEST_2` : `{"hollywood"}`, `{"Quest 2"}`, 1440×1584, `[72,80,90,96,100,120]`, CPU `0..4`,
        GPU `0..4`, CPU 4 / GPU 4 ;
      - `QUEST_PRO` : `{"seacliff"}`, `{"Quest Pro"}`, 1440×1584, `[72,80,90]`, CPU `0..4`,
        GPU `0..4`, CPU 3 / GPU 3 ;
      - `UNKNOWN` : mêmes valeurs que `QUEST_3`, sans nom de code ;
      - `verified = emptySet()` pour tous ;
    - `companion fun fromBuild(device: String, model: String): QuestModel` : DEVICE sans tenir compte
      de la casse, puis MODEL, sinon `UNKNOWN` ;
    - `fun isVerified(p: QuestProperty) = p in verified`.
- [X] T014 Créer `app/src/main/java/io/github/openquesttuner/core/GameProfile.kt` (dépend de T011
  et de T013), en suivant [data-model.md](data-model.md) :
  - `@Serializable data class GameProfile(refreshRate: Int? = null, eyeTexture: EyeTexture? = null,
    cpuLevel: Int? = null, gpuLevel: Int? = null, foveationLevel: FoveationLevel? = null,
    dynamicFoveation: Boolean? = null)`, avec `isEmpty`, `toPropertyValues(): Map<QuestProperty, Int?>`,
    `validateFor(model): List<ProfileViolation>` et `warnings(model): Set<ProfileWarning>` ;
  - `data class ProfileViolation(val property: QuestProperty, val value: Int)` ;
  - `enum ProfileWarning { HEAT, SMOOTHNESS }` ;
  - faire passer T008.
- [X] T015 Créer `app/src/main/java/io/github/openquesttuner/core/ShellCommands.kt` (dépend de T011) :
  - `class ShellCommand private constructor(val text: String)` avec
    `val wireText get() = "$text; echo __OQT_EXIT__:\$?"` ;
  - un `companion object` qui est la **seule** fabrique : `setProperty(prop, value: Int)`,
    `resetProperty(prop)`, `forceStop(pkg)`, `launch(pkg, activity)`, `readProperties()`, `probe()` ;
  - `PACKAGE_REGEX = ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$` et
    `CLASS_REGEX = ^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$`, exposées pour
    `GameRepository` et `ProfileStore` ;
  - `require(...)`, qui lève `IllegalArgumentException`, pour les regex et pour
    `value in prop.absoluteRange` ;
  - quotes simples autour du paquet et de `pkg/activity` ;
  - faire passer T009.
- [X] T016 Créer `app/src/main/java/io/github/openquesttuner/core/ShellOutput.kt` (dépend de T012) :
  - `object ShellOutput`, avec `const val EXIT_MARKER = "__OQT_EXIT__:"` ;
  - `fun parse(raw: String): ShellResult` : dernier marqueur, `\r` retirés, marqueur ôté de la
    sortie, `trimEnd` ;
  - `fun parseGetprop(output: String): Map<String, String>` : regex
    `^\[(debug\.oculus\.[^\]]+)\]: \[(.*)\]$`, valeurs non vides, résultat trié
    (`toSortedMap`) ;
  - `fun isLaunchError(output: String): Boolean` : une ligne commence par `Error` ;
  - faire passer T010.

### Squelette d'interface (couche Android)

- [X] T017 [P] Créer `app/src/main/java/io/github/openquesttuner/ui/theme/Theme.kt` :
  - `OqtTheme` en Material 3 `darkColorScheme` **toujours sombre** (confort en VR) : primaire
    cyan `#7FD4FF`, surfaces `#101418`/`#1A2027`, erreur `#FFB4AB` ;
  - typographie Material 3 par défaut, avec `bodyLarge`/`bodyMedium` agrandis de 2sp pour la
    lisibilité dans le casque.
- [X] T018 [P] Créer deux composants :
  - `app/src/main/java/io/github/openquesttuner/ui/components/ChoiceRow.kt` : `@Composable fun
    <T> ChoiceRow(title: String, options: List<Pair<T?, String>>, selected: T?, onSelect: (T?) -> Unit,
    experimental: Boolean, helpText: String? = null)`. C'est un `FlowRow` de `FilterChip`, dont la
    première option `null` = « Par défaut du jeu ». Hauteur minimale 48dp (FR-030) ;
  - `app/src/main/java/io/github/openquesttuner/ui/components/ExperimentalBadge.kt` : petite
    pastille « Expérimental » (string `experimental`).
- [X] T019 Créer deux fichiers :
  - `app/src/main/java/io/github/openquesttuner/AppContainer.kt` : DI manuelle, avec
    `val questModel = QuestModel.fromBuild(Build.DEVICE, Build.MODEL)` et
    `val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Les autres champs
    seront ajoutés par les stories ;
  - `app/src/main/java/io/github/openquesttuner/OqtApplication.kt` : crée `container` dans
    `onCreate`.
- [X] T020 Créer `app/src/main/java/io/github/openquesttuner/ui/MainViewModel.kt` :
  - `AndroidViewModel`, qui récupère `(application as OqtApplication).container` ;
  - `sealed interface Screen { Games; Connection; data class Profile(val packageName: String) }`
    et une pile `StateFlow<List<Screen>>` avec `navigate(screen)` et `back(): Boolean` ;
  - `data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList())` émis dans
    un `Channel` exposé en `Flow` (snackbars).
- [X] T021 Créer trois fichiers :
  - `app/src/main/java/io/github/openquesttuner/MainActivity.kt` : `ComponentActivity` avec
    `setContent { OqtTheme { OqtApp() } }` ;
  - `app/src/main/java/io/github/openquesttuner/ui/OqtApp.kt` : `Scaffold` avec `SnackbarHost`,
    qui collecte les `UiMessage` via `context.getString(res, *args)`, affiche l'écran du haut de
    la pile, et gère `BackHandler(enabled = pile.size > 1) { vm.back() }` ;
  - `app/src/main/java/io/github/openquesttuner/ui/GamesScreen.kt` provisoire :
    `TopAppBar` « OpenQuestTuner » avec un emplacement `actions` et un contenu vide.
- [X] T022 Vérifier que `./gradlew test assembleDebug` réussit, avec tous les tests
  T007 à T010 verts. Corriger les conflits de packaging s'il y en a (exclusions `META-INF`
  BouncyCastle).

**Checkpoint**: la fondation est prête. Le cœur est testé et l'appli s'installe avec un écran
vide.

---

## Phase 3: User Story 1 - Connecter l'appli au casque (Priority: P1) 🎯 MVP

**Goal**: se connecter à l'adbd du casque sans PC (appairage du débogage sans fil) ou via PC
(port 5555), afficher l'état en permanence, et se reconnecter seul à l'ouverture (FR-001 à
FR-007).

**Independent Test**: sur un Quest 3, suivre les scénarios 1.1 à 1.6 de
[quickstart.md](quickstart.md). L'état passe à « Connecté » avec la bonne méthode, puis se
reconnecte après fermeture et réouverture de l'appli.

### Tests for User Story 1

- [X] T023 [P] [US1] Écrire `app/src/test/java/io/github/openquesttuner/core/ConnectionInputTest.kt` :
  - `isValidPairingCode("123456")` est vrai ;
  - `"12345"`, `"1234567"`, `"12a456"` et `" 123456"` sont refusés ;
  - `parsePort("37123") == 37123` ;
  - `parsePort("0")`, `parsePort("65536")`, `parsePort("")` et `parsePort("12;3")` renvoient `null`.
- [X] T024 [P] [US1] Écrire `app/src/test/java/io/github/openquesttuner/core/ConnectionPolicyTest.kt` :
  - `classify(ConnectException(), CONNECT) == PORT_CLOSED`, et aussi en phase `PAIRING` (mauvais
    port d'appairage) ;
  - `classify(IOException("boom"), PAIRING) == PAIRING_CODE_REJECTED` ;
  - `classify(InterruptedException(), DISCOVERY) == SERVICE_NOT_FOUND` et
    `classify(IOException("Could not find any valid host address or port"), DISCOVERY) == SERVICE_NOT_FOUND` ;
  - une exception déclarée dans le fichier de test sous le nom `AdbPairingRequiredException` donne
    `PAIRING_REQUIRED` ;
  - `classify(IllegalStateException(), CONNECT) == UNKNOWN` ;
  - `connectWithProbe` :
    - `connect` renvoie `false` : `NotAuthorized`, et `reset` est appelé une fois ;
    - `probe` échoue 2 fois puis réussit : `Connected`, après 3 `connect` et 2 `reset` ;
    - `probe` échoue toujours : `ProbeFailed`, après `1 + MAX_PROBE_RETRIES` = 3 tentatives et
      3 `reset` ;
    - une exception levée par `connect` est propagée ;
  - `reconnectAttempts` (modifié le 2026-09-23 après essai sur casque : repli sur l'autre méthode) :
    `(WIRELESS, 37000)` → `[WIRELESS/Discover, WIRELESS/Port(37000), PC/Port(5555)]` ;
    `(WIRELESS, null)` → `[WIRELESS/Discover, PC/Port(5555)]` ;
    `(PC, 37000)` → `[PC/Port(5555), WIRELESS/Discover, WIRELESS/Port(37000)]` ; `(null, _)` → `[]`.

### Implementation for User Story 1

- [X] T025 [P] [US1] Créer `app/src/main/java/io/github/openquesttuner/core/ConnectionInput.kt` :
  `object ConnectionInput` avec `isValidPairingCode(code)` (regex « `^[0-9]{6}$` ») et
  `parsePort(text): Int?` (« entier dans 1–65535 »). Faire passer T023.
- [X] T026 [P] [US1] Créer `app/src/main/java/io/github/openquesttuner/core/ConnectionPolicy.kt`
  (dépend de T012). C'est la politique de connexion, testable en JVM : la couche ADB reste mince
  (principe IV). Contenu :
  - `enum class ConnectPhase { PAIRING, DISCOVERY, CONNECT }` ;
  - `sealed interface ConnectTarget { data object Discover; data class Port(val port: Int) }` ;
  - `sealed interface ProbeOutcome { Connected; NotAuthorized; ProbeFailed }` ;
  - `object ConnectionPolicy` avec `PC_PORT = 5555` et `MAX_PROBE_RETRIES = 2` ;
  - `fun classify(error: Throwable, phase: ConnectPhase): FailureReason` :
    - `java.net.ConnectException` → `PORT_CLOSED`, quelle que soit la phase ;
    - une classe nommée `AdbPairingRequiredException` → `PAIRING_REQUIRED`. On compare
      `javaClass.simpleName` pour que le cœur ne dépende pas de libadb ;
    - en `DISCOVERY`, `InterruptedException`, ou `IOException` dont le message contient
      `Could not find any valid host address or port` → `SERVICE_NOT_FOUND` ;
    - toute autre exception en `PAIRING` → `PAIRING_CODE_REJECTED` ;
    - sinon `UNKNOWN` ;
  - `suspend fun connectWithProbe(connect: suspend () -> Boolean, probe: suspend () -> Boolean,
    reset: suspend () -> Unit): ProbeOutcome`, avec au plus `1 + MAX_PROBE_RETRIES` tentatives :
    - si `connect` renvoie faux : `reset`, puis `NotAuthorized` ;
    - si `probe` renvoie faux : `reset`, puis nouvelle tentative ;
    - les exceptions remontent à l'appelant ;
  - `fun reconnectAttempts(lastMethod: ConnectionMethod?, lastWirelessPort: Int?): List<ReconnectAttempt>`,
    avec `data class ReconnectAttempt(method, target)`. On essaie d'abord la dernière méthode
    réussie, puis l'autre en repli. En sans fil : `Discover`, puis `Port(lastWirelessPort)` s'il
    existe. Via PC : `Port(5555)`. `null` → `[]`. Le port TLS change à chaque activation, et le
    port 5555 survit au redémarrage sur Quest 3 (vros 207).

  Faire passer T024.
- [X] T027 [P] [US1] Créer `app/src/main/java/io/github/openquesttuner/adb/AdbIdentityStore.kt` :
  - `class AdbIdentityStore(dir: File)` avec `@Synchronized fun loadOrCreate(): AdbIdentity`
    (`privateKey: PrivateKey`, `certificate: X509Certificate`) ;
  - génération : `KeyPairGenerator` RSA 2048, puis certificat auto-signé BouncyCastle
    (`JcaX509v3CertificateBuilder`, sujet `CN=OpenQuestTuner`, `JcaContentSignerBuilder("SHA256withRSA")`,
    validité de maintenant à +30 ans, numéro de série = `System.currentTimeMillis()`) ;
  - fichiers `dir/adbkey.pk8` (PKCS#8) et `dir/adbkey.crt` (DER) dans `filesDir/adb`, écrits via un
    fichier temporaire puis renommés ;
  - la clé n'est **jamais** journalisée (FR-027) ;
  - si les fichiers sont illisibles, régénérer la paire.
- [X] T028 [P] [US1] Créer `app/src/main/java/io/github/openquesttuner/adb/OqtAdbConnectionManager.kt` :
  - classe qui étend `io.github.muntashirakon.adb.AbsAdbConnectionManager` et reçoit une
    `AdbIdentity` ;
  - dans `init` : `setApi(Build.VERSION.SDK_INT)` (**obligatoire**, sinon pas de TLS) et
    `setTimeout(30, TimeUnit.SECONDS)` ;
  - redéfinit `getPrivateKey()`, `getCertificate()` et `getDeviceName() = "OpenQuestTuner"` ;
  - un commentaire KDoc précise de ne **jamais** appeler `close()` (qui détruit la clé privée) et
    d'utiliser `disconnect()`.
- [X] T029 [P] [US1] Créer `app/src/main/java/io/github/openquesttuner/adb/ConnectionPrefs.kt` :
  SharedPreferences `connection`, avec `lastMethod: ConnectionMethod?` et
  `lastWirelessPort: Int?` en lecture et en écriture.
- [X] T030 [US1] Créer `app/src/main/java/io/github/openquesttuner/adb/AdbShellBackend.kt`
  (dépend de T012, T015, T016, T026, T027 à T029). Il implémente `ShellBackend` selon
  [contracts/shell-backend.md](contracts/shell-backend.md). Toutes les décisions passent par
  `ConnectionPolicy` ; ce fichier ne fait que brancher libadb dessus (principe IV) :
  - `MutableStateFlow<ConnectionState>` ;
  - `Mutex` sur les opérations de connexion, et tout sur `Dispatchers.IO` ;
  - le gestionnaire est créé à la demande à partir de `AdbIdentityStore.loadOrCreate()` ;
  - `pair(port, code)` : état `Pairing`, puis `manager.pair("127.0.0.1", port, code)`.
    - en cas d'exception : `Failed(WIRELESS, ConnectionPolicy.classify(e, PAIRING))` ;
    - en cas de succès : enchaîner sur `connectWireless()` ;
  - `connectWireless(port: Int? = null)` : état `Connecting(WIRELESS)`, puis :
    - si `port == null` : cible `Discover`, avec `manager.connectTls(context, 10_000)`, en phase
      `DISCOVERY` ;
    - sinon : cible `Port(port)`, avec `manager.connect("127.0.0.1", port)`, en phase `CONNECT` ;
  - `connectPc()` : état `Connecting(PC)`, cible `Port(ConnectionPolicy.PC_PORT)` ;
  - chaque tentative passe par
    `ConnectionPolicy.connectWithProbe(connect = { … }, probe = { rawExec(ShellCommand.probe()).isSuccess }, reset = { manager.disconnect() })`
    (research.md R3, #34). `rawExec` exécute sans toucher à l'état et renvoie un échec au lieu de
    lever une exception. Issues possibles :
    - `Connected` : état `Connected`, puis mise à jour de `ConnectionPrefs` ;
    - `NotAuthorized` : `Failed(m, NOT_AUTHORIZED)` ;
    - `ProbeFailed` : `Failed(m, UNKNOWN)` ;
    - exception : `manager.disconnect()`, puis `Failed(m, ConnectionPolicy.classify(e, phase))` ;
  - `reconnectLast()` : parcourt `ConnectionPolicy.reconnectAttempts(prefs.lastMethod, prefs.lastWirelessPort)`
    et s'arrête à la première cible qui aboutit. Si aucune n'aboutit, ou si la liste est vide,
    l'état est `Disconnected`, jamais `Failed` (FR-005) ;
  - `disconnect()` : `manager.disconnect()`, puis `Disconnected` ;
  - `exec(command)` : `manager.openStream("shell:" + command.wireText)`. Lire jusqu'à EOF avec un
    tampon de 8 Ko ; une `IOException("Stream closed.")` après réception de données vaut fin de
    flux. Puis `ShellOutput.parse`. Une `IOException` à l'ouverture donne `Disconnected` et lève
    `ShellUnavailableException` ;
  - délais maximaux interruptibles (`withTimeoutOrNull` et `runInterruptible`) : 2 s pour le
    probe, 15 s pour `exec`. Ajouté après un blocage constaté sur casque ;
  - aucune autre chaîne ne peut être envoyée au shell.
- [X] T031 [US1] Dans `app/src/main/java/io/github/openquesttuner/AppContainer.kt`, ajouter
  `val adb = AdbShellBackend(context, AdbIdentityStore(File(context.filesDir, "adb")), ConnectionPrefs(context))`.
  Dans `app/src/main/java/io/github/openquesttuner/OqtApplication.kt`, lancer
  `container.appScope.launch { container.adb.reconnectLast() }` dans `onCreate` (FR-005).
- [X] T032 [P] [US1] Créer `app/src/main/java/io/github/openquesttuner/ui/components/ConnectionBadge.kt` :
  - puce cliquable avec une pastille de couleur (vert = connecté, orange = en cours, gris =
    déconnecté, rouge = échec) et un libellé : « Connecté (sans fil) », « Connecté (via PC) »,
    « Connexion… », « Appairage… », « Déconnecté », « Échec » ;
  - `onClick` ouvre l'écran Connexion.
- [X] T033 [US1] Dans `app/src/main/java/io/github/openquesttuner/ui/MainViewModel.kt`, exposer
  `connectionState` et ajouter `pair(portText, code)`, `connectWireless(portText?)`,
  `connectPc()` et `disconnect()`, qui délèguent à `container.adb` dans `viewModelScope`. Les
  saisies passent d'abord par `ConnectionInput`, et ne sont jamais envoyées au shell.
- [X] T034 [US1] Créer `app/src/main/java/io/github/openquesttuner/ui/ConnectionScreen.kt` :
  - carte d'état : état, méthode, cause d'échec et action suggérée pour chaque `FailureReason`
    (FR-004), plus un bouton « Se déconnecter » (FR-006) ;
  - carte « Sans PC (recommandé) » :
    - étapes numérotées (FR-007) : activer le débogage sans fil, accepter « Toujours autoriser
      sur ce réseau », puis « Associer avec un code » ;
    - bouton « Ouvrir les options développeur », qui lance
      `Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)` avec
      `FLAG_ACTIVITY_NEW_DOCUMENT or FLAG_ACTIVITY_MULTIPLE_TASK or FLAG_ACTIVITY_NEW_TASK`, dans
      un `try/catch`. En cas d'échec, snackbar avec les instructions textuelles (research.md R4) ;
    - champs « Port d'appairage » et « Code » en `KeyboardType.Number`. Le bouton « Associer »
      est désactivé tant que la saisie est invalide. Un champ non vide et invalide passe en
      `isError = true`, avec un `supportingText` (« 6 chiffres », « port de 1 à 65535 ») ;
    - champ optionnel « Port de connexion », avec la même signalisation d'erreur, et bouton
      « Se connecter » ;
  - carte « Avec un PC » : étapes avec la commande `adb tcpip 5555` en police mono, et le rappel
    d'accepter l'invite dans le casque. Bouton « Se connecter (via PC) » ;
  - écran défilable, cibles d'au moins 48dp.
- [X] T035 [US1] Dans `app/src/main/java/io/github/openquesttuner/ui/OqtApp.kt` et
  `app/src/main/java/io/github/openquesttuner/ui/GamesScreen.kt`, brancher la route
  `Screen.Connection` et placer `ConnectionBadge` dans les `actions` de la `TopAppBar` de l'écran
  Jeux.
- [X] T036 [US1] Ajouter toutes les chaînes de l'US1 (états, étapes, libellés) dans
  `app/src/main/res/values/strings.xml` (anglais) **et** `app/src/main/res/values-fr/strings.xml`
  (français). Pour chaque `FailureReason`, la cause et l'action suggérée (FR-004) sont, en
  français :
  - `PORT_CLOSED` : « Connexion refusée : active le débogage sans fil, ou relance
    `adb tcpip 5555` depuis le PC. » ;
  - `NOT_AUTHORIZED` : « Autorisation non accordée : accepte l'invite « Autoriser le débogage »
    dans le casque, puis réessaie. » ;
  - `PAIRING_REQUIRED` : « Appairage nécessaire : associe l'appli avec un code. » ;
  - `PAIRING_CODE_REJECTED` : « Code refusé ou expiré : demande un nouveau code et réessaie. » ;
  - `SERVICE_NOT_FOUND` : « Débogage sans fil introuvable : réactive-le dans les options
    développeur. Inutile de réappairer. » ;
  - `UNKNOWN` : « Échec inattendu : réessaie. Si ça persiste, désactive puis réactive le
    débogage sans fil. »

  Les équivalents anglais ont le même sens.
- [X] T037 [P] [US1] *(amendement du 2026-09-23)* Compléter deux fichiers de tests :
  - `app/src/test/java/io/github/openquesttuner/core/ShellCommandsTest.kt` :
    `enableWirelessDebugging().text == "settings put global adb_wifi_enabled 1"` et
    `readWirelessDebugging().text == "settings get global adb_wifi_enabled"`. Ajouter les deux
    commandes au test « aucune commande générable n'écrit une propriété persistante » ;
  - `app/src/test/java/io/github/openquesttuner/core/ShellOutputTest.kt` :
    `isSettingEnabled("1\n")` est vrai ; `"0"`, `"null"` et `""` sont faux.
- [X] T038 [P] [US1] *(amendement)* Compléter `app/src/test/java/io/github/openquesttuner/core/ConnectionPolicyTest.kt`
  avec `awaitWirelessEnabled`, testé en temps virtuel (`runTest`) :
  - `read` renvoie faux, faux, puis vrai : résultat vrai, après 3 lectures ;
  - `read` toujours faux : résultat faux au bout de 60 000 ms virtuelles, soit environ 60 lectures ;
  - une lecture qui lève une exception compte comme faux ; la suivante peut réussir.
- [X] T039 [US1] *(amendement)* Ajouter au cœur :
  - `ShellCommand.enableWirelessDebugging()` (C7) et `ShellCommand.readWirelessDebugging()` (C8)
    dans `app/src/main/java/io/github/openquesttuner/core/ShellCommands.kt` ;
  - `ShellOutput.isSettingEnabled(output)` dans
    `app/src/main/java/io/github/openquesttuner/core/ShellOutput.kt` ;
  - `enum class WirelessSwitchResult { SWITCHED, NOT_ACCEPTED, WIRELESS_FAILED, NOT_CONNECTED }` et
    `suspend fun ConnectionPolicy.awaitWirelessEnabled(read, timeoutMs = 60_000, pollMs = 1_000)`
    dans `app/src/main/java/io/github/openquesttuner/core/ConnectionPolicy.kt`.

  Faire passer T037 et T038.
- [X] T040 [US1] *(amendement)* Ajouter `suspend fun switchToWireless(): WirelessSwitchResult` à
  `app/src/main/java/io/github/openquesttuner/adb/AdbShellBackend.kt`, selon
  [contracts/shell-backend.md](contracts/shell-backend.md) :
  - n'agit que depuis `Connected(PC)`, sinon `NOT_CONNECTED` ; depuis `Connected(WIRELESS)`,
    renvoie directement `SWITCHED` ;
  - `exec(C7)`, puis `awaitWirelessEnabled { ShellOutput.isSettingEnabled(exec(C8).output) }` ;
    l'appli reste `Connected(PC)` pendant l'attente ;
  - si le délai est dépassé : `NOT_ACCEPTED` ;
  - sinon, `connectWireless()` ; en cas d'échec, reconnexion silencieuse au port 5555, puis
    `WIRELESS_FAILED`.
- [X] T041 [US1] *(amendement)* Dans `app/src/main/java/io/github/openquesttuner/ui/MainViewModel.kt`,
  ajouter :
  - `switchingToWireless: StateFlow<Boolean>` ;
  - `switchToWireless()`, avec un `UiMessage` par résultat : `NOT_ACCEPTED` (« Réseau non
    autorisé dans le délai : réessaie et accepte la fenêtre d'Horizon OS »), `WIRELESS_FAILED` et
    `NOT_CONNECTED`. Rien à afficher pour `SWITCHED`, l'état suffit.
- [X] T042 [US1] *(amendement)* Réorganiser `app/src/main/java/io/github/openquesttuner/ui/ConnectionScreen.kt` :
  - carte d'état : si `Connected(PC)`, bouton « Passer en sans fil » (d'au moins 48dp), avec
    l'explication de la fenêtre Horizon OS et un indicateur pendant `switchingToWireless` ;
  - la carte PC passe en premier, sous le titre « Première connexion (une seule fois, avec un PC) » ;
  - la carte d'appairage passe en dernier, sous le titre « Appairage par code (si ton casque le
    propose) », avec une mention indiquant que certaines versions d'Horizon OS n'affichent pas cet
    écran.
- [X] T043 [US1] *(amendement)* Ajouter et mettre à jour les chaînes de l'amendement (titres de
  cartes, bouton, explication, 3 messages) dans `app/src/main/res/values/strings.xml` **et**
  `app/src/main/res/values-fr/strings.xml`. Lancer `./gradlew test assembleDebug`.
- [X] T044 [US1] Installer sur le Quest 3, puis dérouler les scénarios 1.1 à 1.7 du
  [quickstart.md](quickstart.md) amendé. Consigner les résultats dans `docs/compatibility.md`.
  - **Déjà constaté le 2026-09-23 (Quest 3, vros 207)**, avant l'amendement :
    - ✅ 1.1 : connexion via PC ;
    - ✅ 1.2, en manuel (`adb_wifi_enabled=1` écrit depuis le PC, puis « Se connecter » sans fil) :
      connexion TLS sans appairage ;
    - ✅ 1.3 : reconnexion en moins de 1 s, par les deux méthodes ;
    - ✅ 1.5 : repli sur le port 5555 après un redémarrage (après correctif du délai maximal) ;
    - ➖ 1.7 : écran d'appairage absent sur cette version.
    - **Clôture (même jour, après l'amendement)** : ✅ 1.2 avec le vrai bouton (sans fil en 0,7 s,
      sans code). ➖ 1.4 non reproductible sur ce casque : le port 5555 reste ouvert même après
      `adb usb`. Le chemin « Déconnecté sans erreur » a été vu avant l'amendement et reste couvert
      par les tests de `ConnectionPolicy`. ➖ 1.6 (chronométrage avec une personne novice) reporté à
      la validation finale (dernière tâche). ✅ Reconnexion à froid en 2,1 s (probe ramené à 2 s).

**Checkpoint**: l'US1 fonctionne. L'appli se connecte au casque par les deux méthodes.

---

## Phase 4: User Story 2 - Optimiser un jeu et le lancer (Priority: P1) 🎯 MVP

**Goal**: lister les jeux VR, régler un profil par jeu, puis « Appliquer et lancer » en un
geste (FR-008, FR-011 à FR-013, FR-015 à FR-022).

**Independent Test**: scénarios 2.1 à 2.8 de [quickstart.md](quickstart.md). Le jeu démarre
avec le profil, et `getprop` montre les 7 valeurs attendues.

### Tests for User Story 2

- [X] T045 [P] [US2] Écrire `app/src/test/java/io/github/openquesttuner/core/ProfileStoreTest.kt`,
  avec `TemporaryFolder` et [contracts/profiles-json.md](contracts/profiles-json.md) :
  - aller-retour `save` puis `load` ;
  - un profil vide supprime l'entrée ;
  - un profil vide présent dans le fichier est ignoré au chargement ;
  - les champs inconnus sont ignorés ;
  - une clé qui n'est pas un nom de paquet est ignorée ;
  - un fichier corrompu est renommé en `profiles.json.corrupt-<horodatage>` et le résultat est
    vide ;
  - avec `"version": 2`, pas de réécriture tant qu'aucun `save` n'a eu lieu ;
  - l'écriture passe par `profiles.json.tmp`, et il n'en reste aucun après un `save`.
- [X] T046 [P] [US2] Créer `app/src/test/java/io/github/openquesttuner/core/FakeShellBackend.kt`,
  qui enregistre les `command.text` exécutés et renvoie des résultats scriptés par préfixe de
  commande, ou lève `ShellUnavailableException` à la demande.

  Écrire ensuite `app/src/test/java/io/github/openquesttuner/core/TunerApplyTest.kt` :
  - `applyAndLaunch` exécute exactement : `forceStop`, puis les 7 propriétés dans l'ordre de
    l'enum (`setProperty` ou `resetProperty` selon le profil), puis `launch` ;
  - le premier échec arrête la séquence avec `StepFailed(SetProperty(p), output)`, sans `launch` ;
  - une sortie `Error:` de `am start` donne `StepFailed(Launch, …)` ;
  - un profil invalide pour le modèle donne `InvalidProfile`, avec **aucune** commande exécutée ;
  - `ShellUnavailableException` donne `NotConnected`.
- [X] T047 [P] [US2] Écrire `app/src/test/java/io/github/openquesttuner/core/SearchKeyTest.kt` :
  `searchKey("Éléphant Rouge") == "elephant rouge"`, `searchKey("  Beat SABER ") == "beat saber"`,
  et un tri par `searchKey` qui place « Élite » entre « Echo » et « Fable ».

### Implementation for User Story 2

- [X] T048 [P] [US2] Créer `app/src/main/java/io/github/openquesttuner/core/SearchKey.kt` :
  `fun searchKey(s: String)`, qui applique `Normalizer.normalize(NFD)`, retire les marques
  diacritiques `\p{Mn}`, met en minuscules avec `Locale.ROOT` et applique `trim`. Faire passer
  T047.
- [X] T049 [P] [US2] Créer `app/src/main/java/io/github/openquesttuner/core/ProfileStore.kt` :
  - `class ProfileStore(file: File)` avec `profiles: StateFlow<Map<String, GameProfile>>`,
    `suspend fun load()`, `suspend fun save(pkg, profile)` et `suspend fun delete(pkg)` ;
  - `Mutex`, JSON `{ "version": 1, "profiles": {...} }` avec
    `Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }` ;
  - écriture atomique via `.tmp` puis `renameTo` ;
  - toutes les règles de [contracts/profiles-json.md](contracts/profiles-json.md) ;
  - uniquement `java.io`. Faire passer T045.
- [X] T050 [US2] Créer `app/src/main/java/io/github/openquesttuner/core/Tuner.kt` (dépend de
  T013 à T016) :
  - `class Tuner(shell: ShellBackend, model: QuestModel)` ;
  - `suspend fun applyAndLaunch(pkg, activity, profile): TuneResult`, avec les types
    `TuneResult` et `TuneStep` de [data-model.md](data-model.md) ;
  - validation d'abord, puis la séquence « Appliquer et lancer » du contrat : arrêt au premier
    échec, `launch` en échec si `ShellOutput.isLaunchError(output)`. Faire passer T046.
- [X] T051 [P] [US2] Créer `app/src/main/java/io/github/openquesttuner/games/GameRepository.kt` :
  - `data class InstalledGame(packageName, label, launchActivity)` ;
  - `suspend fun loadGames(): List<InstalledGame>` sur `Dispatchers.IO`, avec les deux sources de
    research.md R5 :
    1. `queryIntentActivities(Intent(ACTION_MAIN).addCategory("com.oculus.intent.category.VR"))` ;
    2. `getInstalledApplications(GET_META_DATA)` pour les applis dont les meta-data contiennent
       `com.samsung.android.vr.application.mode` ou `com.oculus.ossplash`, avec l'activité
       `LAUNCHER` (sinon `INFO`) comme point d'entrée ;
  - exclusions : `FLAG_SYSTEM`, `!enabled`, paquets qui exposent un service
    `com.oculus.vrshell.SHELL_MAIN`, et le paquet de l'appli ;
  - `packageName` et `launchActivity` validés par `PACKAGE_REGEX` et `CLASS_REGEX` (une entrée
    invalide est ignorée) ;
  - dédoublonnage par paquet, en privilégiant la source 1, puis tri par `searchKey(label)` ;
  - `fun isInstalled(pkg: String): Boolean`, via `getPackageInfo` (`NameNotFoundException` →
    faux) ;
  - utiliser les API `*Flags.of(0)` sur API 33+, et les API dépréciées en dessous.
- [X] T052 [US2] Dans `app/src/main/java/io/github/openquesttuner/AppContainer.kt`, ajouter
  `profileStore = ProfileStore(File(context.filesDir, "profiles.json"))`, `games = GameRepository(context)`
  et `tuner = Tuner(adb, questModel)`. Lancer `profileStore.load()` au démarrage dans
  `OqtApplication`.
- [X] T053 [P] [US2] Créer `app/src/main/java/io/github/openquesttuner/ui/components/GameIcon.kt` :
  icône 48dp chargée par `produceState` sur `Dispatchers.IO` (`pm.getApplicationIcon(pkg).toBitmap(96, 96)`),
  avec un cache mémoire `LruCache<String, ImageBitmap>(200)` et une icône générique si le
  chargement échoue.
- [X] T054 [US2] Dans `app/src/main/java/io/github/openquesttuner/ui/MainViewModel.kt`, ajouter :
  - `games: StateFlow<List<InstalledGame>>`, chargée à l'initialisation, et `refreshGames()` ;
  - `profiles`, en délégation au store ;
  - `questModel` ;
  - `saveProfile(pkg, profile)` ;
  - `applyAndLaunch(game, profile)` : enregistre d'abord, puis appelle `tuner.applyAndLaunch` et
    traduit le résultat en `UiMessage` :
    - `Success` : message « Jeu lancé — les réglages restent actifs jusqu'à « Tout
      réinitialiser » ou un redémarrage » (FR-022) ;
    - `StepFailed` : message qui nomme la propriété ou l'étape (FR-020) ;
    - `InvalidProfile` et `NotConnected` : message dédié ;
    - `StepFailed(Launch)` quand `games.isInstalled(pkg)` est faux : message « jeu introuvable »,
      puis `refreshGames()` et retour à la liste (cas limite « jeu désinstallé… ») ;
  - un état `busy` pendant l'opération.
- [X] T055 [US2] Dans `app/src/main/java/io/github/openquesttuner/ui/GamesScreen.kt`, remplacer le
  contenu provisoire par une `LazyColumn` de lignes d'au moins 64dp : `GameIcon`, nom, paquet en
  petit. Un appui ouvre `Screen.Profile(pkg)`.
- [X] T056 [US2] Créer `app/src/main/java/io/github/openquesttuner/ui/ProfileScreen.kt` :
  - `TopAppBar` avec retour et nom du jeu. Brouillon local
    `remember(pkg) { mutableStateOf(saved ?: GameProfile()) }` ;
  - six `ChoiceRow` :
    - fréquence : `model.refreshRates`, libellés « N Hz » ;
    - résolution : `RESOLUTION_STEPS`, libellés « ×1,2 · 2016×2112 », calculés avec
      `EyeTexture.forStep(model.defaultEyeTexture, step)`, plus une puce « Personnalisé L×H »
      sélectionnée si `stepOf` renvoie `null` ;
    - CPU : `model.cpuLevels` ;
    - GPU : `model.gpuLevels` ;
    - fovéal fixe : Désactivé, Faible, Moyen, Élevé, Très élevé ;
    - fovéal dynamique : Activé, Désactivé ;
  - pour CPU/GPU, `helpText` quand le niveau choisi dépasse `alwaysAvailableCpuMax` ou
    `alwaysAvailableGpuMax` (« nécessite de désactiver le passthrough », research.md R2) ;
  - `experimental = !model.isVerified(prop)` (FR-017). Si le modèle est `UNKNOWN`, bandeau
    « Casque non reconnu : valeurs du Quest 3 » (FR-016) ;
  - carte d'avertissement selon `draft.warnings(model)` (FR-018) ;
  - carte d'information persistante sur les réglages qui restent actifs (FR-022) ;
  - barre du bas : « Enregistrer » (toujours actif) et « Appliquer et lancer ». Ce dernier est
    désactivé si l'appli n'est pas `Connected`, avec un texte explicatif et un bouton vers
    l'écran Connexion (FR-021).
- [X] T057 [US2] Brancher la route `Screen.Profile` dans
  `app/src/main/java/io/github/openquesttuner/ui/OqtApp.kt`, et ajouter toutes les chaînes de l'US2
  dans `app/src/main/res/values/strings.xml` **et** `app/src/main/res/values-fr/strings.xml`.
- [X] T058 [US2] Lancer `./gradlew test assembleDebug`, installer, puis dérouler les scénarios 2.1
  à 2.8 de [quickstart.md](quickstart.md). Consigner chaque essai de propriété dans le journal de
  `docs/compatibility.md`.
  - **Validé le 2026-09-23 (Quest 3, vros 207)**, jeu de référence Beat Saber 1.44.3 :
    - ✅ 2.1, 2.2, 2.7, 2.8 : confirmés dans le casque par l'utilisateur ;
    - ✅ 2.3 et 2.4 : les 7 valeurs sont visibles dans `getprop`, et la séquence complète prend
      221 ms (SC-003) ;
    - ✅ 2.5 : effet mesuré dans les statistiques VrApi pour 6 propriétés sur 7 (le fovéal
      dynamique n'y apparaît pas), qui passent en « vérifié » pour le Quest 3 dans `QuestModel` ;
    - ✅ 2.6 : confirmé par l'utilisateur ;
    - ✅ second jeu : Hunting VR (Unity), profil appliqué à l'identique ;
    - 2.9 (SC-002, chronométrage) : reporté à la validation finale, comme 1.6.

**Checkpoint**: premier incrément utilisable. Le MVP est complet après l'US4 (phase 5), qui
ajoute l'annulation en un geste exigée par le principe I.

---

## Phase 5: User Story 4 - Tout réinitialiser (Priority: P1) 🎯 MVP

**Goal**: remettre les 7 propriétés gérées à leur valeur par défaut en un geste (FR-023).
Cette story fait partie du MVP : c'est le filet de sécurité exigé par la constitution
(principe I).

**Independent Test**: scénario 4.1 de [quickstart.md](quickstart.md).

### Tests for User Story 4

- [X] T059 [US4] Écrire `app/src/test/java/io/github/openquesttuner/core/TunerResetTest.kt` avec
  `FakeShellBackend` :
  - `resetAll()` exécute `resetProperty` pour les 7 propriétés, **même si** l'une échoue ;
  - le résultat est `ResetResult(failed = [propriétés en échec])` ;
  - `ShellUnavailableException` est propagée sous forme de résultat « non connecté ».

### Implementation for User Story 4

- [X] T060 [US4] Ajouter `suspend fun resetAll(): ResetResult?` à
  `app/src/main/java/io/github/openquesttuner/core/Tuner.kt`. Il renvoie `null` si l'appli n'est
  pas connectée. Faire passer T059.
- [X] T061 [US4] Dans `app/src/main/java/io/github/openquesttuner/ui/MainViewModel.kt`, ajouter
  `resetAll()`, avec un message de succès ou la liste des propriétés en échec.
- [X] T062 [US4] Dans `app/src/main/java/io/github/openquesttuner/ui/ConnectionScreen.kt`, ajouter
  une carte « Outils » avec le bouton « Tout réinitialiser ». Hors connexion, il est désactivé
  avec le rappel : « un redémarrage du casque efface aussi tous les réglages ».
- [ ] T063 [US4] Ajouter les chaînes de l'US4 dans `app/src/main/res/values/strings.xml` **et**
  `app/src/main/res/values-fr/strings.xml`, lancer `./gradlew test assembleDebug`, puis dérouler
  le scénario 4.1 de [quickstart.md](quickstart.md).
  - ❌ 4.1, premier essai du 2026-09-23 (Quest 3, vros 207) : « Tout réinitialiser » déconnecte
    l'appli. D'après les journaux, une commande est restée 15 s sans réponse, puis la connexion a
    été déclarée perdue. Le même blocage avait aussi interrompu un « Appliquer et lancer » (jeu
    arrêté, jamais relancé). Cause : deux réveils perdus dans libadb 3.1.1 (research.md R3).
  - Correctif (contracts/shell-backend.md amendé) : lecture arrêtée au marqueur de fin
    (`ShellOutput.isComplete`), délai de 3 s par commande et 3 essais sur la même connexion
    (`ConnectionPolicy.withRetries`). 7 tests JVM ajoutés, 109 au total. À revalider sur
    casque.

**Checkpoint**: MVP complet. On se connecte, on règle et on lance un jeu, et on annule tout
en un geste (principe I).

---

## Phase 6: User Story 3 - Retrouver et gérer ses profils (Priority: P2)

**Goal**: recherche, indicateur de profil, suppression, rafraîchissement et lancement direct
depuis la liste (FR-009, FR-010, FR-014, FR-015).

**Independent Test**: scénarios 3.1 à 3.3 de [quickstart.md](quickstart.md).

### Tests for User Story 3

- [ ] T064 [US3] Compléter `app/src/test/java/io/github/openquesttuner/core/SearchKeyTest.kt` avec
  `matchesQuery("Beat Saber", "saber")`, `matchesQuery("Élite Dangerous", "elite")` et
  `matchesQuery("X", "")` (requête vide : vrai), ainsi que `matchesQuery("Beat Saber", "zzz")`
  (faux).

### Implementation for User Story 3

- [ ] T065 [US3] Ajouter `fun matchesQuery(label: String, query: String): Boolean` à
  `app/src/main/java/io/github/openquesttuner/core/SearchKey.kt` : il est vrai si
  `searchKey(label)` contient `searchKey(query)`. Faire passer T064.
- [ ] T066 [US3] Dans `app/src/main/java/io/github/openquesttuner/ui/MainViewModel.kt`, ajouter :
  - `query` et `filteredGames`, combinaison de `games` et `query` ;
  - `deleteProfile(pkg)` ;
  - `quickLaunch(game)` : profil enregistré, ou `GameProfile()` pour tout remettre par défaut. Il
    passe par le même chemin qu'`applyAndLaunch`, et hérite donc de la gestion « jeu introuvable ».
- [ ] T067 [US3] Dans `app/src/main/java/io/github/openquesttuner/ui/GamesScreen.kt`, ajouter :
  - un champ de recherche en tête de liste ;
  - une puce « Profil » sur les jeux qui ont un profil non vide (FR-015) ;
  - une action « Actualiser » dans la `TopAppBar` ;
  - un bouton « Lancer », d'au moins 48dp, sur chaque ligne, désactivé si l'appli n'est pas
    connectée. Dans ce cas, un bandeau en tête de liste explique que les lancements sont
    désactivés et propose « Se connecter », qui ouvre l'écran Connexion (FR-021) ;
  - un état vide explicatif s'il n'y a aucun jeu VR, et un autre pour une recherche sans
    résultat.
- [ ] T068 [US3] Dans `app/src/main/java/io/github/openquesttuner/ui/ProfileScreen.kt`, ajouter
  l'action « Supprimer le profil » dans la `TopAppBar` quand un profil existe, avec un
  `AlertDialog` de confirmation (FR-014). Après suppression, le brouillon revient à
  `GameProfile()`.
- [ ] T069 [US3] Ajouter les chaînes de l'US3 dans `app/src/main/res/values/strings.xml` **et**
  `app/src/main/res/values-fr/strings.xml`, lancer `./gradlew test assembleDebug`, puis dérouler
  les scénarios 3.1 à 3.3 de [quickstart.md](quickstart.md).

**Checkpoint**: les US1 à US4 fonctionnent.

---

## Phase 7: User Story 5 - Diagnostiquer les réglages actifs (Priority: P3)

**Goal**: afficher en lecture seule les propriétés `debug.oculus.*` actives (FR-024).

**Independent Test**: scénario 5.1 de [quickstart.md](quickstart.md).

### Tests for User Story 5

- [ ] T070 [US5] Écrire `app/src/test/java/io/github/openquesttuner/core/TunerDiagnosticTest.kt`
  avec `FakeShellBackend` :
  - `readDiagnostic()` exécute uniquement `readProperties()` ;
  - sur une sortie `getprop` réaliste (mélange de propriétés), il renvoie
    `Diagnostic(active = …)` avec seulement les clés `debug.oculus.*` non vides, triées ;
  - il renvoie `null` si l'appli n'est pas connectée.

### Implementation for User Story 5

- [ ] T071 [US5] Ajouter `suspend fun readDiagnostic(): Diagnostic?` à
  `app/src/main/java/io/github/openquesttuner/core/Tuner.kt`. Faire passer T070.
- [ ] T072 [US5] Dans `app/src/main/java/io/github/openquesttuner/ui/MainViewModel.kt`, ajouter
  `diagnostic: StateFlow<Diagnostic?>` et `refreshDiagnostic()`. Relire automatiquement après
  « Appliquer et lancer » et après « Tout réinitialiser ».
- [ ] T073 [US5] Dans `app/src/main/java/io/github/openquesttuner/ui/ConnectionScreen.kt`, ajouter
  une carte « Diagnostic » :
  - liste `clé = valeur` en police mono ;
  - bouton « Actualiser » ;
  - texte « Aucun réglage actif » si la map est vide ;
  - carte masquée hors connexion.
- [ ] T074 [US5] Ajouter les chaînes de l'US5 dans `app/src/main/res/values/strings.xml` **et**
  `app/src/main/res/values-fr/strings.xml`, lancer `./gradlew test assembleDebug`, puis dérouler
  le scénario 5.1 de [quickstart.md](quickstart.md).

**Checkpoint**: les 5 user stories fonctionnent.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: garde-fous de la constitution, documentation, validation complète.

- [ ] T075 [P] Écrire `app/src/test/java/io/github/openquesttuner/core/CoreArchitectureTest.kt` : il
  parcourt `app/src/main/java/io/github/openquesttuner/core/**/*.kt` et échoue si un fichier
  contient `import android.` ou `import androidx.` (principe IV).
- [ ] T076 [P] Écrire `app/src/test/java/io/github/openquesttuner/StringsParityTest.kt` : il parse
  `app/src/main/res/values/strings.xml` et `app/src/main/res/values-fr/strings.xml`, puis échoue
  si les ensembles de `name` diffèrent, en ignorant les chaînes `translatable="false"` comme
  `app_name` (FR-029, SC-010).
- [ ] T077 [P] Créer `README.md` en anglais, pour le public GitHub, avec un paragraphe
  d'introduction en français. Contenu :
  - ce que fait l'appli et sa licence GPL-3.0 ;
  - avertissement sur les propriétés non documentées et « expérimentales » ;
  - installation (sideload) ;
  - connexion sans PC et via PC ;
  - build (`./gradlew test assembleDebug`) ;
  - liens vers `docs/compatibility.md` et `specs/001-game-profiles-mvp/` ;
  - mention clean-room : aucun lien avec Quest Games Optimizer.
- [ ] T078 Retirer le commentaire « Sync Impact Report » en tête de `.specify/memory/constitution.md`
  : c'est une note temporaire à supprimer avant le premier commit.
- [ ] T079 Lancer `./gradlew test assembleDebug lintDebug` et corriger toutes les erreurs de lint,
  notamment `MissingTranslation`. Les avertissements restants sont notés dans la description du
  commit.
- [ ] T080 Validation complète sur Quest 3 (**nécessite le casque**) :
  - dérouler tout [quickstart.md](quickstart.md), y compris 5.2 (langues) et la section 5
    (réseau, SC-009) ;
  - remplir `docs/compatibility.md` ;
  - passer en « vérifié » dans `QuestModel.QUEST_3.verified`
    (`app/src/main/java/io/github/openquesttuner/core/QuestModel.kt`) les propriétés dont l'effet
    est constaté (SC-008), puis ajuster `QuestModelTest` ;
  - amender [contracts/shell-commands.md](contracts/shell-commands.md) si `--user current` ou
    `setprop <clé> ''` se comportent autrement que prévu.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (phase 1)** : aucune dépendance.
- **Foundational (phase 2)** : dépend de la phase 1. Elle **bloque** toutes les user stories.
- **US1 (phase 3)** : dépend de la phase 2.
- **US2 (phase 4)** : dépend de la phase 2. Son code compile sans l'US1, mais sa validation sur
  casque suppose d'être connecté (US1).
- **US4 (phase 5)** : dépend de l'US1 (écran Connexion) et de l'US2 (`core/Tuner.kt`,
  `AppContainer.tuner`). Elle fait partie du MVP (principe I).
- **US3 (phase 6)** : dépend de l'US2 (liste des jeux, profils, `applyAndLaunch`).
- **US5 (phase 7)** : dépend de l'US1 et de l'US2, pour la même raison que l'US4. Elle est
  indépendante de l'US3 et de l'US4, hormis les fichiers partagés.
- **Polish (phase 8)** : dépend des stories livrées.

### Fichiers partagés (donc séquentiels, jamais [P] entre eux)

- `ui/MainViewModel.kt` : T020, T033, T054, T061, T066, T072.
- `core/Tuner.kt` : T050, T060, T071.
- `ui/ConnectionScreen.kt` : T034, T062, T073.
- `ui/GamesScreen.kt` : T021, T035, T055, T067.
- `ui/OqtApp.kt` : T021, T035, T057.
- `AppContainer.kt` : T019, T031, T052.
- `core/SearchKey.kt` : T048, T065. `core/SearchKeyTest.kt` : T047, T064.
- `strings.xml` (en et fr) : T006, T036, T057, T063, T069, T074.

### Within Each User Story

- Les tests du cœur sont écrits d'abord et doivent échouer avant l'implémentation.
- Ordre : cœur (`core/`), puis couche Android (`adb/`, `games/`), puis ViewModel, puis écrans,
  puis chaînes, puis validation sur casque.

### Parallel Opportunities

- Phase 1 : T002, T003, T004 et T006 en parallèle, après T001.
- Phase 2 : les 4 tests T007 à T010 en parallèle. Puis T011, T012, T017 et T018 en parallèle.
  Ensuite T013 → T014, et T015 et T016 dès que T011 et T012 sont prêts.
- US1 : T023 à T029 en parallèle (tests, `ConnectionInput`, `ConnectionPolicy`, identité,
  gestionnaire, préférences), et T032 en parallèle de T030.
- US2 : T045 à T047 en parallèle. Puis T048, T049, T051 et T053 en parallèle.
- Après l'US2, l'US3, l'US4 et l'US5 peuvent avancer en parallèle, à condition de séquencer les
  fichiers partagés.

---

## Parallel Example: User Story 2

```bash
# Tests du cœur de l'US2, en parallèle :
Task: "ProfileStoreTest dans app/src/test/java/io/github/openquesttuner/core/ProfileStoreTest.kt"
Task: "FakeShellBackend + TunerApplyTest dans app/src/test/java/io/github/openquesttuner/core/"
Task: "SearchKeyTest dans app/src/test/java/io/github/openquesttuner/core/SearchKeyTest.kt"

# Puis les fichiers indépendants, en parallèle :
Task: "SearchKey.kt dans app/src/main/java/io/github/openquesttuner/core/"
Task: "ProfileStore.kt dans app/src/main/java/io/github/openquesttuner/core/"
Task: "GameRepository.kt dans app/src/main/java/io/github/openquesttuner/games/"
Task: "GameIcon.kt dans app/src/main/java/io/github/openquesttuner/ui/components/"
```

---

## Implementation Strategy

### MVP first (US1 + US2 + US4)

Le MVP contient les trois stories P1 :
- se connecter (US1) ;
- régler et lancer un jeu (US2) ;
- tout annuler en un geste (US4). La constitution l'exige (principe I) : aucun incrément ne
  doit appliquer des réglages sans offrir cette annulation.

1. Phase 1 (Setup), puis phase 2 (Foundational) : le cœur est testé et l'appli s'installe.
2. Phase 3 (US1) : **STOP et VALIDER** la connexion sur le Quest 3 (quickstart 1.x). C'est le
   plus gros risque technique : appairage sans PC, bug #34 de libadb.
3. Phase 4 (US2) : **VALIDER** sur casque (quickstart 2.x).
4. Phase 5 (US4) : **VALIDER** avec le scénario 4.1. Le MVP est alors complet et livrable.

### Livraison incrémentale

5. US3 (gestion des profils), puis US5 (diagnostic).
6. Phase 8 : garde-fous, README, validation complète et passage en « vérifié ».

---

## Notes

- [P] = fichiers différents, sans dépendance sur une tâche inachevée.
- Chaque tâche de story porte son label [USn], pour la traçabilité vers [spec.md](spec.md).
- Après chaque tâche ou groupe logique, `./gradlew test` doit rester vert (condition
  d'intégration de la constitution).
- Tout ajout de commande shell doit d'abord amender
  [contracts/shell-commands.md](contracts/shell-commands.md) (principe I).
- Les tâches T044, T058, T063, T069, T074 et T080 exigent le Quest 3 physique.
