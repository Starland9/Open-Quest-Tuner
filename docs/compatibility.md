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
| `debug.oculus.refreshRate` | — | — | — | — | doc Meta, fréquences (research R2) |
| `debug.oculus.textureWidth` / `textureHeight` | — | — | — | — | doc Meta, render scale (R2) |
| `debug.oculus.cpuLevel` | — | — | — | — | doc Meta, niveaux CPU/GPU (R2) |
| `debug.oculus.gpuLevel` | — | — | — | — | doc Meta, niveaux CPU/GPU (R2) |
| `debug.oculus.foveation.level` | — | — | — | — | doc Meta, FFR (R1) |
| `debug.oculus.foveation.dynamic` | — | — | — | — | doc Meta, FFR (R1) |
| Réinitialisation `setprop <clé> ''` | — | — | — | — | doc Meta (R7) |

## Journal des essais

Une ligne par essai. Pour les versions récentes, noter la version d'Horizon OS au format 2.x
(`ro.vros.build.version`).

| Date | Casque | Horizon OS | Jeu de référence | Propriété = valeur | Effet observé | Outil de mesure | Résultat |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## Points à trancher lors des premiers essais

Ces points sont repris de la phase de recherche du MVP.

- La texture par œil est-elle lue seulement au lancement du jeu ? Pour l'instant c'est une
  supposition. L'appli relance le jeu de toute façon.
- `refreshRate` l'emporte-t-il sur une fréquence demandée par le jeu en cours de partie ?
- Les niveaux CPU/GPU sont-ils pris en compte en cours de partie, ou seulement au lancement ?
- Niveaux GPU 6 et 7 sur Quest 3, annoncés par Quest Game Tuner : non confirmés, donc non
  proposés.
- `am force-stop` et `am start` avec `--user current` sont-ils acceptés sur Horizon OS 2.x ?
