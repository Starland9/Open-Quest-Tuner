# Quickstart : valider les fréquences au-delà de 120 Hz sur Quest 3

Ce guide prouve de bout en bout que la fonctionnalité répond à la [spec](spec.md). Il fournit
aussi les essais à consigner dans `docs/compatibility.md` (constitution, principe III).

## Prérequis

- Le Quest 3 de test, en mode développeur, avec l'appli connectée (sans fil ou via PC).
- Beat Saber installé : c'est le jeu de référence des mesures du 2026-09-24.
- Le PC avec `adb`, pour **observer** seulement : logcat `VrApi` et `dumpsys display`.
- **L'APK debug** pour le scénario 2.6 : il modifie le fichier des profils avec `run-as`, que
  la release refuse (« package not debuggable », constaté le 2026-09-24). Réinstaller la release
  ensuite si besoin : même clé de signature, les données sont gardées. Si
  l'annonce mDNS n'atteint pas le PC, voir le
  [quickstart de la spec 002](../002-standalone-reconnect/quickstart.md) pour trouver le port.

Mesures utiles :

```bash
adb logcat -s VrApi | grep -o 'FPS=[0-9]*/[0-9]*'          # images du jeu / fréquence de l'écran
adb shell dumpsys display | grep -oE '"Écran intégré": [^,]*, [0-9]+ x [0-9]+, modeId [0-9]+, renderFrameRate [0-9.]+'
```

## 1. Build et tests (sans casque)

```bash
./gradlew test            # RefreshRatePolicy, validation, avertissements, Tuner avec FakeDisplayRates
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Attendu : les deux commandes réussissent.

## 2. Scénarios

Pour chaque scénario, noter ✅ ou ❌, avec la version d'Horizon OS (`getprop ro.vros.build.version`).

### US1 : choisir une fréquence au-delà de 120 Hz

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 1.1 | Ouvrir le profil de Beat Saber. | Fréquences : « Par défaut du jeu », 72 à 120 Hz, puis 144, 160, 180 et 200 Hz. Chacune des quatre dernières porte la pastille « Expérimental ». Rien au-delà de 200. | FR-001, FR-003, SC-002 |
| 1.2 | Choisir 144 Hz. | L'avertissement de cadence, de chauffe et d'autonomie apparaît. Le rappel « réglages actifs » précise que tout le casque reste à 144 Hz. | FR-004, FR-009 |
| 1.3 | « Appliquer et lancer ». Chronométrer. | `FPS=x/144` dans `VrApi` moins de 5 s après le lancement. | SC-001 |
| 1.4 | Refaire 1.3 à 160, 180 et 200 Hz. | `FPS=x/160`, `/180`, `/200`, chaque fois en moins de 5 s. | SC-001 |
| 1.5 | Profil à 90 Hz enregistré avant la mise à jour : l'ouvrir, puis « Appliquer et lancer ». | Rien ne change par rapport au MVP : mêmes options, pas d'avertissement nouveau, `FPS=x/90`. | SC-005 |

### US2 : combinaisons fréquence et résolution

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 2.1 | Profil à 180 Hz. Regarder la résolution. | Paliers ×1,0 à ×1,5 désactivés ; aide « À 180 Hz, la résolution est limitée à ×0,9 ». | FR-005, US2 sc. 1 |
| 2.2 | Profil à ×1,5 (fréquence 120 Hz). Choisir 200 Hz. | Résolution passée à ×0,8, avec le message « Résolution abaissée à ×0,8 pour 200 Hz ». | FR-006, SC-004 |
| 2.3 | Profil à ×0,8. Choisir 200 Hz. | Résolution inchangée, pas de message. | US2 sc. 3 |
| 2.4 | Après 2.2, repasser à 120 Hz. | Tous les paliers redeviennent disponibles ; la résolution reste à ×0,8. | FR-007 |
| 2.5 | 200 Hz avec résolution « Par défaut du jeu ». | Permis ; l'avertissement rappelle que la résolution du jeu peut être trop élevée. | US2 sc. 5 |
| 2.6 | *(APK debug.)* Enregistrer un profil à 200 Hz et ×0,8. Via le PC, modifier `files/profiles.json` de l'appli (`run-as io.github.openquesttuner`) pour mettre la texture à ×1,5. Relancer l'appli, puis « Lancer » depuis la liste des jeux. | Le jeu n'est pas lancé ; message « Résolution trop élevée pour 200 Hz (×0,8 au plus) ». Rien n'est écrit : `adb shell getprop debug.oculus.textureWidth` garde sa valeur d'avant. | FR-008, SC-003 |

### US3 : fréquence réelle de l'écran

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 3.1 | Profil à 180 Hz appliqué. Ouvrir Connexion et outils → Diagnostic. | « Fréquence de l'écran : 180 Hz », et `debug.oculus.refreshRate = 180` dans les propriétés actives. Même valeur que `dumpsys display`. | FR-010, SC-006 |
| 3.2 | « Se déconnecter », diagnostic ouvert. | La fréquence de l'écran reste affichée. | US3 sc. 2 |
| 3.3 | Diagnostic ouvert : « Tout réinitialiser ». | La fréquence affichée revient à celle du casque, en même temps que `dumpsys display` (le retour prend ~12 s dans Home, research.md R2), sans action. | US3 sc. 3, SC-006 |

## 3. Aucune commande nouvelle (principe I)

`ShellCommandsTest` n'a aucune fabrique nouvelle à couvrir : la liste fermée du MVP est
inchangée, et les fréquences élevées passent par C1, dans sa borne absolue de 60 à 240 Hz. La
lecture des modes d'affichage se fait par l'API Android, sans shell : les essais 1.1 et 3.2
fonctionnent aussi appli déconnectée.

## 4. À consigner dans `docs/compatibility.md`

- Modes lus par l'appli (1.1) : identiques à `dumpsys display` (research.md R1).
- Temps d'application de chaque fréquence au lancement (1.3, 1.4).
- Fréquence réelle du diagnostic comparée à `dumpsys display`, et réaction à un changement (3.1,
  3.3 ; research.md R2, `onDisplayChanged`).
- Toute observation sur la tenue du jeu aux limites de résolution (images périmées, `FPS=x/y`),
  pour revoir la table de FR-005.
