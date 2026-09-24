# Research : Reconnexion autonome après un redémarrage

Complète la [recherche du MVP](../001-game-profiles-mvp/research.md), notamment R3 (client ADB),
R4 (connexion sans PC) et R9 (méthode préparée). Les faits viennent des sources d'Android 14
(`android14-release`), des dépôts cités et des essais consignés dans `docs/compatibility.md`.

Légende de confiance : [C] confirmé par la source ou par un essai, [S] probable, [I] inconnu, à
vérifier sur casque.

## R1 : Mécanisme retenu

**Décision** : l'appli s'accorde une fois `android.permission.WRITE_SECURE_SETTINGS` par son
propre shell ADB (C9, `pm grant`). Ensuite, à chaque démarrage de l'appli, elle écrit
`Settings.Global adb_wifi_enabled = 1` elle-même, par l'API Android. Puis elle se connecte en TLS
avec sa clé déjà autorisée : découverte mDNS `_adb-tls-connect._tcp`, puis 127.0.0.1.

**Faits** :
- Le niveau de protection de `WRITE_SECURE_SETTINGS` est `signature|privileged|development|role|installer`.
  `pm grant` accepte toute permission `development`, à condition que l'appli la déclare dans son
  manifeste. Sinon : `SecurityException … has not requested permission`. [C] S1, S2
- Écrire la valeur depuis l'appli a exactement le même effet que `settings put` depuis le shell.
  `AdbService.AdbSettingsObserver.onChange` ne regarde pas qui a écrit ; il appelle
  `setAdbEnabled(true, WIFI)`, qui envoie `MSG_ADBDWIFI_ENABLE` à `AdbDebuggingManager`. [C] S3, S4
- adbd accepte en TLS toute clé présente dans `/data/misc/adb/adb_keys`. C'est le fichier où
  « Toujours autoriser » inscrit la clé, d'où l'absence d'appairage. Une autorisation « une seule
  fois » n'y est pas écrite : elle ne suffirait pas. [C] S5, S6 ; constaté sur Quest 3
  (docs/compatibility.md, 2026-09-23)
- Trois projets font de même :
  - Shizuku v13.6.0 (`BootCompleteReceiver`) ;
  - shizuku4quest (`pm grant` par sa propre connexion ADB, puis `StartService`) ;
  - oculus-wireless-adb (interrupteur manuel, sans démarrage automatique). [C] S7, S8, S9

**Justification** : c'est le seul moyen, sans PC, de rallumer le débogage sans fil quand aucune
connexion shell n'existe plus. Il n'ajoute ni dépendance ni communication réseau.

**Alternatives écartées** :
- Garder le port 5555 ouvert avec `persist.adb.tcp.port` : interdit par le principe I, et ce port
  n'est pas chiffré.
- Envoyer le service `tcpip:5555` d'adbd par la connexion TLS, avant chaque redémarrage :
  impossible à prévoir, et ouvre aussi un port non chiffré.
- Dépendre de Shizuku : une appli tierce à installer et à configurer, contraire au principe V.
- Un récepteur `BOOT_COMPLETED` : voir R3.

## R2 : Lire l'état sans shell

**Décision** : `WirelessDebuggingSwitch` lit trois choses directement :
- `Settings.Global.ADB_ENABLED`, une constante publique ;
- `adb_wifi_enabled`, une clé cachée ;
- la permission, par `checkSelfPermission`.

**Faits** : `ADB_ENABLED`, `ADB_WIFI_ENABLED` et `ADB_ALLOWED_CONNECTION_TIME` sont annotés
`@Readable` dans `Settings.java` d'Android 14, sans `maxTargetSdk`.
`SettingsProvider.checkReadableAnnotation` ne refuse que les clés sans cette annotation. Une appli
qui cible l'API 34 peut donc les lire. [C] S10, S11

**Conséquence** : `isWirelessDebuggingEnabled()` renvoie un `Boolean`, sans cas « illisible ».
Une exception inattendue à la lecture compte comme « désactivé ». L'appli écrit alors le
réglage, puis attend, ce qui reste sans danger.

## R3 : Quand et à quelles conditions écrire

