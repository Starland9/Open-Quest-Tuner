# Research : Fréquences d'affichage au-delà de 120 Hz

Recherche de la phase 0 du [plan](plan.md). Chaque point donne la décision, sa justification et
les alternatives écartées.

Niveau de preuve :
- **[C]** constaté sur le Quest 3 de test (vros 207) ;
- **[S]** tiré des sources ou de la documentation d'Android ;
- **[I]** inféré, à vérifier (voir la fin du document).

## R1 : Lire les fréquences que l'écran déclare, sans shell

**Faits** :
- L'écran physique du Quest 3 est l'écran logique 0 (« Écran intégré », 4128×2208). Il déclare
  chaque fréquence entière de 72 à 207 Hz à sa résolution native, et jusqu'à 240 Hz dans un mode
  à 3104×1664. Les valeurs sont des flottants proches de l'entier : `207.00003`, `72.00001`… [C]
  (`dumpsys display`, 2026-09-24, docs/compatibility.md)
- La fenêtre de l'appli est sur l'écran 0, en mode multi-window. Les autres panneaux système
  d'Horizon OS sont des écrans virtuels à 60 Hz. [C]
- `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY).supportedModes` renvoie la liste des modes
  de l'écran logique 0, sans permission. Un écran public comme l'écran 0 est lisible par toute
  appli. [S]

**Décision** : la couche Android lit les modes de l'écran 0 par `DisplayManager`, et en tire
l'ensemble des fréquences déclarées, arrondies à l'entier le plus proche. Le cœur ne voit qu'un
ensemble d'entiers, par l'interface `DisplayRates` ([contracts/display-rates.md](contracts/display-rates.md)).
Si la lecture échoue, l'ensemble est vide : aucune fréquence élevée n'est proposée (FR-001).

**Justification** : pas de shell, donc pas de connexion nécessaire pour afficher les choix d'un
profil, et aucune commande ajoutée à la liste fermée (principe I).

**Alternatives écartées** :
- `dumpsys display` par le shell : il faudrait une nouvelle commande, et une connexion pour
  simplement afficher un profil ;
- une liste fixe par modèle (`QuestModel`) : elle ne suivrait pas une mise à jour d'Horizon OS
  qui retire une fréquence, cas prévu par la spec ;
- `context.display` : pas disponible depuis le contexte de l'application, et lié à l'écran de la
  fenêtre, qui pourrait devenir un écran virtuel dans une future version d'Horizon OS.

## R2 : Fréquence réelle de l'écran, en direct

**Faits** (essai du 2026-09-24, Home, sans jeu) [C] :
- `setprop debug.oculus.refreshRate 160` : le mode de l'écran physique **et** celui de l'écran
  logique 0 passent de 72 à 160 Hz en ~1 s ;
- remise à vide : retour à 72 Hz ~12 s plus tard, le temps que le compositeur reprenne la main ;
- les deux vues (physique et logique) restent identiques à chaque relevé. C'est la vue logique
  que lisent les applis.

**Décision** : la couche Android expose la fréquence réelle comme un `StateFlow<Int?>`, sur le
modèle de `ThermalMonitor` :
- lecture de `Display.getRefreshRate()` de l'écran 0, arrondie à l'entier ;
- mise à jour sur `DisplayManager.DisplayListener.onDisplayChanged(0)` ;
- relecture toutes les 2 s tant qu'un écran l'affiche (`WhileSubscribed`), par sécurité.

**Justification** : lisible sans connexion (FR-010), et à jour en moins de 5 s (SC-006). La
relecture périodique couvre le cas où Horizon OS changerait de mode sans prévenir les écouteurs.
Elle ne coûte rien : une lecture locale toutes les 2 s, seulement écran ouvert. [I] L'appel de
`onDisplayChanged` à chaque changement de fréquence est à vérifier (quickstart 3.3).

