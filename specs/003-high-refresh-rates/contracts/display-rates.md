# Contrat : `DisplayRates` et moniteur de l'écran

Interface du cœur (`core/DisplayRates.kt`), implémentée côté Android par
`display/DisplayMonitor.kt` et simulée dans les tests (`FakeDisplayRates`). Elle donne au cœur
les fréquences que l'écran déclare, sans shell ni connexion ([research.md](../research.md) R1).

## Opération du cœur

| Opération | Retour | Implémentation Android | Erreurs |
|---|---|---|---|
| `declaredRefreshRates()` | `Set<Int>` | `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY).supportedModes`, chaque `refreshRate` arrondi à l'entier le plus proche | écran introuvable ou exception : ensemble vide, donc aucune fréquence élevée proposée ni permise |

Appel synchrone et rapide : lecture locale, sans réseau ni shell.

Utilisateurs :
- `Tuner.applyAndLaunch` : validation juste avant les commandes (FR-008) ;
- l'écran profil, par le ViewModel : fréquences proposées (FR-001), relues à l'ouverture de
  chaque profil. Une mise à jour d'Horizon OS qui retire une fréquence se voit donc à l'ouverture
  suivante, et le Tuner la refuse de toute façon au lancement.

## Donnée exposée à l'interface (couche Android seulement)

| Propriété | Type | Implémentation | Réf. |
|---|---|---|---|
| `currentRefreshRate` | `StateFlow<Int?>` | `Display.getRefreshRate()` de l'écran 0, arrondie ; `null` si illisible. Écouteur `DisplayListener` (écran 0) et relecture toutes les 2 s, actifs seulement tant qu'un écran collecte le flux (`WhileSubscribed`), comme `ThermalMonitor`. | FR-010, SC-006 |

Le cœur n'en a pas besoin : elle ne sert qu'à l'affichage du diagnostic.

## Règles

- **Lecture seule** : aucune écriture de mode d'affichage. La fréquence ne change que par la
  propriété `debug.oculus.refreshRate`, avec la commande C1 existante, après validation.
- **Aucune nouvelle commande shell** : la liste fermée du
  [contrat du MVP](../../001-game-profiles-mvp/contracts/shell-commands.md) est inchangée. C1
  garde sa borne absolue de 60 à 240 Hz.
- **Toujours l'écran 0** : jamais `context.display`, qui dépend de la fenêtre et pourrait être un
  écran virtuel de panneau (research.md R1).
- **Aucune permission** ajoutée au manifeste.