**Faits sur le comportement d'`AdbDebuggingManager`** [C] S4 :
- **Pas de Wi-Fi connecté** (`getCurrentWifiApInfo() == null`) : la valeur est aussitôt remise à 0.
- **Réseau non autorisé** : `verifyWifiNetwork` échoue. Le système affiche la fenêtre « autoriser
  le débogage sans fil sur ce réseau ? » et remet 0. L'acceptation (`MSG_ADBWIFI_ALLOW`) réécrit 1.
- **Réseau autorisé** : `persist.adb.tls_server.enable=1` est posé par le système (pas par
  l'appli). adbd démarre alors le serveur TLS et l'annonce mDNS.
- **Perte du Wi-Fi ou changement de point d'accès (BSSID)** : remise à 0. C'est ce qu'on a vu
  après une mise en veille, le 2026-09-23.
- **Réseaux autorisés et redémarrage** : ils ne survivaient pas au redémarrage sous Android 11 à
  12L ; c'est corrigé depuis Android 13 (change 2128832). [C] pour AOSP. **Pas sur Horizon OS**
  (Quest 3, vros 207, essai du 2026-09-24) : la fenêtre réapparaît à chaque redémarrage, même
  après « Toujours autoriser » sur la même borne, et `dumpsys adb` ne garde qu'une entrée
  `wifiAP`. Il reste donc un geste dans le casque après chaque redémarrage. [C] pour Horizon OS.
- **Délai avant la remise à 0** : la valeur écrite par l'appli reste lisible quelques
  millisecondes avant que le système ne la remette à 0 pour afficher sa fenêtre. [C] (essai du
  2026-09-24).

**Décisions** :
- Écrire seulement si le Wi-Fi est connecté. Sinon, renvoyer la cause `NO_WIFI` et attendre le
  réseau (R4).
- Après l'écriture, attendre un intervalle (1 s) avant la première relecture, pour ne pas prendre
  l'écriture de l'appli pour une acceptation. Puis sonder la valeur toutes les secondes, jusqu'à
  60 s en tout. On réutilise `ConnectionPolicy.awaitWirelessEnabled` du MVP : si la fenêtre
  réseau apparaît, la valeur repasse à 1 après l'acceptation.
- Écrire **au démarrage de l'appli seulement** (FR-008), jamais depuis un récepteur de démarrage
  du casque. Deux raisons :
  - la fenêtre réseau d'Horizon OS est exclusive : tant qu'elle est ouverte, aucune appli ne se
    lance (constaté), et l'utilisateur ne saurait pas d'où elle vient ;
  - l'auteur d'oculus-wireless-adb a renoncé au démarrage automatique pour cette raison (issue
    #2), même si le correctif d'Android 13 atténue le problème. [C] S9

## R4 : Attendre le Wi-Fi

**Décision** : `AutoReconnectController` s'abonne une fois, pour toute la vie du processus, à
`ConnectivityManager.registerNetworkCallback`, avec une requête `TRANSPORT_WIFI`. Quand un
réseau arrive (`onAvailable`), si la dernière cause est `NO_WIFI` et que l'appli est déconnectée,
il relance `reconnectLast()`. Un simple drapeau évite deux relances en parallèle.

**Justification** : la permission `ACCESS_NETWORK_STATE` est déjà déclarée. Cette API ne fait
aucune communication réseau. Son coût est nul quand rien ne change.

**Alternative écartée** : sonder le Wi-Fi à intervalle fixe, ce qui est plus lent et consomme
davantage.

## R5 : Accorder et retirer la permission (C9, C10)

**Faits** [C] S2 :
- L'octroi est écrit sur disque (`onInstallPermissionGranted` → `writeSettings`) : il **survit au
  redémarrage**.
- Il **survit à la mise à jour** (`install -r`) : `restorePermissionState` garde une permission
  `development` qui était accordée. La variante release, signée avec la clé de debug, s'installe
  par-dessus la debug : la permission est conservée.
- Il **disparaît à la désinstallation**.
- `pm revoke` d'une permission `development` **n'arrête pas** le processus : seul le chemin des
  permissions « runtime » le fait. La permission est immédiatement vue comme absente par
  `checkSelfPermission`.

**Décisions** :
- C9 et C10 n'ont aucun paramètre : le paquet est une constante du cœur, vérifiée par un test
  contre `applicationId` (principe I).
- Écrire les préférences **avant** C10 à la désactivation. Ce n'est plus nécessaire contre un
  arrêt du processus, mais cela garde l'état cohérent si la connexion tombe au milieu.
- Relire la permission après C9 : c'est elle qui fait foi (FR-004), pas le code de sortie seul.

## R6 : Expiration des autorisations de débogage

**Faits** [C] S4, S12 :
- `AdbDebuggingManager.filterOutOldKeys` retire de `adb_keys` les clés qui ne se sont pas
  connectées depuis plus de `adb_allowed_connection_time`. La valeur par défaut d'AOSP est
  604 800 000 ms, soit 7 jours ; `0` signifie « jamais ». Le mécanisme fonctionne depuis
  Android 13.
- **Une connexion TLS ne remet pas le compteur à zéro.** `MSG_WIFI_DEVICE_CONNECTED` ne fait
  qu'ajouter la clé à `mWifiConnectedKeys`. Seule l'authentification classique (USB ou port 5555,
  `MESSAGE_ADB_CONNECTED_KEY`) appelle `setLastConnectionTime`. C'est vrai d'Android 11 à la
  branche principale.
- Shizuku et shizuku4quest contournent ce mécanisme en écrivant `adb_allowed_connection_time=0`.
- Sur le Quest 3 de test, `adb_allowed_connection_time` vaut déjà **0**, les clés n'y expirent
  pas (docs/compatibility.md). Ce n'est **pas** le réglage d'usine : `dumpsys settings` attribue
  cette valeur à SideQuest (`pkg:quest.side.vr`). Sans SideQuest, un Quest garde donc
  probablement le défaut d'AOSP, 7 jours, et le choix de l'US4 y sera proposé. [C]

**Conséquence** : sur un casque à 7 jours, une appli qui ne se connecte qu'en sans fil perd son
autorisation au plus tard 7 jours après sa dernière connexion via PC ou port 5555. Il faut alors
refaire l'étape PC. La reconnexion autonome reste utile d'un redémarrage à l'autre, mais pas au-delà
d'une semaine.

**Décision** (prise par l'utilisateur le 2026-09-24 ; spec amendée : User Story 4, FR-021 à
FR-025) :
- l'appli **lit** le délai (`@Readable`, R2). Clé absente : délai par défaut du système, 7 jours ;
  `0` : jamais ;
- si le délai n'est pas nul, la fenêtre d'explication donne le nombre de jours, et propose un
  choix distinct, non coché : « Ne jamais faire expirer les autorisations de débogage » ;
- l'écriture passe **par le shell**, pendant la connexion : C11 met `0`, C12 rétablit une valeur,
  C13 supprime la clé pour revenir au délai par défaut. L'API Android n'est pas utilisée, et la
  règle « une seule écriture par l'API » (`adb_wifi_enabled`) reste vraie ;
- l'appli retient la valeur d'origine **avant** d'écrire, et ne rétablit que si le réglage vaut
  encore `0` ;
- C12 n'accepte qu'une valeur de 1 ms à 3 650 jours (principe I : « entiers bornés par des
  plages connues »). Hors de cette plage, par exemple pour une valeur négative, le choix n'est pas
  proposé : l'appli n'écrit jamais une valeur qu'elle ne saurait pas rétablir ;
- si l'autorisation a quand même expiré, la cause affichée est `AUTHORIZATION_LOST` (R7).

**Pourquoi le shell plutôt que l'API** : le choix se fait forcément pendant une connexion, qu'il
s'agisse d'activer l'option ou de changer le choix depuis la carte. Le shell dispose déjà de ce
droit. Le rétablissement hors connexion suit le même mécanisme « en attente » que le retrait de la
permission (C10). Pas besoin, donc, d'élargir la surface d'écriture de l'appli elle-même.

**Alternatives écartées** :
- écrire par l'API, avec la permission de R1 : cela ferait une seconde écriture système dans le
  code de l'appli, pour aucun gain ;
- cocher le choix d'office : c'est un compromis de sécurité qui touche la clé du PC. Il reste
  explicite (FR-021).

## R7 : Classer les causes d'échec

| Situation | Signal | Cause (`ReconnectIssue`) |
|---|---|---|
| Débogage désactivé | `adb_enabled` ≠ 1 | `DEBUGGING_DISABLED` |
| Pas de Wi-Fi | pas de réseau `TRANSPORT_WIFI` | `NO_WIFI` |
| Permission perdue, sans-fil coupé | `checkSelfPermission` refusé | `RIGHT_LOST` |
| Fenêtre refusée ou ignorée | valeur toujours à 0 après 60 s | `NETWORK_NOT_ALLOWED` |
| Clé refusée en TLS | `AdbPairingRequiredException` → `FailureReason.PAIRING_REQUIRED` | `AUTHORIZATION_LOST` |
| Service introuvable | `InterruptedException` pendant la découverte → `SERVICE_NOT_FOUND` | `WIRELESS_NOT_STARTED` |
| Autre | `IOException("Connection failed")`, etc. | `UNKNOWN` |

**Fait sur libadb-android 3.1.1** [C] S13 : `AdbPairingRequiredException` n'est levée que pour
une `SSLProtocolException` dont le message contient « protocol error ». Toute autre erreur TLS
devient `IOException("Connection failed")`. On ne sait pas encore laquelle des deux sort quand
adbd refuse une clé expirée. [I] Le quickstart (scénario 2.4) le dira. Si c'est la seconde, on
ajustera `ConnectionPolicy.classify` pour la connexion sans fil qui suit une préparation réussie.
En attendant, le message de `UNKNOWN` propose déjà de repasser par le PC.

## R8 : Statut « expérimental » et vérification

**Décision** : un champ `autoReconnectVerified` par modèle, dans `QuestModel`, faux au départ. La
validation du quickstart sur Quest 3 le passe à vrai pour ce modèle. Les autres modèles restent
« expérimental » tant qu'aucun essai n'est consigné (principe III).

**Justification** : le mécanisme ne dépend pas du modèle (c'est Android), mais Horizon OS peut
différer d'AOSP. On l'a vu avec l'écran d'appairage, absent du casque.

## À vérifier sur casque

- ~~`pm grant` de C9 accepté sur Horizon OS, et permission toujours accordée après un
  redémarrage (quickstart 1.1, 1.2).~~ Confirmé le 2026-09-24.
- ~~Réactivation et connexion en moins de 15 s après l'ouverture (1.2).~~ Confirmé : 5 sur 5,
  de 3,8 à 5,8 s.
- ~~Réseau « toujours autorisé » conservé après un redémarrage (1.2)~~ : non, la fenêtre
  réapparaît à chaque redémarrage (R3). Reste à voir : la fenêtre sur un nouveau réseau (1.5).
- Exception levée quand adbd refuse une clé (2.4). [I]
- Valeur d'usine de `adb_allowed_connection_time` sur d'autres casques (à demander dans l'issue).
  [I]
- C11 à C13 acceptées par le shell d'Horizon OS, et un délai court (1 h) qui retire bien la clé
  sans le choix, mais pas avec (quickstart, US4). [S]

## Sources

- S1 https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/res/AndroidManifest.xml (`WRITE_SECURE_SETTINGS`)
- S2 https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/pm/permission/PermissionManagerServiceImpl.java
- S3 https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/adb/AdbService.java
- S4 https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/adb/AdbDebuggingManager.java
- S5 https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/android14-release/daemon/auth.cpp
- S6 https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android14-release/libs/adbd_auth/adbd_auth.cpp
- S7 https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0 et https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/receiver/BootCompleteReceiver.kt
- S8 https://github.com/metalex201/shizuku4quest/blob/master/manager/src/main/java/moe/shizuku/manager/MainActivity.kt
- S9 https://github.com/thedroidgeek/oculus-wireless-adb (issues #2, #3, #6, #7)
- S10 https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/provider/Settings.java
- S11 https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java
- S12 https://r.android.com/2128832 (restauration des réseaux autorisés et filtrage des clés, Android 13)
- S13 https://github.com/MuntashirAkon/libadb-android/blob/3.1.1/libadb/src/main/java/io/github/muntashirakon/adb/AdbConnection.java