**Alternatives écartées** :
- logcat `VrApi` (`FPS=x/y`) : illisible par une appli, et absent hors d'une appli VR ;
- `getprop debug.oculus.refreshRate` : c'est la valeur **demandée**, déjà affichée par le
  diagnostic, pas la fréquence réelle.

## R3 : Aucune nouvelle commande shell

**Faits** :
- C1 (`setProperty`) accepte déjà `debug.oculus.refreshRate` de 60 à 240, sa borne absolue de
  sécurité (`QuestProperty.REFRESH_RATE`). 144, 160, 180 et 200 y sont. [S] (code du MVP)
- La fréquence s'applique en cours de partie, en 3 s au plus. Aucun autre réglage système n'est
  nécessaire. [C] (docs/compatibility.md, 2026-09-24)

**Décision** : la liste fermée du shell ne change pas. Les fréquences élevées passent par C1,
après la validation du cœur (R5). La borne absolue reste 60..240.

**Justification** : principe I, liste fermée inchangée. La réinitialisation (C2), « Tout
réinitialiser » et l'effacement au redémarrage valent déjà pour ces valeurs.

**Alternative écartée** : baisser la borne absolue à 200. Elle protège le casque ; le plafond de
200 Hz est un choix produit, porté par la liste fermée des fréquences proposées (R4).

## R4 : Fréquences proposées et limites de résolution

**Décisions** :
- **Liste fermée** : `HIGH_REFRESH_RATES = [144, 160, 180, 200]`, dans le cœur. Une fréquence
  élevée n'est proposée que si l'écran la déclare (R1). Les fréquences du modèle (72 à 120 sur
  Quest 3) restent proposées comme aujourd'hui, sans filtre (SC-005).
- **Seuil** : « élevée » = au-delà de 120 Hz, la plus haute fréquence du MVP.
- **Limite de résolution** par fréquence élevée, en palier de `RESOLUTION_STEPS` (%) :
  144 → 100, 160 → 100, 180 → 90, 200 → 80. C'est la table de FR-005, calculée par
  √(160 ÷ fréquence), arrondie au palier inférieur (spec, Assumptions). Elle est écrite en
  constantes, pas calculée : c'est une liste fermée, revue après de nouveaux essais.
- **Comparaison** : une résolution est permise si ses deux dimensions sont inférieures ou égales
  à celles du palier maximal (`EyeTexture.forStep(défaut, max)`). La règle vaut ainsi aussi pour
  une taille personnalisée, qui ne correspond à aucun palier.
- **« Par défaut du jeu »** (texture `null`) reste permis à toute fréquence (spec, Assumptions).
- **Abaissement** (FR-006) : choisir une fréquence élevée alors que la résolution dépasse le
  maximum remplace la résolution par le palier maximal, et le signale. Repasser à 120 Hz ou
  moins ne change pas la résolution (FR-007).

**Justification** : tout est dans le cœur, testé en JVM (principe IV). La comparaison par
dimensions évite un cas particulier pour les tailles personnalisées.

**Alternatives écartées** :
- une limite exprimée en pixels par seconde : plus générale, mais moins lisible pour l'utilisateur
  que « ×0,8 à 200 Hz » ;
- une limite par modèle ou par jeu : hors périmètre (spec).

## R5 : Validation et refus au lancement

**Faits** : `GameProfile.validateFor(model)` renvoie des `ProfileViolation`. `Tuner` refuse de
lancer si la liste n'est pas vide, avant toute commande. « Lancer » depuis la liste passe par le
même chemin. [S] (code du MVP)

**Décisions** :
- `validateFor(model, declaredRates)` : le second paramètre vaut par défaut l'ensemble vide. Sans
  lui, une fréquence élevée est donc refusée : c'est le choix sûr. Les profils à 120 Hz ou moins
  ne sont pas concernés (SC-005).
- `ProfileViolation` gagne une raison :
  - `OUT_OF_RANGE` : cas du MVP ;
  - `RATE_NOT_DECLARED` : fréquence élevée que l'écran ne déclare pas ;
  - `ABOVE_RATE_LIMIT` : résolution au-dessus du maximum de la fréquence.
