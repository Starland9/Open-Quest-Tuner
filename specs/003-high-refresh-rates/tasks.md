---

description: "Liste des tâches : Fréquences d'affichage au-delà de 120 Hz"
---

# Tasks: Fréquences d'affichage au-delà de 120 Hz

**Input**: documents de conception dans `specs/003-high-refresh-rates/` :
- [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md) ;
- contrat : [display-rates.md](contracts/display-rates.md) ;
- [quickstart.md](quickstart.md).

**Prerequisites**: plan.md et spec.md (obligatoires), research.md, data-model.md, contracts/. Le
MVP (001) et la reconnexion autonome (002) sont implémentés : ce document ne liste que les
changements.

**Tests**: **OBLIGATOIRES** pour le cœur `core/`. La constitution (principe IV) l'impose :
« Chaque règle métier du cœur DOIT être couverte par des tests unitaires JVM ». Ce sont des tests
JUnit 4 dans `app/src/test/`. Chaque test est écrit avant le code qu'il couvre, et doit échouer
tant que ce code n'existe pas. La couche Android (`DisplayManager`, UI) est validée sur casque
avec [quickstart.md](quickstart.md).

**Organization**: les tâches sont regroupées par user story, dans l'ordre des priorités de la
spec : US1 (P1), US2 (P2), US3 (P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]** : peut être faite en parallèle (fichier différent, aucune dépendance sur une tâche
  inachevée).
- **[Story]** : user story concernée (US1 à US3, voir [spec.md](spec.md)).
- Chaque description donne le chemin exact du fichier.

## Path Conventions

- Projet Android, un seul module `app/`.
- Code : `app/src/main/java/io/github/openquesttuner/` (abrégé `…/` ci-dessous).
- Tests JVM : `app/src/test/java/io/github/openquesttuner/` (abrégé `test/…/` ci-dessous).
- Le package `core/` NE DOIT importer aucune classe `android.*` ou `androidx.*` (principe IV,
  vérifié par `CoreArchitectureTest`).
- Toute chaîne visible par l'utilisateur va dans `app/src/main/res/values/strings.xml` (anglais)
  **et** dans `app/src/main/res/values-fr/strings.xml` (français), jamais en dur (FR-011).
  `StringsParityTest` vérifie la parité et les paramètres de format. Les textes français proposés
  ci-dessous sont des suggestions ; l'anglais doit en être l'équivalent exact.
- Les facteurs de résolution s'affichent avec `%.1f` (« ×0,8 » en français), comme
  `resolution_step` : on passe le palier divisé par 100.
- Aucune nouvelle commande shell : ne rien ajouter à `ShellCommand` (research.md R3).

---

## Phase 1: Setup

**Purpose**: partir d'une base verte.

- [X] T001 Lancer `./gradlew test assembleDebug` avant tout changement, et noter que la base est
  verte (190 tests au commit `70dbe89`). En cas d'échec, s'arrêter et le signaler.

---

## Phase 2: Foundational (prérequis bloquants)

**Purpose**: l'interface `DisplayRates`, sa conversion testée, et le moniteur Android qui
l'implémente. Toutes les user stories en dépendent.

**⚠️ CRITICAL**: aucune user story ne peut commencer avant la fin de cette phase.

### Tests (écrits d'abord, ils doivent échouer)

- [X] T002 [P] Créer `test/…/core/DisplayRatesTest.kt`, pour `DisplayRates.ratesFromModes` :
  - `[72.00001f, 90.0f, 207.00003f, 144.00002f]` → `{72, 90, 144, 207}` ;
  - doublons fusionnés : `[90.0f, 90.0f]` → `{90}` (modes 4128×2208 et 3104×1664 à la même
    fréquence) ;
  - `59.94f` → `{60}` ;
  - valeurs ignorées : `0f`, valeurs négatives, `Float.NaN`, `Float.POSITIVE_INFINITY` ;
  - liste vide → ensemble vide.
- [X] T003 [P] Créer `test/…/core/FakeDisplayRates.kt` :
  `class FakeDisplayRates(var declared: Set<Int> = (72..207).toSet()) : DisplayRates`, avec un
  compteur `reads` incrémenté par `declaredRefreshRates()`. La valeur par défaut reprend l'écran
  du Quest 3 à sa résolution native (research.md R1).

