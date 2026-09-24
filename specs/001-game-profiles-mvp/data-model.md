# Data Model : Profils par jeu (MVP)

Toutes les entités ci-dessous, sauf `InstalledGame` et la mise en œuvre de la connexion, vivent
dans le cœur Kotlin pur (`core/`) et sont testées en JVM (constitution, principe IV). Les valeurs
chiffrées propres à chaque casque se trouvent dans [research.md](research.md#r2--valeurs-par-modèle-de-casque),
qui fait seul référence.

## QuestProperty : liste blanche des propriétés gérées

Enum fermée. C'est la seule source des clés passées à `setprop` (FR-025).

| Constante | Clé système | Type de valeur | Encodage |
|---|---|---|---|
| `REFRESH_RATE` | `debug.oculus.refreshRate` | Hz | entier décimal |
| `TEXTURE_WIDTH` | `debug.oculus.textureWidth` | pixels (par œil) | entier décimal |
| `TEXTURE_HEIGHT` | `debug.oculus.textureHeight` | pixels (par œil) | entier décimal |
| `CPU_LEVEL` | `debug.oculus.cpuLevel` | niveau | entier décimal |
| `GPU_LEVEL` | `debug.oculus.gpuLevel` | niveau | entier décimal |
| `FOVEATION_LEVEL` | `debug.oculus.foveation.level` | `FoveationLevel` | `OFF`=0, `LOW`=1, `MEDIUM`=2, `HIGH`=3, `HIGH_TOP`=4 |
| `FOVEATION_DYNAMIC` | `debug.oculus.foveation.dynamic` | booléen | `1` / `0` |

L'ordre de l'enum est l'ordre d'application dans « Appliquer et lancer ».

Chaque constante porte aussi une `absoluteRange` : une borne de sécurité, indépendante du casque,
que `ShellCommands.setProperty` vérifie en dernier rempart. La plage propre au modèle est vérifiée
avant, par `GameProfile.validateFor`.

| Constante | `absoluteRange` |
|---|---|
| `REFRESH_RATE` | 60..240 |
| `TEXTURE_WIDTH`, `TEXTURE_HEIGHT` | 512..3072 |
| `CPU_LEVEL`, `GPU_LEVEL` | 0..7 |
| `FOVEATION_LEVEL` | 0..4 |
| `FOVEATION_DYNAMIC` | 0..1 |

## QuestModel : modèle de casque

| Champ | Type | Description |
|---|---|---|
| `displayName` | String | « Quest 3 », « Quest 3S », « Quest 2 », « Quest Pro », « Casque non reconnu » |
| `deviceCodenames` | Set<String> | Valeurs de `Build.DEVICE` reconnues |
| `modelNames` | Set<String> | Valeurs de `Build.MODEL` reconnues (repli) |
| `defaultEyeTexture` | EyeTexture | Résolution de rendu par défaut par œil, base des paliers |
| `refreshRates` | List<Int> | Fréquences proposées, en Hz |
| `cpuLevels` | IntRange | Niveaux CPU proposés |
| `gpuLevels` | IntRange | Niveaux GPU proposés |
| `alwaysAvailableCpuMax` / `alwaysAvailableGpuMax` | Int | Au-delà, le niveau dépend de conditions (passthrough, suivi…) : texte d'aide dans l'interface |
| `verified` | Set<QuestProperty> | Propriétés vérifiées sur ce modèle. Toutes les autres sont « expérimental » (FR-017). **Vide au départ** pour tous les modèles. |

- `UNKNOWN` reprend les valeurs de `QUEST_3` avec `verified = ∅` (FR-016).
- Détection : `fromBuild(device, model)` compare d'abord `Build.DEVICE` (insensible à la casse),
  puis `Build.MODEL`, sinon renvoie `UNKNOWN`.

## EyeTexture et paliers de résolution

`EyeTexture(width: Int, height: Int)` avec `width, height ∈ [MIN_DIM, MAX_DIM] = [512, 3072]`
(borne haute justifiée dans [research.md](research.md#r2--valeurs-par-modèle-de-casque)).

Les paliers sont exprimés en **pourcentage entier**, pour éviter les erreurs d'arrondi des
flottants : `RESOLUTION_STEPS = 70, 80, …, 150` (spec FR-011).

- `EyeTexture.forStep(default, percent)` : chaque dimension vaut `default × percent / 100`,
  **arrondie au multiple de 8 le plus proche**.
- Invariant testé : `forStep(default, 100) == default` pour tous les modèles.
- Pour l'interface, `stepOf(default, texture)` renvoie le palier correspondant, ou `null` si la
  valeur est « personnalisée » (par exemple un profil venu d'un autre casque).

## GameProfile : profil d'un jeu

| Champ | Type | `null` signifie | Validation à l'application (`validateFor(model)`) |
|---|---|---|---|
| `refreshRate` | Int? | Par défaut du jeu | ∈ `model.refreshRates`, ou fréquence élevée déclarée par l'écran (voir [spec 003](../003-high-refresh-rates/data-model.md)) |
| `eyeTexture` | EyeTexture? | Par défaut du jeu | dimensions dans [512, 3072] |
| `cpuLevel` | Int? | Par défaut du jeu | ∈ `model.cpuLevels` |
| `gpuLevel` | Int? | Par défaut du jeu | ∈ `model.gpuLevels` |
| `foveationLevel` | FoveationLevel? | Par défaut du jeu | enum (toujours valide) |
| `dynamicFoveation` | Boolean? | Par défaut du jeu | toujours valide |

- `isEmpty` est vrai quand tous les champs sont `null`. Un profil vide est traité comme une
  absence de profil (FR-015).
- `toPropertyValues(): Map<QuestProperty, Int?>` renvoie les 7 entrées. `null` déclenche une
  réinitialisation (C2) ; sinon la valeur encodée en entier (C1). Pour `FoveationLevel`, c'est le
  code 0–4 ; pour un booléen, 1 ou 0. `eyeTexture` alimente à la fois
  `TEXTURE_WIDTH` et `TEXTURE_HEIGHT`.
- `validateFor(model): List<ProfileViolation>`. Si la liste n'est pas vide, aucune commande n'est
  envoyée.
- `warnings(model): Set<ProfileWarning>` (FR-018). Le rapport `r = eyeTexture.width × 100 /
  model.defaultEyeTexture.width` fonctionne aussi pour les valeurs personnalisées.
  - `HEAT` si `cpuLevel ≥ cpuLevels.last − 1`, ou si `gpuLevel ≥ gpuLevels.last − 1`, ou si
    `r > 100` ;
  - `SMOOTHNESS` si `r > 130`.
- Sérialisation : voir [contracts/profiles-json.md](contracts/profiles-json.md).

Relation : `ProfileStore` associe chaque identifiant de paquet à un `GameProfile` (0..1 profil par
jeu). Le profil n'a pas de lien fort avec `InstalledGame` : il survit à la désinstallation du jeu.

## InstalledGame : jeu installé (couche Android)

| Champ | Type | Source |
|---|---|---|
| `packageName` | String | `ActivityInfo.packageName`, validé par `PACKAGE_REGEX` |
| `label` | String | `loadLabel(pm)` |
| `launchActivity` | String | `ActivityInfo.name` (nom qualifié complet), validé par `CLASS_REGEX` |

- L'icône n'est pas stockée : elle est chargée à la demande et mise en cache en mémoire par le
  composant d'interface.
- Sources, fusionnées et dédoublonnées par paquet (voir [research.md](research.md#r5--lister-les-jeux-vr-installés)) :
  1. activités `ACTION_MAIN` de catégorie `com.oculus.intent.category.VR` ;
  2. en repli, les applis dont les meta-data contiennent `com.samsung.android.vr.application.mode`
     ou `com.oculus.ossplash`, avec l'activité `LAUNCHER` (sinon `INFO`) comme point d'entrée.
- Exclusions : `FLAG_SYSTEM`, applis désactivées, applis panneau (service
  `com.oculus.vrshell.SHELL_MAIN`), et l'appli elle-même.
- Tri : par `label`, en ignorant la casse et les accents. La même normalisation, accents retirés
  avec `java.text.Normalizer` NFD, sert à la recherche (FR-009).

## ConnectionInput : saisies de connexion (cœur)

| Champ | Règle |
|---|---|
| Code d'appairage | exactement 6 chiffres `^[0-9]{6}$` |
| Port (appairage ou connexion manuelle) | entier dans 1–65535 |

Tant qu'une saisie est invalide, le bouton correspondant reste désactivé (cas limite « saisie
invalide »). Ces valeurs ne vont jamais au shell : elles servent uniquement à ouvrir un socket.

## Connexion

`ConnectionMethod`, `ConnectionState` et `FailureReason` sont définis dans
[contracts/shell-backend.md](contracts/shell-backend.md).

Transitions :

```text
                 pair(port, code)
Disconnected ───────────────────────► Pairing ──échec──► Failed(WIRELESS, PAIRING_CODE_REJECTED | UNKNOWN)
     │  ▲                                │ succès
     │  │                                ▼
     │  │  connectWireless()/connectPc() Connecting(m) ──échec──► Failed(m, PORT_CLOSED | NOT_AUTHORIZED
     │  └───────────────── disconnect() ─┤                            | PAIRING_REQUIRED | SERVICE_NOT_FOUND | UNKNOWN)
     └───────────────────────────────────┘ succès
                                          ▼
                                     Connected(m) ──exec() échoue (connexion perdue)──► Disconnected

reconnectLast() : pour chaque tentative, Disconnected → Connecting(m) → Connected(m) ; si toutes échouent, Disconnected (jamais Failed, FR-005)
Failed(...) → toute nouvelle tentative repasse par Pairing ou Connecting.
```

`ConnectionPrefs` (SharedPreferences) enregistre `lastMethod` et, s'il a été saisi, le
`lastWirelessPort` manuel. Ils sont mis à jour à chaque passage à `Connected`. Le port TLS change
à chaque activation du débogage sans fil : le port enregistré ne sert qu'en repli, après la
découverte mDNS.

### ConnectionPolicy (cœur)

Ces règles sont pures et testées en JVM (principe IV).

| Fonction | Règle |
|---|---|
| `classify(error, phase)` | Voir le tableau ci-dessous |
| `connectWithProbe(connect, probe, reset)` | Au plus `1 + MAX_PROBE_RETRIES` (= 3) tentatives. Si `connect` renvoie faux : `NotAuthorized`. Un probe en échec déclenche `reset` puis une nouvelle tentative. Si tous les probes échouent : `ProbeFailed`. |
| `awaitWirelessEnabled(read, timeoutMs = 60 000, pollMs = 1 000)` | Appelle `read` jusqu'à obtenir `true` ou jusqu'à la fin du délai, avec `delay(pollMs)` entre deux lectures. Une lecture en exception compte comme `false`. |
| `reconnectAttempts(lastMethod, lastWirelessPort)` | Liste de `ReconnectAttempt(method, target)`. `WIRELESS` → sans-fil `[Discover, Port(p)?]` puis PC `[Port(5555)]` ; `PC` → PC puis sans-fil ; `null` → `[]` |

Classification des exceptions par `classify(error, phase)`, où `phase` ∈ `PAIRING`, `DISCOVERY`,
`CONNECT` :

| Exception | Phase | Résultat |
|---|---|---|
| `ConnectException` | toutes | `PORT_CLOSED` |
| classe nommée `AdbPairingRequiredException` | toutes | `PAIRING_REQUIRED` |
| `InterruptedException`, ou `IOException` « Could not find any valid host address or port » | `DISCOVERY` | `SERVICE_NOT_FOUND` |
| toute autre exception | `PAIRING` | `PAIRING_CODE_REJECTED` |
| toute autre exception | `DISCOVERY`, `CONNECT` | `UNKNOWN` |

## Résultats d'opérations (cœur)

```kotlin
enum class WirelessSwitchResult { SWITCHED, NOT_ACCEPTED, WIRELESS_FAILED, NOT_CONNECTED }

sealed interface TuneResult {
    data object Success : TuneResult
    data object NotConnected : TuneResult
    data class InvalidProfile(val violations: List<ProfileViolation>) : TuneResult
    data class StepFailed(val step: TuneStep, val output: String) : TuneResult
}

sealed interface TuneStep {
    data object ForceStop : TuneStep
    data class SetProperty(val property: QuestProperty) : TuneStep
    data object Launch : TuneStep
}

data class ResetResult(val failed: List<QuestProperty>)      // vide = succès complet
data class Diagnostic(val active: Map<String, String>)       // clés debug.oculus.* non vides, triées
```

## ThermalLevel : état thermique (cœur, amendement de l'US5)

```kotlin
enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN }
```

| Règle | Détail |
|---|---|
| Conversion | `ThermalLevel.fromAndroidStatus(code)` : 0 à 6 dans l'ordre de l'enum ; toute autre valeur donne `UNKNOWN` |
| Avertissement | `warning` est vrai de `MODERATE` à `SHUTDOWN`, et faux pour `NONE`, `LIGHT` et `UNKNOWN` (FR-032) |
| Source | `PowerManager` côté Android (research.md R10). Sans connexion ADB. |

## Profil actif sur le casque (cœur, amendement de l'US5)

`GameProfile.isActiveOn(active: Map<String, String>): Boolean`, où `active` est la map de
`Diagnostic` :
- vrai si, pour chacune des 7 propriétés de `toPropertyValues()`, la valeur attendue
  (`value?.toString()`) est égale à `active[property.key]` ;
- un réglage `null` (« Par défaut du jeu ») exige donc une propriété absente, puisque le
  diagnostic ne garde que les valeurs non vides ;
- les autres clés `debug.oculus.*` du système sont ignorées (par exemple
  `extraKickoffHeadroom`).

L'écran profil compare le brouillon affiché, et non le profil enregistré, pour que l'indicateur
décrive exactement ce que l'utilisateur voit (FR-033).
