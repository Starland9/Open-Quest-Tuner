# Data Model : Reconnexion autonome après un redémarrage

Complète le [data-model du MVP](../001-game-profiles-mvp/data-model.md). Seuls les ajouts et les
changements figurent ici. Les noms de code sont indicatifs ; les règles, elles, sont le contrat.

## Option persistée (`ConnectionPrefs`, couche Android)

Cinq clés s'ajoutent aux SharedPreferences privées `connection`. Le cœur y accède par l'interface
`AutoReconnectStore`, que `ConnectionPrefs` implémente et que les tests simulent en mémoire :

| Clé | Type | Défaut | Rôle | Réf. |
|---|---|---|---|---|
| `auto_reconnect` | booléen | `false` | Choix de l'utilisateur : option active ou non. | FR-012 |
| `right_granted_by_app` | booléen | `false` | C'est l'appli qui a accordé la permission : C9 l'a fait passer d'absente à détenue. C'est la seule situation où elle a le droit de la retirer (constitution, principe I, condition 3). | FR-013 |
| `revoke_pending` | booléen | `false` | L'option a été désactivée hors connexion alors que `right_granted_by_app` est vrai : il faut retirer la permission à la prochaine connexion. | FR-013 |
| `expiry_original` | chaîne ou absente | absente | Valeur d'origine du délai d'expiration, retenue par le choix « sans expiration » : `"default"` (clé absente sur le casque) ou un nombre de millisecondes. Absente : le choix n'a rien changé. | FR-023 |
| `expiry_restore_pending` | booléen | `false` | Le choix a été désactivé hors connexion : il faut rétablir le délai à la prochaine connexion. | FR-023 |

Ces valeurs survivent à la fermeture de l'appli, au redémarrage du casque et aux mises à jour.
Elles disparaissent à la désinstallation, en même temps que la permission et la clé ADB. Comme
le reste de `filesDir` et des préférences, elles sont exclues des sauvegardes
(`data_extraction_rules.xml`).

## `AutoReconnectStatus` : état affiché de l'option (cœur)

Il est dérivé à chaque lecture, jamais stocké :

| `auto_reconnect` | Permission détenue | Statut | Affichage (FR-011) |
|---|---|---|---|
| `false` | peu importe | `INACTIVE` | « Inactive » et bouton « Activer » (seulement si connecté) |
| `true` | oui | `ACTIVE` | « Active » et bouton « Désactiver » |
| `true` | non | `NEEDS_REACTIVATION` | « À réactiver », marche à suivre, bouton « Réactiver » (seulement si connecté) et « Désactiver » |

La permission est relue à chaque démarrage de l'appli, à chaque passage à l'état `Connected` et
au retour de l'écran Connexion au premier plan (`WirelessDebuggingSwitch.hasWriteRight()`).

Le badge « expérimental » s'affiche si `QuestModel.autoReconnectVerified` est faux pour le modèle
détecté (FR-018).

## `ReconnectIssue` : cause d'un échec de reconnexion autonome (cœur)

Elle n'est calculée que si l'option est `ACTIVE` ou `NEEDS_REACTIVATION`. Option inactive, le
comportement du MVP reste inchangé : « Déconnecté », sans cause.

