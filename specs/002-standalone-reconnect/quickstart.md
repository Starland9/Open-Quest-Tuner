# Quickstart : valider la reconnexion autonome sur Quest 3

Ce guide prouve de bout en bout que la fonctionnalité répond à la [spec](spec.md). Il fournit
aussi les essais à consigner dans `docs/compatibility.md` (constitution, principe III).

## Prérequis

- Le Quest 3 de test, en mode développeur, sur un Wi-Fi déjà autorisé pour le débogage sans fil,
  avec l'appli déjà autorisée par la méthode PC (clé dans `adb_keys`).
- Le PC Linux avec `adb`. Il sert à installer l'appli et à **observer** (logcat, `settings`,
  `dumpsys`), jamais à connecter l'appli pendant les essais.
- Pour US2 : un second réseau Wi-Fi, jamais autorisé pour le débogage (partage de connexion d'un
  téléphone, par exemple).

Rappel propre à ce casque : le port 5555 est resté ouvert après un redémarrage le 2026-09-23,
mais pas le 2026-09-24 (docs/compatibility.md). Pour prouver que la reconnexion passe par le
sans-fil, on vérifie que l'état affiché est **« Connecté (sans fil) »**, et non « via PC ». Les
journaux `OqtAdb` le confirment. Sur vros 207, la fenêtre « autoriser sur ce réseau »
réapparaît à chaque redémarrage : l'accepter fait partie de l'essai.

Le tampon `main` du casque ne garde que quelques minutes : lire les journaux juste après chaque
essai. Si l'annonce mDNS n'atteint pas le PC, trouver le port TLS par
`nmap -Pn -p 1024-65535 --open <ip>`, puis `adb connect <ip>:<port>`.

## 1. Build et tests (sans casque)

```bash
./gradlew test            # Tests JVM : AutoReconnectPolicy, C9/C10, gardes d'écriture et de paquet
./gradlew assembleDebug
```

Attendu : les deux commandes réussissent.

