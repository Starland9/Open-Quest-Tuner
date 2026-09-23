# Contrat : commandes shell autorisées

Ce contrat est la **liste fermée** des commandes qu'OpenQuestTuner peut envoyer au shell du casque
(constitution, principe I ; spec FR-025, FR-026). Aucune autre commande n'est permise. Ajouter une
commande impose de modifier ce contrat, d'ajouter des tests et, pour une propriété, de l'inscrire
dans `docs/compatibility.md`.

## Garantie par les types

Les commandes sont des valeurs de type `ShellCommand`. Ce type n'a qu'un constructeur privé, et
ses seules fabriques sont listées ci-dessous (companion object, dans `core/ShellCommands.kt`).
`ShellBackend.exec` n'accepte qu'un `ShellCommand` : aucune chaîne libre ne peut donc atteindre le
shell, par construction.

## Commandes

| # | Fabrique | Texte envoyé | Entrées admises | Succès si |
|---|---|---|---|---|
| C1 | `setProperty(prop, value)` | `setprop <clé> <entier>` | `prop` ∈ `QuestProperty` (7 clés, voir [data-model.md](../data-model.md)). `value` : entier décimal, dans la plage de `prop` pour le modèle de casque détecté. | code de sortie 0 |
| C2 | `resetProperty(prop)` | `setprop <clé> ''` | `prop` ∈ `QuestProperty` | code de sortie 0 |
| C3 | `forceStop(pkg)` | `am force-stop --user current '<pkg>'` | `pkg` conforme à `PACKAGE_REGEX` | code de sortie 0 |
| C4 | `launch(pkg, activity)` | `am start --user current -n '<pkg>/<activity>'` | `pkg` conforme à `PACKAGE_REGEX`, `activity` conforme à `CLASS_REGEX` | code de sortie 0 et aucune ligne de sortie ne commence par `Error` |
| C5 | `readProperties()` | `getprop` | aucune | code de sortie 0 ; seules les lignes `[debug.oculus.*]: [...]` sont gardées |
| C6 | `probe()` | `true` | aucune | code de sortie 0. Sert à vérifier une connexion tout juste établie (contournement de libadb-android #34, voir research.md R3). |
| C7 | `enableWirelessDebugging()` | `settings put global adb_wifi_enabled 1` | aucune | code de sortie 0. Horizon OS peut alors afficher sa fenêtre « autoriser sur ce réseau » ; le réglage reste à 0 tant que l'utilisateur n'a pas accepté. Il revient à 0 à chaque redémarrage (constaté), donc FR-026 est respecté. |
| C8 | `readWirelessDebugging()` | `settings get global adb_wifi_enabled` | aucune | code de sortie 0. Le débogage sans fil est actif si la sortie, sans espaces, vaut `1`. |

Le dispositif du « code de sortie » est décrit dans la section suivante.

### Validation

- `PACKAGE_REGEX` = `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$`
- `CLASS_REGEX` = `^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$`
- Une entrée non conforme lève `IllegalArgumentException` **avant** toute exécution. Le
  `ShellCommand` n'est jamais construit.
- Les regex excluent déjà les quotes, les espaces et les métacaractères. Les quotes simples
  autour de `<pkg>` et `<activity>` sont une défense en profondeur : elles neutralisent
  notamment `$`, qu'on trouve dans les noms de classes internes.
- Aucune fabrique ne produit une clé commençant par `persist.`. Un test le vérifie sur toutes les
  commandes générables.

### Code de sortie

Le service `shell:` historique d'adbd ne transmet pas le code de sortie de la commande. Chaque
commande est donc envoyée avec un suffixe fixe :

```text
<commande>; echo __OQT_EXIT__:$?
```

`ShellOutput.parse` cherche la **dernière** ligne `__OQT_EXIT__:<n>`, en déduit le code, puis la
retire de la sortie. Sans marqueur (flux coupé), le code vaut `null` et le résultat est un échec.
Les `\r` sont retirés.

## Séquences

### Appliquer et lancer (FR-019, FR-020)

1. C3 `forceStop(pkg)`
2. Pour chacune des 7 `QuestProperty`, dans l'ordre de l'enum : C1 si le profil fixe une valeur,
   sinon C2.
3. C4 `launch(pkg, activity)`, seulement si toutes les étapes précédentes ont réussi.

La séquence s'arrête au premier échec. Le résultat indique l'étape fautive (la propriété, ou
`forceStop` / `launch`) et la sortie du shell.

Avant de commencer, le profil est validé contre le modèle de casque. Une valeur hors plage fait
échouer l'opération sans qu'aucune commande ne soit envoyée.

### Tout réinitialiser (FR-023)

C2 pour chacune des 7 `QuestProperty`. On tente toutes les propriétés même si l'une échoue, puis
on renvoie la liste des échecs.

### Passer en sans fil (FR-001)

Seulement quand l'appli est `Connected(PC)` :
1. C7 `enableWirelessDebugging()` ;
2. C8 `readWirelessDebugging()` toutes les secondes, jusqu'à lire `1` ou jusqu'à 60 s
   (`ConnectionPolicy.awaitWirelessEnabled`) ;
3. puis connexion sans fil : découverte mDNS et TLS avec la même clé, sans appairage.

Si l'utilisateur n'accepte pas dans les 60 s, l'appli reste `Connected(PC)`. Si la connexion
sans fil échoue, l'appli se reconnecte au port 5555 sans passer par l'état `Failed`.

### Diagnostic (FR-024)

C5, puis filtrage et tri par clé côté appli. Les propriétés à valeur vide sont considérées comme
inactives et ne sont pas affichées.

La même lecture sert à l'indicateur « actif sur le casque » de l'écran profil (FR-033,
amendement de l'US5). Aucune nouvelle commande n'est ajoutée pour cela. L'état thermique (FR-031)
ne passe pas par le shell : il est lu avec l'API Android (research.md R10).

## À vérifier sur casque (voir research.md)

- C2 : `setprop <clé> ''` est la méthode officielle de Meta (research.md R7). Reste à confirmer
  sur Quest 3 que `getprop` renvoie bien une valeur vide ensuite.
- C3 et C4 : l'option `--user current` doit être acceptée sur Horizon OS (research.md R6). Sinon,
  on la retire par un amendement de ce contrat.
- C4 : `am start` doit renvoyer un code non nul, ou une ligne `Error`, quand l'activité est
  introuvable.
