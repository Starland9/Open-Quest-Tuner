# Research : Profils par jeu (MVP)

Phase 0 du plan. Chaque décision suit le format Décision / Justification / Alternatives.

Niveaux de confiance :
- **[C]** : confirmé par au moins deux sources ou par une doc officielle ;
- **[S]** : une seule source ;
- **[I]** : déduit.

Tout ce qui est [S] ou [I] et touche au casque doit être vérifié avec le [quickstart](quickstart.md)
avant de passer « vérifié » (constitution, principe III). Les sources sont listées en fin de
document.

## R1 : Propriétés `debug.oculus.*` gérées

**Décision** : le MVP gère 7 propriétés, listées dans [data-model.md](data-model.md#questproperty--liste-blanche-des-propriétés-gérées).
Toutes démarrent au statut « expérimental » jusqu'à leur vérification sur le Quest 3.

| Propriété | Effet | Prise en compte | Confiance |
|---|---|---|---|
| `refreshRate` | Fréquence d'affichage visée, en Hz (entier). Meta l'utilise encore dans sa doc de sept. 2026 (Horizon OS 2.7). | Pas établi : le jeu peut-il imposer sa propre fréquence ? | [C] S21, S22 |
| `textureWidth` / `textureHeight` | Taille du buffer par œil, en pixels. Exposée par l'outil officiel MQDH (runtime OpenXR actuel). | Probablement lue seulement à la création des buffers, d'où le relancement du jeu. | [C] effet ; [I] moment de prise en compte |
| `cpuLevel` / `gpuLevel` | Remplacent le niveau demandé par l'appli (« Set by app »). | Pas établi en cours d'exécution ; relancer le jeu règle la question. | [C] S23 |
| `foveation.level` | 0 = désactivé, 1 = faible, 2 = moyen, 3 = élevé, 4 = très élevé (les applis OpenXR traitent 4 comme 3). Si le mode dynamique est actif, c'est un plafond. | Immédiate, sans redémarrer l'appli (doc Meta). | [C] S24 |
| `foveation.dynamic` | 0 ou 1. Il faut 0 pour qu'un niveau fixe s'applique vraiment. | Immédiate | [C] S24 |

Ces propriétés fonctionnent toujours en 2026, avec les applis OpenXR : les docs Meta 2025–2026 et
Quest Game Tuner 2.2.2 (juillet 2026) en attestent. [C]

**Justification** : ce sont les 7 réglages qu'offrent SideQuest, QLoader, Quest Game Tuner et QGO,
et elles sont documentées par Meta pour ses propres outils. Les autres propriétés restent hors
périmètre : capture, `swapInterval`, `forceSpaceWarp`, `dynResScaler`, `eyeFov*`, `localDimming`,
`forceDisplayScaling`…

**Alternatives** :
- Exposer `forceDisplayScaling` pour aller au-delà de 120 Hz sur Quest 3 (jusqu'à 207 Hz sur OS
  2.7+, et 240 Hz en mode développeur) : reporté. Ce réglage touche l'affichage système, pas
  seulement le jeu, ce qui pose un risque vis-à-vis du principe I.

## R2 : Valeurs par modèle de casque

**Identification** (Build.MANUFACTURER = `Oculus`) [C] :

| Modèle | `Build.DEVICE` | `Build.MODEL` |
|---|---|---|
| Quest 3 | `eureka` | `Quest 3` |
| Quest 3S | `panther` (l'édition Xbox a le produit `xse_panther` ; son DEVICE n'est pas confirmé) | `Quest 3S` |
| Quest 2 | `hollywood` | `Quest 2` |
| Quest Pro | `seacliff` | `Quest Pro` |

**Valeurs retenues pour le catalogue `QuestModel`** :

| Modèle | Résolution de rendu par défaut / œil [C] S25 (doc officielle Meta « Render Scale ») | Fréquences proposées (Hz) [S, doc Meta] | CPU proposés [S, doc Meta] | GPU proposés [S, doc Meta] |
|---|---|---|---|---|
| Quest 3 | 1680×1760 (dalle 2064×2208) | 72, 80, 90, 96, 100, 120 | 0–4 | 0–5 |
| Quest 3S | 1680×1760 (dalle 1832×1920) | 72, 80, 90, 96, 100, 120 | 0–4 | 0–5 |
| Quest 2 | 1440×1584 (dalle 1832×1920) | 72, 80, 90, 96, 100, 120 | 0–4 | 0–4 |
| Quest Pro | 1440×1584 (dalle 1800×1920) | 72, 80, 90 | 0–4 | 0–4 |

Conditions documentées par Meta, affichées dans l'interface en texte d'aide sous les niveaux :
- **Quest 3 / 3S** :
  - CPU 0–3 toujours disponibles, 4 seulement sans passthrough ;
  - GPU 0–2 toujours disponibles, 3–4 sans passthrough, 5 avec l'échange de niveaux CPU/GPU ou
    la résolution dynamique.
- **Quest 2** : CPU 5–6 et GPU 5 existent dans des modes particuliers. Ils ne sont pas proposés.
- **Quest Pro** : les niveaux au-delà de 3 exigent que le passthrough et le suivi des yeux, du
  visage et du corps soient désactivés.
- Quest Game Tuner annonce des niveaux GPU 6–7 sur Quest 3 depuis v71. **Non confirmé**, donc
  non proposé.

**Décision sur les bornes de résolution** : les dimensions acceptées vont de **512 à 3072 px**.
Le palier maximal (×1,5) donne 2520×2640 sur Quest 3, soit sous la borne. La borne de 3072 vient
d'un constat : un utilisateur de QGT a fait planter l'environnement Home à 3070 px. [S] La
validation refuse tout ce qui dépasse, pour protéger le casque (principe I).

**Hors périmètre** : 60 Hz sur Quest 2 (réservé aux applis média), et les fréquences au-delà de
120 Hz (voir R1).

## R3 : Client ADB embarqué

**Décision** : libadb-android 3.1.1 (JitPack), avec conscrypt-android 2.5.3 et
bcpkix-jdk15to18 1.81.

**Justification** :
- C'est la seule bibliothèque Java/Android maintenue qui gère à la fois l'ADB TCP classique,
  l'appairage TLS du débogage sans fil (SPAKE2) et la découverte mDNS. Elle est utilisée en
  production par App Manager. [C]
- Conscrypt est requis pour `exportKeyingMaterial` pendant l'appairage TLS 1.3. Sans lui, la
  bibliothèque passe par l'API cachée `com.android.org.conscrypt`. [C, lecture du code source]
- BouncyCastle sert à générer le certificat X.509 auto-signé. Il est déjà tiré par
  libadb-android via bcprov 1.81. [C]
- Licences, toutes compatibles GPL-3.0 : libadb-android GPL-3.0+ OU Apache-2.0,
  spake2-android LGPL-3.0 (bibliothèques natives, dont arm64-v8a), conscrypt Apache-2.0,
  BouncyCastle de type MIT. [C, POM et dépôts]

**Pièges constatés dans le code source et à gérer** (voir
[contracts/shell-backend.md](contracts/shell-backend.md)) :
- `setApi(Build.VERSION.SDK_INT)` est obligatoire, sinon le TLS n'est jamais négocié.
- Le délai de connexion est infini par défaut.
- `connect()` renvoie `false` si le délai expire.
- `close()` détruit la clé privée : il faut utiliser `disconnect()`.
- La lecture d'un flux peut lever `IOException("Stream closed.")` en fin de flux.

**Risques connus** (tickets ouverts sur libadb-android) :
- **#34** (sept. 2026, v3.1.1) : sur Android 14 en TLS, le premier `openStream()` après
  `connect()` échoue parfois avec « Stream closed » alors que la connexion paraît établie. [S]
  - **Mitigation** : après chaque connexion, exécuter la commande de test C6 (`probe`). Si elle
    échoue, `disconnect()` puis reconnexion, au maximum 2 nouvelles tentatives, avant de
    déclarer l'échec.
  - **Plan B** si le problème persiste sur Quest : une fois connecté en TLS, basculer en ADB TCP
    classique avec le service `tcpip:5555`, puis se connecter à `127.0.0.1:5555`. C'est la
    méthode de TheDroidGeek. Elle ouvre toutefois le port 5555 sur le réseau local ; à ne
    retenir qu'après vérification sur casque.
- **Réveils perdus dans le code de libadb 3.1.1** (constaté sur Quest 3 le 2026-09-23, présent
  aussi sur la branche `master` ; c'est très probablement la cause de #34) :
  - **Ouverture de flux** : `AdbConnection.open()` envoie `OPEN`, *puis* entre dans
    `synchronized (stream) { stream.wait(); }`, sans condition ni délai. Si adbd répond (`OKAY`,
    voire `WRTE` et `CLSE` pour une commande courte) avant que le thread appelant n'atteigne
    `wait()`, la notification est perdue et `open()` bloque indéfiniment. La commande a
    pourtant été exécutée. En TLS sur 127.0.0.1, la réponse arrive assez vite pour que cela se
    produise plusieurs fois par session.
  - **Lecture** : si `CLSE` arrive alors que la dernière donnée n'a pas encore été lue,
    `notifyClose` ne passe que `mPendingClose` à vrai. La boucle d'attente de `AdbStream.read()`
    ne teste que `mIsClosed` : la lecture suivante attend indéfiniment.
  - **Contournement** (contracts/shell-backend.md) :
    - la lecture s'arrête dès la ligne complète du marqueur de fin (`ShellOutput.isComplete`),
      sans attendre `CLSE` ;
    - chaque commande a un délai de 3 s, puis jusqu'à 3 essais sur la même connexion
      (`ConnectionPolicy.withRetries`) avant de déclarer la connexion perdue. C'est sans risque,
      car toutes les commandes de la liste fermée sont idempotentes : `am start` sur un jeu déjà
      lancé le ramène simplement au premier plan.
  - **Correctif de fond** : attendre dans une boucle avec condition (`OKAY` reçu ou flux fermé),
    et tenir compte de `mPendingClose` dans la boucle de lecture. À proposer en amont ; à défaut,
    reprendre la bibliothèque dans le projet avec ce correctif (amendement du plan).
- **#32** (août 2026) : le `SSLContext` est mis en cache statiquement et ignore un changement de
  paire de clés. **Sans impact** : l'appli génère une seule paire de clés et ne la renouvelle
  jamais. Une future fonction « réinitialiser l'identité » devra redémarrer le processus.

**Alternatives** :
- **dadb** (mobile.dev, Maven Central 2.0.0) : pas d'appairage TLS ni de mDNS, donc pas de
  méthode sans PC. Rejetée.
- **Shizuku** : impose à l'utilisateur une appli tierce, et son appairage est cassé sur Quest v83+
  (#2114). Gardé comme `ShellBackend` possible plus tard.
- **Implémentation maison du protocole** : environ 300 lignes pour le TCP, mais SPAKE2 et TLS
  demandent trop de travail et de risques. Rejetée.

## R4 : Connexion sans PC sur Horizon OS

**Constats** :
- L'appairage et la connexion TLS **fonctionnent sur 127.0.0.1**. Shizuku #2114 le rapporte sur
  Quest 3 v83, et shizuku4quest fait de même. [C]
- La découverte mDNS (NsdManager) trouve le port d'appairage et le port de connexion sur Quest. [S]
  L'`autoConnect` de libadb n'accepte que des adresses locales. [S]
- Depuis v83, l'intent qui ouvre l'écran d'appairage est bloqué, et les notifications avec saisie
  (`RemoteInput`) ne fonctionnent pas sur Quest. Il faut donc saisir le code **dans l'appli**,
  avec l'écran d'appairage ouvert à côté. Le Quest garde plusieurs panneaux ouverts, donc le code
  reste valide. [C, Shizuku #2114 et AppManager #1975]
- Le débogage sans fil se trouve dans les options développeur **Android**, cachées, et non
  toujours dans les paramètres Quest. Leur emplacement a varié selon les versions (v81 contre
  v83). [S]
- À la première activation, Android demande « Toujours autoriser sur ce réseau ». Le Wi-Fi est
  obligatoire. [C, AOSP AdbDebuggingManager]
- Le débogage sans fil est très probablement désactivé à chaque redémarrage. [I] La reconnexion
  automatique après un redémarrage est hors périmètre du MVP (voir R9).
- Horizon OS a été renuméroté en 2.x en 2026 (`ro.vros.build.version`). La spec mentionne « v83 »
  comme constat historique ; le code ne doit jamais tester un numéro de version. [C]

**Amendement du 2026-09-23 (essai sur Quest 3, vros 207, voir docs/compatibility.md)** :
- L'intent des options développeur ouvre les **Paramètres Quest**, qui n'ont pas de débogage sans
  fil. L'écran développeur d'Android est désactivé, et le shell ne peut pas le réactiver.
  L'appairage par code est donc inaccessible sur cette version.
- En revanche, une clé autorisée une fois via la méthode PC est acceptée par adbd en **TLS sans
  appairage**. `settings put global adb_wifi_enabled 1`, envoyé par le shell, déclenche la fenêtre
  Horizon OS « autoriser sur ce réseau », puis active le sans-fil.
- **Nouveau parcours retenu** : première autorisation via PC (une fois), puis bouton « Passer en
  sans fil » (commandes C7 et C8). L'appairage par code est gardé en secours.
- Après un redémarrage, le TLS est coupé mais le port 5555 est resté ouvert : la reconnexion
  essaie les deux méthodes (`reconnectAttempts`).

**Décisions initiales** (toujours valables pour l'appairage de secours) :
- L'écran de connexion propose un bouton **« Ouvrir les options développeur »**. Il lance
  `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` avec
  `FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK`, pour ouvrir un panneau séparé à
  côté de l'appli (méthode de shizuku4quest). [S] Si l'intent échoue, l'appli affiche des
  instructions textuelles. Ce bouton est à vérifier sur casque.
- Le port d'appairage est saisi par l'utilisateur, car il est affiché avec le code. Le port de
  connexion est découvert par mDNS (10 s), avec une saisie manuelle en repli (FR-002).
- La méthode PC (`127.0.0.1:5555` après `adb tcpip 5555`) déclenche une invite d'autorisation
  dans le casque pour la clé de l'appli ; elle ne survit pas au redémarrage. [C]

## R5 : Lister les jeux VR installés

**Décision** : deux sources, fusionnées par paquet, avec `QUERY_ALL_PACKAGES`.
1. `queryIntentActivities(ACTION_MAIN + com.oculus.intent.category.VR)`. C'est la source
   principale ; la catégorie est exigée par Meta pour les applis OpenXR. [S]
2. En repli, pour les anciens titres : les applis non système dont les meta-data d'application
   contiennent `com.samsung.android.vr.application.mode` ou `com.oculus.ossplash`. Leur point
   d'entrée est l'activité `LAUNCHER`, ou à défaut `INFO`. [S, LightningLauncher]

Dans les deux cas, on exclut :
- les paquets `FLAG_SYSTEM` ;
- les applis désactivées ;
- les applis panneau (qui exposent un service `com.oculus.vrshell.SHELL_MAIN`) ;
- OpenQuestTuner lui-même.

**Justification** : la catégorie VR seule rate des titres anciens (constat de LightningLauncher,
lanceur open source pour Quest). [S] `QUERY_ALL_PACKAGES` est nécessaire pour lire les meta-data
de toutes les applis. C'est acceptable pour une appli distribuée hors store ; une publication sur
le store Meta devra le justifier.

**Alternatives** :
- `<queries>` avec la seule catégorie VR : plus sobre, mais rate les anciens titres.
- `cmd package query-activities` via le shell : pas de filtre de visibilité, mais la liste ne
  s'afficherait qu'une fois connecté. Rejetée : la liste doit rester visible hors connexion.

## R6 : Lancer et arrêter un jeu

**Décision** : lancer et arrêter par le shell :
- `am force-stop --user current '<pkg>'`
- `am start --user current -n '<pkg>/<activity>'`

Voir [contracts/shell-commands.md](contracts/shell-commands.md) (C3, C4).

**Justification** :
- `am start -n` est la méthode documentée par Meta pour lancer une appli immersive. [C]
- Lancer depuis l'appli avec un intent est fragile sur Horizon OS : LightningLauncher a dû
  changer de méthode en v71, puis à nouveau en juillet 2026. [S] Le shell évite ce chemin.
- `--user current` prévient les soucis avec les comptes secondaires (correctifs mentionnés dans
  le journal de QGO). [I]
- Les propriétés `debug.oculus.*` ne survivent pas au redémarrage. [S, doc Meta]

## R7 : Réinitialiser une propriété

**Décision** : `setprop <clé> ''` (chaîne vide) pour revenir au « Par défaut du jeu ».

**Justification** :
- C'est la méthode officielle de Meta : `setprop debug.oculus.refreshRate ''` rétablit « le
  comportement par défaut ». [S, doc officielle 2026]
- L'outil `setprop` d'Android 14 accepte une valeur vide ; il exige seulement 2 arguments. [C,
  source AOSP]
- SideQuest (« Set by App (Default) » pour CPU/GPU) et QLoader (« Auto ») font de même. [S]
- Le service `shell:` d'adbd passe la commande à `sh -c`, donc les quotes `''` arrivent bien
  comme argument vide. Le piège connu, où l'argument vide disparaît, ne concerne que
  `adb shell setprop X ''` tapé dans un bash local. [I]
- Un redémarrage efface aussi toutes ces propriétés. [S, doc Meta]

**Alternatives rejetées** :
- `0` ou `-1` (MBF-Tools, quest-toolkit-cli) : `0` est un vrai niveau (CPU/GPU au minimum,
  fovéal désactivé), donc on ne peut pas l'utiliser pour dire « par défaut ».
- Écrire la résolution par défaut du casque, comme le fait SideQuest : ce serait forcer une
  valeur au lieu de laisser le jeu décider.

## R8 : Persistance, DI, navigation

**Décision** :
- profils en JSON (kotlinx-serialization 1.9.0) dans `filesDir`, en écriture atomique ;
- SharedPreferences pour la dernière méthode de connexion ;
- DI manuelle (`AppContainer`) ;
- navigation par état dans le ViewModel.

**Justification** :
- Au plus quelques centaines de profils, lus en entier au démarrage : une base de données
  n'apporte rien.
- Le JSON prépare les futurs profils communautaires.
- Hilt, Room et navigation-compose ajouteraient KSP ou kapt et plusieurs dépendances pour 4
  écrans (principe V).
- kotlinx-serialization 1.9.0 correspond à Kotlin 2.2. [C, Maven Central]

**Alternatives** : Room (rejetée, KSP et schéma de migration inutiles), DataStore Proto (rejetée,
plus lourd que le JSON pour aucun gain), Hilt (rejetée, un seul graphe trivial).

## R9 : Hors périmètre, mais préparé

La reconnexion automatique après un redémarrage (méthode QGO, TheDroidGeek et shizuku4quest)
suit cette séquence [C] :
1. une fois connecté, l'appli se donne `WRITE_SECURE_SETTINGS` (`pm grant`) ;
2. au démarrage du casque, elle écrit `Settings.Global adb_wifi_enabled=1`
   (et `adb_allowed_connection_time=0`) ;
3. elle découvre le port par mDNS ;
4. elle se connecte avec la clé déjà appairée.

Le design du MVP n'empêche rien de tout ça : la clé est persistante et `ShellBackend` reste
inchangé. Ce sera une fonctionnalité à part (`/speckit-specify`), avec un amendement du contrat
des commandes (`pm grant`).

## Toolchain (vérifiée)

- Le couple AGP 8.13.2 / Kotlin 2.2.20 / Gradle 9.4.1 / compose-bom 2024.12.01 compile déjà
  sur la machine (projet messengerBot). [C]
- Tous les artefacts sont disponibles, vérifiés par HTTP 200 le 2026-09-23 : libadb-android
  3.1.1 et spake2-android 2.2.1 (JitPack), conscrypt-android 2.5.3, bcpkix/bcprov-jdk15to18
  1.81, kotlinx-serialization-json 1.9.0 et le plugin serialization 2.2.20. [C]
- Le dépôt JitPack est limité par un filtre de contenu à `com.github.MuntashirAkon*`, pour la
  sécurité de la chaîne d'approvisionnement et la vitesse de résolution.

## Sources

- S1 https://developers.meta.com/horizon/documentation/android-apps/port-an-existing-app/
- S3 https://developers.meta.com/horizon/resources/publish-mobile-manifest/
- S4 https://github.com/threethan/LightningLauncher
- S5 https://github.com/metalex201/shizuku4quest
- S6 https://github.com/RikkaApps/Shizuku/issues/2114
- S7 https://github.com/MuntashirAkon/AppManager/issues/1975
- S8 https://github.com/thedroidgeek/oculus-wireless-adb
- S9 https://developers.meta.com/horizon/documentation/unity/ts-systemproperties/
- S10 https://developers.meta.com/horizon/blog/developer-perspective-ue4-logging-and-console-commands-for-mobile-vr/
- S12 https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/adb/AdbDebuggingManager.java
- S13 https://github.com/MuntashirAkon/libadb-android/issues (#5, #19, #32, #34)
- S14 https://threethan.itch.io/quest-game-tuner
- S15 http://web.archive.org/web/20240315151749/https://anagan79.itch.io/quest-games-optimizer/devlog/542777/v700-is-the-magical-adb-update-weve-been-waiting-for
- S17 https://anagan79.itch.io/quest-games-optimizer/devlog/360224/changelog
- S18 https://developer.android.com/training/package-visibility/declaring
- S20 https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0
- S21 https://developers.meta.com/horizon/documentation/unreal/unreal-change-display-refresh-rate/
- S22 https://developers.meta.com/horizon/documentation/spatial-sdk/ts-systemproperties/
- S23 https://developers.meta.com/horizon/documentation/unity/os-cpu-gpu-levels/
- S24 https://developers.meta.com/horizon/documentation/unity/os-fixed-foveated-rendering/
- S25 https://developers.meta.com/horizon/documentation/native/android/os-render-scale/
- S26 https://android.googlesource.com/platform/system/core/+/refs/heads/android14-release/toolbox/setprop.cpp
- S27 https://github.com/skrimix/QLoader
- S28 https://github.com/threethan/Quest-Game-Tuner (dépôt sans code source ni licence : QGT n'est pas open source)
- S29 https://en.wikipedia.org/wiki/Meta_Horizon_OS_version_history (Android 14 / API 34 depuis v76)
- S30 https://github.com/MuntashirAkon/libadb-android/issues/34