### Implémentation

- [X] T004 Créer `…/core/DisplayRates.kt` ([contracts/display-rates.md](contracts/display-rates.md)) :
  - `interface DisplayRates { fun declaredRefreshRates(): Set<Int> }`, avec la KDoc du contrat :
    « Fréquences déclarées par l'écran 0, en Hz entiers. Ensemble vide si la lecture échoue :
    aucune fréquence élevée n'est alors proposée ni permise (FR-001). Lecture seule. » ;
  - `companion object { fun ratesFromModes(rates: Collection<Float>): Set<Int> }` : arrondi à
    l'entier le plus proche (`roundToInt`), en ignorant les valeurs non finies ou ≤ 0.

  T002 passe.
- [X] T005 Créer `…/display/DisplayMonitor.kt(context: Context, scope: CoroutineScope) : DisplayRates`,
  sur le modèle de `…/thermal/ThermalMonitor.kt` :
  - `private val displayManager = context.applicationContext.getSystemService(DisplayManager::class.java)` ;
  - `override fun declaredRefreshRates()` :
    `DisplayRates.ratesFromModes(displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.supportedModes.orEmpty().map { it.refreshRate })`,
    et toute `RuntimeException` donne `emptySet()` (journal `OqtDisplay`, sans rien d'autre) ;
  - KDoc : « Toujours l'écran 0, jamais `context.display` (research.md R1). Aucune écriture de
    mode d'affichage. »

  Le paramètre `scope` servira à US3 (T031).
- [X] T006 Dans `…/AppContainer.kt`, ajouter `val display = DisplayMonitor(appContext, appScope)`,
  déclaré **avant** `tuner`.

**Checkpoint**: `./gradlew test assembleDebug` vert. Le comportement de l'appli est inchangé.

---

## Phase 3: User Story 1 - Choisir une fréquence au-delà de 120 Hz (Priority: P1) 🎯 MVP

**Goal**: 144, 160, 180 et 200 Hz proposés s'ils sont déclarés, marqués « expérimental », avec
avertissement et rappel ; lancement refusé si la fréquence n'est pas déclarée (FR-001 à FR-004,
FR-008 pour la fréquence, FR-009, FR-011).

**Independent Test**: quickstart, scénarios 1.1 à 1.5. Un profil à 144 Hz pour Beat Saber donne
`FPS=x/144` moins de 5 s après « Appliquer et lancer ».

### Tests for User Story 1 (écrits d'abord, ils doivent échouer)

- [X] T007 [P] [US1] Créer `test/…/core/RefreshRatePolicyTest.kt` :
  - `STANDARD_MAX_RATE == 120` ; `HIGH_REFRESH_RATES == listOf(144, 160, 180, 200)` ;
  - `available(QUEST_3, (72..207).toSet()) == listOf(72, 80, 90, 96, 100, 120, 144, 160, 180, 200)` ;
  - `available(QUEST_3, (72..240).toSet())` : même liste, rien au-delà de 200 (FR-001) ;
  - `available(QUEST_3, (72..150).toSet())` : fréquences du modèle, puis 144 seulement ;
  - `available(QUEST_3, emptySet()) == QUEST_3.refreshRates` : lecture impossible, aucune
    fréquence élevée ;
  - `available(QUEST_PRO, (72..207).toSet())` : `[72, 80, 90]`, puis 144 à 200. La règle ne
    dépend que de l'écran ;
  - `available(UNKNOWN, (72..120).toSet()) == UNKNOWN.refreshRates` ;
  - `isHigh(120) == false`, `isHigh(144) == true` ; `isExperimental(r) == isHigh(r)` pour
    toutes les fréquences de `available(QUEST_3, (72..207).toSet())` (FR-003, SC-002).