| Valeur | Condition | Étape proposée à l'utilisateur | Réf. |
|---|---|---|---|
| `DEBUGGING_DISABLED` | Débogage désactivé sur le casque (`adb_enabled` ≠ 1) | Réactiver le mode développeur, puis rouvrir l'appli | spec, cas limites |
| `NO_WIFI` | Aucun réseau Wi-Fi connecté | Connecter le casque au Wi-Fi ; l'appli réessaie seule | FR-009, FR-010 |
| `RIGHT_LOST` | Débogage sans fil coupé et permission absente | Se reconnecter autrement (PC), puis « Réactiver » | FR-009, US2 sc. 4 |
| `NETWORK_NOT_ALLOWED` | Réglage écrit, mais toujours à 0 après 60 s : réseau non autorisé, ou casque qui n'active pas le débogage sans fil. Les deux cas sont indiscernables (research.md R3). | Si la fenêtre « autoriser sur ce réseau » est apparue : « Se reconnecter », puis l'accepter. Sinon : réessayer, ou passer par le PC. | FR-007, FR-009 |
| `AUTHORIZATION_LOST` | Sans-fil actif, mais le casque refuse la clé (`PAIRING_REQUIRED` ou `NOT_AUTHORIZED`) | Refaire une fois l'autorisation via PC | FR-009, US2 sc. 3 |
| `WIRELESS_NOT_STARTED` | Sans-fil actif, mais service de connexion introuvable (`SERVICE_NOT_FOUND`) | Réessayer, ou passer par le PC | FR-009 |
| `UNKNOWN` | Tout autre échec | Réessayer, ou passer par le PC | FR-009 |

Si plusieurs conditions sont vraies, la première du tableau l'emporte : c'est l'ordre des
vérifications de la préparation du sans-fil (section suivante). Une connexion réussie efface la
cause. Si le repli par le port 5555 échoue aussi, c'est la cause du sans-fil qui est affichée,
car c'est la plus utile.

Deux règles d'affichage et de relance sont dans le cœur, et donc testées (principe IV) :
- `AutoReconnectPolicy.visibleIssue(status, issue)` renvoie `null` si le statut est `INACTIVE`,
  sinon la cause ;
- `AutoReconnectPolicy.shouldRetryOnWifi(issue, connected)` renvoie vrai seulement si la cause est
  `NO_WIFI` et que l'appli n'est pas connectée (FR-010).

## Préparation du sans-fil (`AutoReconnectPolicy.prepareWireless`, cœur)

Elle est exécutée avant la première tentative sans fil, si le statut n'est pas `INACTIVE` :

