# Data Model : Fréquences d'affichage au-delà de 120 Hz

Complète le [data-model du MVP](../001-game-profiles-mvp/data-model.md). Seuls les ajouts et les
changements figurent ici. Les noms de code sont indicatifs ; les règles, elles, sont le contrat.

Rien de nouveau n'est persisté : le format des profils (`profiles.json`) ne change pas.

## Fréquences proposées (`RefreshRatePolicy`, cœur)

| Constante | Valeur | Rôle | Réf. |
|---|---|---|---|
| `STANDARD_MAX_RATE` | 120 | Au-delà, une fréquence est « élevée » | FR-003 |
| `HIGH_REFRESH_RATES` | 144, 160, 180, 200 | Liste fermée des fréquences élevées ; rien au-delà de 200 | FR-001, FR-002 |

**Fréquences disponibles** pour un modèle et un écran :

```text
available(model, declared) = model.refreshRates + HIGH_REFRESH_RATES.filter { it in declared }
```

- `declared` : fréquences déclarées par l'écran 0, en entiers (research.md R1). Ensemble vide si
  la lecture échoue.
- Les fréquences du modèle (72 à 120 sur Quest 3) ne sont pas filtrées par l'écran : rien ne
  change pour elles (SC-005).
- Casque non reconnu : même règle, avec les fréquences de repli du Quest 3.

**Statut expérimental** : `isExperimental(rate) = rate > STANDARD_MAX_RATE`, pour tous les
modèles (FR-003, research.md R7). Il s'ajoute au statut par propriété du MVP : la pastille du
titre « Fréquence d'affichage » reste celle de `QuestModel.verified`.

## Limite de résolution (cœur)

| Fréquence | Palier maximal (%) | Taille max. sur Quest 3 (défaut 1680×1760) |
|---|---|---|
| 144 Hz | 100 | 1680×1760 |
| 160 Hz | 100 | 1680×1760 |
| 180 Hz | 90 | 1512×1584 |
| 200 Hz | 80 | 1344×1408 |
| 120 Hz ou moins | aucune limite | — |

`maxResolutionStep(rate): Int?` renvoie le palier maximal, ou `null` s'il n'y a pas de limite.

**Règle de permission** (research.md R4) :

```text
allowed(rate, texture, default) =
    texture == null                                   // « Par défaut du jeu »
    || maxResolutionStep(rate) == null
    || (texture.width  <= forStep(default, max).width
        && texture.height <= forStep(default, max).height)
```

Elle vaut aussi pour une taille personnalisée (hors paliers).

**Paliers indisponibles** : pour l'interface, les paliers de `RESOLUTION_STEPS` dont la taille
n'est pas permise à la fréquence du profil (FR-005). Aucun si la fréquence est `null` ou de
120 Hz au plus (FR-007).

## Choisir une fréquence (`RefreshRatePolicy.selectRate`, cœur)

```text
selectRate(profile, rate, model) -> RateSelection(profile', loweredToStep: Int?)
```

| Situation | `profile'` | `loweredToStep` | Réf. |
|---|---|---|---|
| Fréquence élevée, résolution au-dessus du maximum (palier ou taille personnalisée) | fréquence changée, résolution = `forStep(défaut, max)` | le palier maximal | FR-006 |
| Fréquence élevée, résolution permise ou « Par défaut du jeu » | fréquence changée seulement | `null` | US2 sc. 3 et 5 |
| 120 Hz ou moins, ou « Par défaut du jeu » | fréquence changée seulement ; la résolution ne remonte pas | `null` | FR-007 |

L'interface affiche un message tant que `loweredToStep` n'est pas nul, jusqu'au changement
suivant du profil (SC-004).

## Validation d'un profil (`GameProfile.validateFor`, modifié)

```text
validateFor(model, declaredRates: Set<Int> = ∅) -> List<ProfileViolation>
ProfileViolation(property, value, reason = OUT_OF_RANGE)
```

