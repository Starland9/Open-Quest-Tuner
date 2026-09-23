# Contrat : fichier de profils `profiles.json`

Le fichier est stocké dans le stockage privé de l'appli (`filesDir/profiles.json`) et ne quitte
jamais le casque dans cette version : l'import/export est hors périmètre. Son format est tout de
même versionné, pour préparer les futurs profils communautaires.

## Format (version 1)

```json
{
  "version": 1,
  "profiles": {
    "com.beatgames.beatsaber": {
      "refreshRate": 120,
      "eyeTexture": { "width": 2016, "height": 2112 },
      "cpuLevel": 4,
      "gpuLevel": 4,
      "foveationLevel": "MEDIUM",
      "dynamicFoveation": false
    },
    "com.example.othergame": {
      "refreshRate": 90
    }
  }
}
```

- Clé de `profiles` : identifiant de paquet du jeu (validé par `PACKAGE_REGEX`, voir
  [shell-commands.md](shell-commands.md)).
- Chaque champ est optionnel : un champ absent signifie « Par défaut du jeu ». Les valeurs par
  défaut ne sont pas écrites (`encodeDefaults = false`).
- `foveationLevel` ∈ `OFF`, `LOW`, `MEDIUM`, `HIGH`, `HIGH_TOP`. Le nom est stocké plutôt que le
  chiffre, pour la lisibilité et la robustesse.

## Règles de lecture

- Les champs inconnus sont ignorés (`ignoreUnknownKeys = true`), pour la compatibilité avec les
  versions futures.
- Une entrée dont la clé n'est pas un nom de paquet valide est ignorée.
- Un profil vide (tous les champs absents) est ignoré (FR-015).
- Un fichier illisible ou corrompu n'est jamais écrasé en silence. Il est renommé en
  `profiles.json.corrupt-<horodatage>`, et l'appli repart d'une liste vide.
- `version` supérieure à 1 : l'appli lit ce qu'elle comprend et n'écrase pas le fichier tant que
  l'utilisateur ne modifie pas un profil.
- Les valeurs sont **validées contre le modèle de casque au moment de l'application** (voir
  [data-model.md](../data-model.md)), pas au chargement. Un profil hors plage reste modifiable
  dans l'interface, mais ne peut pas être appliqué.

## Règles d'écriture

- Écriture atomique : on écrit dans `profiles.json.tmp`, puis on renomme sur `profiles.json`.
- Les écritures sont sérialisées par un `Mutex`, en JSON indenté.
- Enregistrer un profil vide supprime l'entrée (FR-015).