- `Tuner` reçoit `DisplayRates` et passe l'ensemble déclaré, lu au moment de « Appliquer et
  lancer ». Les deux nouvelles raisons ont chacune un message qui nomme le réglage à corriger
  (FR-008).
- Le choix du message à afficher, quand plusieurs violations se cumulent, est une règle du cœur
  (`primary()`, data-model.md), testée comme les autres.
- Seules les fréquences au-delà de 120 Hz dépendent de l'écran (spec, FR-008) : un écran
  illisible ne bloque aucun profil à 120 Hz ou moins.

**Justification** : le refus a lieu avant la première commande, comme aujourd'hui (SC-003). Un
profil modifié hors de l'appli, ou venu d'un autre casque, est couvert par le même contrôle.

## R6 : Interface

**Décisions** :
- `ChoiceRow` gagne deux paramètres facultatifs : les options marquées « expérimental » (pastille
  dans la puce) et les options indisponibles (puce désactivée). Les appels existants ne changent
  pas.
- Fréquence : chaque fréquence élevée porte la pastille « Expérimental » (FR-003), même si la
  fréquence est « vérifiée » pour le modèle. Une fréquence enregistrée mais non déclarée reste
  affichée, sélectionnée et désactivée, avec la raison, sur le modèle de la taille personnalisée
  du MVP.
- Résolution : les paliers au-dessus du maximum sont désactivés, et le texte d'aide dit
  « limitée à ×0,9 à 180 Hz » (FR-005). Après un abaissement, un message reste affiché sous la
  résolution jusqu'au changement suivant (FR-006, SC-004). Un message dans la page tient mieux
  dans le casque qu'une snackbar qui disparaît.
- Avertissement FR-004 : nouvel avertissement `HIGH_REFRESH_RATE`, dans la carte existante,
  complété par `HIGH_RATE_GAME_RESOLUTION` seulement si la résolution est laissée au jeu.
- Rappel FR-009 : une phrase s'ajoute au rappel « les réglages restent actifs » quand la
  fréquence du profil est élevée.
- Diagnostic (FR-010) : « Fréquence de l'écran : N Hz », toujours visible, comme l'état
  thermique. La valeur demandée reste dans la liste des propriétés actives.

## R7 : Statut « expérimental »

**Décision** : `RefreshRatePolicy.isExperimental(rate)` vaut vrai pour toute fréquence au-delà
de 120 Hz, quel que soit le modèle (FR-003). Il n'y a pas de drapeau « vérifié » par fréquence :
le choix de l'utilisateur est de les garder toutes expérimentales, car c'est la tenue des jeux
qui reste incertaine. Une décision contraire passerait par le tableau de compatibilité et un
amendement de la spec.

## À vérifier sur casque

- `DisplayManager` renvoie depuis l'appli les mêmes modes que `dumpsys display`, et les
  fréquences 144 à 200 sont déclarées (quickstart 1.1). [I]
- `onDisplayChanged` est appelé quand la fréquence change (quickstart 3.3) ; sinon, la relecture
  toutes les 2 s suffit. [I]
- Chaque fréquence élevée atteinte en moins de 5 s après « Appliquer et lancer » (SC-001). Déjà
  vu en cours de partie (3 s au plus) ; à confirmer au lancement. [I]

## Sources

- docs/compatibility.md, journal du 2026-09-24 : modes déclarés, balayage de 144 à 207 Hz dans
  Beat Saber.
- Essai de recherche du 2026-09-24 (ce document, R1 et R2) : écran de la fenêtre de l'appli, mode
  de l'écran logique 0 à 72, 144 et 160 Hz, temps de passage et de retour.
- Android : `DisplayManager.getDisplay`, `Display.getSupportedModes`, `Display.getRefreshRate`,
  `DisplayManager.DisplayListener`.