## 2. Installation par-dessus la version actuelle

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk   # garde la clé ADB et les profils
adb logcat -s OqtAdb:* &                                  # journaux de connexion, à garder ouverts
```

## 3. Scénarios de validation

Pour chaque scénario, noter ✅ ou ❌, avec la version d'Horizon OS (`getprop ro.vros.build.version`).

### US1 : se reconnecter sans PC après un redémarrage

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 1.1 | Appli connectée (via PC ou sans fil). Écran Connexion et outils → carte « Reconnexion autonome » → « Activer » → lire l'explication → « Activer ». | Statut « Active », badge « expérimental ». `adb shell dumpsys package io.github.openquesttuner \| grep WRITE_SECURE_SETTINGS` affiche `granted=true`. | FR-001, FR-002, FR-004, SC-003 |
| 1.2 | Redémarrer le casque. Via le PC : `adb shell settings get global adb_wifi_enabled` → `0`. Dans le casque, ouvrir l'appli sans rien toucher, chronomètre en main. | « Connecté (sans fil) » en moins de 15 s. Journaux : préparation, puis `Connexion WIRELESS … Connected`. Permission toujours `granted=true` après le redémarrage. | FR-005, SC-001 |
| 1.3 | Refaire 1.2 quatre fois (5 redémarrages en tout). | 5 sur 5. | SC-001 |
| 1.4 | Appli fermée, sans-fil encore actif (pas de redémarrage) : `am force-stop`, puis rouvrir l'appli. | Connexion directe : les journaux ne montrent aucune écriture du réglage. | US1 sc. 3 |
| 1.5 | Casque sur le second Wi-Fi, jamais autorisé. Redémarrer, puis ouvrir l'appli. | Pendant l'attente, l'appli affiche « Réactivation du débogage sans fil… » et explique qu'il faut accepter la fenêtre d'Horizon OS ; le badge affiche « Connexion… ». Après « Toujours autoriser » : « Connecté (sans fil) ». | FR-007, US1 sc. 4 |
| 1.6 | Désactiver l'option, se connecter via PC, puis « Passer en sans fil ». | Après le passage : la fenêtre propose d'activer l'option. « Plus tard » la laisse inactive. | FR-003, US1 sc. 5 |
| 1.7 | Option inactive : redémarrer, puis ouvrir l'appli. | Comportement du MVP. Via le PC, `adb_wifi_enabled` reste à `0` après l'ouverture. | US1 sc. 7, FR-008 |
| 1.8 | Option active : redémarrer le casque **sans ouvrir l'appli**, attendre 2 minutes. | `adb_wifi_enabled` reste à `0` : rien n'est réactivé au démarrage du casque. | FR-008 |
| 1.9 | Option active, appli connectée : « Se déconnecter ». Via le PC : `adb shell settings put global adb_wifi_enabled 0`. Dans la carte d'appairage, toucher « Se connecter », port vide. | L'appli réactive le débogage sans fil, puis passe à « Connecté (sans fil) ». | FR-005 |

### US2 : comprendre un échec

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 2.1 | Option active. Couper le Wi-Fi du casque, redémarrer, puis ouvrir l'appli. | « Déconnecté » avec l'invitation à se connecter au Wi-Fi, sans couleur d'erreur. Réactiver le Wi-Fi, l'appli ouverte : connecté moins de 15 s après l'arrivée du réseau, sans action. | FR-009, FR-010, SC-005 |
| 2.2 | Option active, second Wi-Fi : redémarrer, ouvrir l'appli, puis **refuser** la fenêtre réseau (ou attendre 60 s). | « Déconnecté », avec la marche à suivre pour réessayer. | FR-007, US2 sc. 2 |
| 2.3 | Option active. Via le PC : `adb shell pm revoke io.github.openquesttuner android.permission.WRITE_SECURE_SETTINGS`, puis redémarrer et ouvrir l'appli. | Statut « À réactiver ». Sur ce casque, l'appli se reconnecte via PC (port 5555) ; « Réactiver » la remet à « Active ». | US2 sc. 4, FR-011 |
| 2.4 | *(Facultatif : cet essai oblige à réautoriser le PC.)* Révoquer les autorisations de débogage depuis les paramètres du casque, si l'option existe, puis redémarrer et ouvrir l'appli. | Cause « autorisation plus reconnue » : refaire une fois l'étape PC. | US2 sc. 3 |

### US3 : désactiver

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 3.1 | Option active, appli connectée : « Désactiver ». | Confirmation, connexion conservée, et l'appli ne redémarre pas (research.md R5). `dumpsys package` : `granted=false`. | FR-013, FR-014 |
| 3.2 | Après 3.1 : redémarrer, puis ouvrir l'appli. | `adb_wifi_enabled` reste à `0`. | SC-006 |
| 3.3 | Option active, appli déconnectée (« Se déconnecter ») : « Désactiver ». | Message « la permission sera retirée à la prochaine connexion ». Se reconnecter : `granted=false`. | US3 sc. 2 |
| 3.4 | Option inactive. Via le PC : `adb shell pm grant io.github.openquesttuner android.permission.WRITE_SECURE_SETTINGS`. Activer l'option, puis la désactiver. | Journaux : aucune C9 à l'activation, aucune C10 à la désactivation. `granted=true` reste affiché, et l'appli dit que la permission, accordée hors d'elle, est laissée en place. | spec, cas limites ; constitution I (condition 3) |

### US4 : autorisations sans expiration

Ces essais modifient le délai d'expiration, qui s'applique aussi à la clé du PC : noter la valeur
d'origine et la remettre à la fin (`adb shell settings get global adb_allowed_connection_time`).

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 4.1 | Via le PC : `settings put global adb_allowed_connection_time 604800000`. Option inactive, appli connectée : « Activer ». | L'explication indique 7 jours, et propose « Ne jamais faire expirer… », non coché, avec ses quatre conséquences. | FR-002, FR-021, FR-022 |
| 4.2 | Cocher le choix, puis confirmer. | Option « Active », choix « Actif ». Via le PC : le délai vaut `0`. | US4 sc. 2, SC-009 |
| 4.3 | Décocher le choix dans la carte, puis confirmer. | Via le PC : le délai revaut `604800000`. | US4 sc. 4, SC-009 |
| 4.4 | Choix inactif. Via le PC : `settings delete global adb_allowed_connection_time` (casque sans délai fixé, donc 7 jours par défaut). Cocher le choix et confirmer : le délai vaut `0`. Puis « Se déconnecter » dans l'appli, et décocher. | L'appli annonce le rétablissement à la prochaine connexion. Se reconnecter : `settings get global adb_allowed_connection_time` renvoie `null`, c'est-à-dire que la clé a été supprimée (C13). | US4 sc. 5, FR-023 |
| 4.5 | Choix actif. Via le PC : `settings put global adb_allowed_connection_time 86400000`, puis désactiver l'option dans l'appli. | L'appli n'écrit rien : le délai reste à `86400000` (FR-023, valeur changée par un autre outil). | FR-023 |
| 4.6 | Délai d'origine à `0` (cas de ce casque). « Activer ». | Le choix n'est pas proposé. Délai inchangé. | FR-024, US4 sc. 6 |
| 4.8 | Via le PC : `settings put global adb_allowed_connection_time 400000000000` (plus de 3 650 jours). « Activer ». | Le choix n'est pas proposé, et le délai reste inchangé : il est hors de la plage que l'appli sait rétablir. **Ne pas essayer de valeur négative**, qui pourrait faire expirer toutes les clés. | FR-024 |
| 4.7 | *(Long : environ 2 h.)* Délai à `3600000` (1 h), option active **sans** le choix, dernière connexion de l'appli via PC. Utiliser le casque plus d'une heure sans le port 5555 ; redémarrer, puis ouvrir l'appli. Recommencer **avec** le choix. | Sans le choix : cause « autorisation plus reconnue » (noter l'exception, research.md R7). Avec le choix : « Connecté (sans fil) ». Réautoriser ensuite le PC si sa clé a expiré, et remettre la valeur d'origine. | SC-010 |

## 4. Aucun autre réglage modifié (SC-007)

*(Voir aussi la section 7 pour le suivi après livraison.)*

```bash
adb shell 'settings list global; settings list secure' | sort > avant.txt
# redémarrer le casque, ouvrir l'appli, attendre « Connecté (sans fil) »
adb shell 'settings list global; settings list secure' | sort > apres.txt
diff avant.txt apres.txt
```

Attendu : seule différence liée à l'appli, `adb_wifi_enabled`, plus `adb_allowed_connection_time`
si le choix de l'US4 est actif. D'autres clés changent seules
d'une session à l'autre (horodatages, compteurs) ; les comparer avec un redémarrage témoin, fait
sans ouvrir l'appli.

## 5. Contrôle réseau (FR-017)

Même contrôle que le [quickstart du MVP](../001-game-profiles-mvp/quickstart.md), section 5 :
pendant les scénarios 1.2 et 2.1, l'appli ne communique qu'avec le casque lui-même.

## 6. À consigner dans `docs/compatibility.md`

- Octroi de `WRITE_SECURE_SETTINGS` par le shell de l'appli (1.1).
- Persistance de la permission après un redémarrage (1.2).
- Réactivation par l'appli, puis connexion TLS, avec le temps mesuré (1.2 et 1.3).
- Comportement de la fenêtre réseau sur un nouveau Wi-Fi (1.5).
- Effet du retrait de la permission sur le processus de l'appli (3.1).
- Valeur de `adb_allowed_connection_time` (`adb shell settings get global adb_allowed_connection_time`)
  et texte d'expiration affiché dans l'explication (research.md R6).
- Exception levée quand adbd refuse une clé (2.4 ou 4.7, research.md R7).
- C11 à C13 acceptées par le shell, et effet du choix sur l'expiration (4.2 à 4.7).

Si 1.1 à 1.3 réussissent, passer `QuestModel.QUEST_3.autoReconnectVerified` à `true` : le badge
« expérimental » disparaît sur Quest 3 (FR-018).

## 7. Après la livraison (SC-002)

Pendant une semaine d'usage normal, avec au moins trois redémarrages du casque, noter chaque fois
que le PC a été nécessaire. Attendu : jamais. Sur un casque dont les autorisations expirent, le
choix de l'US4 doit être actif. Consigner le résultat dans `docs/compatibility.md`.
