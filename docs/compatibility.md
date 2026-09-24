# Tableau de compatibilité des réglages

Ce tableau est exigé par la constitution (principe III). Une propriété ou une plage de valeurs
n'est présentée comme « vérifiée » dans l'appli qu'après un essai concluant sur un casque réel,
consigné ci-dessous. Tant que ce n'est pas fait, l'interface la marque « expérimental ».

Procédure d'essai : voir [specs/001-game-profiles-mvp/quickstart.md](../specs/001-game-profiles-mvp/quickstart.md),
section 4.

## Statut par propriété et par casque

Légende :
- ✅ vérifié ;
- ⚠️ essai partiel ou effet variable selon le jeu ;
- ❌ sans effet ou problème ;
- — non testé, ce qui équivaut à « expérimental ».

| Propriété | Quest 3 | Quest 3S | Quest 2 | Quest Pro | Source de la plage |
|---|---|---|---|---|---|
| `debug.oculus.refreshRate` | ✅ | — | — | — | doc Meta, fréquences (research R2) |
| `debug.oculus.textureWidth` / `textureHeight` | ✅ | — | — | — | doc Meta, render scale (R2) |
| `debug.oculus.cpuLevel` | ✅ | — | — | — | doc Meta, niveaux CPU/GPU (R2) |
| `debug.oculus.gpuLevel` | ✅ | — | — | — | doc Meta, niveaux CPU/GPU (R2) |
| `debug.oculus.foveation.level` | ✅ | — | — | — | doc Meta, FFR (R1) |
| `debug.oculus.foveation.dynamic` | — | — | — | — | doc Meta, FFR (R1) |
| Réinitialisation `setprop <clé> ''` | ✅ | — | — | — | doc Meta (R7) |

## Journal des essais

Une ligne par essai. Pour les versions récentes, noter la version d'Horizon OS au format 2.x
(`ro.vros.build.version`).