- [X] T008 [P] [US1] Dans `test/…/core/GameProfileTest.kt`, ajouter :
  - `GameProfile(refreshRate = 144).validateFor(QUEST_3, (72..207).toSet())` est vide ;
  - le même profil avec `validateFor(QUEST_3)` (sans ensemble déclaré) renvoie
    `ProfileViolation(REFRESH_RATE, 144, ViolationReason.RATE_NOT_DECLARED)` ;
  - `refreshRate = 200` et un ensemble déclaré `(72..150).toSet()` : `RATE_NOT_DECLARED` ;
  - `refreshRate = 150` ou `207`, même déclarés : `OUT_OF_RANGE` (pas dans la liste fermée) ;
  - les tests de validation existants restent inchangés et verts ; leurs violations ont la raison
    `OUT_OF_RANGE` (SC-005) ;
  - `warnings` : `HIGH_REFRESH_RATE` pour `refreshRate = 144` ; absent pour 120, pour `null` et
    pour tous les autres profils des tests existants ;
  - `warnings` : `HIGH_RATE_GAME_RESOLUTION` pour 144 Hz avec une texture `null` ; absent pour
    144 Hz avec `forStep(default, 80)`, et pour 120 Hz avec une texture `null` (US2 sc. 5) ;
  - `primary()` sur des violations construites à la main :
    `[OUT_OF_RANGE (CPU_LEVEL), RATE_NOT_DECLARED]` → la `RATE_NOT_DECLARED` ;
    `[ABOVE_RATE_LIMIT, RATE_NOT_DECLARED]` → la `RATE_NOT_DECLARED` ;
    `[OUT_OF_RANGE, ABOVE_RATE_LIMIT]` → la `ABOVE_RATE_LIMIT` ;
    `[OUT_OF_RANGE]` → `null` ; liste vide → `null`.
- [X] T009 [P] [US1] Dans `test/…/core/TunerApplyTest.kt`, `TunerResetTest.kt` et
  `TunerDiagnosticTest.kt`, construire `Tuner(shell, QuestModel.QUEST_3, display)` avec
  `private val display = FakeDisplayRates()`. Ajouter dans `TunerApplyTest.kt` :
  - profil à 160 Hz, écran qui la déclare : `Success`, et la deuxième commande exécutée est
    `setprop debug.oculus.refreshRate 160` ;
  - `display.declared = (72..120).toSet()`, profil à 160 Hz :
    `InvalidProfile([ProfileViolation(REFRESH_RATE, 160, RATE_NOT_DECLARED)])`, et
    `shell.executed` vide ;
  - `display.reads == 1` après un `applyAndLaunch` : l'écran est lu juste avant la validation.

### Implementation for User Story 1

- [X] T010 [US1] Créer `…/core/RefreshRatePolicy.kt`, `object RefreshRatePolicy` (data-model.md,
  « Fréquences proposées ») :
  - `const val STANDARD_MAX_RATE = 120` ; `val HIGH_REFRESH_RATES = listOf(144, 160, 180, 200)`,
    avec la KDoc « liste fermée ; rien au-delà de 200 Hz, même déclaré (FR-001, FR-002) » ;
  - `fun isHigh(rate: Int): Boolean = rate > STANDARD_MAX_RATE` ;
  - `fun isExperimental(rate: Int): Boolean = isHigh(rate)`, avec la KDoc « pour tous les modèles,
    en plus du statut par propriété (FR-003, research.md R7) » ;
  - `fun available(model: QuestModel, declared: Set<Int>): List<Int> = model.refreshRates + HIGH_REFRESH_RATES.filter { it in declared }`.

  T007 passe.
