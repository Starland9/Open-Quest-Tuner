# Implementation Plan: Reconnexion autonome après un redémarrage (sans PC)

**Branch**: `002-standalone-reconnect` (aucune branche git créée : travail sur `main`) | **Date**: 2026-09-24 (amendé le même jour : User Story 4) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-standalone-reconnect/spec.md`

## Summary

Après un redémarrage, Horizon OS coupe le débogage sans fil. Si le port 5555 s'est refermé lui
aussi, l'appli ne peut plus se reconnecter sans PC. La fonctionnalité ajoute l'option
« Reconnexion autonome ».

Pendant qu'elle est connectée, et après confirmation de l'utilisateur, l'appli s'accorde une fois
la permission système `WRITE_SECURE_SETTINGS`, par son propre shell ADB (`pm grant`). Cette
permission survit aux redémarrages. Ensuite, à chaque démarrage de l'appli :
1. l'appli écrit elle-même `adb_wifi_enabled=1`, par l'API Android, sans shell ;
2. elle attend l'éventuelle fenêtre réseau d'Horizon OS ;
3. elle se connecte en TLS avec sa clé déjà autorisée, comme le fait déjà « Passer en sans fil ».

C'est la méthode de Shizuku et de shizuku4quest, préparée dans la recherche du MVP
([R9](../001-game-profiles-mvp/research.md)).

L'approche technique est détaillée dans [research.md](research.md) :
- la décision (faut-il réactiver ? quelle cause afficher ?) est prise dans le cœur pur, par
  `AutoReconnectPolicy`, testée en JVM derrière une petite interface `WirelessDebuggingSwitch` ;
- la couche Android se limite à trois lectures (permission, Wi-Fi, réglages de débogage) et à
  une seule écriture de réglage système ;
- deux commandes s'ajoutent à la liste fermée du shell : C9 accorde la permission à l'appli
  elle-même, C10 la retire.

La recherche confirme le mécanisme dans les sources d'Android 14, et apporte une limite. Une
connexion sans fil ne remet pas à zéro le délai d'expiration des autorisations de débogage
(7 jours par défaut dans Android). Sur un casque réglé ainsi, il faudrait donc refaire l'étape PC
au plus tard une semaine après la dernière connexion via PC. Le Quest 3 de test n'est pas
concerné : ses autorisations n'expirent pas.

L'utilisateur a donc décidé de proposer d'empêcher cette expiration (spec amendée : User Story 4,
FR-021 à FR-025). C'est un choix explicite, non coché d'office, et réversible. Trois commandes
shell de plus, exécutées pendant une connexion, s'en chargent :
- C11 fixe le délai à « jamais » ;
- C12 rétablit la valeur d'origine ;
- C13 revient au délai par défaut.

L'appli retient la valeur d'origine avant d'écrire, et ne rétablit que ce qu'elle a elle-même
changé (research.md R6).

## Technical Context

**Language/Version**: Kotlin 2.2.20 (bytecode JVM 17, compilé avec le JDK 21), inchangé.

**Primary Dependencies**: aucune nouvelle dépendance. Celles du MVP : Jetpack Compose (BOM
2024.12.01, Material 3), AndroidX, kotlinx-coroutines 1.9.0, kotlinx-serialization-json 1.9.0,
libadb-android 3.1.1, conscrypt-android 2.5.3, bcpkix-jdk15to18 1.81. APIs Android du SDK :
`Settings.Global`, `ConnectivityManager`, `Context.checkSelfPermission`.

**Storage**: quatre clés de plus dans les SharedPreferences `connection` (`ConnectionPrefs`) :
- `auto_reconnect` et `revoke_pending` (booléens) ;
- `expiry_original` (chaîne ou absente) et `expiry_restore_pending` (booléen).

Rien d'autre n'est persisté.

**Testing**: JUnit 4 et kotlinx-coroutines-test (temps virtuel pour les attentes de 60 s) sur le
cœur. Deux tests de garde en JVM : une seule écriture de réglage système dans tout le code, et le
paquet visé par C9 et C10 égal à l'`applicationId`. Validation manuelle sur Quest 3 en suivant
[quickstart.md](quickstart.md).

**Target Platform**: Meta Quest 3 (prioritaire), 3S, 2 et Pro, Horizon OS (Android 14, API 34).
minSdk 29, compileSdk 35, targetSdk 34, inchangés.

**Project Type**: application Android, un seul module Gradle `app`.

**Performance Goals**:
- « Connecté (sans fil) » moins de 15 s après l'ouverture de l'appli, sur un Wi-Fi déjà autorisé
  (SC-001) ;
- nouvelle tentative moins de 15 s après l'arrivée du Wi-Fi (SC-005) ;
- attente de la fenêtre réseau d'Horizon OS plafonnée à 60 s (FR-007).

**Constraints**:
- une seule écriture de réglage système par l'API Android dans tout le code :
  `adb_wifi_enabled = 1`. Le délai d'expiration ne change que par le shell (C11 à C13), sur choix
  explicite (FR-015, FR-021) ;
- permission accordée seulement à l'appli elle-même, et seulement après confirmation (FR-016) ;
- aucune nouvelle communication réseau : localhost et mDNS local, comme aujourd'hui (FR-017) ;
- aucune réactivation hors du démarrage de l'appli, d'une demande de l'utilisateur ou du retour du
  Wi-Fi (FR-008) : pas de récepteur `BOOT_COMPLETED`, pas de service ;
- cibles d'interaction d'au moins 48 dp, textes en français et en anglais (FR-020).

**Scale/Scope**:
- 1 option, 3 états affichés, 6 causes d'échec ;
- 5 commandes shell (C9 à C13) et 1 écriture de réglage par l'API ;
- 1 carte ajoutée à l'écran Connexion et outils, et 1 fenêtre de confirmation ;
- une vingtaine de textes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Exigence | Comment le plan la respecte | Statut |
|---|---|---|---|
| I. Sécurité (NON NÉGOCIABLE) | Commandes construites uniquement à partir de valeurs validées | C9 et C10 sont deux fabriques sans paramètre de `ShellCommand`. Le paquet visé est une constante du cœur (`io.github.openquesttuner`), et un test la compare à l'`applicationId`. L'appli ne peut donc accorder ni retirer une permission qu'à elle-même, et seulement `WRITE_SECURE_SETTINGS`. | ✅ |
| I | Pas de console shell libre | Rien de nouveau : aucune saisie n'est ajoutée. | ✅ |
| I | Clé ADB privée | Inchangé : la reconnexion réutilise la clé existante, qui ne quitte jamais `filesDir/adb/`. | ✅ |
| I | Réversible, rien qui survive au redémarrage | `adb_wifi_enabled` revient à 0 à chaque redémarrage (constaté). Deux changements survivent **par construction** : la permission, et, sur choix explicite, le délai d'expiration à « jamais ». Chacun se défait en un geste (FR-013 avec C10 ; FR-023 avec C12 ou C13, qui rétablissent la valeur d'origine retenue). Seule limite : le délai reste à « jamais » si l'appli est désinstallée sans avoir désactivé le choix, ce que l'explication dit (FR-022). Voir Complexity Tracking. | ✅ justifié |
| I | Pas d'accès détourné au système | La permission autoriserait l'écriture de n'importe quel réglage sécurisé. Le code n'en écrit qu'un par l'API, dans une seule classe, et un test JVM échoue si une autre écriture de `Settings` apparaît dans les sources (FR-015). C11 à C13 ne visent qu'une clé, écrite en constante, avec pour seul paramètre un entier positif (C12). | ✅ |
| II. Libre, clean-room, vie privée | Hors ligne, sans télémétrie, licences | Aucune dépendance ni communication réseau nouvelle. La méthode vient de projets open source (Shizuku, shizuku4quest) et de la doc AOSP, sans rien de QGO. | ✅ |
| III. Vérifié sur casque réel | Tableau de compatibilité, « expérimental » | L'option est marquée « expérimental » tant qu'elle n'est pas vérifiée pour le modèle détecté (FR-018). Le quickstart ajoute au tableau trois essais : octroi de la permission, persistance après redémarrage, reconnexion. | ✅ |
| IV. Cœur testable sans casque | Cœur pur, `ShellBackend`, tests JVM | Décisions dans `core/AutoReconnectPolicy.kt`, sans import Android (déjà vérifié par `CoreArchitectureTest`). Les lectures et l'écriture Android passent par l'interface `WirelessDebuggingSwitch`, simulée dans les tests. C9 et C10 passent par `ShellBackend`. | ✅ |
| V. Simplicité et UX VR | Un module, pas d'abstraction spéculative, 48 dp, pas de PC après la configuration | Aucun module ni dépendance ajouté. `WirelessDebuggingSwitch` n'est pas spéculative : sans elle, la logique de reconnexion ne serait pas testable en JVM (principe IV). Cette fonctionnalité réalise la dernière exigence du principe V : plus de PC après la première configuration. | ✅ |
| Workflow | `test` et `assembleDebug` verts ; nouvelles commandes au contrat | C9 à C13 sont ajoutées au contrat des commandes ([contracts/shell-commands.md](contracts/shell-commands.md), repris dans celui du MVP). FR-026 du MVP porte une note d'exception qui renvoie à FR-021 à FR-023. | ✅ |

**Résultat** : aucune violation bloquante. Un écart assumé est consigné dans Complexity Tracking.

**Re-check après la phase 1** : le design ne fait que détailler ces choix.
- [data-model.md](data-model.md) ajoute deux booléens persistés et deux énumérations.
- [contracts/wireless-switch.md](contracts/wireless-switch.md) limite l'interface Android à six
  opérations, dont une seule écriture.
- [contracts/shell-commands.md](contracts/shell-commands.md) ajoute deux commandes sans
  paramètre.

Aucune nouvelle surface shell libre, aucune dépendance. ✅

## Project Structure

### Documentation (this feature)

```text
specs/002-standalone-reconnect/
├── plan.md              # Ce fichier
├── research.md          # Phase 0 : mécanisme, lectures, causes, octroi et retrait
├── data-model.md        # Phase 1 : option, statut, causes, séquence de reconnexion
├── quickstart.md        # Phase 1 : validation sur Quest 3
├── contracts/
│   ├── shell-commands.md    # Amendement du contrat du MVP : C9, C10 et deux séquences
│   └── wireless-switch.md   # Interface WirelessDebuggingSwitch et règle d'écriture unique
├── checklists/
│   └── requirements.md  # Checklist qualité de la spec
└── tasks.md             # Phase 2 (/speckit-tasks, pas créé ici)
```

### Source Code (repository root)

Fichiers ajoutés (+) ou modifiés (~) :

```text
README.md                                  # ~ Section « Connect the app » : option de reconnexion autonome
docs/compatibility.md                      # ~ Essais : octroi, persistance, reconnexion (quickstart)
specs/001-game-profiles-mvp/contracts/shell-commands.md   # ~ Renvoi vers C9 et C10
app/src/main/
├── AndroidManifest.xml                    # ~ uses-permission WRITE_SECURE_SETTINGS (tools:ignore)
├── java/io/github/openquesttuner/
│   ├── OqtApplication.kt                  # ~ Reconnexion au démarrage via le contrôleur
│   ├── AppContainer.kt                    # ~ Crée le switch et le contrôleur, surveille le Wi-Fi
│   ├── core/
│   │   ├── AutoReconnectPolicy.kt         # + Statut, causes, préparation du sans-fil (pur)
│   │   ├── WirelessDebuggingSwitch.kt     # + Interface vers les lectures et l'écriture Android
│   │   ├── ShellCommands.kt               # ~ C9/C10 permission, C11/C12/C13 délai d'expiration
│   │   ├── ConnectionPolicy.kt            # ~ Tentatives marquées « préparer le sans-fil d'abord »
│   │   └── QuestModel.kt                  # ~ autoReconnectVerified par modèle
│   ├── adb/
│   │   ├── AndroidWirelessSwitch.kt       # + Implémentation : Settings.Global, ConnectivityManager
│   │   ├── AutoReconnectController.kt     # + Activer, désactiver, choix d'expiration, opérations en attente, Wi-Fi, statut
│   │   ├── AdbShellBackend.kt             # ~ reconnectLast() prépare le sans-fil et rend la cause
│   │   └── ConnectionPrefs.kt             # ~ auto_reconnect, revoke_pending, expiry_original, expiry_restore_pending
│   └── ui/
│       ├── MainViewModel.kt               # ~ Statut, cause, activer, désactiver, proposition (FR-003)
│       ├── ConnectionScreen.kt            # ~ Carte « Reconnexion autonome », cause sous « Déconnecté »
│       └── components/ConnectionBadge.kt  # ~ Textes des causes (ReconnectIssue)
└── res/values/strings.xml, values-fr/strings.xml   # ~ Nouveaux textes FR et EN, étapes PC révisées
app/src/test/java/io/github/openquesttuner/
├── core/AutoReconnectPolicyTest.kt        # + Statut, chaque cause, attente de 60 s en temps virtuel
├── core/FakeWirelessDebuggingSwitch.kt    # + Faux switch
├── core/ShellCommandsTest.kt              # ~ Texte exact de C9 à C13, C12 refuse ms ≤ 0, pas de persist.
├── core/ExpiryChoiceTest.kt               # + Statut du choix, « ne rétablir que si 0 », valeur "default"
├── core/ConnectionPolicyTest.kt           # ~ Ordre des tentatives avec l'option active
├── AppPackageTest.kt                      # + Constante du cœur = applicationId du build
└── SystemSettingsWriteTest.kt             # + Une seule écriture de Settings dans les sources
```

**Structure Decision**: même structure que le MVP : un module `app`, cœur pur dans `core/`,
couche Android mince dans `adb/`. La logique d'activation et de désactivation est dans un nouveau
`AutoReconnectController`, pour ne pas alourdir `AdbShellBackend` (258 lignes). Celui-ci ne gagne
qu'une étape dans `reconnectLast()`.

## Complexity Tracking

Aucune violation de la constitution. Un écart est consigné par transparence :

| Écart | Pourquoi il est nécessaire | Alternative plus simple écartée, et pourquoi |
|---|---|---|
| Une permission système (`WRITE_SECURE_SETTINGS`) reste accordée à l'appli après un redémarrage. Le principe I veut que « un redémarrage du casque efface tout ». | C'est le seul moyen, sans PC, de rallumer le débogage sans fil après un redémarrage. La phrase du principe I vise les réglages de performance (`debug.oculus.*`), qui restent effacés au redémarrage. | Écrire `persist.adb.tcp.port` pour garder le port 5555 ouvert : interdit par le principe I (`persist.*`), et le port 5555 n'a aucun chiffrement. Réactiver le sans-fil depuis le shell : impossible, puisque c'est justement la connexion qui manque après le redémarrage. |
| Sur choix explicite, le délai d'expiration des autorisations de débogage (`adb_allowed_connection_time`) passe à « jamais » et le reste après un redémarrage. Il touche toutes les clés du casque, celle du PC comprise. | Une connexion sans fil ne prolonge pas une autorisation (research.md R6). Sans ce choix, un casque à 7 jours oblige à repasser par le PC chaque semaine. Décision de l'utilisateur, le 2026-09-24. | Ne rien faire et prévenir : c'était le plan initial, mais l'utilisateur l'a jugé insuffisant. Se reconnecter régulièrement par le port 5555 pour prolonger la clé : il faudrait rouvrir ce port non chiffré. |

Proposition : un amendement MINOR du principe I pourrait autoriser explicitement ces changements
durables de l'accès au débogage, à trois conditions : un choix explicite, un retour en arrière en
un geste, et une valeur d'origine retenue. Il est à lancer par l'utilisateur
(`/speckit-constitution`), hors de ce plan.