| Date | Casque | Horizon OS | Jeu de référence | Propriété = valeur | Effet observé | Outil de mesure | Résultat |
|---|---|---|---|---|---|---|---|
| 2026-09-23 | Quest 3 (`eureka`) | `ro.vros.build.version=207` (Android 14 / API 34, `UP1A.231005.007.A1`) | — | `debug.oculus.cpuLevel = 2` puis `''` | `setprop … ''` renvoie 0 et `getprop` renvoie une valeur vide. La clé reste listée avec `[]`, et `parseGetprop` l'ignore. | `adb shell` | ✅ contrat C2 confirmé |
| 2026-09-23 | Quest 3 | vros 207 | — | `am force-stop` / `am start` avec `--user current` | Option documentée par `am help` pour les deux commandes. `am start --user current -n …` lance bien OpenQuestTuner. | `adb shell` | ✅ contrats C3 et C4 (syntaxe) |
| 2026-09-23 | Quest 3 | vros 207 | — | Intent `ACTION_APPLICATION_DEVELOPMENT_SETTINGS` | Résolu par `com.oculus.vrshell/.intents.AndroidIntentsRelayActivity`. L'écran d'appairage direct (`ADB_WIRELESS_SETTINGS`) est introuvable. | `cmd package resolve-activity` | ⚠️ à confirmer visuellement (quickstart 1.1) |
| 2026-09-23 | Quest 3 | vros 207 | — | Propriété système préexistante | `debug.oculus.extraKickoffHeadroom = 0.25e-3`, définie par le système, hors des propriétés gérées. Elle apparaîtra dans le diagnostic. | `getprop` | ℹ️ information |
| 2026-09-23 | Quest 3 | vros 207 | — | Accès au débogage sans fil depuis le casque | Le bouton « Ouvrir les options développeur » ouvre les **Paramètres Quest** (relais vrshell), qui ne proposent que « Débogage USB » : aucune chaîne « débogage sans fil » dans l'APK `SettingsPanelApp`. Les options développeur Android sont désactivées : `development_settings_enabled` n'est pas défini, et le composant `com.android.settings/.Settings$DevelopmentSettingsDashboardActivity` est dans `disabledComponents`. `am start` répond « does not exist ». | `dumpsys package`, `aapt2 dump resources`, `am start` | ❌ appairage sans PC inaccessible par l'interface ; la première configuration exige un PC |
| 2026-09-23 | Quest 3 | vros 207 | — | Connexion « via PC » (`adb tcpip 5555` puis 127.0.0.1:5555) | L'invite d'autorisation apparaît dans le casque pour la clé « OpenQuestTuner ». Après « Toujours autoriser », la clé est enregistrée dans `/data/misc/adb/adb_keys`. Le premier probe a échoué (comportement proche de libadb #34) ; la nouvelle tentative de `ConnectionPolicy` a abouti. | logcat (adbd, appli), `/proc/net/tcp6` | ✅ scénario 1.5 |
| 2026-09-23 | Quest 3 | vros 207 | — | Activation du débogage sans fil par `settings put global adb_wifi_enabled 1` | Horizon OS affiche sa propre fenêtre système `com.oculus.os.vrusb/.WifiDebuggingAlertActivity` (« autoriser sur ce réseau »). Le réglage reste à 0 jusqu'à l'acceptation. Réseau « toujours autorisé » : les activations suivantes se font sans fenêtre. | logcat (WindowManager, vrshell) | ✅ chemin sans-fil utilisable sans les paramètres Android |
| 2026-09-23 | Quest 3 | vros 207 | — | Connexion TLS sans appairage | Découverte mDNS du port TLS (42911), poignée de main TLS 1.3 via Conscrypt. adbd accepte le certificat de l'appli car sa clé publique est dans `adb_keys` (« Matched auth_key… OpenQuestTuner »). **Aucun code d'appairage nécessaire** une fois la clé autorisée par la méthode PC. | logcat (adbd `adbwifi tls handshake`) | ✅ |
| 2026-09-23 | Quest 3 | vros 207 | — | Reconnexion automatique au lancement de l'appli | Via PC : socket en 0,6 s. Sans fil (mDNS + TLS) : socket en 0,74 s. L'état affiché est « Connecté (…) » sans action. | `am force-stop` / `am start`, uiautomator | ✅ scénario 1.3, SC-007 |
| 2026-09-23 | Quest 3 | vros 207 | — | Reconnexion quand le débogage sans fil est coupé | Après 10 s de découverte mDNS : « Déconnecté », sans message d'erreur. | uiautomator | ✅ scénario 1.4 |
| 2026-09-23 | Quest 3 | vros 207 | — | Déblocage des options développeur Android par le shell | `settings put global development_settings_enabled 1` passe ; `pm enable …DevelopmentSettingsDashboardActivity` est refusé (« Shell cannot change component state »). | `adb shell` | ❌ l'écran d'appairage d'Android reste inaccessible |
| 2026-09-23 | Quest 3 | vros 207 | — | État après redémarrage | `adb_wifi_enabled` revient à **0** (TLS coupé). `service.adb.tcp.port` reste à **5555** : le port classique survit au redémarrage (constaté une fois, à reconfirmer). `adb_allowed_connection_time=0` : les clés autorisées n'expirent pas. | `settings get`, `getprop`, mDNS côté PC (`_adb._tcp` sur 5555) | ℹ️ |
| 2026-09-23 | Quest 3 | vros 207 | — | Reconnexion au lancement juste après un redémarrage | 1er essai : l'appli est restée bloquée sur « Connexion… » après `adb client authorized`, parce qu'un appel libadb n'avait pas de délai maximal. Corrigé avec des délais interruptibles (probe 2 s, commande 15 s). La reconnexion essaie aussi l'autre méthode en repli (`reconnectAttempts`). 2e essai : mDNS en échec après 10 s, puis port 5555 connecté en 34 ms. | logs `OqtAdb` | ✅ après correctif |
| 2026-09-23 | Quest 3 | vros 207 | — | Bouton « Passer en sans fil » (C7 puis C8, puis mDNS et TLS) | Réseau déjà « toujours autorisé » : aucune fenêtre, `adb_wifi_enabled=1`, puis « Connecté (sans fil) » en 0,7 s, sans code. Préférence de connexion enregistrée : `WIRELESS`. | logs `OqtAdb`, retour utilisateur | ✅ scénario 1.2 (quickstart amendé) |
| 2026-09-23 | Quest 3 | vros 207 | — | Fermeture du port 5555 | Après `adb usb`, `service.adb.tcp.port` vaut toujours 5555 et adbd écoute toujours sur 5555, comme après un redémarrage. Hypothèse : réglage persistant d'Horizon OS, du type « ADB over Wi-Fi » de MQDH ; cause non déterminée. Le scénario 1.4 (aucune méthode disponible) n'est donc pas reproductible sur ce casque. | `adb usb`, `getprop`, `/proc/net/tcp6` | ℹ️ |
| 2026-09-23 | Quest 3 | vros 207 | — | Premier probe après connexion | Bloqué jusqu'au délai maximal à chaque lancement à froid de l'appli, puis réussi à la nouvelle tentative (proche de libadb #34). Délai du probe ramené de 5 s à 2 s : reconnexion en 2,1 s. | logs `OqtAdb` | ✅ contourné |
| 2026-09-23 | Quest 3 | vros 207 | — | Fenêtre « autoriser sur ce réseau » après une mise en veille | Le réseau était « toujours autorisé », mais la fenêtre `WifiDebuggingAlertActivity` est réapparue à la réactivation (hypothèse : changement de borne ou BSSID, la confiance étant liée au BSSID sous Android). Tant qu'elle est ouverte, c'est une fenêtre système exclusive : **aucune appli ne se lance** (`am start` sans effet). | `dumpsys activity`, `pidof` | ℹ️ l'appli doit expliquer d'accepter cette fenêtre (fait dans le texte d'aide de « Passer en sans fil ») |
| 2026-09-23 | Quest 3 | vros 207 | — | Nouveau passage des 3 reconnexions à froid | Sans fil actif : 0,27 s. Sans fil coupé : repli sur 5555 en 53 ms après 10 s de mDNS. Via PC : 42 ms. Aucun blocage du probe cette fois. | logs `OqtAdb` | ✅ |
| 2026-09-23 | Quest 3 | vros 207 | Beat Saber 1.44.3 | « Appliquer et lancer » : 120 Hz, ×1,5 (2520×2640), CPU 4, GPU 5, fovéal fixe Moyen (2), fovéal dynamique désactivé (0) | `getprop` montre les 7 valeurs attendues pendant la partie. 221 ms entre `Force stopping` et `START u0` pour la séquence complète (arrêt, 7 `setprop`, `am start`). | `getprop`, logcat ActivityManager | ✅ scénarios 2.3 et 2.4, SC-003, SC-004 |
| 2026-09-23 | Quest 3 | vros 207 | Beat Saber 1.44.3 | `debug.oculus.refreshRate = 120` | `FPS=120/120` : le second nombre est la fréquence de l'écran. | logcat `VrApi` (définitions : doc Meta « Logcat Stats Definitions ») | ✅ |
| 2026-09-23 | Quest 3 | vros 207 | Beat Saber 1.44.3 | `debug.oculus.textureWidth = 2520`, `textureHeight = 2640` | `SF=1.50` : rapport entre le framebuffer soumis et la taille recommandée. Cela confirme sur casque la base de 1680×1760 (2520 ÷ 1,5). `DpuScale=1.00`. | logcat `VrApi` | ✅ |
| 2026-09-23 | Quest 3 | vros 207 | Beat Saber 1.44.3 | `debug.oculus.cpuLevel = 4`, `gpuLevel = 5` | `CPU4/GPU=4/5`, horloges 1920/599 MHz. Les deux niveaux sont accordés ensemble, dans un jeu immersif (passthrough coupé). Température `Temp=60.0C` en début de partie. | logcat `VrApi` | ✅ |
| 2026-09-23 | Quest 3 | vros 207 | Beat Saber 1.44.3 | `debug.oculus.foveation.level = 2` | `Fov=2` : niveau de rendu fovéal fixe. | logcat `VrApi` | ✅ |
| 2026-09-23 | Quest 3 | vros 207 | Beat Saber 1.44.3 | `debug.oculus.foveation.dynamic = 0` | Aucun champ des statistiques `VrApi` ne rend compte du fovéal dynamique. Effet non observable avec cet outil. | logcat `VrApi` | — reste expérimental |
| 2026-09-23 | Quest 3 | vros 207 | Hunting VR (`com.woodcock.huntingVR`, Unity) | Profil : 120 Hz, ×1,5, CPU 4, GPU 5, fovéal fixe Très élevé (4), fovéal dynamique activé (1) | `getprop` montre les 7 valeurs du profil. 171 à 181 ms entre `Force stopping` et `START u0`. Après la partie, l'environnement Home (vrshell) tourne à `FPS=120/120` tant que la propriété est active. Reste à vérifier si c'est bien l'effet de la propriété ou le réglage système de fréquence, en comparant après réinitialisation. | `getprop`, logcat ActivityManager et `VrApi` | ✅ 2ᵉ jeu |
| 2026-09-23 | Quest 3 | vros 207 | — | « Lancer » d'un jeu sans profil (C2 × 7) | Confirmé dans le casque par l'utilisateur. Les journaux avaient déjà tourné au moment de la vérification. | retour utilisateur | ✅ scénario 2.6 |
| 2026-09-23 | Quest 3 | vros 207 | — | « Tout réinitialiser » | **Déconnexion** : connecté en TLS à 22:20:51, puis à 22:21:08 une commande reste sans réponse pendant les 15 s du délai et la connexion est déclarée perdue. De même, à 22:16:47, un « Appliquer et lancer » s'est arrêté après `Force stopping`, sans `START`. Côté adbd, rien d'anormal avant la fermeture par l'appli. Cause : réveils perdus dans libadb 3.1.1 (research.md R3). | logs `OqtAdb`, adbd, ActivityManager | ❌ corrigé par contournement, à revalider |
| 2026-09-23 | Quest 3 | vros 207 | — | « Tout réinitialiser » après correctif (lecture arrêtée au marqueur, 3 s × 3 essais) | Pas de déconnexion. `getprop` renvoie une valeur vide pour les 7 clés. Lancement suivant (Beat Saber, profil 72 Hz, ×0,7, CPU 0, GPU 0, fovéal 0) : 194 ms. | `getprop`, logcat, retour utilisateur | ✅ scénario 4.1 |
| 2026-09-23 | Quest 3 | vros 207 | — | Variante release (R8, signée avec la clé de debug) installée par-dessus la version debug | Installation acceptée (même signature), données conservées. Reconnexion sans fil TLS en 77 ms sans nouvelle autorisation, 35 jeux VR listés, aucun plantage. Appli non débogable (`run-as` n'est donc plus disponible pour inspecter ses fichiers). | logcat `OqtAdb`, `dumpsys package` | ✅ (génération de clé et appairage non couverts) |
| 2026-09-24 | Quest 3 | vros 207 | — (environnement Home, `com.oculus.vrshell`) | `debug.oculus.refreshRate = 144`, posée à la main (`adb shell setprop`), hors du catalogue de l'appli | `FPS=145/144` : l'écran tourne à 144 Hz, sans image périmée ni déchirure (`Stale=0`, `Tear=0`). Home est une charge légère (`App=0.66ms`, `SF=0.01`) : cet essai ne dit rien de la tenue en jeu. | logcat `VrApi` | ℹ️ accepté par l'écran (essai en jeu : ligne suivante) |
| 2026-09-24 | Quest 3 | vros 207 | Beat Saber 1.44.3 | `debug.oculus.refreshRate = 144` (à la main), aucun autre réglage : résolution, CPU/GPU et fovéal laissés au jeu | `FPS=x/144` pendant toute la partie : le jeu suit l'écran à 144 Hz, sans relance nécessaire. Sur la fenêtre enregistrée (27 s, 20:04:48–20:05:14) : moyenne de 141,7 images/s, minimum 120, 85 % des secondes à 143–145, 87 images périmées au total. Une lecture juste avant montrait des creux plus marqués (104 à 119 images/s, jusqu'à `Stale=48` en une seconde). Charge GPU de 57 à 88 %, niveaux choisis par le jeu `CPU4/GPU=4/3` à `4/5`, `Temp=41.0C`. | logcat `VrApi` | ✅ fréquence appliquée en jeu ; ⚠️ fluidité variable, des images sautent quand le jeu est chargé |
| 2026-09-24 | Quest 3 | vros 207 | — | Modes d'affichage déclarés par l'écran | 4128×2208 : chaque fréquence entière de 72 à **207 Hz**. 3104×1664 (mode réduit) : de 72 à **240 Hz**. Mode par défaut : 90 Hz. | `dumpsys display` (`supportedModes`) | ℹ️ confirme la note R1 du MVP (207 Hz et 240 Hz), restée jusque-là sans source |
| 2026-09-24 | Quest 3 | vros 207 | Beat Saber 1.44.3 | Balayage de `debug.oculus.refreshRate`, modifiée en cours de partie : 144, 160, 180, 200, 207 (environ 9 s mesurées par palier), autres réglages laissés au jeu | L'écran passe à chaque fréquence demandée, en 3 s au plus, sans relancer le jeu (`FPS=x/160`, `/180`, `/200`, `/207`). Le jeu, lui, plafonne vers 145 à 165 images/s : charge GPU de 93 à 98 %, `CPU4/GPU=4/4`. Moyennes : 141,9 images/s à 144 Hz ; 156,4 à 160 Hz ; 145,8 à 180 Hz ; 139,1 à 200 Hz (creux à 54) ; 159,2 à 207 Hz. Images périmées par palier : 20, 56, 348, 631, 224 (ce dernier sur 4 s seulement). Température de 43 à 45 °C, état thermique 0 (normal). | logcat `VrApi`, `dumpsys display`, `dumpsys thermalservice` | ✅ l'écran suit jusqu'à 207 Hz ; ⚠️ au-delà de 160 Hz, Beat Saber (réglages par défaut) ne suit plus et répète d'une image sur cinq (180 Hz) à près d'une sur trois (200 Hz) |

## Points à trancher lors des premiers essais

Ces points sont repris de la phase de recherche du MVP.

- La texture par œil est-elle lue seulement au lancement du jeu ? Pour l'instant c'est une
  supposition. L'appli relance le jeu de toute façon.
- `refreshRate` l'emporte-t-il sur une fréquence demandée par le jeu en cours de partie ?
- Les niveaux CPU/GPU sont-ils pris en compte en cours de partie, ou seulement au lancement ?
- Niveaux GPU 6 et 7 sur Quest 3, annoncés par Quest Game Tuner : non confirmés, donc non
  proposés.
- ~~`am force-stop` et `am start` avec `--user current` sont-ils acceptés sur Horizon OS 2.x ?~~
  Oui, sur Quest 3, vros 207 (journal du 2026-09-23).