- [X] T011 [US1] Dans `…/core/GameProfile.kt` :
  - `enum class ViolationReason { OUT_OF_RANGE, RATE_NOT_DECLARED, ABOVE_RATE_LIMIT }`, avec une
    KDoc par valeur (data-model.md, « Validation d'un profil ») ;
  - `ProfileViolation(property, value, reason: ViolationReason = ViolationReason.OUT_OF_RANGE)` ;
  - `validateFor(model: QuestModel, declaredRates: Set<Int> = emptySet())`. Pour la fréquence :
    permise si elle est dans `model.refreshRates` ; sinon `RATE_NOT_DECLARED` si elle est dans
    `HIGH_REFRESH_RATES` mais absente de `declaredRates` ; sinon, si elle n'est pas dans
    `HIGH_REFRESH_RATES`, `OUT_OF_RANGE`. KDoc : « `declaredRates` vide par défaut : sans lui,
    toute fréquence élevée est refusée (research.md R5) » ;
  - `ProfileWarning.HIGH_REFRESH_RATE`, ajouté par `warnings(model)` quand
    `refreshRate?.let(RefreshRatePolicy::isHigh) == true` (FR-004) ;
  - `ProfileWarning.HIGH_RATE_GAME_RESOLUTION`, ajouté en plus quand la fréquence est élevée et
    que `eyeTexture == null` (FR-004, US2 sc. 5) ;
  - `fun List<ProfileViolation>.primary(): ProfileViolation?` : la première de raison
    `RATE_NOT_DECLARED`, sinon la première de raison `ABOVE_RATE_LIMIT`, sinon `null` (message du
    MVP). KDoc : « la règle de priorité des messages de refus est ici, pas dans l'interface
    (principe IV, data-model.md) ».

  T008 passe.
- [X] T012 [US1] Dans `…/core/Tuner.kt`, ajouter le paramètre de constructeur
  `private val display: DisplayRates` (troisième). Dans `applyAndLaunch`, remplacer la validation
  par `profile.validateFor(model, display.declaredRefreshRates())`, lu une fois juste avant, avec
  la KDoc « écran lu au moment du lancement : couvre un profil venu d'un autre casque ou une mise
  à jour d'Horizon OS (cas limites) ». Dans `…/AppContainer.kt` : `Tuner(adb, questModel, display)`.
  T009 passe.
- [X] T013 [P] [US1] Dans `…/ui/components/ChoiceRow.kt`, ajouter deux paramètres facultatifs,
  sans changer les appels existants :
  - `experimentalOptions: Set<T> = emptySet()` : la puce affiche son libellé suivi de
    `ExperimentalBadge()`, dans une `Row` centrée verticalement, avec 6 dp d'écart ;
  - `disabledOptions: Set<T> = emptySet()` : la puce a `enabled = enabled && value !in disabledOptions`.
    Une option indisponible mais sélectionnée reste affichée comme sélectionnée.

  Garder `heightIn(min = 48.dp)` sur chaque puce.
- [X] T014 [P] [US1] Ajouter les textes en anglais et en français (`values/` et `values-fr/`) :
  - `warning_high_refresh_rate` : « Au-delà de 120 Hz, le jeu doit produire autant d'images par
    seconde. Sinon, il répète des images et peut sembler moins fluide qu'à 120 Hz : baisse la
    résolution ou monte le niveau GPU. La chauffe augmente et l'autonomie baisse. » (FR-004) ;
  - `warning_high_rate_game_resolution` : « La résolution est laissée au jeu : elle peut être
    trop élevée pour cette fréquence. » (US2 sc. 5) ;
  - `settings_persist_high_rate` : « Tout le casque, menu compris, reste à %1$d Hz jusqu'à
    « Tout réinitialiser » ou un redémarrage, ce qui consomme davantage de batterie. » (FR-009) ;
  - `refresh_rate_unavailable` : « %1$d Hz n'est pas disponible sur ce casque : choisis une
    autre fréquence. » ;
  - `tune_rate_not_declared` : « %1$d Hz n'est pas disponible sur ce casque : modifie le
    profil. » (FR-008).
- [X] T015 [US1] Dans `…/ui/MainViewModel.kt` :
  - `private val _refreshRates = MutableStateFlow(RefreshRatePolicy.available(questModel, container.display.declaredRefreshRates()))`
    et `val refreshRates: StateFlow<List<Int>>` ;
  - le recalculer dans `navigate(screen)` quand `screen is Screen.Profile` (un changement
    d'Horizon OS se voit à l'ouverture suivante) ;
  - dans `onTuneResult`, pour `TuneResult.InvalidProfile` : `when (val primary = result.violations.primary())`.
    Raison `RATE_NOT_DECLARED` : afficher `tune_rate_not_declared` avec `primary.value`. `null` :
    le message actuel. Aucune règle de priorité dans le ViewModel.
- [X] T016 [US1] Dans `…/ui/ProfileScreen.kt` et `…/ui/OqtApp.kt` :
  - `ProfileScreen` reçoit `refreshRates: List<Int>`, transmis par `OqtApp` depuis
    `vm.refreshRates` ;
  - puces de fréquence : `null`, puis `refreshRates`, puis la fréquence du brouillon si elle n'y
    est pas (profil venu d'un autre casque), sur le modèle de la taille personnalisée ;
    `experimentalOptions = refreshRates.filter(RefreshRatePolicy::isExperimental).toSet()` ;
    `disabledOptions` = la fréquence du brouillon si elle n'est pas dans `refreshRates` ;
    `helpText = stringResource(R.string.refresh_rate_unavailable, rate)` dans ce cas ;
  - `Warnings` : `ProfileWarning.HIGH_REFRESH_RATE -> R.string.warning_high_refresh_rate` et
    `ProfileWarning.HIGH_RATE_GAME_RESOLUTION -> R.string.warning_high_rate_game_resolution` ;
  - `InfoCard` : `settings_persist_info`, suivi de `settings_persist_high_rate` (avec la
    fréquence) quand `draft.refreshRate` est élevée, séparés par un saut de ligne.

**Checkpoint**: `./gradlew test assembleDebug` vert. Sur casque : quickstart 1.1 à 1.5.

---

## Phase 4: User Story 2 - Combinaisons fréquence et résolution limitées (Priority: P2)

**Goal**: résolution maximale par fréquence élevée, paliers au-dessus désactivés, abaissement
signalé, lancement refusé hors limites (FR-005 à FR-008, SC-003, SC-004).

**Independent Test**: quickstart, scénarios 2.1 à 2.6.

### Tests for User Story 2 (écrits d'abord, ils doivent échouer)

- [X] T017 [P] [US2] Dans `test/…/core/RefreshRatePolicyTest.kt`, ajouter (défaut du Quest 3 :
  `EyeTexture(1680, 1760)`) :
  - `maxResolutionStep` : 144 → 100, 160 → 100, 180 → 90, 200 → 80 ; 120, 72 et `null` →
    `null` ;
  - `allowed(rate, texture, default)` : `null` toujours permis ; à 200 Hz, `forStep(default, 80)`
    (1344×1408) permis et `forStep(default, 90)` refusé ; tailles personnalisées : 1344×1400
    permis, 1352×1400 refusé (largeur), 1344×1416 refusé (hauteur) ; à 120 Hz, `forStep(default, 150)`
    permis ;
  - `unavailableSteps(rate, default)` : 180 → `[100, 110, 120, 130, 140, 150]` ; 200 →
    `[90, 100, 110, 120, 130, 140, 150]` ; 144 → `[110, 120, 130, 140, 150]` ; 120 et `null` →
    vide ;
  - `selectRate(profile, rate, QUEST_3)`, selon le tableau de data-model.md :
    - ×1,5 puis 200 → texture `forStep(default, 80)`, `loweredToStep == 80` ;
    - ×0,8 puis 200 → profil avec 200 Hz seulement, `loweredToStep == null` ;
    - texture `null` puis 200 → `loweredToStep == null`, texture toujours `null` ;
    - taille personnalisée 1600×1700 puis 180 → `forStep(default, 90)`, `loweredToStep == 90` ;
    - 200 Hz et ×0,8 puis 120 → texture inchangée (FR-007) ;
    - ×1,5 puis `null` (« Par défaut du jeu ») → texture inchangée ;
    - les autres champs du profil ne changent jamais.
- [X] T018 [P] [US2] Dans `test/…/core/GameProfileTest.kt`, ajouter, avec l'écran déclaré
  `(72..207).toSet()` :
  - 200 Hz et `forStep(default, 150)` :
    `ProfileViolation(TEXTURE_WIDTH, forStep(default, 150).width, ABOVE_RATE_LIMIT)` ;
  - 200 Hz et texture `null`, ou `forStep(default, 80)` : aucune violation ;
  - 120 Hz et `forStep(default, 150)` : aucune violation (SC-005).
- [X] T019 [P] [US2] Dans `test/…/core/TunerApplyTest.kt`, ajouter : 200 Hz et ×1,5 →
  `InvalidProfile` avec la raison `ABOVE_RATE_LIMIT`, et `shell.executed` vide (SC-003).

### Implementation for User Story 2

- [X] T020 [US2] Dans `…/core/RefreshRatePolicy.kt`, ajouter (data-model.md, « Limite de
  résolution » et « Choisir une fréquence ») :
  - `private val RESOLUTION_CAPS = mapOf(144 to 100, 160 to 100, 180 to 90, 200 to 80)`, avec la
    KDoc « table de FR-005 : √(160 ÷ fréquence), arrondi au palier inférieur ; liste fermée, à
    revoir seulement avec un amendement de la spec » ;
  - `fun maxResolutionStep(rate: Int?): Int?` ;
  - `fun allowed(rate: Int?, texture: EyeTexture?, default: EyeTexture): Boolean` : vrai si
    `texture == null`, si `maxResolutionStep(rate) == null`, ou si les deux dimensions sont
    inférieures ou égales à celles de `EyeTexture.forStep(default, max)` ;
  - `fun unavailableSteps(rate: Int?, default: EyeTexture): List<Int>` : les paliers de
    `RESOLUTION_STEPS` dont `forStep` n'est pas permis ;
  - `data class RateSelection(val profile: GameProfile, val loweredToStep: Int?)` et
    `fun selectRate(profile: GameProfile, rate: Int?, model: QuestModel): RateSelection`.

  T017 passe.
- [X] T021 [US2] Dans `…/core/GameProfile.kt`, `validateFor` ajoute
  `ProfileViolation(TEXTURE_WIDTH, texture.width, ABOVE_RATE_LIMIT)` quand la fréquence est
  permise mais que `RefreshRatePolicy.allowed(refreshRate, eyeTexture, model.defaultEyeTexture)`
  est faux. Les bornes absolues de la texture restent vérifiées avant. T018 et T019 passent.
- [X] T022 [P] [US2] Ajouter les textes en anglais et en français :
  - `resolution_limited_for_rate` : « À %1$d Hz, la résolution est limitée à ×%2$.1f. » (FR-005) ;
  - `resolution_lowered` : « Résolution abaissée à ×%1$.1f pour %2$d Hz. » (FR-006) ;
  - `tune_resolution_above_limit` : « Résolution trop élevée pour %1$d Hz (×%2$.1f au plus) :
    modifie le profil. » (FR-008).
- [X] T023 [US2] Dans `…/ui/MainViewModel.kt`, `onTuneResult` : ajouter au `when` de T015 la
  raison `ABOVE_RATE_LIMIT` de `primary()`. Afficher `tune_resolution_above_limit` avec la
  fréquence du profil et `RefreshRatePolicy.maxResolutionStep(rate)!! / 100.0`. La priorité
  reste celle de `primary()` (T011).
- [X] T024 [US2] Dans `…/ui/ProfileScreen.kt` :
  - `var loweredToStep by remember(game.packageName, saved) { mutableStateOf<Int?>(null) }` ;
  - puce de fréquence : `val selection = RefreshRatePolicy.selectRate(draft, it, model)`, puis
    `onChange(selection.profile)` et `loweredToStep = selection.loweredToStep` ; tout autre
    changement du brouillon remet `loweredToStep` à `null` ;
  - résolution : `disabledOptions` = les textures de `unavailableSteps(draft.refreshRate, default)`,
    plus la taille personnalisée si `allowed` est faux ; `helpText` = l'aide actuelle, suivie de
    `resolution_limited_for_rate` quand `maxResolutionStep(draft.refreshRate)` n'est pas nul ;
  - sous la résolution, tant que `loweredToStep` n'est pas nul : `resolution_lowered`, en
    `bodyMedium`, couleur `tertiary`, dans la page (pas de snackbar, research.md R6).

**Checkpoint**: `./gradlew test assembleDebug` vert. Sur casque : quickstart 2.1 à 2.6.

---

## Phase 5: User Story 3 - Voir la fréquence réelle de l'écran (Priority: P3)

**Goal**: « Fréquence de l'écran : N Hz » dans le diagnostic, sans connexion, à jour en moins de
5 s (FR-010, SC-006).

**Independent Test**: quickstart, scénarios 3.1 à 3.3.

### Implementation for User Story 3

La logique est dans la couche Android (lecture d'une valeur) ; son arrondi est déjà testé par
`DisplayRatesTest` (T002).

- [X] T025 [US3] Dans `…/display/DisplayMonitor.kt`, ajouter
  `val currentRefreshRate: StateFlow<Int?>` ([contracts/display-rates.md](contracts/display-rates.md)) :
  - `callbackFlow` : envoie la lecture courante, enregistre un `DisplayManager.DisplayListener`
    dont `onDisplayChanged(displayId)` relit si `displayId == Display.DEFAULT_DISPLAY`, et
    relance une lecture toutes les 2 s (`while (isActive) { delay(2_000); trySend(read()) }`
    dans un `launch`) ; `awaitClose` désenregistre l'écouteur ;
  - `read()` : `displayManager?.getDisplay(DEFAULT_DISPLAY)?.refreshRate`, puis
    `DisplayRates.ratesFromModes(listOf(it)).singleOrNull()` ; toute `RuntimeException` donne
    `null` ;
  - `.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(5_000), read())`,
    comme `ThermalMonitor`.
- [X] T026 [P] [US3] Ajouter les textes en anglais et en français :
  - `display_refresh_rate` : « Fréquence de l'écran : %1$d Hz » ;
  - `display_refresh_rate_unknown` : « Fréquence de l'écran : inconnue ».
- [X] T027 [US3] Dans `…/ui/MainViewModel.kt`, `…/ui/ConnectionScreen.kt` et `…/ui/OqtApp.kt` :
  - ViewModel : `val displayRefreshRate: StateFlow<Int?> = container.display.currentRefreshRate` ;
  - `DiagnosticCard` reçoit `displayRefreshRate: Int?` et l'affiche juste sous l'état thermique,
    en `bodyLarge`, qu'il y ait une connexion ou non. La valeur demandée
    (`debug.oculus.refreshRate`) reste dans la liste des réglages actifs, quand l'appli est
    connectée (US3 sc. 1) ;
  - `OqtApp` collecte le flux et le transmet à `ConnectionScreen`.

**Checkpoint**: `./gradlew test assembleDebug` vert. Sur casque : quickstart 3.1 à 3.3. Toutes
les user stories fonctionnent.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T028 [P] `README.md` :
  - section « Features », puce des profils : fréquences jusqu'à 200 Hz sur les casques qui les
    déclarent, marquées *Experimental*, avec une résolution maximale par fréquence ;
  - section « Diagnostic » : fréquence réelle de l'écran ;
  - section « Documentation » : lien vers `specs/003-high-refresh-rates/`.
- [X] T029 [P] Dans `specs/001-game-profiles-mvp/data-model.md`, ligne `refreshRate` du profil :
  compléter la contrainte `∈ model.refreshRates` par « ou fréquence élevée déclarée par l'écran
  (voir [spec 003](../003-high-refresh-rates/data-model.md)) ».
- [X] T030 Lancer `./gradlew test assembleDebug lintDebug`. Corriger tout échec ou tout nouvel
  avertissement de lint dans les fichiers touchés.
- [ ] T031 Valider sur le Quest 3 en suivant [quickstart.md](quickstart.md), section 2. Consigner
  une ligne par essai dans `docs/compatibility.md` (section 4 du quickstart) :
  - modes lus par l'appli comparés à `dumpsys display` ;
  - temps d'application de chaque fréquence au lancement ;
  - fréquence réelle du diagnostic, et réaction à un changement (`onDisplayChanged` ou relecture) ;
  - tenue du jeu aux limites de résolution (`FPS=x/y`, images périmées).

  Si les essais invitent à changer la table de FR-005, ne pas modifier le code : proposer un
  amendement de la spec.

  *Avancement au 2026-09-24* : 1.1 et 2.2 validés (docs/compatibility.md). Restent 1.2 à 1.5
  (dont les temps d'application, SC-001), 2.1, 2.3 à 2.6 et 3.1 à 3.3.
- [X] T032 Mettre à jour la ligne `debug.oculus.refreshRate` du tableau « Statut par propriété »
  de `docs/compatibility.md` : noter, en source de la plage, « 144–200 Hz : spec 003, marquées
  expérimentales (FR-003) ».

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)** : aucune dépendance.
- **Foundational (T002 à T006)** : après le Setup. Bloque toutes les user stories.
- **US1 (T007 à T016)** : après la phase 2. C'est le MVP.
- **US2 (T017 à T024)** : après US1, car elle étend `RefreshRatePolicy`, la validation et l'écran
  profil d'US1.
- **US3 (T025 à T027)** : après la phase 2 seulement ; indépendante d'US1 et d'US2, sauf sur les
  fichiers d'interface partagés (voir plus bas).
- **Polish (T028 à T032)** : après les user stories voulues. T031 exige le casque.

### Fichiers partagés (donc séquentiels, jamais [P] entre eux)

- `…/core/RefreshRatePolicy.kt` : T010 → T020.
- `…/core/GameProfile.kt` : T011 → T021.
- `test/…/core/RefreshRatePolicyTest.kt` : T007 → T017.
- `test/…/core/GameProfileTest.kt` : T008 → T018.
- `test/…/core/TunerApplyTest.kt` : T009 → T019.
- `…/display/DisplayMonitor.kt` : T005 → T025.
- `…/AppContainer.kt` : T006 → T012.
- `…/ui/MainViewModel.kt` : T015 → T023 → T027.
- `…/ui/ProfileScreen.kt` : T016 → T024. `…/ui/OqtApp.kt` : T016 → T027.
- `strings.xml` (les deux langues) : T014 → T022 → T026. Elles sont marquées [P] par rapport au
  code, pas entre elles.

### Within Each User Story

- Les tests d'abord : ils doivent échouer. Puis le cœur, la couche Android, et l'interface en
  dernier.
- Chaque checkpoint exige `./gradlew test` vert.

### Parallel Opportunities

- Phase 2 : T002 et T003 en parallèle.
- US1 : T007, T008 et T009 en parallèle ; T013 (`ChoiceRow`) et T014 (textes) en parallèle du
  cœur.
- US2 : T017, T018 et T019 en parallèle ; T022 (textes) en parallèle du cœur.
- US3 peut avancer en parallèle d'US2 sur `DisplayMonitor.kt` (T025) et les textes (T026), après
  T014 pour les textes.

---

## Parallel Example: User Story 1

```bash
# Tests du cœur, en parallèle (fichiers distincts) :
Task: "T007 RefreshRatePolicyTest.kt (fréquences disponibles, expérimental)"
Task: "T008 GameProfileTest.kt (RATE_NOT_DECLARED, HIGH_REFRESH_RATE)"
Task: "T009 Tuner*Test.kt (FakeDisplayRates, refus avant toute commande)"

# Pendant ce temps, l'interface générique et les textes :
Task: "T013 ChoiceRow.kt (options expérimentales et indisponibles)"
Task: "T014 strings.xml EN/FR de l'avertissement, du rappel et des refus"
```

---

## Implementation Strategy

### MVP first (User Story 1)

1. Phases 1 et 2.
2. US1 : T007 à T016.
3. **STOP** : quickstart 1.1 à 1.5 sur le Quest 3. C'est la réponse à l'issue « 144 Hz ».

### Livraison incrémentale

1. US1 : fréquences élevées, expérimentales, avec avertissement.
2. US2 : limites de résolution et abaissement signalé.
3. US3 : fréquence réelle dans le diagnostic.
4. Polish : README, validation sur casque, tableau de compatibilité.

---

## Notes

- 32 tâches.
- [P] = fichiers distincts, sans dépendance sur une tâche inachevée.
- Principe I : aucune nouvelle commande shell ; les fréquences élevées passent par C1, après
  `validateFor` avec l'écran déclaré.
- Principe IV : toutes les règles (fréquences, limites, abaissement, validation) sont dans
  `core/` et testées. La couche Android ne fait que lire l'écran 0.