1. débogage désactivé → `Issue(DEBUGGING_DISABLED)` ;
2. pas de Wi-Fi → `Issue(NO_WIFI)` ;
3. débogage sans fil déjà actif → `Ready` (la permission n'est pas nécessaire) ;
4. permission absente → `Issue(RIGHT_LOST)` ;
5. écriture de `adb_wifi_enabled = 1` ;
6. après un intervalle de sondage (la valeur écrite reste lisible un instant avant que le système
   ne la remette à 0 pour sa fenêtre, research.md R3), attente de la lecture `1`, par
   `ConnectionPolicy.awaitWirelessEnabled` (sondage toutes les secondes, 60 s en tout) → `Ready`,
   sinon `Issue(NETWORK_NOT_ALLOWED)`.

Le réglage est lisible par l'appli (research.md R2). Une exception inattendue à la lecture
compte comme « désactivé » : l'appli écrit alors, puis attend.

**Pendant la préparation** (FR-007), le contrôleur expose `preparing = true`. L'interface
affiche alors « Réactivation du débogage sans fil… », avec un indicateur d'activité. Elle
explique aussi d'accepter la fenêtre d'Horizon OS « autoriser sur ce réseau » si elle apparaît.
Le badge de connexion affiche « Connexion… », et non « Déconnecté ».

```text
PrepareResult = Ready | Issue(ReconnectIssue)
```

## Tentatives de reconnexion (`ConnectionPolicy.reconnectAttempts`, modifié)

`ReconnectAttempt` gagne un champ `prepareWireless: Boolean`. Il est vrai pour la première
tentative sans fil quand le statut n'est pas `INACTIVE`. L'ordre du MVP ne change pas (FR-006) :

| Dernière méthode | Option | Tentatives |
|---|---|---|
| `WIRELESS` | inactive | Discover → Port(dernier port saisi) → PC 5555 (MVP) |
| `WIRELESS` | active | **préparer**, puis Discover → Port(dernier port) → PC 5555 |
| `PC` | active | PC 5555 → **préparer**, puis Discover → Port(dernier port) |
| `null` | peu importe | aucune (jamais connecté) |

Si la préparation renvoie une cause, les tentatives sans fil suivantes sont sautées et on passe
au port 5555. `reconnectLast()` renvoie la cause retenue, ou `null` si la connexion a réussi.

**Connexion sans fil demandée par l'utilisateur** (FR-005) : « Se connecter » sans port, quand
le statut n'est pas `INACTIVE`, fait une seule tentative `Discover`. Elle est marquée
`prepareWireless` et n'est pas silencieuse : un échec de connexion passe à l'état `Failed`,
comme dans le MVP. Avec un port saisi, ou quand l'option est inactive, rien ne change.

## Cycle de vie de l'option

```text
            Activer + confirmer, connecté,
            C9 (si besoin) et permission relue
 INACTIVE ───────────────────────────────▶ ACTIVE
    ▲  │  C9 en échec ou permission absente        │ permission absente
    │  └──▶ reste INACTIVE + « indisponible »      ▼ (relue au démarrage)
    │                                      NEEDS_REACTIVATION
    │                                              │ Réactiver (connecté, C9)
    │                                              ▼
    │                                            ACTIVE
    │  Désactiver (depuis ACTIVE ou NEEDS_REACTIVATION)
    └──── auto_reconnect=false, revoke_pending=right_granted_by_app, puis :
          connecté et revoke_pending   : C10, revoke_pending=false, right_granted_by_app=false
          déconnecté et revoke_pending : C10 à la prochaine connexion
          right_granted_by_app faux    : permission laissée en place (accordée hors de l'appli)
```

Règles :
- **Ordre des écritures à la désactivation** : les préférences d'abord, C10 ensuite. Le retrait
  n'arrête pas l'appli (research.md R5), mais la connexion peut tomber entre les deux : le retrait
  en attente est alors rejoué ou constaté à la connexion suivante.
- **Retrait en attente** : à chaque passage à `Connected`, si `revoke_pending` est vrai, l'appli
  exécute C10 si elle détient encore la permission. Elle remet ensuite `revoke_pending` et
  `right_granted_by_app` à `false`.
- **Ne retirer que ce que l'appli a accordé** (constitution, principe I, condition 3). Si la
  permission est déjà détenue avant l'activation (accordée à la main depuis un PC, par exemple),
  C9 n'est pas envoyée et `right_granted_by_app` reste faux. À la désactivation, la permission est
  alors laissée en place, et l'appli le dit. Le même principe vaut pour le délai d'expiration
  (`expiry_original`, plus bas).
- **Vérification après l'octroi (FR-004)** : `auto_reconnect` ne passe à `true` que si la
  permission est effectivement détenue après C9. `right_granted_by_app` ne passe à `true` que si
  elle était absente avant C9.
- **Désactiver** ne coupe ni la connexion ni le débogage sans fil déjà actif (FR-014).

## Durée de vie de l'autorisation (FR-002, FR-021)

L'appli lit `WirelessDebuggingSwitch.authorizationLifetimeMs()`, c'est-à-dire
`adb_allowed_connection_time` (research.md R6) :

| Valeur lue | Délai effectif | Dans l'explication |
|---|---|---|
| `0` | jamais | rien, et le choix « sans expiration » n'est pas proposé (FR-024) |
| de 1 à `MAX_KEY_LIFETIME_MS` | cette valeur | « l'autorisation expire après N jours sans connexion via PC », puis le choix |
| clé absente (`null`) | défaut du système : 7 jours | idem, avec N = 7 |
| négative, non numérique, ou au-delà de `MAX_KEY_LIFETIME_MS` | hors plage | rien, et le choix n'est pas proposé (FR-024) : l'appli ne saurait pas rétablir cette valeur |

`MAX_KEY_LIFETIME_MS` = 315 360 000 000 ms, soit 3 650 jours. C'est la borne de C12
(constitution, principe I : « entiers bornés par des plages connues »).

N est arrondi au jour inférieur, avec un minimum de 1 (sous un jour, on affiche « moins d'un
jour »).

## `ExpiryChoiceStatus` : choix « autorisations sans expiration » (cœur)

Il est dérivé, jamais stocké :

| `expiry_original` | `expiry_restore_pending` | Délai lu | Statut |
|---|---|---|---|
| absente | `false` | `0`, ou hors plage | `NOT_APPLICABLE` : le casque n'expire déjà pas les autorisations, ou l'appli ne saurait pas rétablir la valeur |
| absente | `false` | `> 0` ou absent | `OFF` |
| présente | `false` | peu importe | `ON` |
| présente | `true` | peu importe | `RESTORE_PENDING` |

Le choix n'est affiché que si l'option de reconnexion autonome n'est pas `INACTIVE`, ou dans la
fenêtre d'activation.

Transitions (voir [contracts/shell-commands.md](contracts/shell-commands.md) pour les commandes) :

```text
 OFF ── cocher + confirmer, connecté : retenir la valeur d'origine, C11, relire 0 ──▶ ON
  ▲                                     (relue ≠ 0 : oublier la valeur, message d'échec, OFF)
  │
 ON ── décocher, ou désactiver l'option ─┬─ connecté   : si délai lu = 0, C12(valeur) ou C13
  │                                      │               (si "default"), puis oublier ──▶ OFF
  │                                      └─ déconnecté : expiry_restore_pending = true ─▶ RESTORE_PENDING
  │
 RESTORE_PENDING ── prochaine connexion : si délai lu = 0, C12 ou C13 ; puis oublier ─▶ OFF
```

Règles :
- **Retenir avant d'écrire** : `expiry_original` est enregistrée avant C11. Si la connexion tombe
  pendant C11, l'état reste `ON` et le rétablissement reste possible.
- **Ne rétablir que ce qu'on a changé** : si le délai lu ne vaut plus `0` au moment de rétablir,
  quelqu'un d'autre l'a modifié. L'appli n'écrit rien et oublie la valeur (FR-023).
- **Valeur retenue invalide** (non numérique, ou hors de 1 ms à 3 650 jours, après une
  modification manuelle des préférences par exemple) : l'appli n'écrit rien, oublie la valeur, et
  renvoie `FAILED`.
- **Désactiver l'option** désactive aussi ce choix, s'il est `ON`.
- **Désinstallation** : le délai reste à `0`, puisque l'appli ne peut plus le rétablir.
  L'explication le dit (FR-022).

## Résultats d'opérations (cœur)

```text
EnableResult       = ENABLED | UNAVAILABLE | NOT_CONNECTED
DisableResult      = REVOKED | REVOKE_PENDING | KEPT_EXTERNAL_GRANT
ExpiryChangeResult = APPLIED | RESTORE_PENDING | NOT_APPLICABLE | FAILED | NOT_CONNECTED
```

L'activation de l'option avec le choix coché enchaîne les deux opérations : d'abord
l'option (C9), puis le choix (C11). Si C11 échoue, l'option reste active et un message signale
que le choix n'a pas pu être appliqué.

- `UNAVAILABLE` : C9 a échoué, ou la permission reste absente. L'interface dit que l'option n'est
  pas disponible sur ce casque (FR-004).
- `REVOKE_PENDING` : l'option est coupée, et la permission sera retirée à la prochaine connexion
  (US3, scénario 2).
- `KEPT_EXTERNAL_GRANT` : l'option est coupée ; la permission, accordée hors de l'appli, est
  laissée en place. L'interface explique comment la retirer depuis un PC.

## Proposition après « Passer en sans fil » (FR-003)

Quand `switchToWireless()` renvoie `SWITCHED` et que le statut est `INACTIVE`, l'interface ouvre
la fenêtre d'explication de FR-002, avec « Activer » et « Plus tard ». Rien n'est persisté :
l'appli le proposera à nouveau au prochain passage en sans fil réussi.

## `QuestModel` (ajout)

| Champ | Type | Valeur initiale |
|---|---|---|
| `autoReconnectVerified` | booléen | `false` pour tous les modèles. `QUEST_3` passe à `true` après la validation du quickstart, consignée dans `docs/compatibility.md`. |
