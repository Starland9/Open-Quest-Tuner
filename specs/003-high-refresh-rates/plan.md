# Implementation Plan: Fréquences d'affichage au-delà de 120 Hz

**Branch**: `003-high-refresh-rates` (aucune branche git créée : travail sur `main`) | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-high-refresh-rates/spec.md`

## Summary

Une issue demande si l'on peut passer à 144 Hz. Les essais du 2026-09-24 montrent que l'écran du
Quest 3 suit jusqu'à 207 Hz avec la propriété que l'appli gère déjà,
`debug.oculus.refreshRate`. La fonctionnalité ajoute 144, 160, 180 et 200 Hz au choix de la
fréquence, tous marqués « expérimental ». Pour chacune, elle fixe une résolution maximale, et
elle affiche la fréquence réelle de l'écran dans le diagnostic.

L'approche technique est détaillée dans [research.md](research.md) :
- **aucune nouvelle commande shell** : les fréquences élevées passent par C1, déjà bornée de 60 à
  240 Hz (R3) ;
- **les fréquences déclarées et la fréquence réelle sont lues par l'API Android** (`DisplayManager`,
  écran 0), sans shell ni connexion. Un essai sur le casque a confirmé que l'écran logique vu par
  les applis suit la propriété en ~1 s (R1, R2) ;
- **toutes les règles sont dans le cœur pur**, par `RefreshRatePolicy` : fréquences proposées,
  statut expérimental, limites de résolution, abaissement, validation au lancement. Elles sont
  testées en JVM derrière une petite interface `DisplayRates` (R4, R5) ;
- l'interface étend `ChoiceRow` (options expérimentales et indisponibles), et ajoute un
  avertissement, un rappel et une ligne au diagnostic (R6).

## Technical Context

**Language/Version**: Kotlin 2.2.20 (bytecode JVM 17, compilé avec le JDK 21), inchangé.

**Primary Dependencies**: aucune nouvelle dépendance. APIs Android du SDK : `DisplayManager`,
`Display.getSupportedModes`, `Display.getRefreshRate`, `DisplayManager.DisplayListener`.

**Storage**: rien de nouveau. Le format des profils (`profiles.json`) ne change pas : une
fréquence élevée est un entier comme les autres.

**Testing**: JUnit 4 et kotlinx-coroutines-test sur le cœur :
- `RefreshRatePolicy` : fréquences disponibles, limites, abaissement ;
- `GameProfile` : validation avec les deux nouvelles raisons, avertissement ;
- `Tuner` : refus avant toute commande, avec un faux `DisplayRates`.

Validation manuelle sur Quest 3 en suivant [quickstart.md](quickstart.md).

**Target Platform**: Meta Quest 3 (prioritaire), 3S, 2 et Pro, Horizon OS (Android 14, API 34).
minSdk 29, compileSdk 35, targetSdk 34, inchangés.

**Project Type**: application Android, un seul module Gradle `app`.

**Performance Goals**:
- l'écran tourne à la fréquence du profil moins de 5 s après « Appliquer et lancer » (SC-001) ;
- la fréquence réelle affichée se met à jour moins de 5 s après un changement (SC-006).

**Constraints**:
- liste fermée du shell inchangée ; fréquences proposées en liste fermée, choisies dans un menu
  (FR-002) ;
- aucune fréquence au-delà de 200 Hz, même déclarée par l'écran (FR-001) ;
- aucune nouvelle permission, aucune communication réseau ;
- cibles d'interaction d'au moins 48 dp, textes en français et en anglais (FR-011).

**Scale/Scope**:
- 4 fréquences élevées, 4 limites de résolution, 3 raisons de refus (dont celle du MVP) ;
- 1 interface du cœur (`DisplayRates`) et 1 moniteur Android (`DisplayMonitor`) ;
- 2 écrans modifiés (profil, Connexion et outils) ;
- une douzaine de textes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Exigence | Comment le plan la respecte | Statut |
|---|---|---|---|
| I. Sécurité (NON NÉGOCIABLE) | Commandes construites à partir de valeurs validées ; entiers bornés | Aucune commande nouvelle. Une fréquence élevée vient d'une liste fermée du cœur, doit être déclarée par l'écran, puis passe par `validateFor` avant C1, qui garde sa borne absolue de 60 à 240 Hz. Un profil modifié hors de l'appli est refusé avant la première commande (FR-008, SC-003). | ✅ |
| I | Pas de console shell libre | Aucune saisie ajoutée : les fréquences et les résolutions se choisissent dans des puces. | ✅ |
| I | Réversible, rien qui survive au redémarrage | Seule `debug.oculus.refreshRate` est écrite, comme aujourd'hui : « Tout réinitialiser » et le redémarrage l'effacent. Aucun changement durable, donc la règle des changements durables ne s'applique pas. | ✅ |
| II. Libre, clean-room, vie privée | Hors ligne, sans télémétrie, licences | Fréquences et limites issues de nos propres mesures (docs/compatibility.md). Aucune dépendance ni communication réseau. | ✅ |
| III. Vérifié sur casque réel | « Expérimental » tant que non vérifié ; tableau de compatibilité | Toute fréquence au-delà de 120 Hz est marquée « expérimental », sur tous les modèles (FR-003, R7). Les mesures de départ sont consignées. Le quickstart ajoute les essais au lancement et ceux du diagnostic. | ✅ |
| IV. Cœur testable sans casque | Cœur pur, `ShellBackend`, tests JVM | Règles dans `core/RefreshRatePolicy.kt` et `GameProfile`, sans import Android (vérifié par `CoreArchitectureTest`). Les fréquences déclarées arrivent par l'interface `DisplayRates`, simulée dans les tests. Aucun accès shell nouveau. | ✅ |
| V. Simplicité et UX VR | Un module, pas d'abstraction spéculative, 48 dp | Aucun module ni dépendance. `DisplayRates` n'est pas spéculative : sans elle, `Tuner` ne serait pas testable en JVM avec des fréquences déclarées (principe IV). Les nouvelles puces gardent 48 dp, et les messages restent affichés dans la page plutôt qu'en snackbar. | ✅ |
| Workflow | `test` et `assembleDebug` verts ; contrat des commandes | Contrat des commandes inchangé ; [contracts/display-rates.md](contracts/display-rates.md) décrit la seule nouvelle interface. | ✅ |

**Résultat** : aucune violation.

**Re-check après la phase 1** : le design ne fait que détailler ces choix.
- [data-model.md](data-model.md) ajoute des constantes, une règle de permission et deux raisons
  de refus. Rien n'est persisté.
- [contracts/display-rates.md](contracts/display-rates.md) limite l'interface à une lecture.

Aucune surface shell nouvelle, aucune dépendance. ✅

## Project Structure

### Documentation (this feature)

```text
specs/003-high-refresh-rates/
├── plan.md              # Ce fichier
├── research.md          # Phase 0 : lecture des modes, fréquence réelle, limites, validation, interface
├── data-model.md        # Phase 1 : fréquences, limites, choix d'une fréquence, validation
├── quickstart.md        # Phase 1 : validation sur Quest 3
├── contracts/
│   └── display-rates.md # Interface DisplayRates et moniteur de l'écran
├── checklists/
│   └── requirements.md  # Checklist qualité de la spec
└── tasks.md             # Phase 2 (/speckit-tasks, pas créé ici)
```

### Source Code (repository root)

Fichiers ajoutés (+) ou modifiés (~) :

```text
README.md                                  # ~ Features : fréquences jusqu'à 200 Hz, expérimentales
docs/compatibility.md                      # ~ Essais du quickstart (application au lancement, diagnostic)
app/src/main/
├── java/io/github/openquesttuner/
│   ├── AppContainer.kt                    # ~ Crée DisplayMonitor et le passe au Tuner
│   ├── core/
│   │   ├── RefreshRatePolicy.kt           # + Fréquences élevées, statut expérimental, limites de résolution, selectRate (pur)
│   │   ├── DisplayRates.kt                # + Interface : fréquences déclarées par l'écran
│   │   ├── GameProfile.kt                 # ~ validateFor(model, declaredRates), raisons de refus, avertissement HIGH_REFRESH_RATE
│   │   └── Tuner.kt                       # ~ Lit DisplayRates juste avant de valider
│   ├── display/
│   │   └── DisplayMonitor.kt              # + DisplayManager (écran 0) : fréquences déclarées, fréquence réelle en direct
│   └── ui/
│       ├── MainViewModel.kt               # ~ Fréquences disponibles, fréquence réelle, messages de refus
│       ├── ProfileScreen.kt               # ~ Puces expérimentales et indisponibles, abaissement, avertissement, rappel
│       ├── ConnectionScreen.kt            # ~ Diagnostic : « Fréquence de l'écran »
│       ├── OqtApp.kt                      # ~ Transmet les nouveaux états
│       └── components/ChoiceRow.kt        # ~ Options expérimentales et options indisponibles
└── res/values/strings.xml, values-fr/strings.xml   # ~ Nouveaux textes FR et EN
app/src/test/java/io/github/openquesttuner/core/
├── RefreshRatePolicyTest.kt               # + Fréquences disponibles, limites, permission, abaissement
├── FakeDisplayRates.kt                    # + Faux écran
├── GameProfileTest.kt                     # ~ Raisons RATE_NOT_DECLARED et ABOVE_RATE_LIMIT, avertissement, SC-005
└── TunerApplyTest.kt                      # ~ Refus avant toute commande ; fréquence élevée déclarée acceptée
```

**Structure Decision**: même structure que le MVP et que la spec 002 : un module `app`, cœur pur
dans `core/`, couche Android mince. Le nouveau paquet `display/` suit le modèle de `thermal/`
(`ThermalMonitor`) : un moniteur d'état du casque lisible sans connexion. `DisplayMonitor`
implémente l'interface du cœur `DisplayRates` pour la validation, et expose en plus la fréquence
réelle à l'interface.

## Complexity Tracking

Aucune violation de la constitution v1.1.0 : section vide.