| Raison | Condition | Propriété, valeur | Réf. |
|---|---|---|---|
| `OUT_OF_RANGE` | règles du MVP, inchangées ; fréquence ni du modèle ni de `HIGH_REFRESH_RATES` | celle du MVP | MVP FR-016, FR-025 |
| `RATE_NOT_DECLARED` | fréquence de `HIGH_REFRESH_RATES` absente de `declaredRates` | `REFRESH_RATE`, la fréquence | FR-008, cas limites |
| `ABOVE_RATE_LIMIT` | `allowed(rate, texture, default)` faux | `TEXTURE_WIDTH`, la largeur | FR-008, SC-003 |

`declaredRates` vide par défaut : un appel qui ne le fournit pas refuse toute fréquence élevée
(research.md R5). `Tuner.applyAndLaunch` le lit par `DisplayRates` juste avant de valider.

**Violation à expliquer en premier** (`List<ProfileViolation>.primary()`, cœur) : la première
de raison `RATE_NOT_DECLARED`, sinon la première de raison `ABOVE_RATE_LIMIT`, sinon `null`.
Avec `null`, l'interface garde le message du MVP, qui liste les réglages hors plage. L'interface
n'a donc aucune règle de priorité à elle (principe IV).

## Avertissements (`GameProfile.warnings`, modifié)

`ProfileWarning` gagne deux valeurs (FR-004) :
- `HIGH_REFRESH_RATE`, présent quand la fréquence du profil dépasse 120 Hz ;
- `HIGH_RATE_GAME_RESOLUTION`, présent en plus quand la résolution est aussi « Par défaut du
  jeu » : la résolution choisie par le jeu peut être trop élevée pour cette fréquence (US2
  scénario 5).

Les règles de `HEAT` et `SMOOTHNESS` ne changent pas.

## Fréquence réelle de l'écran (couche Android)

| Donnée | Type | Source | Mise à jour | Réf. |
|---|---|---|---|---|
| Fréquences déclarées | `Set<Int>` | `Display.supportedModes` de l'écran 0, arrondies | relues par le Tuner à chaque lancement, et par le ViewModel à l'ouverture de chaque profil (appel local, rapide) | FR-001, FR-008 |
| Fréquence réelle | `StateFlow<Int?>` | `Display.getRefreshRate()` de l'écran 0, arrondie ; `null` si illisible | `onDisplayChanged(0)`, plus une relecture toutes les 2 s tant qu'elle est affichée | FR-010, SC-006 |

Voir [contracts/display-rates.md](contracts/display-rates.md).

## Textes affichés (résumé)

| Où | Quand | Texte (FR) | Réf. |
|---|---|---|---|
| Puce de fréquence | fréquence élevée | pastille « Expérimental » | FR-003 |
| Aide de la fréquence | fréquence enregistrée non déclarée | « N Hz n'est pas disponible sur ce casque : choisis une autre fréquence. » | cas limites |
| Aide de la résolution | fréquence élevée avec limite | « À N Hz, la résolution est limitée à ×M. » | FR-005 |
| Sous la résolution | après un abaissement | « Résolution abaissée à ×M pour N Hz. » | FR-006 |
| Carte d'avertissements | `HIGH_REFRESH_RATE` | cadence du jeu, résolution ou GPU, chauffe et autonomie | FR-004 |
| Carte d'avertissements | `HIGH_RATE_GAME_RESOLUTION` | la résolution du jeu peut être trop élevée pour cette fréquence | FR-004, US2 sc. 5 |
| Rappel « réglages actifs » | fréquence élevée | tout le casque reste à cette fréquence, batterie | FR-009 |
| Diagnostic | toujours | « Fréquence de l'écran : N Hz » (ou « inconnue ») | FR-010 |
| Message au lancement | `RATE_NOT_DECLARED` | « N Hz n'est pas disponible sur ce casque : modifie le profil. » | FR-008 |
| Message au lancement | `ABOVE_RATE_LIMIT` | « Résolution trop élevée pour N Hz (×M au plus) : modifie le profil. » | FR-008 |
