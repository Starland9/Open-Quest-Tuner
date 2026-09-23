# Implementation Plan: Profils par jeu (MVP d'OpenQuestTuner)

**Branch**: `001-game-profiles-mvp` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-game-profiles-mvp/spec.md`

## Summary

Première version d'OpenQuestTuner. C'est une appli Android 2D (panneau Horizon OS) qui se connecte
à l'adbd du casque lui-même, de deux façons :
- par appairage du débogage sans fil (TLS), sans PC ;
- par le port TCP 5555 ouvert une fois depuis un PC.

Une fois connectée, l'appli applique par le shell les propriétés `debug.oculus.*` d'un profil
propre à chaque jeu, puis lance ce jeu.

L'approche technique est détaillée dans [research.md](research.md). Elle repose sur trois couches :
- un cœur en Kotlin pur, testé en JVM (principe IV) : modèle de profil, catalogue des propriétés
  et des modèles de casque, génération de commandes shell validées et échappées, parsing des
  sorties shell ;
- une couche Android mince : client ADB libadb-android derrière l'interface `ShellBackend`,
  PackageManager pour lister les jeux, fichier JSON pour les profils ;
- une interface Compose avec un seul ViewModel et une navigation par état.

La recherche (phase 0) a apporté quatre ajustements au design initial :
- les valeurs par casque proviennent des docs Meta de 2025–2026 (R2) ;
- `setprop <clé> ''` est confirmé comme méthode officielle de réinitialisation (R7) ;
- une vérification `probe` suivie de nouvelles tentatives contourne un bug ouvert de
  libadb-android (R3) ;
- un bouton « Ouvrir les options développeur » facilite l'appairage sans PC (R4).

## Technical Context

**Language/Version**: Kotlin 2.2.20 (bytecode JVM 17, compilé avec le JDK 21)

**Primary Dependencies**:
- Jetpack Compose (compose-bom 2024.12.01, Material 3) ;
- AndroidX : core-ktx 1.13.1, activity-compose 1.9.3, lifecycle-runtime-ktx et
  lifecycle-viewmodel-compose 2.8.7 ;
- kotlinx-coroutines 1.9.0 et kotlinx-serialization-json 1.9.0 ;
- libadb-android 3.1.1 (JitPack), conscrypt-android 2.5.3 et bcpkix-jdk15to18 1.81.

- Tests : JUnit 4.13.2 et kotlinx-coroutines-test 1.9.0, pour les fonctions `suspend` du cœur.

  Build : AGP 8.13.2, Gradle 9.4.1 (wrapper déjà présent).

**Storage**: fichier JSON `profiles.json` dans `filesDir`, en écriture atomique. SharedPreferences
pour la dernière méthode de connexion. Clé RSA et certificat ADB dans `filesDir/adb/`.

**Testing**: JUnit 4 (tests unitaires JVM, `./gradlew test`) sur le cœur pur. Validation
manuelle sur un Quest 3 réel en suivant [quickstart.md](quickstart.md).

**Target Platform**: Meta Quest 3 (prioritaire), 3S, 2 et Pro, sous Horizon OS (base Android,
voir research.md). minSdk 29, compileSdk 35, targetSdk 34. ABI : arm64-v8a, plus x86_64 pour l'émulateur.

**Project Type**: application mobile Android, un seul module Gradle `app`.

**Performance Goals**:
- lancement du jeu moins de 5 s après « Appliquer et lancer » (SC-003) ;
- reconnexion automatique en moins de 5 s (SC-007) ;
- liste de jeux fluide jusqu'à environ 300 jeux.

**Constraints**:
- entièrement hors ligne : seule communication réseau, localhost et le mDNS local (FR-028) ;
- aucune commande shell construite à partir d'une saisie libre (FR-025) ;
- aucune écriture de `persist.*` (FR-026) ;
- interface en panneau VR, avec des cibles d'au moins 48 dp (FR-030).

**Scale/Scope**:
- 3 écrans : Jeux, Profil, et Connexion et outils (qui contient aussi le diagnostic) ;
- 7 propriétés gérées, 4 modèles de casque ;
- au plus quelques centaines de profils.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Exigence | Comment le plan la respecte | Statut |
|---|---|---|---|
| I. Sécurité (NON NÉGOCIABLE) | Commandes construites uniquement à partir de valeurs validées | `ShellCommands` (cœur pur) est le seul endroit qui produit des commandes. Il n'accepte que des `QuestProperty` d'une enum fermée, des entiers bornés par `QuestModel`, et des noms de paquets et d'activités validés par regex puis échappés entre quotes simples. Tests unitaires dédiés, y compris des entrées malveillantes. | ✅ |
| I | Pas de console shell libre | Aucun écran n'accepte de commande. Le diagnostic est un `getprop` fixe. | ✅ |
| I | Clé ADB privée | Clé dans `filesDir/adb/` (stockage privé), jamais journalisée ni exportée. `android:allowBackup="false"` pour qu'elle ne parte pas dans les sauvegardes. | ✅ |
| I | Réversible, pas de `persist.*` | Seules les 7 clés `debug.oculus.*` de la liste blanche sont écrites. « Tout réinitialiser » les remet à la chaîne vide. Un test vérifie qu'aucune commande générée ne contient `persist.`. | ✅ |
| II. Libre, clean-room, vie privée | GPL-3.0, dépendances compatibles | libadb-android (GPL-3.0+ OU Apache-2.0), spake2-android (LGPL-3.0), conscrypt (Apache-2.0), BouncyCastle (type MIT), AndroidX et kotlinx (Apache-2.0) : tout est compatible. Aucun élément de QGO n'est utilisé. | ✅ |
| II | Hors ligne, sans télémétrie | Permission INTERNET requise pour le socket localhost uniquement. Aucune bibliothèque d'analytics ou de crash reporting. | ✅ |
| II | Liste des applis installées | `QUERY_ALL_PACKAGES` sert uniquement à repérer localement les jeux VR, y compris les anciens titres (research.md R5). La liste ne quitte jamais le casque. | ✅ |
| III. Vérifié sur casque réel | Tableau de compatibilité, marquage « expérimental » | `docs/compatibility.md` est créé avec chaque propriété à l'état « non vérifié ». Le catalogue `QuestModel` porte un statut vérifié ou expérimental par propriété, et l'interface affiche un badge « expérimental ». Tout commence expérimental jusqu'aux tests sur le Quest 3 (quickstart). | ✅ |
| IV. Cœur testable sans casque | Cœur Kotlin pur, `ShellBackend`, tests JVM | Le package `core/` n'importe rien d'`android.*`. `ShellBackend` est une interface, avec `AdbShellBackend` pour le casque et un faux backend dans les tests. La politique de connexion (`ConnectionPolicy`) est aussi dans le cœur, ce qui garde la couche ADB mince. JUnit 4 couvre chaque règle. | ✅ |
| V. Simplicité et UX VR | Un module, dépendances justifiées, 48 dp, pas de PC après la configuration | Un seul module `app`, pas de Room, Hilt ni navigation-compose. Chaque dépendance est justifiée dans research.md. Chips Material 3 d'au moins 48 dp. L'appairage sans PC est la méthode par défaut. | ✅ |
| Workflow | `test` et `assembleDebug` verts avant intégration | Critère de fin de chaque tâche dans tasks.md. | ✅ |

**Résultat** : aucune violation. La section Complexity Tracking reste vide.

**Re-check après la phase 1** : le design (data-model, contrats) n'ajoute ni module, ni
dépendance, ni surface shell. Le contrat [shell-commands.md](contracts/shell-commands.md) fixe la
liste exhaustive des commandes autorisées, ce qui renforce le principe I. ✅

## Project Structure

### Documentation (this feature)

```text
specs/001-game-profiles-mvp/
├── plan.md              # Ce fichier
├── research.md          # Phase 0 : décisions techniques et faits vérifiés/à vérifier
├── data-model.md        # Phase 1 : entités, validation, transitions d'état
├── quickstart.md        # Phase 1 : guide de validation sur casque réel
├── contracts/
│   ├── shell-commands.md    # Liste fermée des commandes shell que l'appli peut émettre
│   ├── shell-backend.md     # Interface ShellBackend et états de connexion
│   └── profiles-json.md     # Format du fichier de profils (versionné)
├── checklists/
│   └── requirements.md  # Checklist qualité de la spec
└── tasks.md             # Phase 2 (/speckit-tasks, pas créé ici)
```

### Source Code (repository root)

```text
settings.gradle.kts            # Dépôts google/mavenCentral + jitpack filtré sur com.github.MuntashirAkon
build.gradle.kts               # Plugins AGP, Kotlin, Compose, serialization (apply false)
gradle.properties
gradle/wrapper/                # Déjà présent (Gradle 9.4.1)
docs/
└── compatibility.md           # Tableau de compatibilité (constitution III)
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/io/github/openquesttuner/
    │   │   ├── OqtApplication.kt        # Crée l'AppContainer, lance la reconnexion
    │   │   ├── AppContainer.kt          # DI manuelle
    │   │   ├── MainActivity.kt
    │   │   ├── core/                    # Kotlin pur, aucun import android.* (principe IV)
    │   │   │   ├── QuestProperty.kt     # Liste blanche des propriétés + encodage des valeurs
    │   │   │   ├── QuestModel.kt        # Modèles de casque, plages, résolution par défaut, statut vérifié
    │   │   │   ├── EyeTexture.kt        # Taille par œil + paliers de résolution
    │   │   │   ├── GameProfile.kt       # Profil, validation, avertissements
    │   │   │   ├── ShellCommands.kt     # Seule fabrique de commandes (validation + échappement)
    │   │   │   ├── ShellOutput.kt       # Marqueur de code de sortie, parsing getprop
    │   │   │   ├── ShellBackend.kt      # Interface + ConnectionState + ShellResult
    │   │   │   ├── Tuner.kt             # Orchestration : appliquer et lancer, tout réinitialiser, diagnostic
    │   │   │   ├── ConnectionInput.kt   # Validation du code d'appairage et des ports
    │   │   │   ├── ConnectionPolicy.kt  # Classification des échecs, probe et nouvelles tentatives, ordre de reconnexion
    │   │   │   ├── SearchKey.kt         # Normalisation pour le tri et la recherche
    │   │   │   └── ProfileStore.kt      # Sérialisation JSON + écriture atomique (java.io uniquement)
    │   │   ├── adb/                     # Couche Android/ADB
    │   │   │   ├── AdbIdentityStore.kt  # Génération et chargement de la clé RSA + certificat
    │   │   │   ├── OqtAdbConnectionManager.kt
    │   │   │   ├── AdbShellBackend.kt   # Implémente ShellBackend : pair/connect/exec
    │   │   │   └── ConnectionPrefs.kt   # Dernière méthode réussie
    │   │   ├── games/
    │   │   │   └── GameRepository.kt    # PackageManager : jeux VR installés
    │   │   └── ui/
    │   │       ├── MainViewModel.kt
    │   │       ├── OqtApp.kt            # Scaffold + navigation par état
    │   │       ├── GamesScreen.kt
    │   │       ├── ProfileScreen.kt
    │   │       ├── ConnectionScreen.kt  # Connexion, outils (Tout réinitialiser, Diagnostic)
    │   │       ├── components/          # ConnectionBadge, ChoiceRow, ExperimentalBadge, GameIcon
    │   │       └── theme/Theme.kt
    │   └── res/
    │       ├── values/strings.xml       # Anglais (défaut)
    │       ├── values-fr/strings.xml    # Français
    │       └── mipmap-anydpi-v26/, drawable/  # Icône adaptative
    └── test/java/io/github/openquesttuner/core/
        ├── ShellCommandsTest.kt
        ├── ShellOutputTest.kt
        ├── GameProfileTest.kt
        ├── QuestModelTest.kt
        ├── ProfileStoreTest.kt
        └── TunerTest.kt                 # Avec un faux ShellBackend
```

**Structure Decision**: un seul module Gradle `app` (principe V). La séparation cœur/Android
passe par le package `core/`, qui n'importe rien d'Android, ce qui le rend testable en JVM pur
(principe IV). Un module Gradle séparé serait prématuré. Si la frontière est un jour franchie par
erreur, un test d'architecture simple (grep des imports) pourra être ajouté.

## Complexity Tracking

Aucune violation de la constitution : section vide.
