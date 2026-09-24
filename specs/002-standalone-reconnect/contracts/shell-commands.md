# Contrat : commandes shell ajoutées (amendement)

Ce document amende la liste fermée du [contrat du MVP](../../001-game-profiles-mvp/contracts/shell-commands.md).
Tout le reste de ce contrat s'applique tel quel : type `ShellCommand` à constructeur privé,
marqueur de code de sortie, aucune clé `persist.`. À l'implémentation, le contrat du MVP reçoit
un renvoi vers ces deux lignes, pour que la liste reste complète en un seul endroit.

## Commandes

| # | Fabrique | Texte envoyé | Entrées admises | Succès si |
|---|---|---|---|---|
| C9 | `grantWriteSecureSettings()` | `pm grant 'io.github.openquesttuner' android.permission.WRITE_SECURE_SETTINGS` | aucune | permission relue comme détenue par l'appli (`WirelessDebuggingSwitch.hasWriteRight()`). La relecture fait foi, même si le code de sortie est perdu ou non nul : un octroi réel doit être retenu, pour que l'appli puisse le retirer. |
| C10 | `revokeWriteSecureSettings()` | `pm revoke 'io.github.openquesttuner' android.permission.WRITE_SECURE_SETTINGS` | aucune | code de sortie 0, ou permission relue comme absente |
| C11 | `disableAuthorizationExpiry()` | `settings put global adb_allowed_connection_time 0` | aucune | code de sortie 0 **et** délai relu à `0` (`authorizationLifetimeMs()`) |
| C12 | `restoreAuthorizationExpiry(ms)` | `settings put global adb_allowed_connection_time <ms>` | `ms` : entier `Long` de 1 à `MAX_KEY_LIFETIME_MS` (315 360 000 000, soit 3 650 jours), lu dans `expiry_original` | code de sortie 0 |
| C13 | `resetAuthorizationExpiry()` | `settings delete global adb_allowed_connection_time` | aucune | code de sortie 0. La clé absente redonne le délai par défaut du système (7 jours). |

### Règles

- **C9 et C10 n'ont aucun paramètre.** Le paquet est la constante `ShellCommand.APP_PACKAGE`,
  et la permission est une constante. L'appli ne peut ni accorder une permission à une autre
  appli, ni s'accorder une autre permission.
- **C11 à C13 ne visent qu'une clé**, `adb_allowed_connection_time`, écrite en constante. Le seul
  paramètre, `ms` de C12, est un entier borné formaté en décimal :
  `require(ms in 1..MAX_KEY_LIFETIME_MS)`, sinon `IllegalArgumentException` avant tout envoi.
  Une valeur d'origine hors de cette plage n'est jamais remplacée par `0` (FR-024) : l'appli
  n'écrit jamais une valeur qu'elle ne saurait pas rétablir. Aucune autre clé de `settings` n'est modifiable par
  le shell de l'appli. C7, du MVP, reste la seule autre écriture.
- **Garde de build** : un test JVM lit `app/build.gradle.kts` et vérifie que `applicationId` est
  égal à `APP_PACKAGE`. Un changement d'identifiant d'appli (ou un suffixe de variante) sans mise
  à jour de la constante fait échouer `./gradlew test`.
- **Idempotence** : accorder une permission déjà accordée, retirer une permission absente,
  réécrire la même valeur ou supprimer une clé absente ne change rien.
  `ConnectionPolicy.withRetries` peut donc rejouer C9 à C13 sans risque, comme C1 à C8.
- La quote simple autour du paquet est une défense en profondeur, comme pour C3 et C4.

## Séquences

### Activer la reconnexion autonome (FR-001 à FR-004, FR-016)

Seulement quand l'appli est `Connected`, quelle que soit la méthode, et après la confirmation de
l'utilisateur dans la fenêtre d'explication :
1. lecture de la permission (`hasWriteRight()`). Si elle est déjà détenue, on n'envoie pas C9,
   `right_granted_by_app` reste faux, et on passe à l'étape 4 ;
2. C9 ;
3. relecture de la permission. Si elle est détenue : `right_granted_by_app = true`. Sinon,
   résultat `UNAVAILABLE`, et l'option reste inactive ;
4. `auto_reconnect = true`, résultat `ENABLED`.

« Réactiver », depuis `NEEDS_REACTIVATION`, suit la même séquence.

### Désactiver (FR-013, FR-014)

1. `auto_reconnect = false`, et `revoke_pending = right_granted_by_app`, écrits **avant** C10 ;
2. si `right_granted_by_app` est faux : pas de C10, résultat `KEPT_EXTERNAL_GRANT`. La permission
   avait été accordée hors de l'appli, qui ne retire que ce qu'elle a accordé (constitution,
   principe I, condition 3) ;
3. sinon, si l'appli est `Connected` : C10, puis `revoke_pending = false` et
   `right_granted_by_app = false`, résultat `REVOKED` ;
4. sinon : résultat `REVOKE_PENDING`.

La connexion en cours n'est pas coupée, et le débogage sans fil n'est pas désactivé.

### Retrait en attente

À chaque passage à `Connected`, si `revoke_pending` est vrai :
- si la permission est encore détenue : C10 ;
- dans tous les cas : `revoke_pending = false` et `right_granted_by_app = false`.

### Ne jamais faire expirer les autorisations (FR-021, FR-024)

Seulement quand l'appli est `Connected`, après confirmation, et si le délai lu n'est ni `0` ni
hors plage (FR-024) :
1. `expiry_original` = délai lu, ou `"default"` si la clé est absente (écrit **avant** C11) ;
2. C11 ;
3. relecture : `0` → résultat `APPLIED` ; sinon, `expiry_original` est effacée et le résultat
   est `FAILED`.

### Rétablir le délai (FR-023)

Quand l'utilisateur décoche le choix, ou désactive l'option alors que le choix est actif :
- **connecté** : si le délai lu vaut `0`, C12(`expiry_original`) ou C13 (si `"default"`). Dans
  tous les cas, effacer ensuite `expiry_original`. Résultat `APPLIED` ;
- **déconnecté** : `expiry_restore_pending = true`, résultat `RESTORE_PENDING`. À la prochaine
  connexion, même traitement que « connecté », puis `expiry_restore_pending = false`.
- **C12 ou C13 refusée** (code de sortie non nul) : la valeur reste retenue,
  `expiry_restore_pending = true`, résultat `FAILED`. Un nouvel essai a lieu à la connexion
  suivante (C12 et C13 sont idempotentes).

Si le délai lu ne vaut plus `0`, un autre outil l'a changé : l'appli n'écrit rien (FR-023).

## Ce qui ne passe pas par le shell

La réactivation du débogage sans fil au démarrage ne passe **pas** par le shell, puisque le shell
n'est justement pas disponible à ce moment-là. Elle passe par l'écriture unique décrite dans
[wireless-switch.md](wireless-switch.md). Les commandes C7 et C8 du MVP restent utilisées par
« Passer en sans fil », qui part d'une connexion via PC.

## À vérifier sur casque (voir research.md)

- C9 : `pm grant` d'une permission `development` est accepté sur Horizon OS, et la permission
  survit au redémarrage (R1, R5).
- C10 : d'après AOSP, le retrait d'une permission `development` n'arrête pas le processus
  (R5). À confirmer au quickstart 3.1.
- C11 à C13 : acceptées par le shell d'Horizon OS, et prises en compte par l'expiration des clés
  (R6, quickstart US4).
